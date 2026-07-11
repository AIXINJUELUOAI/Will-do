package com.antgskds.calendarassistant.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

class AndroidLocationProvider(context: Context) : LocationProvider {
    private val appContext = context.applicationContext
    private val locationManager = appContext.getSystemService(LocationManager::class.java)
    private val executor by lazy { ContextCompat.getMainExecutor(appContext) }

    override fun hasLocationPermission(): Boolean {
        return hasFineLocationPermission() || hasCoarseLocationPermission()
    }

    @SuppressLint("MissingPermission")
    override suspend fun getCurrentLocation(): Result<LocationFix> {
        if (!hasLocationPermission()) return Result.failure(LocationFailure.PermissionDenied)

        val providers = buildList {
            if (hasFineLocationPermission()) add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }.distinct()

        providers.forEach { provider ->
            getLiveLocation(provider)?.let { return Result.success(it.toFix(provider, LocationSource.LIVE)) }
        }
        providers.forEach { provider ->
            getLastKnownLocation(provider)?.let { return Result.success(it.toFix(provider, LocationSource.LAST_KNOWN)) }
        }

        return Result.failure(LocationFailure.Unavailable)
    }

    @SuppressLint("MissingPermission")
    private suspend fun getLiveLocation(provider: String): Location? {
        if (!isProviderEnabled(provider)) return null
        return withTimeoutOrNull(LOCATION_TIMEOUT_MILLIS) {
            runCatching {
                suspendCancellableCoroutine { continuation ->
                    val signal = CancellationSignal()
                    val resumed = AtomicBoolean(false)
                    continuation.invokeOnCancellation { signal.cancel() }
                    locationManager.getCurrentLocation(provider, signal, executor) { location ->
                        if (resumed.compareAndSet(false, true)) continuation.resume(location)
                    }
                }
            }.getOrNull()
        }
    }

    @SuppressLint("MissingPermission")
    private fun getLastKnownLocation(provider: String): Location? {
        if (!isProviderEnabled(provider)) return null
        return runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
    }

    private fun isProviderEnabled(provider: String): Boolean {
        return runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)
    }

    private fun Location.toFix(provider: String, source: LocationSource): LocationFix {
        return LocationFix(
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracy,
            timestampMillis = time,
            provider = provider,
            source = source
        )
    }

    private fun hasFineLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun hasCoarseLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    private sealed class LocationFailure(message: String) : IllegalStateException(message) {
        data object PermissionDenied : LocationFailure("Location permission not granted")
        data object Unavailable : LocationFailure("Location unavailable")
    }

    private companion object {
        const val LOCATION_TIMEOUT_MILLIS = 8_000L
    }
}
