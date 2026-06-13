package com.antgskds.calendarassistant.core.weather

import com.antgskds.calendarassistant.data.model.WeatherData
import com.antgskds.calendarassistant.data.model.WeatherAlertData
import com.antgskds.calendarassistant.data.model.WeatherDailyForecast
import com.antgskds.calendarassistant.data.model.WeatherHourlyForecast
import org.json.JSONObject

object WeatherApiAdapter {
    const val PROVIDER_QWEATHER = "qweather"

    fun defaultUrl(provider: String): String {
        return "https://YOUR_HOST"
    }

    fun resolveRequestUrl(provider: String, rawValue: String, path: String = "/v7/weather/now"): String {
        val value = rawValue.trim().trimEnd('/')
        if (value.isBlank()) return ""
        val markerIndex = listOf("/v7/", "/geo/", "/weatheralert/")
            .map { value.indexOf(it) }
            .filter { it >= 0 }
            .minOrNull()
        val base = if (markerIndex != null) value.substring(0, markerIndex) else value
        return "$base$path"
    }

    fun parse(provider: String, rawBody: String, location: WeatherLocation): WeatherData {
        val root = JSONObject(rawBody)
        return parseQWeather(root, location)
    }

    fun parseHourly(rawBody: String): List<WeatherHourlyForecast> {
        val root = JSONObject(rawBody)
        ensureQWeatherSuccess(root)
        val hourly = root.optJSONArray("hourly") ?: return emptyList()
        return buildList {
            for (index in 0 until hourly.length()) {
                val item = hourly.optJSONObject(index) ?: continue
                add(
                    WeatherHourlyForecast(
                        fxTime = item.optString("fxTime"),
                        temp = item.optString("temp"),
                        icon = item.optString("icon"),
                        text = item.optString("text"),
                        windDir = item.optString("windDir"),
                        windScale = item.optString("windScale"),
                        windSpeed = item.optString("windSpeed"),
                        humidity = item.optString("humidity"),
                        pop = item.optString("pop"),
                        precip = item.optString("precip"),
                        pressure = item.optString("pressure"),
                        cloud = item.optString("cloud")
                    )
                )
            }
        }
    }

    fun parseDaily(rawBody: String): List<WeatherDailyForecast> {
        val root = JSONObject(rawBody)
        ensureQWeatherSuccess(root)
        val daily = root.optJSONArray("daily") ?: return emptyList()
        return buildList {
            for (index in 0 until daily.length()) {
                val item = daily.optJSONObject(index) ?: continue
                add(
                    WeatherDailyForecast(
                        fxDate = item.optString("fxDate"),
                        tempMax = item.optString("tempMax"),
                        tempMin = item.optString("tempMin"),
                        iconDay = item.optString("iconDay"),
                        textDay = item.optString("textDay"),
                        iconNight = item.optString("iconNight"),
                        textNight = item.optString("textNight"),
                        windDirDay = item.optString("windDirDay"),
                        windScaleDay = item.optString("windScaleDay"),
                        windDirNight = item.optString("windDirNight"),
                        windScaleNight = item.optString("windScaleNight"),
                        humidity = item.optString("humidity"),
                        precip = item.optString("precip"),
                        uvIndex = item.optString("uvIndex"),
                        sunrise = item.optString("sunrise"),
                        sunset = item.optString("sunset")
                    )
                )
            }
        }
    }

    fun parseAlerts(rawBody: String): Pair<List<WeatherAlertData>, List<String>> {
        val root = JSONObject(rawBody)
        val metadata = root.optJSONObject("metadata")
        val attributionsArray = metadata?.optJSONArray("attributions")
        val attributions = buildList {
            if (attributionsArray != null) {
                for (index in 0 until attributionsArray.length()) {
                    val value = attributionsArray.optString(index)
                    if (value.isNotBlank()) add(value)
                }
            }
        }
        val alerts = root.optJSONArray("alerts") ?: return emptyList<WeatherAlertData>() to attributions
        return buildList {
            for (index in 0 until alerts.length()) {
                val item = alerts.optJSONObject(index) ?: continue
                val eventType = item.optJSONObject("eventType")
                val color = item.optJSONObject("color")
                val messageType = item.optJSONObject("messageType")
                val supersedes = messageType?.optJSONArray("supersedes")
                add(
                    WeatherAlertData(
                        id = item.optString("id"),
                        senderName = item.optString("senderName"),
                        eventName = eventType?.optString("name").orEmpty(),
                        eventCode = eventType?.optString("code").orEmpty(),
                        severity = item.optString("severity"),
                        colorCode = color?.optString("code").orEmpty(),
                        messageTypeCode = messageType?.optString("code").orEmpty(),
                        messageTypeSupersedes = buildList {
                            if (supersedes != null) {
                                for (supersedeIndex in 0 until supersedes.length()) {
                                    supersedes.optString(supersedeIndex).takeIf { it.isNotBlank() }?.let(::add)
                                }
                            }
                        },
                        issuedTime = item.optString("issuedTime"),
                        effectiveTime = item.optString("effectiveTime"),
                        onsetTime = item.optString("onsetTime"),
                        expireTime = item.optString("expireTime"),
                        headline = item.optString("headline"),
                        description = item.optString("description"),
                        instruction = item.optString("instruction")
                    )
                )
            }
        } to attributions
    }

    fun parseGeoLocation(rawBody: String): WeatherLocation? {
        val root = JSONObject(rawBody)
        ensureQWeatherSuccess(root)
        val locations = root.optJSONArray("location") ?: return null
        val item = locations.optJSONObject(0) ?: return null
        val lat = item.optString("lat").toDoubleOrNull() ?: return null
        val lon = item.optString("lon").toDoubleOrNull() ?: return null
        return WeatherLocation(
            latitude = lat,
            longitude = lon,
            source = "geo",
            locationId = item.optString("id"),
            name = item.optString("name"),
            adm1 = item.optString("adm1"),
            adm2 = item.optString("adm2"),
            country = item.optString("country")
        )
    }

    private fun parseQWeather(root: JSONObject, location: WeatherLocation): WeatherData {
        ensureQWeatherSuccess(root)
        val now = root.optJSONObject("now") ?: throw IllegalStateException("QWeather missing now")
        val locationName = location.name.ifBlank {
            when (location.source) {
                "cached" -> "最近位置"
                "manual" -> "手动位置"
                else -> "当前位置"
            }
        }
        return WeatherData(
            temperature = now.optString("temp"),
            feelsLike = now.optString("feelsLike"),
            text = now.optString("text"),
            icon = now.optString("icon"),
            windDir = now.optString("windDir"),
            windScale = now.optString("windScale"),
            windSpeed = now.optString("windSpeed"),
            humidity = now.optString("humidity"),
            precip = now.optString("precip"),
            pressure = now.optString("pressure"),
            vis = now.optString("vis"),
            obsTime = now.optString("obsTime"),
            city = locationName,
            locationId = location.locationId,
            locationName = locationName,
            adm1 = location.adm1,
            adm2 = location.adm2,
            country = location.country,
            latitude = location.latitude,
            longitude = location.longitude,
            locationSource = location.source,
            provider = PROVIDER_QWEATHER,
            updateTime = System.currentTimeMillis()
        )
    }

    private fun ensureQWeatherSuccess(root: JSONObject) {
        val code = root.optString("code")
        if (code.isNotBlank() && code != "200") {
            throw IllegalStateException("QWeather error $code")
        }
    }
}
