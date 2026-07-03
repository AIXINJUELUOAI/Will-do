package com.antgskds.calendarassistant.feature.api.notification.ports

import com.antgskds.calendarassistant.feature.api.notification.model.NotificationKey
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationQuery
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationSnapshot

interface NotificationRegistryStore {
    suspend fun upsert(snapshot: NotificationSnapshot)

    suspend fun get(key: NotificationKey): NotificationSnapshot?

    suspend fun list(query: NotificationQuery = NotificationQuery()): List<NotificationSnapshot>

    suspend fun delete(key: NotificationKey)

    suspend fun deleteAll(keys: Collection<NotificationKey>): List<NotificationSnapshot> {
        val removed = mutableListOf<NotificationSnapshot>()
        keys.distinctBy { it.value }.forEach { key ->
            get(key)?.let { snapshot ->
                removed += snapshot
                delete(key)
            }
        }
        return removed
    }
}
