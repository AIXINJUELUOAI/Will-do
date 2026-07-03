package com.antgskds.calendarassistant.feature.api.notification.ports

import com.antgskds.calendarassistant.feature.api.notification.model.NotificationKey
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationResult
import com.antgskds.calendarassistant.feature.api.notification.model.PlatformNotificationPayload

interface PlatformPublisher {
    suspend fun publish(payload: PlatformNotificationPayload): NotificationResult

    suspend fun cancel(key: NotificationKey): NotificationResult
}
