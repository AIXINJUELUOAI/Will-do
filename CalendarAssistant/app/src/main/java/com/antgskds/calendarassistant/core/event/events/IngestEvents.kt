package com.antgskds.calendarassistant.core.event.events

data class IngestSucceededEvent(
    val sourceType: String,
    val sourceId: String,
    val createdEventIds: List<String>,
    val dedupedCount: Int,
    val createdCount: Int
)

data class IngestFailedEvent(
    val sourceType: String,
    val sourceId: String,
    val stage: String,
    val errorCode: String,
    val retryable: Boolean,
    val message: String
)
