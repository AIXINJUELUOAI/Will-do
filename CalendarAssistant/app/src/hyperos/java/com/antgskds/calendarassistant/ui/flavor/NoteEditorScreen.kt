package com.antgskds.calendarassistant.ui.flavor

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.antgskds.calendarassistant.ui.contract.NoteEditorUiAction
import com.antgskds.calendarassistant.ui.contract.NoteEditorUiState
import com.antgskds.calendarassistant.ui.page_display.MaterialNoteEditorScreen

@Composable
fun NoteEditorScreen(
    state: NoteEditorUiState,
    onAction: (NoteEditorUiAction) -> Unit,
    modifier: Modifier = Modifier
) {
    MaterialNoteEditorScreen(state = state, onAction = onAction, modifier = modifier)
}
