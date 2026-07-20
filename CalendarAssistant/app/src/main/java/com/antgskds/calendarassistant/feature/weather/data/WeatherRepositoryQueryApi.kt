package com.antgskds.calendarassistant.feature.weather.data

import com.antgskds.calendarassistant.feature.weather.api.WeatherQueryApi
import com.antgskds.calendarassistant.feature.weather.domain.WeatherRepository
import com.antgskds.calendarassistant.feature.weather.domain.model.WeatherData
import kotlinx.coroutines.flow.StateFlow

class WeatherRepositoryQueryApi(
    private val weatherRepository: WeatherRepository
) : WeatherQueryApi {
    override val weatherData: StateFlow<WeatherData?>
        get() = weatherRepository.weatherData
}
