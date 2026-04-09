package com.antgskds.calendarassistant.core.weather

import androidx.compose.ui.graphics.Color
import com.antgskds.calendarassistant.data.model.WeatherData

object WeatherColorMapper {
    fun gradient(data: WeatherData): List<Color> {
        val text = data.text.lowercase()
        return when {
            text.contains("雷") -> listOf(Color(0xFF455A64), Color(0xFF1C313A))
            text.contains("雪") -> listOf(Color(0xFFDCEEFF), Color(0xFFB8D7F2))
            text.contains("雨") -> listOf(Color(0xFF4F83CC), Color(0xFF284B8F))
            text.contains("阴") || text.contains("云") -> listOf(Color(0xFF90A4AE), Color(0xFF607D8B))
            text.contains("雾") || text.contains("霾") -> listOf(Color(0xFFB0BEC5), Color(0xFF90A4AE))
            text.contains("晴") -> listOf(Color(0xFFFFB74D), Color(0xFFFFD54F))
            else -> listOf(Color(0xFF81C784), Color(0xFF4DB6AC))
        }
    }
}
