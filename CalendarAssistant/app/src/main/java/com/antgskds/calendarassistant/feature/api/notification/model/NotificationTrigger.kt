package com.antgskds.calendarassistant.feature.api.notification.model

sealed interface NotificationTrigger {
    data class ByKey(
        val key: NotificationKey,
        val reason: NotificationTriggerReason = NotificationTriggerReason.SCHEDULED_ALARM
    ) : NotificationTrigger

    data class Due(
        val nowEpochMillis: Long? = null,
        val reason: NotificationTriggerReason = NotificationTriggerReason.APP_START
    ) : NotificationTrigger

    data class Debug(
        val key: NotificationKey,
        val forceImmediate: Boolean = true
    ) : NotificationTrigger
}

enum class NotificationTriggerReason {
    SCHEDULED_ALARM,
    APP_START,
    USER_ACTION,
    DEBUG,
    SYSTEM_EVENT
}
