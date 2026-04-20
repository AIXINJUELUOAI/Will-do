package com.antgskds.calendarassistant.core.query

import com.antgskds.calendarassistant.core.calendar.CalendarManager
import com.antgskds.calendarassistant.core.calendar.CalendarSyncManager
import com.antgskds.calendarassistant.data.model.MySettings
import kotlinx.coroutines.flow.StateFlow

interface SettingsQueryApi {
    val settings: StateFlow<MySettings>

    suspend fun getSyncStatus(): CalendarSyncManager.SyncStatus
    suspend fun getSelectableSyncCalendars(): List<CalendarManager.CalendarInfo>
}
