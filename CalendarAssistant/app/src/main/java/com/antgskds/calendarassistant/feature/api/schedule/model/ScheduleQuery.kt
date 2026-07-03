package com.antgskds.calendarassistant.feature.api.schedule.model

data class ScheduleQuery(
    val key: ScheduleInstanceKey? = null,
    val rangeStartEpochSeconds: Long? = null,
    val rangeEndEpochSeconds: Long? = null,
    val includeArchived: Boolean = false,
    val includeRecurringInstances: Boolean = true,
    val tag: String? = null,
    val limit: Int? = null
)
