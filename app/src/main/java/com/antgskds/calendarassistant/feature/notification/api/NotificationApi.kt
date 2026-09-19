package com.antgskds.calendarassistant.feature.notification.api

import com.antgskds.calendarassistant.feature.notification.model.NotificationKey
import com.antgskds.calendarassistant.feature.notification.model.NotificationQuery
import com.antgskds.calendarassistant.feature.notification.model.NotificationRequest
import com.antgskds.calendarassistant.feature.notification.model.NotificationResult
import com.antgskds.calendarassistant.feature.notification.model.NotificationSnapshot
import com.antgskds.calendarassistant.feature.notification.model.NotificationTrigger

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
