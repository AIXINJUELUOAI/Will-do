package com.antgskds.calendarassistant.data.query

import com.antgskds.calendarassistant.core.query.WeatherQueryApi
import com.antgskds.calendarassistant.core.weather.WeatherRepository
import com.antgskds.calendarassistant.data.model.WeatherData
import kotlinx.coroutines.flow.StateFlow

class WeatherRepositoryQueryApi(
    private val weatherRepository: WeatherRepository
) : WeatherQueryApi {
    override val weatherData: StateFlow<WeatherData?>
        get() = weatherRepository.weatherData
}
