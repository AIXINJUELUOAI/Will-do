package com.antgskds.calendarassistant.feature.api.notification.model

sealed interface NotificationResult {
    data class Success(
        val key: NotificationKey,
        val state: NotificationState? = null
    ) : NotificationResult

    data class Failure(
        val key: NotificationKey? = null,
        val reason: NotificationFailureReason,
        val message: String? = null,
        val cause: Throwable? = null
    ) : NotificationResult
}

enum class NotificationFailureReason {
    NOT_FOUND,
    VALIDATION_FAILED,
    PERMISSION_DENIED,
    SCHEDULE_FAILED,
    PUBLISH_FAILED,
    STORAGE_FAILED,
    UNKNOWN
}
