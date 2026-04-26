package com.antgskds.calendarassistant.core.event.events

import com.antgskds.calendarassistant.core.model.RecognitionDraft

data class RecognitionCompletedEvent(
    val sourceType: String,
    val sourceId: String,
    val candidates: List<RecognitionDraft>,
    val sourceImagePath: String? = null,
    val ingestRequested: Boolean = false,
    val modelName: String? = null,
    val confidence: Float? = null
)

data class RecognitionFailedEvent(
    val sourceType: String,
    val sourceId: String,
    val errorCode: String,
    val retryable: Boolean,
    val message: String
)
