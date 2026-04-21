package com.antgskds.calendarassistant.core.query

import com.antgskds.calendarassistant.service.capsule.NetworkSpeedMonitor
import kotlinx.coroutines.flow.Flow

interface NetworkSpeedProbeQueryApi {
    fun observeDownloadSpeed(): Flow<NetworkSpeedMonitor.NetworkSpeed>
}
