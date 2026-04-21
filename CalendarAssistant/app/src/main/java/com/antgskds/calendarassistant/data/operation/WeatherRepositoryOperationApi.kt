package com.antgskds.calendarassistant.data.operation

import com.antgskds.calendarassistant.core.operation.WeatherOperationApi
import com.antgskds.calendarassistant.core.weather.WeatherRepository
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.data.model.WeatherData

class WeatherRepositoryOperationApi(
    private val weatherRepository: WeatherRepository
) : WeatherOperationApi {
    override suspend fun refreshIfNeeded(settings: MySettings): Result<WeatherData?> {
        return weatherRepository.refreshIfNeeded(settings)
    }

    override suspend fun forceRefresh(settings: MySettings): Result<WeatherData> {
        return weatherRepository.forceRefresh(settings)
    }
}
