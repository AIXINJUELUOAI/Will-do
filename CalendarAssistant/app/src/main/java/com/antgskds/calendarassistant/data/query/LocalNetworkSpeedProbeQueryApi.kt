package com.antgskds.calendarassistant.data.query

import com.antgskds.calendarassistant.core.query.NetworkSpeedProbeQueryApi
import com.antgskds.calendarassistant.data.node.capsule.NetworkSpeedProbeNode
import com.antgskds.calendarassistant.service.capsule.NetworkSpeedMonitor
import kotlinx.coroutines.flow.Flow

class LocalNetworkSpeedProbeQueryApi : NetworkSpeedProbeQueryApi {
    override fun observeDownloadSpeed(): Flow<NetworkSpeedMonitor.NetworkSpeed> {
        return NetworkSpeedProbeNode.observeDownloadSpeed()
    }
}
