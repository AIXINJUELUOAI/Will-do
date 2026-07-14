package com.antgskds.calendarassistant.feature.schedule.ui.render

import androidx.compose.runtime.Composable
import com.antgskds.calendarassistant.feature.schedule.ui.contract.TimeTableEditorUiAction
import com.antgskds.calendarassistant.feature.schedule.ui.contract.TimeTableEditorUiState
import com.antgskds.calendarassistant.ui.page_display.settings.MaterialTimeTableEditorScreen

@Composable
fun TimeTableEditorContent(state: TimeTableEditorUiState, uiSize: Int = 2, onAction: (TimeTableEditorUiAction) -> Unit) {
    MaterialTimeTableEditorScreen(state, uiSize, onAction)
}
