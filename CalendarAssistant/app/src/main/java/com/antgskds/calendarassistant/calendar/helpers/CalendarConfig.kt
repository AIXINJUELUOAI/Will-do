package com.antgskds.calendarassistant.calendar.helpers

import android.content.Context

class CalendarConfig private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("calendar_config", Context.MODE_PRIVATE)

    var caldavSync: Boolean
        get() = prefs.getBoolean(CALDAV_SYNC, false)
        set(value) = prefs.edit().putBoolean(CALDAV_SYNC, value).apply()

    var caldavSyncedCalendarIds: String
        get() = prefs.getString(CALDAV_SYNCED_CALENDAR_IDS, "") ?: ""
        set(value) = prefs.edit().putString(CALDAV_SYNCED_CALENDAR_IDS, value).apply()

    var lastUsedCaldavCalendarId: Int
        get() = prefs.getInt(LAST_USED_CALDAV_CALENDAR, getSyncedCalendarIdsAsList().firstOrNull() ?: 0)
        set(value) = prefs.edit().putInt(LAST_USED_CALDAV_CALENDAR, value).apply()

    var syncIntervalSeconds: Int
        get() = prefs.getInt(SYNC_INTERVAL_SECONDS, 60)
        set(value) = prefs.edit().putInt(SYNC_INTERVAL_SECONDS, value.coerceIn(1, 300)).apply()

    fun getSyncedCalendarIdsAsList(): ArrayList<Int> {
        return caldavSyncedCalendarIds.split(',')
            .mapNotNull { it.trim().toIntOrNull() }
            .toCollection(ArrayList())
    }

    companion object {
        private const val CALDAV_SYNC = "caldav_sync"
        private const val CALDAV_SYNCED_CALENDAR_IDS = "caldav_synced_calendar_ids"
        private const val LAST_USED_CALDAV_CALENDAR = "last_used_caldav_calendar"
        private const val SYNC_INTERVAL_SECONDS = "sync_interval_seconds"

        fun newInstance(context: Context): CalendarConfig = CalendarConfig(context)
    }
}
