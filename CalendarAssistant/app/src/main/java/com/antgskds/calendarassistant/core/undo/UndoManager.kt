package com.antgskds.calendarassistant.core.undo

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Undo 窗口管理器。
 *
 * 工作流程：
 * 1. 用户执行操作（完成、删除等），调用方立即写入数据库并刷新 UI
 * 2. 操作进入 Undo 窗口，等待 [COMMIT_DELAY_MS]
 * 3. 用户在窗口内按「撤销」→ 执行 rollbackAction 恢复旧数据
 * 4. 窗口到期 → 执行 commitAction 确认/清理 pending 状态
 *
 * 同一时间只有一个 pending 操作（新操作会立即提交旧操作）。
 */
class UndoManager(private val scope: CoroutineScope) {

    companion object {
        const val COMMIT_DELAY_MS = 60_000L  // 1 分钟
    }

    /**
     * 一个可撤销的操作。
     *
     * @param id 操作唯一标识
     * @param label 操作描述（供 UI 显示 Snackbar）
     * @param commitAction 窗口到期或被新操作替换时执行的确认/清理操作
     * @param rollbackAction 撤销时执行的数据恢复操作
     */
    data class PendingAction(
        val id: String,
        val label: String,
        val commitAction: suspend () -> Unit,
        val rollbackAction: suspend () -> Unit
    )

    private var pendingJob: Job? = null
    private var pendingAction: PendingAction? = null

    private val _currentPending = MutableStateFlow<PendingAction?>(null)

    /**
     * 当前待撤销的操作，UI 监听此 Flow 显示 Undo Snackbar。
     */
    val currentPending: StateFlow<PendingAction?> = _currentPending.asStateFlow()

    /**
     * 提交一个延迟操作。
     *
     * 如果当前有未提交的操作，会先立即提交它（避免丢失）。
     */
    fun submit(action: PendingAction) {
        // 先提交上一个 pending
        commitNowIfPending()

        pendingAction = action
        _currentPending.value = action

        pendingJob = scope.launch {
            delay(COMMIT_DELAY_MS)
            commitInternal(action)
        }
    }

    /**
     * 撤销当前 pending 操作。
     *
     * @return true 如果成功撤销
     */
    fun undo(): Boolean {
        val action = pendingAction ?: return false
        pendingJob?.cancel()
        pendingJob = null
        pendingAction = null
        _currentPending.value = null

        scope.launch {
            action.rollbackAction()
        }
        return true
    }

    /**
     * 撤销当前 pending，并在调用协程内等待 rollbackAction 完成。
     */
    suspend fun undoNow(): Boolean {
        val action = pendingAction ?: return false
        pendingJob?.cancel()
        pendingJob = null
        pendingAction = null
        _currentPending.value = null

        action.rollbackAction()
        return true
    }

    /**
     * 立即提交当前 pending 操作（不等延迟）。
     * 适用于：页面离开、新操作进来等场景。
     */
    fun commitNowIfPending() {
        val action = pendingAction ?: return
        pendingJob?.cancel()
        pendingJob = null
        pendingAction = null
        _currentPending.value = null

        scope.launch {
            action.commitAction()
        }
    }

    private fun commitInternal(action: PendingAction) {
        if (pendingAction?.id != action.id) return // 已被替换或取消
        pendingAction = null
        _currentPending.value = null

        scope.launch {
            action.commitAction()
        }
    }
}
