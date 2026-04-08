package com.antgskds.calendarassistant.data.repository

import android.content.Context
import com.antgskds.calendarassistant.core.course.TimeTableLayoutUtils
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.data.source.SettingsDataSource

class SettingsRepository(context: Context) {
    private val appContext = context.applicationContext
    private val settingsSource = SettingsDataSource(appContext)
    private val roomReadPrefs by lazy {
        appContext.getSharedPreferences(ROOM_READ_PREFS, Context.MODE_PRIVATE)
    }
    private val roomMainPrefs by lazy {
        appContext.getSharedPreferences(ROOM_MAIN_PREFS, Context.MODE_PRIVATE)
    }

    fun loadSettings(): MySettings {
        return ensureTimeTableConfig(settingsSource.loadSettings())
    }

    fun saveSettings(settings: MySettings) {
        settingsSource.saveSettings(settings)
    }

    fun setRoomReadEnabled(enabled: Boolean) {
        roomReadPrefs.edit().putBoolean(ROOM_READ_KEY, enabled).apply()
    }

    fun isRoomReadEnabled(): Boolean {
        return roomReadPrefs.getBoolean(ROOM_READ_KEY, false)
    }

    fun setRoomMainEnabled(enabled: Boolean) {
        roomMainPrefs.edit().putBoolean(ROOM_MAIN_KEY, enabled).apply()
    }

    fun isRoomMainEnabled(): Boolean {
        return roomMainPrefs.getBoolean(ROOM_MAIN_KEY, true)
    }

    private fun ensureTimeTableConfig(settings: MySettings): MySettings {
        if (settings.timeTableConfigJson.isNotBlank() || settings.timeTableJson.isBlank()) {
            return settings
        }

        val resolvedConfig = TimeTableLayoutUtils.resolveLayoutConfig(
            configJsonString = "",
            timeTableJson = settings.timeTableJson
        )
        val configJson = TimeTableLayoutUtils.encodeLayoutConfig(resolvedConfig)
        val updated = settings.copy(timeTableConfigJson = configJson)
        settingsSource.saveSettings(updated)
        return updated
    }

    private companion object {
        const val ROOM_READ_PREFS = "room_read_prefs"
        const val ROOM_READ_KEY = "room_read_enabled"
        const val ROOM_MAIN_PREFS = "room_main_prefs"
        const val ROOM_MAIN_KEY = "room_main_enabled"
    }
}
