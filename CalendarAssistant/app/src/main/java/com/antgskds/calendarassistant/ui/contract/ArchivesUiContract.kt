package com.antgskds.calendarassistant.ui.contract

import com.antgskds.calendarassistant.data.model.ScheduleDisplayItem
import java.time.LocalDate

data class ArchivesUiState(
    val groups: List<ArchivesDateGroupUi>,
    val currentYear: Int,
    val timeRefreshToken: Long,
    val hapticEnabled: Boolean
)

data class ArchivesDateGroupUi(
    val date: LocalDate,
    val items: List<ScheduleDisplayItem>
)

sealed interface ArchivesUiAction {
    data class Delete(val eventId: Long) : ArchivesUiAction
    data class Restore(val eventId: Long) : ArchivesUiAction
}
