package com.antgskds.calendarassistant.shared.management.resource.notification.display.normal

object WeatherNormalDisplay {
    private const val MAX_CONTENT_CHARS = 120

    fun officialTitle(title: String): String {
        return title
    }

    fun officialContent(description: String, instruction: String, eventName: String): String {
        return trimContent(description.ifBlank { instruction.ifBlank { eventName } })
    }

    fun riskTitle(title: String): String {
        return title.ifBlank { "天气风险提醒" }
    }

    fun riskContent(message: String, weatherText: String): String {
        return trimContent(message.ifBlank { weatherText })
    }

    private fun trimContent(content: String): String {
        return content.take(MAX_CONTENT_CHARS)
    }
}
