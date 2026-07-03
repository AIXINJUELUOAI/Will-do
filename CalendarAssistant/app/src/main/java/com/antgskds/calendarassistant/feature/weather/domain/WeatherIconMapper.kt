package com.antgskds.calendarassistant.feature.weather.domain

import com.antgskds.calendarassistant.data.model.WeatherData

object WeatherIconMapper {
    fun iconRes(data: WeatherData): Int {
        return WeatherForecastIconMapper.iconRes(data.text, data.icon)
    }
}
