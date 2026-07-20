package com.antgskds.calendarassistant.shared.query

import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import kotlinx.coroutines.flow.StateFlow

interface SettingsQueryApi {
    val settings: StateFlow<MySettings>
}
