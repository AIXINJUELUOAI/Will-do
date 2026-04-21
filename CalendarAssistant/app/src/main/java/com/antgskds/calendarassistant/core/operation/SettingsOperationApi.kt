package com.antgskds.calendarassistant.core.operation

import com.antgskds.calendarassistant.data.model.MySettings

interface SettingsOperationApi {
    suspend fun updateSettings(settings: MySettings)

    suspend fun enableCalendarSync(): Result<Unit>
    suspend fun disableCalendarSync(): Result<Unit>
    suspend fun enableCalendarSyncAndSyncNow(): Result<Unit>
    suspend fun updateSourceCalendars(calendarIds: List<Long>): Result<Unit>
    suspend fun manualSync(): Result<Unit>
    suspend fun syncFromCalendar(): Result<Int>
}
