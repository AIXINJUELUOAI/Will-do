package com.antgskds.calendarassistant.data.repository

import android.content.Context
import com.antgskds.calendarassistant.data.model.SyncData
import com.antgskds.calendarassistant.data.source.SyncJsonDataSource

class SyncMappingRepository(context: Context) {
    private val syncDataSource = SyncJsonDataSource.getInstance(context.applicationContext)

    suspend fun load(): SyncData = syncDataSource.loadSyncData()

    suspend fun save(syncData: SyncData) {
        syncDataSource.saveSyncData(syncData)
    }

    suspend fun removeMappings(eventIds: Collection<String>) {
        if (eventIds.isEmpty()) return
        val syncData = load()
        val updatedMapping = syncData.mapping.toMutableMap().apply {
            eventIds.forEach(::remove)
        }
        if (updatedMapping != syncData.mapping) {
            save(syncData.copy(mapping = updatedMapping))
        }
    }
}
