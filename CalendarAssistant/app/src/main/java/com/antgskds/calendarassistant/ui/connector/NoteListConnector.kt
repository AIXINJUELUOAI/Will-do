package com.antgskds.calendarassistant.ui.connector

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.core.note.NoteEntity
import com.antgskds.calendarassistant.feature.note.domain.model.NoteParagraphType
import com.antgskds.calendarassistant.feature.note.domain.model.plainTextContent
import com.antgskds.calendarassistant.ui.contract.NoteListItemUiModel
import com.antgskds.calendarassistant.ui.contract.NoteListUiAction
import com.antgskds.calendarassistant.ui.contract.NoteListUiState
import com.antgskds.calendarassistant.ui.contract.NoteTaskPreviewUiModel
import com.antgskds.calendarassistant.ui.contract.NoteTextSpanUiModel
import com.antgskds.calendarassistant.feature.note.ui.render.NoteListScreen
import com.antgskds.calendarassistant.ui.viewmodel.MainViewModel

internal data class NoteListConnection(
    val state: NoteListUiState,
    val notesByKey: Map<String, NoteEntity>
)

@Composable
fun NoteListRoute(
    viewModel: MainViewModel,
    searchQuery: String = "",
    extraBottomPadding: Dp = 0.dp,
    onEditNote: (NoteEntity) -> Unit = {},
    onRequestDeleteNote: (NoteEntity) -> Unit = {},
    hapticEnabled: Boolean = true
) {
    val notes by viewModel.notes.collectAsState()
    val connection = remember(notes, searchQuery, hapticEnabled) {
        buildNoteListConnection(notes, searchQuery, hapticEnabled)
    }

    NoteListScreen(
        state = connection.state,
        extraBottomPadding = extraBottomPadding,
        onAction = { action ->
            when (action) {
                is NoteListUiAction.OpenNote -> {
                    connection.notesByKey[action.itemKey]?.let(onEditNote)
                }

                is NoteListUiAction.RequestDelete -> {
                    connection.notesByKey[action.itemKey]?.let(onRequestDeleteNote)
                }

                is NoteListUiAction.ToggleTodo -> {
                    connection.notesByKey[action.itemKey]?.id?.let { noteId ->
                        viewModel.toggleNoteTodo(noteId, action.paragraphId)
                    }
                }
            }
        }
    )
}

internal fun buildNoteListConnection(
    notes: List<NoteEntity>,
    searchQuery: String,
    hapticEnabled: Boolean
): NoteListConnection {
    val filteredNotes = notes.filter { note ->
        searchQuery.isBlank() ||
            note.document().searchableText(note.title).contains(searchQuery, ignoreCase = true)
    }
    val keyedNotes = filteredNotes.mapIndexed { index, note -> noteUiKey(note, index) to note }
    val items = keyedNotes.map { (key, note) -> note.toNoteListItemUiModel(key) }
    val pendingTaskCount = filteredNotes.sumOf { it.document().pendingTodoCount() }

    return NoteListConnection(
        state = NoteListUiState(
            items = items,
            searchQuery = searchQuery,
            pendingTaskCount = pendingTaskCount,
            hapticEnabled = hapticEnabled
        ),
        notesByKey = keyedNotes.toMap()
    )
}

private fun noteUiKey(note: NoteEntity, index: Int): String =
    note.id?.let { "note:$it" }
        ?: "transient:${note.createdAt}:${note.updatedAt}:$index"

private fun NoteEntity.toNoteListItemUiModel(key: String): NoteListItemUiModel {
    val document = document()
    val tasks = document.paragraphs.filter { it.type == NoteParagraphType.TODO }
    val previewTasks = tasks.take(3).map { paragraph ->
        NoteTaskPreviewUiModel(
            paragraphId = paragraph.id,
            text = paragraph.text,
            checked = paragraph.checked,
            spans = paragraph.spans.map { span ->
                NoteTextSpanUiModel(
                    start = span.start,
                    end = span.end,
                    bold = span.bold,
                    italic = span.italic,
                    underline = span.underline,
                    strike = span.strike
                )
            }
        )
    }

    return NoteListItemUiModel(
        key = key,
        title = displayTitle,
        previewText = buildNotePreview(document.paragraphs),
        previewTasks = previewTasks,
        remainingTaskCount = (tasks.size - previewTasks.size).coerceAtLeast(0),
        updatedAt = updatedAt,
        pinned = pinnedAt != null,
        allTodosCompleted = document.allTodosCompleted()
    )
}

private fun buildNotePreview(paragraphs: List<com.antgskds.calendarassistant.feature.note.domain.model.NoteParagraph>): String? {
    val summary = paragraphs
        .asSequence()
        .filterNot { it.type == NoteParagraphType.TODO }
        .map { it.plainTextContent().trim() }
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .replace(Regex("\\s+"), " ")
        .trim()

    return summary.takeIf { it.isNotBlank() }
}
