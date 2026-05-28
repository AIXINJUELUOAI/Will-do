package com.antgskds.calendarassistant.core.center

import com.antgskds.calendarassistant.core.operation.CapsuleCommandApi
import com.antgskds.calendarassistant.core.query.CapsuleQueryApi
import com.antgskds.calendarassistant.data.model.WeatherAlertData
import com.antgskds.calendarassistant.data.model.WeatherRiskAlert
import com.antgskds.calendarassistant.data.state.CapsuleUiState
import com.antgskds.calendarassistant.service.capsule.NetworkSpeedMonitor

class CapsuleCenter(
    private val capsuleCommandApi: CapsuleCommandApi,
    private val capsuleQueryApi: CapsuleQueryApi
) {
    val uiState = capsuleQueryApi.uiState

    fun currentState(): CapsuleUiState {
        return capsuleQueryApi.uiState.value
    }

    fun forceRefresh() {
        capsuleCommandApi.forceRefresh()
    }

    fun updateNetworkSpeed(speed: NetworkSpeedMonitor.NetworkSpeed?) {
        capsuleCommandApi.updateNetworkSpeed(speed)
    }

    fun showOcrProgress(title: String, content: String) {
        capsuleCommandApi.showOcrProgress(title, content)
    }

    fun showOcrResult(title: String, content: String, durationMs: Long = 8000L) {
        capsuleCommandApi.showOcrResult(title, content, durationMs)
    }

    fun clearOcrCapsule() {
        capsuleCommandApi.clearOcrCapsule()
    }

    fun showModelLoading(title: String, content: String) {
        capsuleCommandApi.showModelLoading(title, content)
    }

    fun clearModelLoading() {
        capsuleCommandApi.clearModelLoading()
    }

    fun showWeatherAlert(locationName: String, alert: WeatherAlertData) {
        capsuleCommandApi.showWeatherAlert(locationName, alert)
    }

    fun showWeatherRisk(locationName: String, risk: WeatherRiskAlert) {
        capsuleCommandApi.showWeatherRisk(locationName, risk)
    }

    fun clearWeatherCapsules() {
        capsuleCommandApi.clearWeatherCapsules()
    }
}
