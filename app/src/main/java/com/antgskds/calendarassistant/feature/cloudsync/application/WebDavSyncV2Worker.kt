package com.antgskds.calendarassistant.feature.cloudsync.application

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import java.util.concurrent.TimeUnit

class WebDavSyncV2Worker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as App
        if (!app.settingsQueryApi.settings.value.webDavSyncEnabled) return Result.success()
        return app.webDavSyncV2Center.syncNow()
            .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "webdav_v2_periodic_sync"

        fun scheduleForSettings(context: Context, settings: MySettings) {
            val manager = WorkManager.getInstance(context)
            if (!settings.webDavSyncEnabled) {
                manager.cancelUniqueWork(UNIQUE_WORK_NAME)
                return
            }
            val networkType = if (settings.webDavWifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
            val request = PeriodicWorkRequestBuilder<WebDavSyncV2Worker>(15L, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(networkType).build())
                .build()
            manager.enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
