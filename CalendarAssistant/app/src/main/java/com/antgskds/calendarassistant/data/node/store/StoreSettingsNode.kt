package com.antgskds.calendarassistant.data.node.store

import com.antgskds.calendarassistant.data.model.MySettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal object StoreSettingsNode {
    fun applySettingsNow(
        newSettings: MySettings,
        onSettingsUpdated: (MySettings) -> Unit,
        saveSettings: (MySettings) -> Unit,
        syncWeatherForSettings: (MySettings) -> Unit
    ) {
        onSettingsUpdated(newSettings)
        saveSettings(newSettings)
        syncWeatherForSettings(newSettings)
    }

    fun updateSettings(
        scope: CoroutineScope,
        newSettings: MySettings,
        applySettingsNow: (MySettings) -> Unit
    ) {
        scope.launch {
            applySettingsNow(newSettings)
        }
    }
}
