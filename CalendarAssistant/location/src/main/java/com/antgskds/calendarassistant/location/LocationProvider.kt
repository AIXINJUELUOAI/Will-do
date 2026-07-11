package com.antgskds.calendarassistant.location

interface LocationProvider {
    fun hasLocationPermission(): Boolean

    suspend fun getCurrentLocation(): Result<LocationFix>
}

data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val timestampMillis: Long,
    val provider: String,
    val source: LocationSource
) {
    init {
        require(latitude in -90.0..90.0) { "Latitude out of range" }
        require(longitude in -180.0..180.0) { "Longitude out of range" }
    }
}

enum class LocationSource(val key: String) {
    LIVE("live"),
    LAST_KNOWN("last")
}
