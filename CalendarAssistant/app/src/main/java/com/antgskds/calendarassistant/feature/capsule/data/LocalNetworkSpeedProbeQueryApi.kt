package com.antgskds.calendarassistant.feature.capsule.data

import com.antgskds.calendarassistant.shared.query.NetworkSpeedProbeQueryApi
import com.antgskds.calendarassistant.feature.capsule.data.NetworkSpeedProbeNode
import com.antgskds.calendarassistant.platform.capsule.network.NetworkSpeedMonitor
import kotlinx.coroutines.flow.Flow

class LocalNetworkSpeedProbeQueryApi : NetworkSpeedProbeQueryApi {
    override fun observeDownloadSpeed(): Flow<NetworkSpeedMonitor.NetworkSpeed> {
        return NetworkSpeedProbeNode.observeDownloadSpeed()
    }
}
