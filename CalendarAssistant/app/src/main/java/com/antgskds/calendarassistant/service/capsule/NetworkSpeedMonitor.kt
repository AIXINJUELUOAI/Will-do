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
            bytesPerSecond < 1024L -> "${bytesPerSecond}B/s"
            bytesPerSecond < 1024L * 1000L -> "${bytesPerSecond / 1024}KB/s"
            else -> {
                val megaBytes = bytesPerSecond / (1024.0 * 1024.0)
                if (megaBytes >= 1000.0) {
                    String.format("%.1fGB/s", megaBytes / 1024.0)
                } else {
                    String.format("%.1fMB/s", megaBytes)
                }
            }
        }
    }
}
