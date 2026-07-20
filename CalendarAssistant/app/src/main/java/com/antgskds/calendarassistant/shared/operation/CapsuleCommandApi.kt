package com.antgskds.calendarassistant.shared.operation

import com.antgskds.calendarassistant.platform.capsule.network.NetworkSpeedMonitor
import com.antgskds.calendarassistant.feature.weather.domain.model.WeatherAlertData
import com.antgskds.calendarassistant.feature.weather.domain.model.WeatherRiskAlert
import com.antgskds.calendarassistant.feature.capsule.domain.CapsuleActionSpec

interface CapsuleCommandApi {
    fun forceRefresh()
    fun updateNetworkSpeed(speed: NetworkSpeedMonitor.NetworkSpeed?)
    fun showOcrProgress(
        title: String,
        content: String,
        actions: List<CapsuleActionSpec> = emptyList()
    )
    fun showOcrResult(
        title: String,
        content: String,
        durationMs: Long = 8000L,
        actions: List<CapsuleActionSpec> = emptyList()
    )
    fun clearOcrCapsule()
    fun showVoiceTranscription(memoId: Long, title: String, durationMs: Long = 0L)
    fun clearVoiceTranscription()
    fun showTextQuickMemo(memoId: Long, title: String, durationMs: Long = 0L)
    fun clearTextQuickMemo()
    fun showQuickMemoRecording(title: String, content: String = "松开保存")
    fun clearQuickMemoRecording()
    fun showModelLoading(title: String, content: String)
    fun clearModelLoading()
    fun showWeatherAlert(locationName: String, alert: WeatherAlertData)
    fun showWeatherRisk(locationName: String, risk: WeatherRiskAlert)
    fun clearWeatherCapsules()
}
