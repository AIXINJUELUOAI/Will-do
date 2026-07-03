package com.antgskds.calendarassistant.feature.api.notification

import com.antgskds.calendarassistant.feature.api.notification.model.NotificationKey
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationQuery
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationRequest
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationResult
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationSnapshot
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationTrigger

interface NotificationApi {
    suspend fun create(request: NotificationRequest): NotificationResult

    suspend fun update(request: NotificationRequest): NotificationResult

    suspend fun cancel(key: NotificationKey): NotificationResult

    suspend fun cancelAll(keys: Collection<NotificationKey>) {
        keys.distinctBy { it.value }.forEach { cancel(it) }
    }

    suspend fun get(key: NotificationKey): NotificationSnapshot?

    suspend fun list(query: NotificationQuery = NotificationQuery()): List<NotificationSnapshot>

    suspend fun trigger(trigger: NotificationTrigger): NotificationResult
}
