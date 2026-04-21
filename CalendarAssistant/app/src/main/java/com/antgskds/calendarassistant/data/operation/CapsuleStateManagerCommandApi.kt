package com.antgskds.calendarassistant.data.operation

import com.antgskds.calendarassistant.core.capsule.CapsuleStateManager
import com.antgskds.calendarassistant.core.operation.CapsuleCommandApi
import com.antgskds.calendarassistant.service.capsule.NetworkSpeedMonitor

class CapsuleStateManagerCommandApi(
    private val capsuleStateManager: CapsuleStateManager
) : CapsuleCommandApi {
    override fun forceRefresh() {
        capsuleStateManager.forceRefresh()
    }

    override fun updateNetworkSpeed(speed: NetworkSpeedMonitor.NetworkSpeed?) {
        capsuleStateManager.updateNetworkSpeed(speed)
    }

    override fun showOcrProgress(title: String, content: String) {
        capsuleStateManager.showOcrProgress(title, content)
    }

    override fun showOcrResult(title: String, content: String, durationMs: Long) {
        capsuleStateManager.showOcrResult(title, content, durationMs)
    }

    override fun clearOcrCapsule() {
        capsuleStateManager.clearOcrCapsule()
    }
}
