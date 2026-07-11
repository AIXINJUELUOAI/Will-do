package com.antgskds.calendarassistant.ui.contract

import java.time.LocalDate

data class ScheduleSettingsUiState(
    val semesterStartDate: LocalDate?,
    val currentWeek: Int,
    val totalWeeks: Int,
    val hapticEnabled: Boolean
)

sealed interface ScheduleSettingsUiAction {
    data class UpdateSemesterStartDate(val date: String) : ScheduleSettingsUiAction
    data class UpdateTotalWeeks(val weeks: Int) : ScheduleSettingsUiAction
    data object OpenCourseManager : ScheduleSettingsUiAction
    data object OpenTimeTableManager : ScheduleSettingsUiAction
}
