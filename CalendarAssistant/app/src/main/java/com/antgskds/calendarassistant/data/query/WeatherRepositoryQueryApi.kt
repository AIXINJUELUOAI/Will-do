package com.antgskds.calendarassistant.data.query

import com.antgskds.calendarassistant.feature.weather.api.WeatherQueryApi
import com.antgskds.calendarassistant.feature.weather.domain.WeatherRepository
import com.antgskds.calendarassistant.data.model.WeatherData
import kotlinx.coroutines.flow.StateFlow

class WeatherRepositoryQueryApi(
    private val weatherRepository: WeatherRepository
) : WeatherQueryApi {
    override val weatherData: StateFlow<WeatherData?>
        get() = weatherRepository.weatherData
}
