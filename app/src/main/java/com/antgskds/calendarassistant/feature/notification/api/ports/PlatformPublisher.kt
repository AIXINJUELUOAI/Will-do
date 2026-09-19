package com.antgskds.calendarassistant.feature.notification.api.ports

import com.antgskds.calendarassistant.feature.notification.model.NotificationKey
import com.antgskds.calendarassistant.feature.notification.model.NotificationResult
import com.antgskds.calendarassistant.feature.notification.model.PlatformNotificationPayload

interface PlatformPublisher {
    suspend fun publish(payload: PlatformNotificationPayload): NotificationResult

    suspend fun cancel(key: NotificationKey): NotificationResult
}
