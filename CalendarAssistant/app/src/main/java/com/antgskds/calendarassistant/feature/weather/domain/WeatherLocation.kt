package com.antgskds.calendarassistant.feature.weather.domain

data class WeatherLocation(
    val latitude: Double,
    val longitude: Double,
    val source: String,
    val locationId: String = "",
    val name: String = "",
    val adm1: String = "",
    val adm2: String = "",
    val country: String = ""
)
