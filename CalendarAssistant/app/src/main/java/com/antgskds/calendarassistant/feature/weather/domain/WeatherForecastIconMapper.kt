package com.antgskds.calendarassistant.feature.weather.domain

import com.antgskds.calendarassistant.R

object WeatherForecastIconMapper {
    fun iconRes(text: String, icon: String): Int {
        val weatherText = text.lowercase()
        val iconCode = icon.lowercase().trim()
        return when {
            weatherText.contains("雷") -> R.drawable.ic_weather_thunderstorm
            weatherText.contains("雹") -> R.drawable.ic_weather_hail
            weatherText.contains("雨夹雪") || weatherText.contains("冻雨") || iconCode in sleetIcons -> R.drawable.ic_weather_sleet
            weatherText.contains("暴雪") || weatherText.contains("大雪") || iconCode in heavySnowIcons -> R.drawable.ic_weather_snow_heavy
            weatherText.contains("雪") -> R.drawable.ic_weather_snow
            weatherText.contains("暴雨") || weatherText.contains("大雨") || iconCode in heavyRainIcons -> R.drawable.ic_weather_rain_heavy
            weatherText.contains("雨") -> R.drawable.ic_weather_rain
            weatherText.contains("台风") || weatherText.contains("飓风") || weatherText.contains("龙卷") -> R.drawable.ic_weather_wind
            weatherText.contains("沙") || weatherText.contains("尘") || weatherText.contains("霾") -> R.drawable.ic_weather_haze
            weatherText.contains("雾") -> R.drawable.ic_weather_fog
            weatherText.contains("阴") -> R.drawable.ic_weather_overcast
            weatherText.contains("云") && isNightIcon(iconCode) -> R.drawable.ic_weather_partly_cloudy_night
            weatherText.contains("云") -> R.drawable.ic_weather_partly_cloudy
            isNightIcon(iconCode) -> R.drawable.ic_weather_clear_night
            weatherText.contains("晴") || iconCode == "100" || iconCode == "150" -> R.drawable.ic_weather_sunny
            else -> R.drawable.ic_weather_partly_cloudy
        }
    }

    private val sleetIcons = setOf("313", "314", "399", "404", "405", "406")
    private val heavySnowIcons = setOf("402", "403")
    private val heavyRainIcons = setOf("311", "312", "316", "317", "398")

    private fun isNightIcon(iconCode: String): Boolean {
        return iconCode == "150" || iconCode == "151" || iconCode == "152" || iconCode == "153"
    }
}
