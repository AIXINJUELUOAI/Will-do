package com.antgskds.calendarassistant.feature.api.notification.model

data class NotificationQuery(
    val key: NotificationKey? = null,
    val kind: NotificationKind? = null,
    val route: NotificationRoute? = null,
    val state: NotificationState? = null,
    val dueAtOrBeforeEpochMillis: Long? = null,
    val includeCancelled: Boolean = false,
    val limit: Int? = null
)
