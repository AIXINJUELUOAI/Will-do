package com.antgskds.calendarassistant.core.quickmemo.audio

enum class QuickMemoVoiceCaptureStatus {
    IDLE,
    RECORDING,
    CONFIRMING,
    SAVING,
    SAVED,
    TOO_SHORT,
    ERROR
}

data class QuickMemoVoiceCaptureState(
    val status: QuickMemoVoiceCaptureStatus = QuickMemoVoiceCaptureStatus.IDLE,
    val tempAudioPath: String? = null,
    val durationMs: Long = 0L,
    val message: String = ""
) {
    val isActive: Boolean get() = status != QuickMemoVoiceCaptureStatus.IDLE
}
