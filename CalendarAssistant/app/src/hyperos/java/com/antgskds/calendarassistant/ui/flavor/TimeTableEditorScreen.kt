package com.antgskds.calendarassistant.ui.flavor

import androidx.compose.runtime.Composable
import com.antgskds.calendarassistant.ui.contract.TimeTableEditorUiAction
import com.antgskds.calendarassistant.ui.contract.TimeTableEditorUiState
import com.antgskds.calendarassistant.ui.page_display.settings.MaterialTimeTableEditorScreen

@Composable
fun TimeTableEditorContent(state: TimeTableEditorUiState, uiSize: Int = 2, onAction: (TimeTableEditorUiAction) -> Unit) {
    MaterialTimeTableEditorScreen(state, uiSize, onAction)
}
