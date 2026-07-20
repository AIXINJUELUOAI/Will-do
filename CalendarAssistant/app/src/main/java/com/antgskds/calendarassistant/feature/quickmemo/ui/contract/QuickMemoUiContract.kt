package com.antgskds.calendarassistant.feature.quickmemo.ui.contract

import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoEntity
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoSuggestionEntity
import com.antgskds.calendarassistant.feature.quickmemo.application.audio.AudioPlaybackState

data class QuickMemoListUiState(
    val memos: List<QuickMemoEntity>,
    val suggestions: List<QuickMemoSuggestionEntity>,
    val playbackState: AudioPlaybackState,
    val pinnedMemoId: Long?
)

data class QuickMemoDetailUiState(
    val memo: QuickMemoEntity?,
    val suggestions: List<QuickMemoSuggestionEntity>,
    val playbackState: AudioPlaybackState,
    val isPinned: Boolean
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
    data class CreateSuggestionEvent(val suggestionId: Long) : QuickMemoUiAction
}
