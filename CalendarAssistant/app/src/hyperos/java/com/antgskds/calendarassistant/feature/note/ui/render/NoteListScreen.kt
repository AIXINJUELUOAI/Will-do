package com.antgskds.calendarassistant.feature.note.ui.render

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import com.antgskds.calendarassistant.ui.contract.NoteListUiAction
import com.antgskds.calendarassistant.ui.contract.NoteListUiState
import com.antgskds.calendarassistant.ui.page_display.MaterialNoteListScreen

@Composable
fun NoteListScreen(
    state: NoteListUiState,
    extraBottomPadding: Dp,
    onAction: (NoteListUiAction) -> Unit
) {
    MaterialNoteListScreen(
        state = state,
        extraBottomPadding = extraBottomPadding,
        onAction = onAction
    )
}
