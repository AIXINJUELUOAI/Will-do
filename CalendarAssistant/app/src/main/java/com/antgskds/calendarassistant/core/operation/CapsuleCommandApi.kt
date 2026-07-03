package com.antgskds.calendarassistant.core.operation

import com.antgskds.calendarassistant.service.capsule.NetworkSpeedMonitor
import com.antgskds.calendarassistant.data.model.WeatherAlertData
import com.antgskds.calendarassistant.data.model.WeatherRiskAlert

interface CapsuleCommandApi {
    fun forceRefresh()
    fun updateNetworkSpeed(speed: NetworkSpeedMonitor.NetworkSpeed?)
    fun showOcrProgress(title: String, content: String)
    fun showOcrResult(title: String, content: String, durationMs: Long = 8000L)
    fun clearOcrCapsule()
    fun showVoiceTranscription(memoId: Long, title: String, durationMs: Long = 0L)
    fun clearVoiceTranscription()
    fun showTextQuickMemo(memoId: Long, title: String, durationMs: Long = 0L)
    fun clearTextQuickMemo()
    fun showModelLoading(title: String, content: String)
    fun clearModelLoading()
    fun showWeatherAlert(locationName: String, alert: WeatherAlertData)
    fun showWeatherRisk(locationName: String, risk: WeatherRiskAlert)
    fun clearWeatherCapsules()
}
