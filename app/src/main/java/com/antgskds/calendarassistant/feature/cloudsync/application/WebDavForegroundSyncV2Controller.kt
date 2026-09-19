package com.antgskds.calendarassistant.feature.cloudsync.application

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import com.antgskds.calendarassistant.shared.query.SettingsQueryApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class WebDavForegroundSyncV2Controller(
    context: Context,
    private val appScope: CoroutineScope,
    private val settingsQueryApi: SettingsQueryApi,
    private val coordinator: WebDavSyncV2Coordinator,
) {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    private val wakeups = Channel<Unit>(Channel.CONFLATED)
    private var pollingJob: Job? = null
    private var settingsJob: Job? = null

    val isRunning: Boolean get() = pollingJob?.isActive == true

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            requestImmediateSync()
        }
    }

    init {
        runCatching { connectivityManager.registerDefaultNetworkCallback(networkCallback) }
    }

    fun start() {
        if (isRunning) return
        settingsJob = appScope.launch {
            settingsQueryApi.settings
                .map { Triple(it.webDavSyncEnabled, it.webDavWifiOnly, it.webDavForegroundSyncIntervalSeconds) }
                .distinctUntilChanged()
                .drop(1)
                .collect { requestImmediateSync() }
        }
        pollingJob = appScope.launch {
            while (true) {
                while (wakeups.tryReceive().isSuccess) {
                    // Drain stale wakeups before calculating the next polling delay.
                }
                if (settingsQueryApi.settings.value.webDavSyncEnabled) coordinator.syncNow()
                val seconds = settingsQueryApi.settings.value.webDavForegroundSyncIntervalSeconds
                    .coerceIn(MIN_INTERVAL_SECONDS, MAX_INTERVAL_SECONDS)
                withTimeoutOrNull(seconds * 1_000L) { wakeups.receive() }
            }
        }
    }

    fun stop() {
        pollingJob?.cancel()
        pollingJob = null
        settingsJob?.cancel()
        settingsJob = null
        while (wakeups.tryReceive().isSuccess) {
            // Leave no pending wakeup for the next foreground session.
        }
    }

    fun requestImmediateSync() {
        if (isRunning) wakeups.trySend(Unit)
    }

    companion object {
        const val MIN_INTERVAL_SECONDS = 1
        const val MAX_INTERVAL_SECONDS = 300
        const val DEFAULT_INTERVAL_SECONDS = 30
    }
}
