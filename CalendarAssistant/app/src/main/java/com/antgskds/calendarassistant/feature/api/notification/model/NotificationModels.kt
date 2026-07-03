package com.antgskds.calendarassistant.feature.api.notification.model

enum class NotificationKind {
    SCHEDULE_REMINDER,
    RECOGNITION_STATUS,
    WEATHER_ALERT,
    SYSTEM_STATUS,
    DEBUG,
    GENERIC
}

enum class NotificationRoute {
    AUTO,
    NORMAL,
    LIVE,
    NORMAL_AND_LIVE,
    SILENT
}

enum class NotificationState {
    DRAFT,
    SCHEDULED,
    READY,
    POSTED,
    CANCELLED,
    EXPIRED,
    FAILED
}

data class NotificationBehavior(
    val triggerAtEpochMillis: Long? = null,
    val timeoutAfterMillis: Long? = null,
    val ongoing: Boolean = false,
    val autoCancel: Boolean = true,
    val onlyAlertOnce: Boolean = true,
    val allowWhileIdle: Boolean = true,
    val replaceExisting: Boolean = true,
    val priority: NotificationPriority = NotificationPriority.DEFAULT
)

enum class NotificationPriority {
    MIN,
    LOW,
    DEFAULT,
    HIGH,
    MAX
}
