package com.antgskds.calendarassistant.feature.api.schedule.model

sealed interface ScheduleInstanceKey {
    val stableKey: String

    data class Single(
        val eventId: Long
    ) : ScheduleInstanceKey {
        override val stableKey: String = "single:$eventId"
    }

    data class Recurring(
        val parentId: Long,
        val occurrenceEpochSeconds: Long
    ) : ScheduleInstanceKey {
        override val stableKey: String = "rec:$parentId:$occurrenceEpochSeconds"
    }

    data class External(
        val source: String,
        val id: String
    ) : ScheduleInstanceKey {
        override val stableKey: String = "external:$source:$id"
    }
}
