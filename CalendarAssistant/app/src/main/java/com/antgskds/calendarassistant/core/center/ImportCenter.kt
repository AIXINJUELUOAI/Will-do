package com.antgskds.calendarassistant.core.center

import com.antgskds.calendarassistant.core.ai.convertDraftToEvent
import com.antgskds.calendarassistant.core.operation.IngestCommandApi
import com.antgskds.calendarassistant.core.model.RecognitionDraft
import com.antgskds.calendarassistant.core.query.SettingsQueryApi
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.*
import java.time.LocalDate

class ImportCenter(
    private val scheduleCenter: ScheduleCenter,
    private val settingsQueryApi: SettingsQueryApi
) : IngestCommandApi {

    private val defaultDurationMinutes: Int
        get() = settingsQueryApi.settings.value.defaultEventDurationMinutes

    private val forceInstantCodeTimeToNow: Boolean
        get() = settingsQueryApi.settings.value.forceInstantCodeTimeToNow

    override suspend fun ingestSmsPickup(eventData: RecognitionDraft): Event? {
        val existingEvents = scheduleCenter.events.value
        val isDuplicate = existingEvents.any { existing ->
            existing.tag == eventData.tag &&
                existing.description == eventData.description &&
                !existing.endDate.isBefore(LocalDate.now())
        }
        if (isDuplicate) {
            return null
        }

        val event = convertDraftToEvent(
            eventData,
            defaultDurationMinutes = defaultDurationMinutes,
            forceInstantCodeTimeToNow = forceInstantCodeTimeToNow
        )
        scheduleCenter.addEvent(event)
        return event
    }

    override suspend fun ingestRecognizedEvents(
        events: List<RecognitionDraft>,
        sourceImagePath: String?
    ): List<Event> {
        if (events.isEmpty()) return emptyList()

        val durationMinutes = defaultDurationMinutes
        val added = mutableListOf<Event>()
        val knownEvents = scheduleCenter.events.value.toMutableList()
        events.forEach { eventData ->
            if (eventData.title.isBlank()) return@forEach

            val event = convertDraftToEvent(
                eventData,
                sourceImagePath,
                defaultDurationMinutes = durationMinutes,
                forceInstantCodeTimeToNow = forceInstantCodeTimeToNow
            )
            val isDuplicate = knownEvents.any { existing ->
                val isExpired = existing.endDate.isBefore(LocalDate.now())
                if (isExpired) return@any false

                existing.startDate == event.startDate &&
                    existing.startTime == event.startTime &&
                    existing.title.trim().equals(event.title, ignoreCase = true)
            }
            if (isDuplicate) {
                return@forEach
            }

            scheduleCenter.addEvent(event)
            knownEvents.add(event)
            added.add(event)
        }

        return added
    }
}
