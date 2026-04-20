package com.antgskds.calendarassistant.data.query

import com.antgskds.calendarassistant.core.calendar.CalendarManager
import com.antgskds.calendarassistant.core.calendar.CalendarSyncManager
import com.antgskds.calendarassistant.core.query.SettingsQueryApi
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.data.repository.AppRepository
import kotlinx.coroutines.flow.StateFlow

class AppRepositorySettingsQueryApi(
    private val repository: AppRepository
) : SettingsQueryApi {
    override val settings: StateFlow<MySettings>
        get() = repository.settings

    override suspend fun getSyncStatus(): CalendarSyncManager.SyncStatus {
        return repository.getSyncStatus()
    }

    override suspend fun getSelectableSyncCalendars(): List<CalendarManager.CalendarInfo> {
        return repository.getSelectableSyncCalendars()
    }
}
