package com.antgskds.calendarassistant.core.event.events

enum class ScheduleChangeType {
    CREATE,
    UPDATE,
    DELETE,
    ARCHIVE,
    RESTORE,
    BULK
}

enum class ScheduleChangeOrigin {
    MANUAL,
    INGEST,
    SYNC,
    IMPORT,
    SYSTEM
}

data class ScheduleChangedEvent(
    val changeType: ScheduleChangeType,
    val eventIds: List<String>,
    val origin: ScheduleChangeOrigin,
    val entityVersion: Long,
    val updatedAt: Long
)
