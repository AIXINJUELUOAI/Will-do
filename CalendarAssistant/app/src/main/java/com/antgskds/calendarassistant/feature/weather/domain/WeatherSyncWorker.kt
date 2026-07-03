package com.antgskds.calendarassistant.feature.weather.domain

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.data.repository.SettingsRepository
import java.util.concurrent.TimeUnit

class WeatherSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val settings = SettingsRepository(applicationContext).loadSettings()
            if (!settings.hasWeatherConfig()) {
                Result.success()
            } else {
                WeatherRepository.getInstance(applicationContext).forceRefresh(settings)
                Result.success()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Weather worker failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "WeatherSyncWorker"
        private const val UNIQUE_WORK_NAME = "weather_periodic_sync"

        fun syncForSettings(context: Context, settings: MySettings) {
            val workManager = WorkManager.getInstance(context)
            if (!settings.hasWeatherConfig()) {
                workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
                return
            }

            val request = PeriodicWorkRequestBuilder<WeatherSyncWorker>(
                WeatherRepository.normalizeRefreshIntervalMinutes(settings.weatherRefreshInterval).toLong(),
                TimeUnit.MINUTES
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            workManager.enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
