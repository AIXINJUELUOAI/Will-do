package com.antgskds.calendarassistant.feature.notification.api.ports

import com.antgskds.calendarassistant.feature.notification.model.NotificationKey
import com.antgskds.calendarassistant.feature.notification.model.NotificationResult

interface SystemAlarmGateway {
    suspend fun schedule(
        key: NotificationKey,
        triggerAtEpochMillis: Long,
        allowWhileIdle: Boolean = true
    ): NotificationResult

    suspend fun cancel(key: NotificationKey): NotificationResult
}
