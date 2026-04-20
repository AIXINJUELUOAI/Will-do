package com.antgskds.calendarassistant.core.operation

import com.antgskds.calendarassistant.core.calendar.CalendarManager
import com.antgskds.calendarassistant.core.calendar.CalendarSyncManager
import com.antgskds.calendarassistant.core.importer.ImportMode
import com.antgskds.calendarassistant.data.model.ImportResult
import com.antgskds.calendarassistant.data.model.MySettings

interface SettingsOperationApi {
    suspend fun updateSettings(settings: MySettings)

    suspend fun exportCoursesData(): String
    suspend fun importCoursesData(jsonString: String): Result<Unit>
    suspend fun exportEventsData(): String
    suspend fun importEventsData(jsonString: String): Result<ImportResult>
    suspend fun importWakeUpFile(content: String, mode: ImportMode, importSettings: Boolean): Result<Int>

    suspend fun getSyncStatus(): CalendarSyncManager.SyncStatus
    suspend fun getSelectableSyncCalendars(): List<CalendarManager.CalendarInfo>
    suspend fun enableCalendarSync(): Result<Unit>
    suspend fun disableCalendarSync(): Result<Unit>
    suspend fun enableCalendarSyncAndSyncNow(): Result<Unit>
    suspend fun updateSourceCalendars(calendarIds: List<Long>): Result<Unit>
    suspend fun manualSync(): Result<Unit>
    suspend fun syncFromCalendar(): Result<Int>
}
