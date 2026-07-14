package com.antgskds.calendarassistant.platform.floating.ui.render

import androidx.compose.runtime.Composable
import com.antgskds.calendarassistant.ui.contract.FloatingScheduleUiActions
import com.antgskds.calendarassistant.ui.contract.FloatingScheduleUiState
import com.antgskds.calendarassistant.ui.floating.MaterialFloatingScheduleScreen

@Composable
fun FloatingScheduleScreen(state: FloatingScheduleUiState, actions: FloatingScheduleUiActions) {
    MaterialFloatingScheduleScreen(state = state, actions = actions)
}
