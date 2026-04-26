package com.antgskds.calendarassistant.core.operation

import com.antgskds.calendarassistant.data.model.MySettings

interface SettingsOperationApi {
    fun updateSettings(newSettings: MySettings)
}
