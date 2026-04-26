package com.antgskds.calendarassistant.data.repository

import android.content.Context
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.data.source.SettingsDataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {
    private val appContext = context.applicationContext
    private val settingsSource = SettingsDataSource(appContext)

    private val _settingsFlow = MutableStateFlow(loadSettings())
    val settingsFlow: StateFlow<MySettings> = _settingsFlow.asStateFlow()

    fun loadSettings(): MySettings {
        return settingsSource.loadSettings()
    }

    fun saveSettings(settings: MySettings) {
        settingsSource.saveSettings(settings)
        _settingsFlow.value = settings
    }

    // Room 相关开关（保留用于渐进迁移）
    fun setRoomReadEnabled(enabled: Boolean) {
        val prefs = appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("room_read_enabled", enabled).apply()
    }

    fun isRoomReadEnabled(): Boolean {
        val prefs = appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("room_read_enabled", false)
    }

    fun setRoomMainEnabled(enabled: Boolean) {
        val prefs = appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("room_main_enabled", enabled).apply()
    }

    fun isRoomMainEnabled(): Boolean {
        val prefs = appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("room_main_enabled", false)
    }
}
