package com.antgskds.calendarassistant.data.operation

import com.antgskds.calendarassistant.feature.weather.api.WeatherOperationApi
import com.antgskds.calendarassistant.feature.weather.domain.WeatherRepository
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

    override fun clearCache() {
        weatherRepository.clearCache()
    }
}
