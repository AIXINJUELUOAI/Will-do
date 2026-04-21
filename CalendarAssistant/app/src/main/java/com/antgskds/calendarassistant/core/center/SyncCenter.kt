package com.antgskds.calendarassistant.core.center

import com.antgskds.calendarassistant.core.calendar.CalendarManager
import com.antgskds.calendarassistant.core.calendar.CalendarSyncManager
import com.antgskds.calendarassistant.core.operation.SettingsOperationApi
import com.antgskds.calendarassistant.core.query.SettingsQueryApi

class SyncCenter(
    private val settingsOperationApi: SettingsOperationApi,
    private val settingsQueryApi: SettingsQueryApi
) {
    suspend fun getSyncStatus(): CalendarSyncManager.SyncStatus {
        return settingsQueryApi.getSyncStatus()
    }

    suspend fun getSelectableSyncCalendars(): List<CalendarManager.CalendarInfo> {
        return settingsQueryApi.getSelectableSyncCalendars()
    }

    suspend fun enableCalendarSync(): Result<Unit> {
        return settingsOperationApi.enableCalendarSync()
    }

    suspend fun disableCalendarSync(): Result<Unit> {
        return settingsOperationApi.disableCalendarSync()
    }

    suspend fun enableCalendarSyncAndSyncNow(): Result<Unit> {
        return settingsOperationApi.enableCalendarSyncAndSyncNow()
    }

    suspend fun updateSourceCalendars(calendarIds: List<Long>): Result<Unit> {
        return settingsOperationApi.updateSourceCalendars(calendarIds)
    }

    suspend fun manualSync(): Result<Unit> {
        return settingsOperationApi.manualSync()
    }

    suspend fun syncFromCalendar(): Result<Int> {
        return settingsOperationApi.syncFromCalendar()
    }
}
