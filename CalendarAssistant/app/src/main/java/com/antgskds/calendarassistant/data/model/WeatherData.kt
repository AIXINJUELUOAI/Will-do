package com.antgskds.calendarassistant.data.model

import kotlinx.serialization.Serializable

@Serializable
data class WeatherData(
    val temperature: String = "",
    val text: String = "",
    val icon: String = "",
    val windDir: String = "",
    val windScale: String = "",
    val humidity: String = "",
    val city: String = "",
    val provider: String = "",
    val updateTime: Long = 0L
)
