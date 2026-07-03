package com.antgskds.calendarassistant.feature.api.notification.ports

import com.antgskds.calendarassistant.feature.api.notification.model.NotificationKey
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationResult

interface SystemAlarmGateway {
    suspend fun schedule(
        key: NotificationKey,
        triggerAtEpochMillis: Long,
        allowWhileIdle: Boolean = true
    ): NotificationResult

    suspend fun cancel(key: NotificationKey): NotificationResult
}
