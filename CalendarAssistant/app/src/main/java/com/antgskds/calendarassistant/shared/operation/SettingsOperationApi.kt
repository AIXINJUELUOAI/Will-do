package com.antgskds.calendarassistant.shared.operation

import com.antgskds.calendarassistant.feature.settings.data.model.MySettings

interface SettingsOperationApi {
    fun updateSettings(newSettings: MySettings)
}
