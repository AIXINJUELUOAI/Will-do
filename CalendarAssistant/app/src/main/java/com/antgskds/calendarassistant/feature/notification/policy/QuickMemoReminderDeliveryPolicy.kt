package com.antgskds.calendarassistant.feature.notification.policy

import com.antgskds.calendarassistant.feature.notification.model.NotificationResult
import com.antgskds.calendarassistant.feature.notification.model.NotificationRoute
import com.antgskds.calendarassistant.feature.notification.model.NotificationState

object QuickMemoReminderDeliveryPolicy {
    fun route(liveCapsuleEnabled: Boolean): NotificationRoute =
        if (liveCapsuleEnabled) NotificationRoute.LIVE else NotificationRoute.NORMAL

    fun isDelivered(result: NotificationResult): Boolean =
        result is NotificationResult.Success && result.state == NotificationState.POSTED
}
