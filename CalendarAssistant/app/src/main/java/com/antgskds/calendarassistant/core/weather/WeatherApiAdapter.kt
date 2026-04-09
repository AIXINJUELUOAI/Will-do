package com.antgskds.calendarassistant.core.weather

import com.antgskds.calendarassistant.data.model.WeatherData
import org.json.JSONObject

object WeatherApiAdapter {
    const val PROVIDER_QWEATHER = "qweather"

    fun defaultUrl(provider: String): String {
        return "https://YOUR_HOST"
    }

    fun resolveRequestUrl(provider: String, rawValue: String): String {
        val value = rawValue.trim().trimEnd('/')
        return if (value.isBlank()) {
            ""
        } else if (value.contains("/v7/")) {
            value
        } else {
            "$value/v7/weather/now"
        }
    }

    fun parse(provider: String, rawBody: String, city: String): WeatherData {
        val root = JSONObject(rawBody)
        return parseQWeather(root, city)
    }

    private fun parseQWeather(root: JSONObject, city: String): WeatherData {
        val code = root.optString("code")
        if (code.isNotBlank() && code != "200") {
            throw IllegalStateException("QWeather error $code")
        }
        val now = root.optJSONObject("now") ?: throw IllegalStateException("QWeather missing now")
        return WeatherData(
            temperature = now.optString("temp"),
            text = now.optString("text"),
            icon = now.optString("icon"),
            windDir = now.optString("windDir"),
            windScale = now.optString("windScale"),
            humidity = now.optString("humidity"),
            city = city,
            provider = PROVIDER_QWEATHER,
            updateTime = System.currentTimeMillis()
        )
    }
}
