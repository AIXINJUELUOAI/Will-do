package com.antgskds.calendarassistant.core.weather

import com.antgskds.calendarassistant.data.model.WeatherAlertData

object WeatherWarningText {
    fun officialTitle(alert: WeatherAlertData): String {
        val color = colorName(alert.colorCode.ifBlank { alert.severity })
        val event = alert.eventName.ifBlank { extractEventName(alert.headline) }.ifBlank { "天气" }
        return listOf(color, event, "预警")
            .filter { it.isNotBlank() }
            .joinToString("")
            .replace("预警预警", "预警")
    }

    fun colorName(value: String): String {
        return when (value.lowercase()) {
            "white" -> "白色"
            "blue" -> "蓝色"
            "green" -> "绿色"
            "yellow" -> "黄色"
            "orange" -> "橙色"
            "red" -> "红色"
            "black" -> "黑色"
            else -> value
        }
    }

    private fun extractEventName(value: String): String {
        val normalized = value.replace("预警信号", "预警").replace("发布", "")
        val start = listOf("暴雨", "雷暴大风", "大风", "台风", "暴雪", "高温", "寒潮", "冰雹", "道路结冰")
            .firstOrNull { normalized.contains(it) }
        return start.orEmpty()
    }
}
