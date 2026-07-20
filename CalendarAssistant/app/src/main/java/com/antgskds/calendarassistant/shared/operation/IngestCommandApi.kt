package com.antgskds.calendarassistant.shared.operation

import com.antgskds.calendarassistant.feature.recognition.domain.model.RecognitionDraft
import com.antgskds.calendarassistant.feature.schedule.domain.model.Event
import com.antgskds.calendarassistant.feature.schedule.domain.model.*

interface IngestCommandApi {
    suspend fun ingestSmsPickup(eventData: RecognitionDraft): Event?
    suspend fun ingestInstantCode(eventData: RecognitionDraft, sourceType: String = "instant_code"): Event?
    suspend fun ingestRecognizedEvents(events: List<RecognitionDraft>, sourceImagePath: String?): List<Event>
}
