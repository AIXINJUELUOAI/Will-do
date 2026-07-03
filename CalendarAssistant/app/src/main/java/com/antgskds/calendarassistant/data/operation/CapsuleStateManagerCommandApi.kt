package com.antgskds.calendarassistant.data.operation

import com.antgskds.calendarassistant.core.capsule.CapsuleStateManager
import com.antgskds.calendarassistant.core.operation.CapsuleCommandApi
import com.antgskds.calendarassistant.data.model.WeatherAlertData
import com.antgskds.calendarassistant.data.model.WeatherRiskAlert
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

    override fun showVoiceTranscription(memoId: Long, title: String, durationMs: Long) {
        capsuleStateManager.showVoiceTranscription(memoId, title, durationMs)
    }

    override fun clearVoiceTranscription() {
        capsuleStateManager.clearVoiceTranscription()
    }

    override fun showTextQuickMemo(memoId: Long, title: String, durationMs: Long) {
        capsuleStateManager.showTextQuickMemo(memoId, title, durationMs)
    }

    override fun clearTextQuickMemo() {
        capsuleStateManager.clearTextQuickMemo()
    }

    override fun showModelLoading(title: String, content: String) {
        capsuleStateManager.showModelLoading(title, content)
    }

    override fun clearModelLoading() {
        capsuleStateManager.clearModelLoading()
    }

    override fun showWeatherAlert(locationName: String, alert: WeatherAlertData) {
        capsuleStateManager.showWeatherAlert(locationName, alert)
    }

    override fun showWeatherRisk(locationName: String, risk: WeatherRiskAlert) {
        capsuleStateManager.showWeatherRisk(locationName, risk)
    }

    override fun clearWeatherCapsules() {
        capsuleStateManager.clearWeatherCapsules()
    }
}
