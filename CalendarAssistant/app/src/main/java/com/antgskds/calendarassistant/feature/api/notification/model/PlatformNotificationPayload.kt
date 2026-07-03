package com.antgskds.calendarassistant.feature.api.notification.model

data class PlatformNotificationPayload(
    val key: NotificationKey,
    val notificationId: Int,
    val smallIconResId: Int? = null,
    val route: NotificationRoute,
    val display: NotificationDisplaySnapshot,
    val behavior: NotificationBehavior = NotificationBehavior(),
    val tapTarget: NotificationTapTarget? = null,
    val actions: List<NotificationAction> = emptyList(),
    val channelKey: String? = null,
    val category: String? = null,
    val vendorOptions: Map<String, String> = emptyMap()
)
