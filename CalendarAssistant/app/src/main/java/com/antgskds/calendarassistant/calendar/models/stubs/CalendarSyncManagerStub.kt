package com.antgskds.calendarassistant.calendar.models.stubs

object CalendarSyncManager {
    data class SyncStatus(
        val isEnabled: Boolean = false,
        val hasPermission: Boolean = false,
        val targetCalendarId: Long = -1L,
        val sourceCalendarIds: List<Long> = emptyList(),
        val syncIntervalSeconds: Int = 60,
        val lastSyncTime: Long = 0L,
        val mappedEventCount: Int = 0
    )
}
