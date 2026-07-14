package com.antgskds.calendarassistant.feature.schedule.ui.render

import androidx.compose.runtime.Composable
import com.antgskds.calendarassistant.ui.contract.ScheduleColorUiAction
import com.antgskds.calendarassistant.ui.contract.ScheduleColorUiState
import com.antgskds.calendarassistant.ui.page_display.settings.MaterialScheduleColorScreen

@Composable
fun ScheduleColorScreen(state: ScheduleColorUiState, uiSize: Int = 2, onAction: (ScheduleColorUiAction) -> Unit) {
    MaterialScheduleColorScreen(state = state, uiSize = uiSize, onAction = onAction)
}
