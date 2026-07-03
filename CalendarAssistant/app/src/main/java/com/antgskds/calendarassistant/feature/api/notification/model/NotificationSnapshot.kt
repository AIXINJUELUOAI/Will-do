package com.antgskds.calendarassistant.feature.api.notification.model

import com.antgskds.calendarassistant.feature.api.schedule.model.ScheduleInstanceKey

data class NotificationSnapshot(
    val key: NotificationKey,
    val kind: NotificationKind,
    val state: NotificationState,
    val route: NotificationRoute,
    val display: NotificationDisplaySnapshot,
    val notificationId: Int? = null,
    val smallIconResId: Int? = null,
    val scheduleInstanceKey: ScheduleInstanceKey? = null,
    val offsetMinutes: Int? = null,
    val channelKey: String? = null,
    val category: String? = null,
    val behavior: NotificationBehavior = NotificationBehavior(),
    val tapTarget: NotificationTapTarget? = null,
    val actions: List<NotificationAction> = emptyList(),
    val updatedAtEpochMillis: Long? = null,
    val version: Long = 0L,
    val metadata: Map<String, String> = emptyMap()
)
