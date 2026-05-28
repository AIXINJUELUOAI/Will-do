package com.antgskds.calendarassistant.core.weather

import com.antgskds.calendarassistant.data.model.WeatherData

object WeatherIconMapper {
    fun iconRes(data: WeatherData): Int {
        return WeatherForecastIconMapper.iconRes(data.text, data.icon)
    }
}
