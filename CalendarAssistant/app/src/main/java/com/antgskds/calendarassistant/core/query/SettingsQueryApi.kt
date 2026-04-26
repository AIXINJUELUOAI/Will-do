package com.antgskds.calendarassistant.core.query

import com.antgskds.calendarassistant.data.model.MySettings
import kotlinx.coroutines.flow.StateFlow

interface SettingsQueryApi {
    val settings: StateFlow<MySettings>
}
