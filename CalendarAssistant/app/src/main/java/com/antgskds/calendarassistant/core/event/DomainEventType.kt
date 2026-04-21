package com.antgskds.calendarassistant.core.event

object DomainEventType {
    const val RECOGNITION_COMPLETED = "recognition.completed"
    const val RECOGNITION_FAILED = "recognition.failed"
    const val INGEST_SUCCEEDED = "ingest.succeeded"
    const val INGEST_FAILED = "ingest.failed"
    const val SCHEDULE_CHANGED = "schedule.changed"
    const val CAPSULE_REFRESH_REQUESTED = "capsule.refresh.requested"
}
