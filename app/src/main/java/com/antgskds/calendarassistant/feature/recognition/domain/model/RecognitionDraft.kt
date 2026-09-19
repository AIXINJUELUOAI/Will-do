package com.antgskds.calendarassistant.feature.recognition.domain.model

data class RecognitionDraft(
    val title: String,
    val startTS: Long,
    val endTS: Long,
    val location: String = "",
    val description: String = "",
    val timeZone: String = "",
    val tag: String = "general",
    val qrPayload: String = ""
)
