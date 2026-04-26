package com.antgskds.calendarassistant.core.query

import com.antgskds.calendarassistant.calendar.models.Event
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow

interface ScheduleQueryApi {
    val events: StateFlow<List<Event>>
}
