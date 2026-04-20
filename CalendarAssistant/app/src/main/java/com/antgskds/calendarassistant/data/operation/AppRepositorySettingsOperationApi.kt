package com.antgskds.calendarassistant.data.operation

import com.antgskds.calendarassistant.core.calendar.CalendarManager
import com.antgskds.calendarassistant.core.calendar.CalendarSyncManager
import com.antgskds.calendarassistant.core.importer.ImportMode
import com.antgskds.calendarassistant.core.operation.SettingsOperationApi
import com.antgskds.calendarassistant.data.model.ImportResult
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.data.repository.AppRepository

class AppRepositorySettingsOperationApi(
    private val repository: AppRepository
) : SettingsOperationApi {
    override suspend fun updateSettings(settings: MySettings) {
        repository.updateSettings(settings)
    }

    override suspend fun exportCoursesData(): String {
        return repository.exportCoursesData()
    }

    override suspend fun importCoursesData(jsonString: String): Result<Unit> {
        return repository.importCoursesData(jsonString)
    }

    override suspend fun exportEventsData(): String {
        return repository.exportEventsData()
    }

    override suspend fun importEventsData(jsonString: String): Result<ImportResult> {
        return repository.importEventsData(jsonString)
    }

    override suspend fun importWakeUpFile(content: String, mode: ImportMode, importSettings: Boolean): Result<Int> {
        return repository.importWakeUpFile(content, mode, importSettings)
    }

    override suspend fun getSyncStatus(): CalendarSyncManager.SyncStatus {
        return repository.getSyncStatus()
    }

    override suspend fun getSelectableSyncCalendars(): List<CalendarManager.CalendarInfo> {
        return repository.getSelectableSyncCalendars()
    }

    override suspend fun enableCalendarSync(): Result<Unit> {
        return repository.enableCalendarSync()
    }

    override suspend fun disableCalendarSync(): Result<Unit> {
        return repository.disableCalendarSync()
    }

    override suspend fun enableCalendarSyncAndSyncNow(): Result<Unit> {
        return repository.enableCalendarSyncAndSyncNow()
    }

    override suspend fun updateSourceCalendars(calendarIds: List<Long>): Result<Unit> {
        return repository.updateSourceCalendars(calendarIds)
    }

    override suspend fun manualSync(): Result<Unit> {
        return repository.manualSync()
    }

    override suspend fun syncFromCalendar(): Result<Int> {
        return repository.syncFromCalendar()
    }
}
