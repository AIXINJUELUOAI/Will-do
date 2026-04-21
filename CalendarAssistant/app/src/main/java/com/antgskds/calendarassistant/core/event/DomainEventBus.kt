package com.antgskds.calendarassistant.core.event

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import java.util.UUID

class DomainEventBus {
    private val events = MutableSharedFlow<DomainEventEnvelope<*>>(extraBufferCapacity = 64)

    fun allEvents(): Flow<DomainEventEnvelope<*>> = events.asSharedFlow()

    suspend fun emit(envelope: DomainEventEnvelope<*>) {
        events.emit(envelope)
    }

    suspend fun <T : Any> emit(
        eventType: String,
        traceId: String,
        source: String,
        entityKey: String,
        payload: T,
        version: Int = 1,
        occurredAt: Long = System.currentTimeMillis()
    ) {
        emit(
            DomainEventEnvelope(
                eventId = UUID.randomUUID().toString(),
                eventType = eventType,
                occurredAt = occurredAt,
                traceId = traceId,
                source = source,
                entityKey = entityKey,
                version = version,
                payload = payload
            )
        )
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : Any> eventsOfType(eventType: String): Flow<DomainEventEnvelope<T>> {
        return allEvents()
            .filter { it.eventType == eventType && it.payload is T }
            .map { it as DomainEventEnvelope<T> }
    }
}
