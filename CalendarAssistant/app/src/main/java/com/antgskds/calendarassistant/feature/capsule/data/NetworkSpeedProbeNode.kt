package com.antgskds.calendarassistant.feature.capsule.data

import com.antgskds.calendarassistant.platform.capsule.network.NetworkSpeedMonitor
import kotlinx.coroutines.flow.Flow

object NetworkSpeedProbeNode {
    fun observeDownloadSpeed(): Flow<NetworkSpeedMonitor.NetworkSpeed> {
        return NetworkSpeedMonitor.monitorDownloadSpeed()
    }
}
