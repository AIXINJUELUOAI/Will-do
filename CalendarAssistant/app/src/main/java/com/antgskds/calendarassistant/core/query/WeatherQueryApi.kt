package com.antgskds.calendarassistant.core.query

import com.antgskds.calendarassistant.data.model.WeatherData
import kotlinx.coroutines.flow.StateFlow

interface WeatherQueryApi {
    val weatherData: StateFlow<WeatherData?>
}
