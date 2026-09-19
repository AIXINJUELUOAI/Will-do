package com.antgskds.calendarassistant.feature.schedule.data.store.config

import android.content.Context
import com.antgskds.calendarassistant.feature.schedule.domain.calendar.CalendarConfig

class SyncConfigStore(context: Context) {
    private val config = CalendarConfig.newInstance(context.applicationContext)

    fun isSyncEnabled(): Boolean = config.caldavSync

    fun setSyncEnabled(enabled: Boolean) {
        config.caldavSync = enabled
    }

    fun getSyncedCalendarIdsRaw(): String = config.caldavSyncedCalendarIds

    fun setSyncedCalendarIdsRaw(ids: String) {
        config.caldavSyncedCalendarIds = ids
    }

    fun getSyncIntervalSeconds(): Int = config.syncIntervalSeconds

    fun setSyncIntervalSeconds(seconds: Int) {
        config.syncIntervalSeconds = seconds
    }
}
