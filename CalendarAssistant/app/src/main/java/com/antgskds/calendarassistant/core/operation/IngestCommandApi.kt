package com.antgskds.calendarassistant.core.operation

import com.antgskds.calendarassistant.data.model.CalendarEventData
import com.antgskds.calendarassistant.data.model.MyEvent

interface IngestCommandApi {
    suspend fun ingestSmsPickup(eventData: CalendarEventData): MyEvent?
    suspend fun ingestRecognizedEvents(events: List<CalendarEventData>, sourceImagePath: String?): List<MyEvent>
}
