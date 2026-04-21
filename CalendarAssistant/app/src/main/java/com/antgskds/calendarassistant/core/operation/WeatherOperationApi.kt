package com.antgskds.calendarassistant.core.operation

import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.data.model.WeatherData

interface WeatherOperationApi {
    suspend fun refreshIfNeeded(settings: MySettings): Result<WeatherData?>
    suspend fun forceRefresh(settings: MySettings): Result<WeatherData>
}
