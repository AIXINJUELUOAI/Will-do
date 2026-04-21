package com.antgskds.calendarassistant.core.event

data class DomainEventEnvelope<T : Any>(
    val eventId: String,
    val eventType: String,
    val occurredAt: Long,
    val traceId: String,
    val source: String,
    val entityKey: String,
    val version: Int,
    val payload: T
)
