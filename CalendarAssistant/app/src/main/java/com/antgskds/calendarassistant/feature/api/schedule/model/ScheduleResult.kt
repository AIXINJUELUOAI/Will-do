package com.antgskds.calendarassistant.feature.api.schedule.model

sealed interface ScheduleResult {
    data class Success(
        val key: ScheduleInstanceKey? = null,
        val snapshot: ScheduleSnapshot? = null,
        val affectedCount: Int = 1
    ) : ScheduleResult

    data class Failure(
        val reason: ScheduleFailureReason,
        val message: String? = null,
        val cause: Throwable? = null
    ) : ScheduleResult
}

enum class ScheduleFailureReason {
    NOT_FOUND,
    VALIDATION_FAILED,
    CONFLICT,
    STORAGE_FAILED,
    SYNC_FAILED,
    UNKNOWN
}
