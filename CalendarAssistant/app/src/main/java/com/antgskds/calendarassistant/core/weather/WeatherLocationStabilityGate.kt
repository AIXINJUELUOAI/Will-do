package com.antgskds.calendarassistant.core.weather

import android.content.Context
import java.util.Locale

class WeatherLocationStabilityGate(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun shouldAllowNotifications(location: WeatherLocation, requiredHits: Int = DEFAULT_REQUIRED_HITS): Boolean {
        if (location.source == "manual") return true
        val normalizedRequiredHits = requiredHits.coerceIn(1, MAX_REQUIRED_HITS)
        if (normalizedRequiredHits <= 1) return true

        val signature = location.stabilitySignature()
        val stableSignature = prefs.getString(KEY_STABLE_SIGNATURE, null)
        if (signature == stableSignature) return true

        val lastSignature = prefs.getString(KEY_LAST_SIGNATURE, null)
        val nextHits = if (signature == lastSignature) {
            prefs.getInt(KEY_LAST_HITS, 0) + 1
        } else {
            1
        }

        prefs.edit()
            .putString(KEY_LAST_SIGNATURE, signature)
            .putInt(KEY_LAST_HITS, nextHits)
            .apply()

        if (nextHits < normalizedRequiredHits) return false

        prefs.edit()
            .putString(KEY_STABLE_SIGNATURE, signature)
            .apply()
        return true
    }

    private fun WeatherLocation.stabilitySignature(): String {
        val placeSignature = listOf(locationId, adm1, adm2, name)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("|")

        if (placeSignature.isNotBlank()) return placeSignature

        return String.format(Locale.US, "%.2f|%.2f", latitude, longitude)
    }

    companion object {
        private const val PREFS_NAME = "weather_location_stability"
        private const val KEY_LAST_SIGNATURE = "last_signature"
        private const val KEY_LAST_HITS = "last_hits"
        private const val KEY_STABLE_SIGNATURE = "stable_signature"
        private const val DEFAULT_REQUIRED_HITS = 2
        private const val MAX_REQUIRED_HITS = 3
    }
}
