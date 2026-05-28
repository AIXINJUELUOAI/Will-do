package com.antgskds.calendarassistant.core.center

import android.content.Context
import android.util.Log
import com.antgskds.calendarassistant.core.query.NetworkSpeedProbeQueryApi
import com.antgskds.calendarassistant.core.query.SettingsQueryApi
import com.antgskds.calendarassistant.service.receiver.DailySummaryReceiver
import com.antgskds.calendarassistant.service.receiver.KeepAliveReceiver
import com.antgskds.calendarassistant.service.receiver.ReminderReconcileReceiver
import com.antgskds.calendarassistant.service.receiver.SmsNotificationListenerService
import com.antgskds.calendarassistant.service.clipboard.ClipboardCodeMonitorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class RuntimeCenter(
    private val appContext: Context,
    private val settingsQueryApi: SettingsQueryApi,
    private val permissionCenter: PermissionCenter,
    private val floatingCenter: FloatingCenter,
    private val networkSpeedProbeQueryApi: NetworkSpeedProbeQueryApi,
    private val capsuleCenter: CapsuleCenter,
    private val appScope: CoroutineScope
) {
    companion object {
        private const val TAG = "RuntimeCenter"
    }

    private var networkSpeedMonitorJob: Job? = null
    private var clipboardCodeMonitorJob: Job? = null

    fun startAppRoutines() {
        restoreSmsNotificationListenerIfNeeded()
        startPeriodicSync()
        scheduleKeepAlive()
        scheduleReminderReconcile()
        startNetworkSpeedMonitoring()
        startEdgeBarIfNeeded()
        startClipboardCodeMonitoring()
    }

    fun restoreAfterBoot() {
        scheduleDailySummary()
        scheduleKeepAlive()
        scheduleReminderReconcile()
        startPeriodicSync()
        restoreSmsNotificationListenerIfNeeded()
        startClipboardCodeMonitoring()
    }

    fun startPeriodicSync() {
    }

    fun scheduleDailySummary() {
        DailySummaryReceiver.schedule(appContext)
    }

    fun scheduleKeepAlive() {
        KeepAliveReceiver.schedule(appContext)
    }

    fun scheduleReminderReconcile() {
        ReminderReconcileReceiver.schedule(appContext)
    }

    fun restoreSmsNotificationListenerIfNeeded() {
        try {
            val settings = settingsQueryApi.settings.value
            if (settings.isSmsMonitoringEnabled) {
                SmsNotificationListenerService.rebind(appContext)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Restore SMS notification listener failed", e)
        }
    }

    fun startNetworkSpeedMonitoring() {
        if (networkSpeedMonitorJob?.isActive == true) return

        networkSpeedMonitorJob = appScope.launch {
            settingsQueryApi.settings.collectLatest { settings ->
                if (!settings.isLiveCapsuleEnabled || !settings.isNetworkSpeedCapsuleEnabled) {
                    capsuleCenter.updateNetworkSpeed(null)
                    return@collectLatest
                }

                Log.d(TAG, "Network speed capsule enabled, start monitor")
                networkSpeedProbeQueryApi.observeDownloadSpeed().collectLatest { speed ->
                    capsuleCenter.updateNetworkSpeed(speed)
                }
            }
        }
    }

    fun startEdgeBarIfNeeded() {
        try {
            val settings = settingsQueryApi.settings.value
            if (settings.edgeBarEnabled && permissionCenter.canDrawOverlays(appContext)) {
                floatingCenter.startEdgeBarServiceIfPermitted()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Start edge bar failed", e)
        }
    }

    fun startClipboardCodeMonitoring() {
        if (clipboardCodeMonitorJob?.isActive == true) return
        clipboardCodeMonitorJob = appScope.launch {
            settingsQueryApi.settings.collectLatest { settings ->
                if (settings.clipboardCodeRecognitionEnabled) {
                    ClipboardCodeMonitorService.startIfNeeded(appContext)
                } else {
                    ClipboardCodeMonitorService.stop(appContext)
                }
            }
        }
    }
}
