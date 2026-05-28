package com.antgskds.calendarassistant.data.model

import kotlinx.serialization.Serializable

@Serializable
data class WeatherData(
    val temperature: String = "",
    val feelsLike: String = "",
    val text: String = "",
    val icon: String = "",
    val windDir: String = "",
    val windScale: String = "",
    val windSpeed: String = "",
    val humidity: String = "",
    val precip: String = "",
    val pressure: String = "",
    val vis: String = "",
    val obsTime: String = "",
    val city: String = "",
    val locationId: String = "",
    val locationName: String = "",
    val adm1: String = "",
    val adm2: String = "",
    val country: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val locationSource: String = "",
    val provider: String = "",
    val updateTime: Long = 0L,
    val hourlyForecast: List<WeatherHourlyForecast> = emptyList(),
    val dailyForecast: List<WeatherDailyForecast> = emptyList(),
    val alerts: List<WeatherAlertData> = emptyList(),
    val riskAlerts: List<WeatherRiskAlert> = emptyList(),
    val attributions: List<String> = emptyList()
)

@Serializable
data class WeatherHourlyForecast(
    val fxTime: String = "",
    val temp: String = "",
    val icon: String = "",
    val text: String = "",
    val windDir: String = "",
    val windScale: String = "",
    val windSpeed: String = "",
    val humidity: String = "",
    val pop: String = "",
    val precip: String = "",
    val pressure: String = "",
    val cloud: String = ""
)

@Serializable
data class WeatherDailyForecast(
    val fxDate: String = "",
    val tempMax: String = "",
    val tempMin: String = "",
    val iconDay: String = "",
    val textDay: String = "",
    val iconNight: String = "",
    val textNight: String = "",
    val windDirDay: String = "",
    val windScaleDay: String = "",
    val windDirNight: String = "",
    val windScaleNight: String = "",
    val humidity: String = "",
    val precip: String = "",
    val uvIndex: String = "",
    val sunrise: String = "",
    val sunset: String = ""
)

@Serializable
data class WeatherAlertData(
    val id: String = "",
    val senderName: String = "",
    val eventName: String = "",
    val eventCode: String = "",
    val severity: String = "",
    val colorCode: String = "",
    val issuedTime: String = "",
    val effectiveTime: String = "",
    val onsetTime: String = "",
    val expireTime: String = "",
    val headline: String = "",
    val description: String = "",
    val instruction: String = ""
)

@Serializable
data class WeatherRiskAlert(
    val id: String = "",
    val title: String = "",
    val level: String = "",
    val fxTime: String = "",
    val weatherText: String = "",
    val message: String = ""
)

fun WeatherData.displayLocationName(short: Boolean = false): String {
    val primary = locationName.ifBlank { city }
    if (primary.isBlank()) return "当前位置"
    if (short) return primary

    val cityName = adm2.removeSuffix("市")
    val placeName = primary.removeSuffix("区").removeSuffix("县")
    return when {
        cityName.isBlank() || cityName == placeName -> primary
        primary == adm1 || primary == adm2 -> primary
        else -> "$cityName $primary"
    }
}
