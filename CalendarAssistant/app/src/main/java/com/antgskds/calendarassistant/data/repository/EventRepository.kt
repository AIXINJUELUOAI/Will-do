package com.antgskds.calendarassistant.data.repository

import android.content.Context
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.data.source.EventJsonDataSource

class EventRepository(context: Context) {
    private val eventSource = EventJsonDataSource(context.applicationContext)

    suspend fun loadEvents(): List<MyEvent> = eventSource.loadEvents()

    fun getAndClearCleanupInfo(): String = eventSource.getAndClearCleanupInfo()

    suspend fun saveEvents(events: List<MyEvent>) {
        eventSource.saveEvents(events)
    }

    suspend fun saveEventsBackup(events: List<MyEvent>) {
        eventSource.saveEventsBackup(events)
    }
}
