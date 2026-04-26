package com.antgskds.calendarassistant.core.operation

import com.antgskds.calendarassistant.core.model.RecognitionDraft
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.*

interface IngestCommandApi {
    suspend fun ingestSmsPickup(eventData: RecognitionDraft): Event?
    suspend fun ingestRecognizedEvents(events: List<RecognitionDraft>, sourceImagePath: String?): List<Event>
}
