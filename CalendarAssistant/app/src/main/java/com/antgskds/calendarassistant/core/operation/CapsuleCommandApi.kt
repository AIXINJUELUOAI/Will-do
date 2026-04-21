package com.antgskds.calendarassistant.core.operation

import com.antgskds.calendarassistant.service.capsule.NetworkSpeedMonitor

interface CapsuleCommandApi {
    fun forceRefresh()
    fun updateNetworkSpeed(speed: NetworkSpeedMonitor.NetworkSpeed?)
    fun showOcrProgress(title: String, content: String)
    fun showOcrResult(title: String, content: String, durationMs: Long = 8000L)
    fun clearOcrCapsule()
}
