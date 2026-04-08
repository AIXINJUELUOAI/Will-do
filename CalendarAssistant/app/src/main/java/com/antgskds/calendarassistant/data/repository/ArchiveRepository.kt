package com.antgskds.calendarassistant.data.repository

import android.content.Context
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.data.source.ArchiveJsonDataSource

class ArchiveRepository(context: Context) {
    private val archiveSource = ArchiveJsonDataSource(context.applicationContext)

    suspend fun loadArchivedEvents(): List<MyEvent> = archiveSource.loadArchivedEvents()

    suspend fun saveArchivedEvents(events: List<MyEvent>) {
        archiveSource.saveArchivedEvents(events)
    }

    suspend fun saveArchivedEventsBackup(events: List<MyEvent>) {
        archiveSource.saveArchivedEventsBackup(events)
    }
}
