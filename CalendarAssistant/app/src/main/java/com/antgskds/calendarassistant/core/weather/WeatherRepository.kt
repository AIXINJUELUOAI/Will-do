package com.antgskds.calendarassistant.core.weather

import android.content.Context
import android.util.Log
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.data.model.WeatherData
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Locale

class WeatherRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }
    private val mutex = Mutex()
    private val client by lazy { HttpClient(Android) }
    private val locationProvider by lazy { WeatherLocationProvider(appContext) }
    private val _weatherData = MutableStateFlow(loadCachedWeather())
    val weatherData: StateFlow<WeatherData?> = _weatherData.asStateFlow()

    suspend fun refreshIfNeeded(settings: MySettings): Result<WeatherData?> {
        if (!settings.hasWeatherConfig()) return Result.success(null)
        val cached = _weatherData.value
        if (cached != null && !isExpired(cached, settings.weatherRefreshInterval)) {
            return Result.success(cached)
        }
        val refreshed = forceRefresh(settings)
        return if (refreshed.isSuccess) {
            Result.success(refreshed.getOrNull())
        } else {
            Result.failure(refreshed.exceptionOrNull() ?: IllegalStateException("Weather refresh failed"))
        }
    }

    suspend fun forceRefresh(settings: MySettings): Result<WeatherData> = mutex.withLock {
        try {
            if (!settings.hasWeatherConfig()) {
                Result.failure(IllegalStateException("Weather not configured"))
            } else {
                val requestLocation = resolveRequestLocation()
                val rawBody = requestWeather(settings, requestLocation)
                val cityLabel = if (requestLocation.source == "cached") "最近位置" else "当前位置"
                val parsed = WeatherApiAdapter.parse(WeatherApiAdapter.PROVIDER_QWEATHER, rawBody, cityLabel)
                saveCachedWeather(parsed)
                _weatherData.value = parsed
                Result.success(parsed)
            }
        } catch (e: Exception) {
            Log.e(TAG, "refresh weather failed", e)
            Result.failure(e)
        }
    }

    private suspend fun requestWeather(settings: MySettings, requestLocation: WeatherLocation): String {
        val endpoint = WeatherApiAdapter.resolveRequestUrl(
            provider = WeatherApiAdapter.PROVIDER_QWEATHER,
            rawValue = settings.weatherApiUrl.ifBlank { WeatherApiAdapter.defaultUrl(WeatherApiAdapter.PROVIDER_QWEATHER) }
        )
        val response: HttpResponse = client.get {
            url(endpoint)
            parameter("location", toCoordinateParam(requestLocation))
            header("X-QW-Api-Key", settings.weatherApiKey.trim())
        }
        if (!response.status.isSuccess()) {
            throw IllegalStateException("HTTP ${response.status.value}")
        }
        return response.bodyAsText()
    }

    private suspend fun resolveRequestLocation(): WeatherLocation {
        val currentResult = locationProvider.resolveCurrentLocation()
        if (currentResult.isSuccess) {
            val current = currentResult.getOrThrow()
            saveCachedLocation(current)
            return current
        }

        val cached = loadCachedLocation()
        if (cached != null) {
            return cached.copy(source = "cached")
        }

        throw currentResult.exceptionOrNull() ?: IllegalStateException("Location unavailable")
    }

    private fun toCoordinateParam(location: WeatherLocation): String {
        return String.format(Locale.US, "%.6f,%.6f", location.longitude, location.latitude)
    }

    private fun loadCachedWeather(): WeatherData? {
        val raw = prefs.getString(KEY_WEATHER_JSON, null) ?: return null
        return runCatching { json.decodeFromString<WeatherData>(raw) }.getOrNull()
    }

    private fun saveCachedWeather(data: WeatherData) {
        prefs.edit().putString(KEY_WEATHER_JSON, json.encodeToString(data)).apply()
    }

    private fun saveCachedLocation(location: WeatherLocation) {
        prefs.edit()
            .putString(KEY_LOCATION_LAT, location.latitude.toString())
            .putString(KEY_LOCATION_LON, location.longitude.toString())
            .putString(KEY_LOCATION_SOURCE, location.source)
            .putLong(KEY_LOCATION_TIME, System.currentTimeMillis())
            .apply()
    }

    private fun loadCachedLocation(): WeatherLocation? {
        val lat = prefs.getString(KEY_LOCATION_LAT, null)?.toDoubleOrNull() ?: return null
        val lon = prefs.getString(KEY_LOCATION_LON, null)?.toDoubleOrNull() ?: return null
        val source = prefs.getString(KEY_LOCATION_SOURCE, "cached") ?: "cached"
        return WeatherLocation(latitude = lat, longitude = lon, source = source)
    }

    companion object {
        private const val TAG = "WeatherRepository"
        private const val PREFS_NAME = "weather_cache"
        private const val KEY_WEATHER_JSON = "weather_json"
        private const val KEY_LOCATION_LAT = "location_lat"
        private const val KEY_LOCATION_LON = "location_lon"
        private const val KEY_LOCATION_SOURCE = "location_source"
        private const val KEY_LOCATION_TIME = "location_time"

        @Volatile
        private var INSTANCE: WeatherRepository? = null

        fun getInstance(context: Context): WeatherRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WeatherRepository(context).also { INSTANCE = it }
            }
        }

        fun isExpired(data: WeatherData, intervalHours: Int): Boolean {
            if (data.updateTime <= 0L) return true
            val intervalMillis = intervalHours.coerceAtLeast(1) * 60L * 60L * 1000L
            return System.currentTimeMillis() - data.updateTime >= intervalMillis
        }
    }
}

fun MySettings.hasWeatherConfig(): Boolean {
    return weatherEnabled && weatherApiKey.isNotBlank() && weatherApiUrl.isNotBlank()
}
