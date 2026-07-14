package com.antgskds.calendarassistant.feature.quickmemo.ui.connector

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.core.quickmemo.QuickMemoEntity
import com.antgskds.calendarassistant.core.quickmemo.QuickMemoSuggestionStatus
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.data.state.CapsuleType
import com.antgskds.calendarassistant.data.state.CapsuleUiState
import com.antgskds.calendarassistant.feature.quickmemo.ui.contract.QuickMemoDetailUiState
import com.antgskds.calendarassistant.feature.quickmemo.ui.contract.QuickMemoListUiState
import com.antgskds.calendarassistant.feature.quickmemo.ui.contract.QuickMemoUiAction
import com.antgskds.calendarassistant.feature.quickmemo.ui.render.QuickMemoDetailScreen
import com.antgskds.calendarassistant.feature.quickmemo.ui.render.QuickMemoScreen
import com.antgskds.calendarassistant.ui.viewmodel.MainViewModel

private const val TEXT_QUICK_MEMO_ID_PREFIX = "TEXT_QUICK_MEMO_"

@Composable
fun QuickMemoPage(
    viewModel: MainViewModel,
    searchQuery: String = "",
    uiSize: Int = 2,
    extraBottomPadding: Dp = 0.dp,
    onOpenDetail: (Long) -> Unit = {},
    onPendingDeleteChange: (QuickMemoEntity?) -> Unit = {},
    hapticEnabled: Boolean = true
) {
    val quickMemos by viewModel.quickMemos.collectAsState()
    val suggestions by viewModel.quickMemoSuggestions.collectAsState()
    val playbackState by viewModel.audioPlaybackState.collectAsState()
    val capsuleUiState by viewModel.capsuleUiState.collectAsState()
    val context = LocalContext.current
    val state = remember(quickMemos, suggestions, playbackState, capsuleUiState) {
        QuickMemoListUiState(
            memos = quickMemos,
            suggestions = suggestions,
            playbackState = playbackState,
            pinnedMemoId = activeTextQuickMemoId(capsuleUiState)
        )
    }

    QuickMemoScreen(
        state = state,
        searchQuery = searchQuery,
        uiSize = uiSize,
        extraBottomPadding = extraBottomPadding,
        hapticEnabled = hapticEnabled,
        onAction = { action ->
            handleQuickMemoAction(
                action = action,
                viewModel = viewModel,
                context = context,
                onOpenDetail = onOpenDetail,
                onPendingDeleteChange = onPendingDeleteChange
            )
        }
    )
}

@Composable
fun QuickMemoDetailPage(
    memoId: Long,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    uiSize: Int = 2,
    hapticEnabled: Boolean = true,
    backgroundMode: Boolean = false,
    miuiBlurEnabled: Boolean = false,
    cardAlphaPercent: Int = MySettings.APP_BACKGROUND_CARD_ALPHA_DEFAULT_PERCENT
) {
    val quickMemos by viewModel.quickMemos.collectAsState()
    val suggestions by viewModel.quickMemoSuggestions.collectAsState()
    val playbackState by viewModel.audioPlaybackState.collectAsState()
    val capsuleUiState by viewModel.capsuleUiState.collectAsState()
    val context = LocalContext.current
    val state = remember(memoId, quickMemos, suggestions, playbackState, capsuleUiState) {
        val memo = quickMemos.firstOrNull { it.id == memoId }
        QuickMemoDetailUiState(
            memo = memo,
            suggestions = suggestions.filter {
                it.quickMemoId == memoId &&
                    (it.status == QuickMemoSuggestionStatus.PENDING ||
                        it.status == QuickMemoSuggestionStatus.CREATED)
            },
            playbackState = playbackState,
            isPinned = memo?.id?.let { it == activeTextQuickMemoId(capsuleUiState) } == true
        )
    }

    QuickMemoDetailScreen(
        state = state,
        onBack = onBack,
        uiSize = uiSize,
        hapticEnabled = hapticEnabled,
        backgroundMode = backgroundMode,
        miuiBlurEnabled = miuiBlurEnabled,
        cardAlphaPercent = cardAlphaPercent,
        onAction = { action ->
            handleQuickMemoAction(
                action = action,
                viewModel = viewModel,
                context = context,
                onOpenDetail = {},
                onPendingDeleteChange = {}
            )
        }
    )
}

private fun handleQuickMemoAction(
    action: QuickMemoUiAction,
    viewModel: MainViewModel,
    context: Context,
    onOpenDetail: (Long) -> Unit,
    onPendingDeleteChange: (QuickMemoEntity?) -> Unit
) {
    when (action) {
        is QuickMemoUiAction.OpenDetail -> onOpenDetail(action.memoId)
        is QuickMemoUiAction.RequestDelete -> onPendingDeleteChange(action.memo)
        is QuickMemoUiAction.ToggleTodoCompletion -> viewModel.toggleQuickMemoTodoCompletion(action.memoId)
        is QuickMemoUiAction.MarkTodo -> viewModel.markQuickMemoTodo(action.memoId)
        is QuickMemoUiAction.RemoveTodo -> viewModel.removeQuickMemoTodo(action.memoId)
        is QuickMemoUiAction.ToggleAudio -> viewModel.toggleAudioPlayback(action.audioPath)
        is QuickMemoUiAction.UpdateBody -> viewModel.updateQuickMemoBody(action.memoId, action.body)
        is QuickMemoUiAction.AttachImage ->
            viewModel.attachImageToQuickMemo(action.memoId, action.imagePath, action.onResult)
        is QuickMemoUiAction.RemoveImage ->
            viewModel.removeImageFromQuickMemo(action.memoId, action.onResult)
        is QuickMemoUiAction.AttachVoice ->
            viewModel.attachVoiceToQuickMemo(
                action.memoId,
                action.audioPath,
                action.durationMs,
                action.onResult
            )
        is QuickMemoUiAction.RetryTranscription -> viewModel.retryQuickMemoTranscription(action.memoId)
        is QuickMemoUiAction.CreateSuggestionEvent ->
            viewModel.createEventFromQuickMemoSuggestion(action.suggestionId)
        is QuickMemoUiAction.TogglePinned -> {
            if (action.isPinned) {
                viewModel.clearPinnedQuickMemo(action.memoId) { result ->
                    result
                        .onSuccess { Toast.makeText(context, "已移除挂起", Toast.LENGTH_SHORT).show() }
                        .onFailure {
                            Toast.makeText(context, it.message ?: "移除挂起失败", Toast.LENGTH_SHORT).show()
                        }
                }
            } else {
                viewModel.pinQuickMemo(action.memoId) { result ->
                    result
                        .onSuccess { Toast.makeText(context, "已挂起到胶囊", Toast.LENGTH_SHORT).show() }
                        .onFailure { Toast.makeText(context, it.message ?: "挂起失败", Toast.LENGTH_SHORT).show() }
                }
            }
        }
    }
}

private fun activeTextQuickMemoId(state: CapsuleUiState): Long? {
    val active = state as? CapsuleUiState.Active ?: return null
    return active.capsules.firstOrNull { it.type == CapsuleType.TEXT_QUICK_MEMO }
        ?.id
        ?.removePrefix(TEXT_QUICK_MEMO_ID_PREFIX)
        ?.toLongOrNull()
}
