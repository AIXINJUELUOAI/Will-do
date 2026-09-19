package com.antgskds.calendarassistant.platform.floating.ui.render

import androidx.compose.runtime.Composable
import com.antgskds.calendarassistant.platform.floating.ui.contract.FloatingScheduleUiActions
import com.antgskds.calendarassistant.platform.floating.ui.contract.FloatingScheduleUiState
import com.antgskds.calendarassistant.platform.floating.ui.render.material.MaterialFloatingScheduleScreen

@Composable
fun FloatingScheduleScreen(state: FloatingScheduleUiState, actions: FloatingScheduleUiActions) {
    MaterialFloatingScheduleScreen(state = state, actions = actions)
}
