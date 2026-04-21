package com.antgskds.calendarassistant.data.node.capsule

import com.antgskds.calendarassistant.service.capsule.NetworkSpeedMonitor
import kotlinx.coroutines.flow.Flow

object NetworkSpeedProbeNode {
    fun observeDownloadSpeed(): Flow<NetworkSpeedMonitor.NetworkSpeed> {
        return NetworkSpeedMonitor.monitorDownloadSpeed()
    }
}
