package com.antgskds.calendarassistant.feature.notification.policy

import com.antgskds.calendarassistant.feature.notification.model.NotificationResult
import com.antgskds.calendarassistant.feature.notification.model.NotificationRoute
import com.antgskds.calendarassistant.feature.notification.model.NotificationState

object QuickMemoReminderDeliveryPolicy {
    fun route(liveCapsuleEnabled: Boolean): NotificationRoute =
        if (liveCapsuleEnabled) NotificationRoute.LIVE else NotificationRoute.NORMAL

    fun acceptsOccurrence(expectedAt: Long?, currentAt: Long, nextAfter: (Long) -> Long?): Boolean =
        expectedAt == null || expectedAt == currentAt ||
            (expectedAt > currentAt && nextAfter(expectedAt - 1L) == expectedAt)

    fun isDelivered(result: NotificationResult): Boolean =
        result is NotificationResult.Success && result.state == NotificationState.POSTED
}
