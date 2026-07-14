package com.antgskds.calendarassistant.feature.note.ui.contract

import com.antgskds.calendarassistant.feature.note.domain.model.NoteDocument

data class NoteEditorUiState(
    val editorSessionKey: Int,
    val initialNoteId: Long? = null,
    val initialTitle: String = "",
    val initialDocument: NoteDocument = NoteDocument(),
    val initialCreatedAt: Long? = null,
    val initiallyPinned: Boolean = false,
    val hapticEnabled: Boolean = true,
    val backgroundEnabled: Boolean = false,
    val backgroundBlurEnabled: Boolean = false,
    val backgroundCardAlphaPercent: Int = 100,
    val predictiveBackEnabled: Boolean = true,
    val recognitionReady: Boolean = false,
    val recognitionMissingMessage: String = ""
)

enum class NoteEditorMessageKind {
    INFO,
    SUCCESS,
    ERROR
}

enum class NoteEditorExportFormat {
    DEFAULT,
    MARKDOWN
}

enum class NoteEditorAnalysisOutcome {
    SUCCESS,
    EMPTY,
    FAILURE
}

sealed interface NoteEditorUiAction {
    data object Dismiss : NoteEditorUiAction

    data class Save(
        val noteId: Long?,
        val title: String,
        val document: NoteDocument,
        val createdAt: Long?,
        val onSaved: (Long) -> Unit
    ) : NoteEditorUiAction

    data class Delete(
        val noteId: Long,
        val onDeleted: () -> Unit
    ) : NoteEditorUiAction

    data class SetPinned(val noteId: Long, val pinned: Boolean) : NoteEditorUiAction

    data class Export(
        val noteId: Long,
        val uri: String,
        val format: NoteEditorExportFormat,
        val onResult: (Result<Unit>) -> Unit
    ) : NoteEditorUiAction

    data class Import(
        val uri: String,
        val onResult: (Result<Long>) -> Unit
    ) : NoteEditorUiAction

    data class OpenImportedNote(val noteId: Long) : NoteEditorUiAction
    data class ToggleAudioAttachment(val path: String) : NoteEditorUiAction
    data class ShowMessage(val message: String, val kind: NoteEditorMessageKind) : NoteEditorUiAction

    data class AnalyzeText(
        val text: String,
        val onResult: (NoteEditorAnalysisOutcome) -> Unit
    ) : NoteEditorUiAction
}
