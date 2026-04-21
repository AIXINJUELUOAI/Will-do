package com.antgskds.calendarassistant.core.query

import com.antgskds.calendarassistant.data.model.MyEvent
import kotlinx.coroutines.flow.StateFlow

interface ScheduleQueryApi {
    val events: StateFlow<List<MyEvent>>
    val archivedEvents: StateFlow<List<MyEvent>>

    fun fetchArchivedEvents()

    fun getEventsCount(): Int
    fun getTotalEventsCount(): Int
}
