package com.antgskds.calendarassistant.ui.flavor

import androidx.compose.runtime.Composable
import com.antgskds.calendarassistant.ui.contract.ScheduleSettingsUiAction
import com.antgskds.calendarassistant.ui.contract.ScheduleSettingsUiState
import com.antgskds.calendarassistant.ui.page_display.settings.MaterialScheduleSettingsScreen

@Composable
fun ScheduleSettingsScreen(state: ScheduleSettingsUiState, uiSize: Int = 2, onAction: (ScheduleSettingsUiAction) -> Unit) {
    MaterialScheduleSettingsScreen(state = state, uiSize = uiSize, onAction = onAction)
}
