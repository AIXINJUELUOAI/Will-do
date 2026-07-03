package com.antgskds.calendarassistant.feature.weather.domain

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

data class WeatherLocation(
    val latitude: Double,
    val longitude: Double,
    val source: String,
    val locationId: String = "",
    val name: String = "",
    val adm1: String = "",
    val adm2: String = "",
    val country: String = ""
)

class WeatherLocationProvider(context: Context) {
    private val appContext = context.applicationContext
    private val locationManager = appContext.getSystemService(LocationManager::class.java)
    private val executor by lazy { ContextCompat.getMainExecutor(appContext) }

    fun hasAnyLocationPermission(): Boolean {
        return hasFineLocationPermission() || hasCoarseLocationPermission()
    }

    suspend fun resolveCurrentLocation(): Result<WeatherLocation> {
        if (!hasAnyLocationPermission()) {
            return Result.failure(IllegalStateException("Location permission not granted"))
        }

        val providers = buildList {
            if (hasFineLocationPermission()) {
                add(LocationManager.GPS_PROVIDER)
            }
            if (hasCoarseLocationPermission() || hasFineLocationPermission()) {
                add(LocationManager.NETWORK_PROVIDER)
            }
            add(LocationManager.PASSIVE_PROVIDER)
        }.distinct()

        providers.forEach { provider ->
            val current = getCurrentLocation(provider)
            if (current != null) {
                return Result.success(
                    WeatherLocation(
                        latitude = current.latitude,
                        longitude = current.longitude,
                        source = "live:$provider"
                    )
                )
            }
        }

        providers.forEach { provider ->
            val last = getLastKnownLocation(provider)
            if (last != null) {
                return Result.success(
                    WeatherLocation(
                        latitude = last.latitude,
                        longitude = last.longitude,
                        source = "last:$provider"
                    )
                )
            }
        }

        return Result.failure(IllegalStateException("Location unavailable"))
    }

    private suspend fun getCurrentLocation(provider: String): Location? {
        if (!locationManager.isProviderEnabled(provider)) return null
        return withTimeoutOrNull(8_000L) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    suspendCancellableCoroutine { cont ->
                        val signal = CancellationSignal()
                        val resumed = AtomicBoolean(false)
                        cont.invokeOnCancellation { signal.cancel() }
                        locationManager.getCurrentLocation(provider, signal, executor) { location ->
                            if (resumed.compareAndSet(false, true)) {
                                cont.resume(location)
                            }
                        }
                    }
                } else {
                    suspendCancellableCoroutine { cont ->
                        val resumed = AtomicBoolean(false)
                        val listener = object : LocationListener {
                            override fun onLocationChanged(location: Location) {
                                if (resumed.compareAndSet(false, true)) {
                                    locationManager.removeUpdates(this)
                                    cont.resume(location)
                                }
                            }
                        }
                        cont.invokeOnCancellation {
                            locationManager.removeUpdates(listener)
                        }
                        @Suppress("DEPRECATION")
                        locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                    }
                }
            }.getOrNull()
        }
    }

    private fun getLastKnownLocation(provider: String): Location? {
        if (!locationManager.isProviderEnabled(provider)) return null
        return runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
    }

    private fun hasFineLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasCoarseLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
}
