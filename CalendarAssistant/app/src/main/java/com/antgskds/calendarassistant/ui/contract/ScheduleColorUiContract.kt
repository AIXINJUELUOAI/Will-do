package com.antgskds.calendarassistant.ui.contract

data class ScheduleColorUiState(
    val colors: List<String>,
    val hapticEnabled: Boolean
)

sealed interface ScheduleColorUiAction {
    data class UpdatePalette(val colors: List<String>) : ScheduleColorUiAction
    data object ResetPalette : ScheduleColorUiAction
}
