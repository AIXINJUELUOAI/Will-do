package com.antgskds.calendarassistant.core.query

data class NotificationPresentation(
    val timeText: String,
    val locationText: String,
    val contentText: String
)

interface NotificationPresentationQueryApi {
    fun buildPresentation(
        startTime: String,
        endTime: String,
        location: String,
        label: String,
        actionText: String
    ): NotificationPresentation
}
