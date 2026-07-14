package com.antgskds.calendarassistant.core.query

import com.antgskds.calendarassistant.platform.capsule.network.NetworkSpeedMonitor
import kotlinx.coroutines.flow.Flow

interface NetworkSpeedProbeQueryApi {
    fun observeDownloadSpeed(): Flow<NetworkSpeedMonitor.NetworkSpeed>
}
