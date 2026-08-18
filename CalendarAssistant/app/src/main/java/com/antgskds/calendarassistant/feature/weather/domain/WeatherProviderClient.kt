package com.antgskds.calendarassistant.feature.weather.domain

import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import com.antgskds.calendarassistant.feature.weather.domain.model.WeatherAlertData
import com.antgskds.calendarassistant.feature.weather.domain.model.WeatherData
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import java.util.Locale

internal interface WeatherProviderClient {
    val providerId: String

    suspend fun fetch(settings: MySettings, location: WeatherLocation): WeatherData

    suspend fun enrichLocation(settings: MySettings, location: WeatherLocation): WeatherLocation = location
}

internal class QWeatherProviderClient(
    private val client: HttpClient
) : WeatherProviderClient {
    override val providerId: String = WeatherApiAdapter.PROVIDER_QWEATHER

    override suspend fun fetch(settings: MySettings, location: WeatherLocation): WeatherData {
        val rawCurrent = requestWeather(settings, location, "/v7/weather/now")
        val hourly = runCatching {
            WeatherApiAdapter.parseHourly(requestWeather(settings, location, "/v7/weather/24h"))
        }.getOrDefault(emptyList())
        val daily = runCatching {
            WeatherApiAdapter.parseDaily(requestWeather(settings, location, "/v7/weather/7d"))
        }.getOrDefault(emptyList())
        val (alerts, attributions) = if (settings.weatherWarningEnabled) {
            runCatching { requestAlerts(settings, location) }
                .getOrDefault(emptyList<WeatherAlertData>() to emptyList())
        } else {
            emptyList<WeatherAlertData>() to emptyList()
        }
        return WeatherApiAdapter.parse(providerId, rawCurrent, location).copy(
            hourlyForecast = hourly,
            dailyForecast = daily,
            alerts = alerts,
            attributions = attributions
        )
    }

    override suspend fun enrichLocation(settings: MySettings, location: WeatherLocation): WeatherLocation {
        if (location.name.isNotBlank() && location.locationId.isNotBlank()) return location
        if (location.source == "manual") return location
        val endpoint = WeatherApiAdapter.resolveRequestUrl(
            provider = providerId,
            rawValue = settings.weatherApiUrl.ifBlank { WeatherApiAdapter.defaultUrl(providerId) },
            path = "/geo/v2/city/lookup"
        )
        val response = client.get {
            url(endpoint)
            parameter("location", coordinate(location))
            parameter("number", "1")
            parameter("lang", "zh")
            header("X-QW-Api-Key", settings.weatherApiKey.trim())
        }
        ensureHttpSuccess(response.status.value, response.status.isSuccess())
        return WeatherApiAdapter.parseGeoLocation(response.bodyAsText())
            ?.copy(source = location.source)
            ?: location
    }

    private suspend fun requestWeather(settings: MySettings, location: WeatherLocation, path: String): String {
        val endpoint = WeatherApiAdapter.resolveRequestUrl(
            provider = providerId,
            rawValue = settings.weatherApiUrl.ifBlank { WeatherApiAdapter.defaultUrl(providerId) },
            path = path
        )
        val response = client.get {
            url(endpoint)
            parameter("location", location.locationId.ifBlank { coordinate(location) })
            header("X-QW-Api-Key", settings.weatherApiKey.trim())
        }
        ensureHttpSuccess(response.status.value, response.status.isSuccess())
        return response.bodyAsText()
    }

    private suspend fun requestAlerts(settings: MySettings, location: WeatherLocation): Pair<List<WeatherAlertData>, List<String>> {
        val endpoint = WeatherApiAdapter.resolveRequestUrl(
            provider = providerId,
            rawValue = settings.weatherApiUrl.ifBlank { WeatherApiAdapter.defaultUrl(providerId) },
            path = "/weatheralert/v1/current/${formatCoordinate(location.latitude)}/${formatCoordinate(location.longitude)}"
        )
        val response = client.get {
            url(endpoint)
            parameter("localTime", "true")
            header("X-QW-Api-Key", settings.weatherApiKey.trim())
        }
        ensureHttpSuccess(response.status.value, response.status.isSuccess())
        return WeatherApiAdapter.parseAlerts(response.bodyAsText())
    }
}

internal class CaiyunWeatherProviderClient(
    private val client: HttpClient
) : WeatherProviderClient {
    override val providerId: String = WeatherApiAdapter.PROVIDER_CAIYUN

    override suspend fun fetch(settings: MySettings, location: WeatherLocation): WeatherData {
        val endpoint = WeatherApiAdapter.resolveCaiyunRequestUrl(
            rawValue = settings.weatherApiUrl,
            token = settings.weatherApiKey,
            location = location
        )
        val response = client.get {
            url(endpoint)
            parameter("alert", settings.weatherWarningEnabled)
            parameter("dailysteps", 7)
            parameter("hourlysteps", 24)
            parameter("lang", "zh_CN")
            parameter("unit", "metric")
        }
        ensureHttpSuccess(response.status.value, response.status.isSuccess())
        return WeatherApiAdapter.parse(providerId, response.bodyAsText(), location).let { data ->
            if (settings.weatherWarningEnabled) data else data.copy(alerts = emptyList())
        }
    }
}

private fun coordinate(location: WeatherLocation): String {
    return "${formatCoordinate(location.longitude)},${formatCoordinate(location.latitude)}"
}

private fun formatCoordinate(value: Double): String {
    return String.format(Locale.US, "%.2f", value)
}

private fun ensureHttpSuccess(statusCode: Int, success: Boolean) {
    if (!success) throw IllegalStateException("HTTP $statusCode")
}
