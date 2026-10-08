package com.antgskds.calendarassistant.feature.quickmemo.ui.contract

import com.antgskds.calendarassistant.feature.quickmemo.application.audio.AudioPlaybackState
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoEntity
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoReminderEntity
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoSuggestionEntity

/** null 显示全部，空串显示未分组；只保留在主界面会话，详情返回不重置。 */
data class QuickMemoBrowserState(
    val folderId: String? = null,
    val selectionMode: Boolean = false,
    val selectedIds: Set<Long> = emptySet(),
    val foldersVisible: Boolean = false,
) {
    fun select(id: Long) = copy(selectionMode = true, selectedIds = selectedIds + id)
    fun toggle(id: Long) = copy(selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id)
    fun cancelSelection() = copy(selectionMode = false, selectedIds = emptySet())
    fun filter(folderId: String?) = QuickMemoBrowserState(folderId = folderId)
}

/** New records stay outside the browser while edited; only meaningful edits allocate storedId. */
data class QuickMemoDraftState(
    val memo: QuickMemoEntity,
    val storedId: Long? = null,
)

data class QuickMemoListUiState(
    val memos: List<QuickMemoEntity>,
    val suggestions: List<QuickMemoSuggestionEntity>,
    val playbackState: AudioPlaybackState,
    val pinnedMemoId: Long?
)

data class QuickMemoDetailUiState(
    val memo: QuickMemoEntity?,
    val reminders: List<QuickMemoReminderEntity>,
    val suggestions: List<QuickMemoSuggestionEntity>,
    val playbackState: AudioPlaybackState,
    val isPinned: Boolean,
    val linkAnalysis: com.antgskds.calendarassistant.feature.linkanalysis.data.LinkAnalysisEntity? = null,
)

sealed interface QuickMemoUiAction {
    data class OpenDetail(val memoId: Long) : QuickMemoUiAction
    data class RequestDelete(val memo: QuickMemoEntity) : QuickMemoUiAction
    data class Delete(val memoId: Long) : QuickMemoUiAction
    data class ToggleTodoCompletion(val memoId: Long) : QuickMemoUiAction
    data class MarkTodo(val memoId: Long) : QuickMemoUiAction
    data class RemoveTodo(val memoId: Long) : QuickMemoUiAction
    data class TogglePinned(val memoId: Long, val isPinned: Boolean) : QuickMemoUiAction
    data class ToggleAudio(val audioPath: String?) : QuickMemoUiAction
    data class UpdateBody(val memoId: Long, val body: String) : QuickMemoUiAction
    data class UpdateTitle(val memoId: Long, val title: String) : QuickMemoUiAction
    data class SaveReminder(
        val memoId: Long,
        val reminderId: Long?,
        val reminderAt: Long?,
        val reminderRRule: String
    ) : QuickMemoUiAction
    data class DeleteReminder(val reminderId: Long) : QuickMemoUiAction
    data class AttachImage(
        val memoId: Long,
        val imagePath: String,
        val onResult: (Result<Unit>) -> Unit
    ) : QuickMemoUiAction
    data class RemoveImage(
        val memoId: Long,
        val onResult: (Result<Unit>) -> Unit
    ) : QuickMemoUiAction
    data class AttachVoice(
        val memoId: Long,
        val audioPath: String,
        val durationMs: Long,
        val onResult: (Result<Unit>) -> Unit
    ) : QuickMemoUiAction
    data class RetryTranscription(val memoId: Long) : QuickMemoUiAction
    data class AnalyzeLink(val memoId: Long) : QuickMemoUiAction
    data class CancelLinkAnalysis(val memoId: Long) : QuickMemoUiAction
    data class CreateSuggestionEvent(val suggestionId: Long) : QuickMemoUiAction
}
