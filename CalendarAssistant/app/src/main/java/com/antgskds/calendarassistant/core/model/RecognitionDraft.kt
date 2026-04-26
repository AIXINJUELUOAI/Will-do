package com.antgskds.calendarassistant.core.model

data class RecognitionDraft(
    val title: String,
    val startTS: Long,
    val endTS: Long,
    val location: String = "",
    val description: String = "",
    val timeZone: String = "",
    val tag: String = "general"
)
