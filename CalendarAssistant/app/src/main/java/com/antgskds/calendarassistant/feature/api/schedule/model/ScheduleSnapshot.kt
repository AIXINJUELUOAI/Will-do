package com.antgskds.calendarassistant.feature.api.schedule.model

data class ScheduleSnapshot(
    val key: ScheduleInstanceKey,
    val title: String,
    val startEpochSeconds: Long,
    val endEpochSeconds: Long,
    val location: String = "",
    val description: String = "",
    val timeZone: String = "",
    val tag: String = "general",
    val color: Int = 0,
    val recurrenceRule: String = "",
    val reminders: List<ScheduleReminderSpec> = emptyList(),
    val state: ScheduleState = ScheduleState.PENDING,
    val source: String = "",
    val archivedAtEpochSeconds: Long? = null,
    val lastUpdatedEpochSeconds: Long? = null,
    val metadata: Map<String, String> = emptyMap()
)

enum class ScheduleState {
    PENDING,
    COMPLETED,
    CHECKED_IN,
    UNKNOWN
}
