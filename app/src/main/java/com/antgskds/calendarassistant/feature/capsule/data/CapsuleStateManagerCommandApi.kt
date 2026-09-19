package com.antgskds.calendarassistant.feature.capsule.data

import com.antgskds.calendarassistant.feature.capsule.application.CapsuleStateManager
import com.antgskds.calendarassistant.shared.operation.CapsuleCommandApi
import com.antgskds.calendarassistant.feature.weather.domain.model.WeatherAlertData
import com.antgskds.calendarassistant.feature.weather.domain.model.WeatherRiskAlert
import com.antgskds.calendarassistant.feature.capsule.domain.CapsuleActionSpec
import com.antgskds.calendarassistant.platform.capsule.network.NetworkSpeedMonitor

class CapsuleStateManagerCommandApi(
    private val capsuleStateManager: CapsuleStateManager
) : CapsuleCommandApi {
    override fun showAccountingResult(display: com.antgskds.calendarassistant.feature.capsule.domain.CapsuleDisplayModel) {
        capsuleStateManager.showAccountingResult(display)
    }
    override fun forceRefresh() {
        capsuleStateManager.forceRefresh()
    }

    override fun updateNetworkSpeed(speed: NetworkSpeedMonitor.NetworkSpeed?) {
        capsuleStateManager.updateNetworkSpeed(speed)
    }

    override fun showOcrProgress(
        title: String,
        content: String,
        actions: List<CapsuleActionSpec>
    ) {
        capsuleStateManager.showOcrProgress(title, content, actions)
    }

    override fun showOcrResult(
        title: String,
        content: String,
        durationMs: Long,
        actions: List<CapsuleActionSpec>
    ) {
        capsuleStateManager.showOcrResult(title, content, durationMs, actions)
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

    override fun showQuickMemoRecording(title: String, content: String) {
        capsuleStateManager.showQuickMemoRecording(title, content)
    }

    override fun clearQuickMemoRecording() {
        capsuleStateManager.clearQuickMemoRecording()
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
