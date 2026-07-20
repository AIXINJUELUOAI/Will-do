package com.antgskds.calendarassistant.feature.weather.api

import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import com.antgskds.calendarassistant.feature.weather.domain.model.WeatherData

interface WeatherOperationApi {
    suspend fun refreshIfNeeded(settings: MySettings): Result<WeatherData?>
    suspend fun forceRefresh(settings: MySettings): Result<WeatherData>
    fun clearCache()
}
