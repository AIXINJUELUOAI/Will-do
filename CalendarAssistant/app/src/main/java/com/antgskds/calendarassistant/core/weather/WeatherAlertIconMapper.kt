package com.antgskds.calendarassistant.core.weather

import com.antgskds.calendarassistant.R
import com.antgskds.calendarassistant.data.model.WeatherAlertData
import com.antgskds.calendarassistant.data.model.WeatherRiskAlert

object WeatherAlertIconMapper {
    fun officialIconRes(alert: WeatherAlertData): Int {
        return iconRes(
            WeatherWarningText.officialTitle(alert),
            alert.eventName,
            alert.headline,
            alert.description,
            alert.instruction,
            alert.eventCode
        )
    }

    fun riskIconRes(risk: WeatherRiskAlert): Int {
        return riskIconRes(risk.title, risk.weatherText, risk.message)
    }

    fun riskIconRes(title: String, weatherText: String, message: String): Int {
        iconResOrNull(title)?.let { return it }
        iconResOrNull(weatherText)?.let { return it }
        return iconRes(message)
    }

    fun iconRes(vararg values: String): Int {
        val text = values
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .lowercase()

        return iconResOrNull(text) ?: R.drawable.ic_weather_partly_cloudy
    }

    private fun iconResOrNull(value: String): Int? {
        val text = value.lowercase().trim()
        return when {
            text.isBlank() -> null
            text.contains("高温") || text.contains("热") || text.contains("干旱") -> R.drawable.ic_weather_sunny
            text.contains("冰雹") || text.contains("雹") -> R.drawable.ic_weather_hail
            text.contains("雷") || text.contains("强对流") || text.contains("龙卷") -> R.drawable.ic_weather_thunderstorm
            text.contains("雨夹雪") || text.contains("冻雨") -> R.drawable.ic_weather_sleet
            text.contains("暴雪") || text.contains("大雪") || text.contains("结冰") || text.contains("冰冻") || text.contains("霜冻") || text.contains("寒潮") || text.contains("强降温") -> R.drawable.ic_weather_snow_heavy
            text.contains("雪") || text.contains("低温") || text.contains("寒冷") || text.contains("降温") -> R.drawable.ic_weather_snow
            text.contains("暴雨") || text.contains("大暴雨") || text.contains("特大暴雨") || text.contains("强降雨") || text.contains("山洪") || text.contains("洪水") || text.contains("内涝") -> R.drawable.ic_weather_rain_heavy
            text.contains("雨") || text.contains("降水") || text.contains("降雨") -> R.drawable.ic_weather_rain
            text.contains("台风") || text.contains("大风") || text.contains("风") -> R.drawable.ic_weather_wind
            text.contains("雾") -> R.drawable.ic_weather_fog
            text.contains("霾") || text.contains("沙") || text.contains("尘") || text.contains("能见度") -> R.drawable.ic_weather_haze
            text.contains("阴") -> R.drawable.ic_weather_overcast
            text.contains("云") -> R.drawable.ic_weather_partly_cloudy
            else -> null
        }
    }
}
