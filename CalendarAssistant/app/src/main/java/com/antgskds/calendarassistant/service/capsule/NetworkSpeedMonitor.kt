package com.antgskds.calendarassistant.service.capsule

import android.net.TrafficStats
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

object NetworkSpeedMonitor {

    private const val TAG = "NetworkSpeedMonitor"
    private const val UPDATE_INTERVAL_MS = 1000L

    data class NetworkSpeed(
        val downloadSpeed: Long,
        val formattedSpeed: String
    )

    fun monitorDownloadSpeed(): Flow<NetworkSpeed> = flow {
        var lastRxBytes = TrafficStats.getTotalRxBytes()

        while (true) {
            delay(UPDATE_INTERVAL_MS)

            val currentRxBytes = TrafficStats.getTotalRxBytes()
            val downloadSpeed = if (currentRxBytes > lastRxBytes) {
                currentRxBytes - lastRxBytes
            } else {
                0L
            }
            lastRxBytes = currentRxBytes

            val formattedSpeed = formatSpeed(downloadSpeed)
            emit(NetworkSpeed(downloadSpeed, formattedSpeed))
        }
    }

    fun getCurrentDownloadSpeed(): NetworkSpeed {
        return NetworkSpeed(0L, formatSpeed(0L))
    }

    private fun formatSpeed(bytesPerSecond: Long): String {
        return when {
            bytesPerSecond < 1024 -> "$bytesPerSecond B/s"
            bytesPerSecond < 1024 * 1024 -> String.format("%.1f KB/s", bytesPerSecond / 1024.0)
            bytesPerSecond < 1024 * 1024 * 1024 -> String.format("%.1f MB/s", bytesPerSecond / (1024.0 * 1024.0))
            else -> String.format("%.2f GB/s", bytesPerSecond / (1024.0 * 1024.0 * 1024.0))
        }
    }
}
