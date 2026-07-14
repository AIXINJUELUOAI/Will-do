package com.antgskds.calendarassistant.feature.schedule.ui.contract

import com.antgskds.calendarassistant.feature.schedule.domain.course.TimeTableLayoutConfig

data class TimeTableEditorUiState(
    val resolvedConfig: TimeTableLayoutConfig,
    val hasStoredLayoutConfig: Boolean,
    val hapticEnabled: Boolean
)

sealed interface TimeTableEditorUiAction {
    data class Save(val nodesJson: String, val configJson: String) : TimeTableEditorUiAction
}
