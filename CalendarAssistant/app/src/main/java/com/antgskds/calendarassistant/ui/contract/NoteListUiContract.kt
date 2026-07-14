package com.antgskds.calendarassistant.ui.contract

data class NoteListUiState(
    val items: List<NoteListItemUiModel> = emptyList(),
    val searchQuery: String = "",
    val pendingTaskCount: Int = 0,
    val hapticEnabled: Boolean = true
)

data class NoteListItemUiModel(
    val key: String,
    val title: String,
    val previewText: String? = null,
    val previewTasks: List<NoteTaskPreviewUiModel> = emptyList(),
    val remainingTaskCount: Int = 0,
    val updatedAt: Long,
    val pinned: Boolean = false,
    val allTodosCompleted: Boolean = false
)

data class NoteTaskPreviewUiModel(
    val paragraphId: String,
    val text: String,
    val checked: Boolean,
    val spans: List<NoteTextSpanUiModel> = emptyList()
)

data class NoteTextSpanUiModel(
    val start: Int,
    val end: Int,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strike: Boolean = false
)

sealed interface NoteListUiAction {
    data class OpenNote(val itemKey: String) : NoteListUiAction
    data class RequestDelete(val itemKey: String) : NoteListUiAction
    data class ToggleTodo(val itemKey: String, val paragraphId: String) : NoteListUiAction
}
