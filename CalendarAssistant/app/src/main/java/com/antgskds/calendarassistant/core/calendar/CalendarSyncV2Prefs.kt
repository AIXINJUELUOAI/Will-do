package com.antgskds.calendarassistant.core.calendar

import android.content.Context

object CalendarSyncV2Prefs {
    private const val PREFS_NAME = "calendar_sync_v2_prefs"
    private const val KEY_SYNC_V2_ENABLED = "calendar_sync_v2_enabled"

    fun isEnabled(context: Context): Boolean {
        return true
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SYNC_V2_ENABLED, enabled)
            .apply()
    }
}
