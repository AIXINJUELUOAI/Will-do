package com.antgskds.calendarassistant.feature.api.notification.model

data class NotificationDisplaySnapshot(
    val shortText: String,
    val primaryText: String,
    val secondaryText: String? = null,
    val tertiaryText: String? = null,
    val expandedText: String? = null
)

data class NotificationAction(
    val key: String,
    val label: String,
    val payload: Map<String, String> = emptyMap()
)

data class NotificationTapTarget(
    val type: NotificationTapTargetType,
    val payload: Map<String, String> = emptyMap()
)

enum class NotificationTapTargetType {
    APP_HOME,
    SCHEDULE_DETAIL,
    PICKUP_LIST,
    SETTINGS,
    DEBUG,
    NONE
}
