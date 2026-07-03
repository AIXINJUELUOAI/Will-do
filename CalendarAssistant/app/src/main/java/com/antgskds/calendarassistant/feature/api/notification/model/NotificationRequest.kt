package com.antgskds.calendarassistant.feature.api.notification.model

import com.antgskds.calendarassistant.feature.api.schedule.model.ScheduleInstanceKey

data class NotificationRequest(
    val key: NotificationKey,
    val kind: NotificationKind,
    val display: NotificationDisplaySnapshot,
    val route: NotificationRoute = NotificationRoute.AUTO,
    val notificationId: Int? = null,
    val smallIconResId: Int? = null,
    val scheduleInstanceKey: ScheduleInstanceKey? = null,
    val offsetMinutes: Int? = null,
    val channelKey: String? = null,
    val category: String? = null,
    val behavior: NotificationBehavior = NotificationBehavior(),
    val tapTarget: NotificationTapTarget? = null,
    val actions: List<NotificationAction> = emptyList(),
    val source: String? = null,
    val metadata: Map<String, String> = emptyMap()
)
