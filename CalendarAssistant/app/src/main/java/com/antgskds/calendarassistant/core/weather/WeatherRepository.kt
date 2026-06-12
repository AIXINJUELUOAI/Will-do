package com.antgskds.calendarassistant.core.weather

import android.content.Context
import android.util.Log
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.data.model.WeatherAlertData
import com.antgskds.calendarassistant.data.model.WeatherData
import com.antgskds.calendarassistant.data.model.displayLocationName
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
    private val locationStabilityGate by lazy { WeatherLocationStabilityGate(appContext) }
    private val notifier by lazy { WeatherNotifier(appContext) }
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
                val requestLocation = resolveRequestLocation(settings)
                val resolvedLocation = enrichLocation(settings, requestLocation)
                val rawBody = requestWeather(settings, resolvedLocation, "/v7/weather/now")
                val hourly = runCatching {
                    WeatherApiAdapter.parseHourly(requestWeather(settings, resolvedLocation, "/v7/weather/24h"))
                }.getOrDefault(emptyList())
                val daily = runCatching {
                    WeatherApiAdapter.parseDaily(requestWeather(settings, resolvedLocation, "/v7/weather/7d"))
                }.getOrDefault(emptyList())
                val (alerts, attributions) = if (settings.weatherWarningEnabled) {
                    runCatching { requestAlerts(settings, resolvedLocation) }.getOrDefault(emptyList<WeatherAlertData>() to emptyList())
                } else {
                    emptyList<WeatherAlertData>() to emptyList()
                }
                val dedupedAlerts = dedupeAlerts(alerts, resolvedLocation)
                val risks = if (settings.weatherRiskWarningEnabled) {
                    WeatherRiskAnalyzer.analyze(hourly, settings.weatherWarningLookaheadHours)
                        .filterNot { risk -> riskCategory(risk.title, risk.weatherText) in officialRiskCategories(dedupedAlerts) }
                } else {
                    emptyList()
                }
                val parsed = WeatherApiAdapter.parse(WeatherApiAdapter.PROVIDER_QWEATHER, rawBody, resolvedLocation).copy(
                    hourlyForecast = hourly,
                    dailyForecast = daily,
                    alerts = dedupedAlerts,
                    riskAlerts = risks,
                    attributions = attributions
                )
                saveCachedWeather(parsed)
                _weatherData.value = parsed
                if (
                    (settings.weatherWarningEnabled || settings.weatherRiskWarningEnabled) &&
                    locationStabilityGate.shouldAllowNotifications(
                        resolvedLocation,
                        settings.weatherLocationStabilityRequiredHits
                    )
                ) {
                    notifier.notifyAlerts(
                        locationName = parsed.displayLocationName(),
                        alerts = dedupedAlerts,
                        risks = risks,
                        showLiveNotification = settings.isLiveCapsuleEnabled
                    )
                }
                Result.success(parsed)
            }
        } catch (e: Exception) {
            Log.e(TAG, "refresh weather failed", e)
            Result.failure(e)
        }
    }

    private suspend fun requestWeather(settings: MySettings, requestLocation: WeatherLocation, path: String): String {
        val endpoint = WeatherApiAdapter.resolveRequestUrl(
            provider = WeatherApiAdapter.PROVIDER_QWEATHER,
            rawValue = settings.weatherApiUrl.ifBlank { WeatherApiAdapter.defaultUrl(WeatherApiAdapter.PROVIDER_QWEATHER) },
            path = path
        )
        val response: HttpResponse = client.get {
            url(endpoint)
            parameter("location", toLocationParam(requestLocation))
            header("X-QW-Api-Key", settings.weatherApiKey.trim())
        }
        if (!response.status.isSuccess()) {
            throw IllegalStateException("HTTP ${response.status.value}")
        }
        return response.bodyAsText()
    }

    private suspend fun requestAlerts(settings: MySettings, requestLocation: WeatherLocation): Pair<List<WeatherAlertData>, List<String>> {
        val endpoint = WeatherApiAdapter.resolveRequestUrl(
            provider = WeatherApiAdapter.PROVIDER_QWEATHER,
            rawValue = settings.weatherApiUrl.ifBlank { WeatherApiAdapter.defaultUrl(WeatherApiAdapter.PROVIDER_QWEATHER) },
            path = "/weatheralert/v1/current/${formatCoordinate(requestLocation.latitude)}/${formatCoordinate(requestLocation.longitude)}"
        )
        val response: HttpResponse = client.get {
            url(endpoint)
            parameter("localTime", "true")
            header("X-QW-Api-Key", settings.weatherApiKey.trim())
        }
        if (!response.status.isSuccess()) {
            throw IllegalStateException("HTTP ${response.status.value}")
        }
        return WeatherApiAdapter.parseAlerts(response.bodyAsText())
    }

    private suspend fun requestGeoLocation(settings: MySettings, query: String): WeatherLocation? {
        val endpoint = WeatherApiAdapter.resolveRequestUrl(
            provider = WeatherApiAdapter.PROVIDER_QWEATHER,
            rawValue = settings.weatherApiUrl.ifBlank { WeatherApiAdapter.defaultUrl(WeatherApiAdapter.PROVIDER_QWEATHER) },
            path = "/geo/v2/city/lookup"
        )
        val response: HttpResponse = client.get {
            url(endpoint)
            parameter("location", query)
            parameter("number", "1")
            parameter("lang", "zh")
            header("X-QW-Api-Key", settings.weatherApiKey.trim())
        }
        if (!response.status.isSuccess()) {
            throw IllegalStateException("HTTP ${response.status.value}")
        }
        return WeatherApiAdapter.parseGeoLocation(response.bodyAsText())
    }

    private fun dedupeAlerts(alerts: List<WeatherAlertData>, location: WeatherLocation): List<WeatherAlertData> {
        return alerts
            .groupBy { WeatherWarningText.officialTitle(it) }
            .values
            .mapNotNull { group ->
                group.maxWithOrNull(
                    compareBy<WeatherAlertData> { alert -> alert.senderScore(location) }
                        .thenBy { alert -> alert.issuedTime.ifBlank { alert.effectiveTime } }
                )
            }
    }

    private fun WeatherAlertData.senderScore(location: WeatherLocation): Int {
        val sender = senderName
        return when {
            location.name.isNotBlank() && sender.contains(location.name.removeSuffix("区").removeSuffix("县")) -> 3
            location.adm2.isNotBlank() && sender.contains(location.adm2.removeSuffix("市")) -> 2
            location.adm1.isNotBlank() && sender.contains(location.adm1.removeSuffix("省").removeSuffix("市")) -> 1
            else -> 0
        }
    }

    private fun officialRiskCategories(alerts: List<WeatherAlertData>): Set<String> {
        return alerts.flatMap { alert ->
            val value = listOf(alert.eventName, alert.headline, alert.description).joinToString(" ")
            buildList {
                if (value.contains("高温")) add("heat")
                if (value.contains("低温") || value.contains("寒潮") || value.contains("强降温") || value.contains("寒冷") || value.contains("霜冻") || value.contains("冰冻")) add("cold")
                if (value.contains("雷") || value.contains("强对流") || value.contains("冰雹") || value.contains("雹")) add("thunder")
                if (value.contains("暴雨") || value.contains("降雨") || value.contains("雨")) add("rain")
                if (value.contains("大风") || value.contains("台风") || value.contains("风")) add("wind")
                if (value.contains("雪") || value.contains("冻雨") || value.contains("结冰")) add("snow")
                if (value.contains("雾") || value.contains("霾") || value.contains("沙") || value.contains("尘")) add("visibility")
            }
        }.toSet()
    }

    private fun riskCategory(title: String, text: String): String {
        val value = "$title $text"
        return when {
            value.contains("高温") -> "heat"
            value.contains("低温") || value.contains("寒潮") || value.contains("强降温") || value.contains("寒冷") || value.contains("霜冻") || value.contains("冰冻") -> "cold"
            value.contains("雷") || value.contains("强对流") || value.contains("冰雹") || value.contains("雹") -> "thunder"
            value.contains("雪") || value.contains("冻雨") || value.contains("结冰") -> "snow"
            value.contains("雨") || value.contains("降雨") -> "rain"
            value.contains("风") || value.contains("台风") -> "wind"
            value.contains("雾") || value.contains("霾") || value.contains("沙") || value.contains("尘") -> "visibility"
            else -> value
        }
    }

    private suspend fun resolveRequestLocation(settings: MySettings): WeatherLocation {
        val mode = normalizeLocationMode(settings.weatherLocationMode)
        if (mode == LOCATION_MODE_MANUAL) {
            return manualLocation(settings) ?: throw IllegalStateException("Manual weather location not selected")
        }

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

    private suspend fun enrichLocation(settings: MySettings, location: WeatherLocation): WeatherLocation {
        if (location.name.isNotBlank() && location.locationId.isNotBlank()) return location
        if (location.source == "manual") return location

        val query = toCoordinateParam(location)
        return runCatching { requestGeoLocation(settings, query) }
            .getOrNull()
            ?.copy(source = location.source)
            ?: location
    }

    private fun manualLocation(settings: MySettings): WeatherLocation? {
        if (settings.weatherManualLocationId.isBlank() && (settings.weatherManualLat == 0.0 || settings.weatherManualLon == 0.0)) return null
        return WeatherLocation(
            latitude = settings.weatherManualLat,
            longitude = settings.weatherManualLon,
            source = "manual",
            locationId = settings.weatherManualLocationId,
            name = settings.weatherManualLocationName,
            adm1 = settings.weatherManualAdm1,
            adm2 = settings.weatherManualAdm2,
            country = settings.weatherManualCountry
        )
    }

    private fun toLocationParam(location: WeatherLocation): String {
        return location.locationId.ifBlank { toCoordinateParam(location) }
    }

    private fun toCoordinateParam(location: WeatherLocation): String {
        return "${formatCoordinate(location.longitude)},${formatCoordinate(location.latitude)}"
    }

    private fun formatCoordinate(value: Double): String {
        return String.format(Locale.US, "%.2f", value)
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
            .putString(KEY_LOCATION_ID, location.locationId)
            .putString(KEY_LOCATION_NAME, location.name)
            .putString(KEY_LOCATION_ADM1, location.adm1)
            .putString(KEY_LOCATION_ADM2, location.adm2)
            .putString(KEY_LOCATION_COUNTRY, location.country)
            .putLong(KEY_LOCATION_TIME, System.currentTimeMillis())
            .apply()
    }

    private fun loadCachedLocation(): WeatherLocation? {
        val lat = prefs.getString(KEY_LOCATION_LAT, null)?.toDoubleOrNull() ?: return null
        val lon = prefs.getString(KEY_LOCATION_LON, null)?.toDoubleOrNull() ?: return null
        val source = prefs.getString(KEY_LOCATION_SOURCE, "cached") ?: "cached"
        return WeatherLocation(
            latitude = lat,
            longitude = lon,
            source = source,
            locationId = prefs.getString(KEY_LOCATION_ID, "").orEmpty(),
            name = prefs.getString(KEY_LOCATION_NAME, "").orEmpty(),
            adm1 = prefs.getString(KEY_LOCATION_ADM1, "").orEmpty(),
            adm2 = prefs.getString(KEY_LOCATION_ADM2, "").orEmpty(),
            country = prefs.getString(KEY_LOCATION_COUNTRY, "").orEmpty()
        )
    }

    fun clearCache() {
        prefs.edit().remove(KEY_WEATHER_JSON).apply()
        _weatherData.value = null
    }

    private fun normalizeLocationMode(mode: String): String {
        return if (mode == LOCATION_MODE_MANUAL) LOCATION_MODE_MANUAL else LOCATION_MODE_AUTO
    }

    companion object {
        private const val TAG = "WeatherRepository"
        private const val PREFS_NAME = "weather_cache"
        private const val KEY_WEATHER_JSON = "weather_json"
        private const val KEY_LOCATION_LAT = "location_lat"
        private const val KEY_LOCATION_LON = "location_lon"
        private const val KEY_LOCATION_SOURCE = "location_source"
        private const val KEY_LOCATION_ID = "location_id"
        private const val KEY_LOCATION_NAME = "location_name"
        private const val KEY_LOCATION_ADM1 = "location_adm1"
        private const val KEY_LOCATION_ADM2 = "location_adm2"
        private const val KEY_LOCATION_COUNTRY = "location_country"
        private const val KEY_LOCATION_TIME = "location_time"
        const val LOCATION_MODE_AUTO = "auto"
        const val LOCATION_MODE_MANUAL = "manual"
        const val LOCATION_MODE_AUTO_FALLBACK_MANUAL = "auto_fallback_manual"

        @Volatile
        private var INSTANCE: WeatherRepository? = null

        fun getInstance(context: Context): WeatherRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WeatherRepository(context).also { INSTANCE = it }
            }
        }

        fun isExpired(data: WeatherData, intervalMinutes: Int): Boolean {
            if (data.updateTime <= 0L) return true
            val intervalMillis = normalizeRefreshIntervalMinutes(intervalMinutes) * 60L * 1000L
            return System.currentTimeMillis() - data.updateTime >= intervalMillis
        }

        fun normalizeRefreshIntervalMinutes(value: Int): Int {
            return when (value) {
                1 -> 15
                3 -> 30
                6 -> 60
                in 1..14 -> 15
                in 15..29 -> 15
                in 30..59 -> 30
                else -> 60
            }
        }
    }
}

fun MySettings.hasWeatherConfig(): Boolean {
    return weatherEnabled && weatherApiKey.isNotBlank() && weatherApiUrl.isNotBlank()
}
