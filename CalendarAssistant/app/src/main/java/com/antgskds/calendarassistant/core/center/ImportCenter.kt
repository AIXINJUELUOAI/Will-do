package com.antgskds.calendarassistant.core.center

import com.antgskds.calendarassistant.core.ai.convertDraftToEvent
import com.antgskds.calendarassistant.core.attachment.EventAttachmentManager
import com.antgskds.calendarassistant.core.operation.IngestCommandApi
import com.antgskds.calendarassistant.core.model.RecognitionDraft
import com.antgskds.calendarassistant.core.query.SettingsQueryApi
import com.antgskds.calendarassistant.core.sms.SmsPickupFingerprint
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

class ImportCenter(
    private val scheduleCenter: ScheduleCenter,
    private val settingsQueryApi: SettingsQueryApi,
    private val attachmentManager: EventAttachmentManager
) : IngestCommandApi {

    private val smsIngestMutex = Mutex()

    override suspend fun ingestSmsPickup(eventData: RecognitionDraft): Event? = ingestInstantCode(eventData, "sms")

    override suspend fun ingestInstantCode(eventData: RecognitionDraft, sourceType: String): Event? = smsIngestMutex.withLock {
        val settings = settingsQueryApi.settings.value
        val event = convertDraftToEvent(
            eventData,
            defaultDurationMinutes = settings.defaultEventDurationMinutes,
            forceInstantCodeTimeToNow = settings.forceInstantCodeTimeToNow,
            eventColorPaletteHex = settings.eventColorPaletteHex
        )

        val incomingFingerprint = SmsPickupFingerprint.fromDraft(eventData)
            ?: SmsPickupFingerprint.fromEvent(event)
        val existingEvents = scheduleCenter.getLatestActiveEvents()
        val isDuplicate = existingEvents.any { existing ->
            !existing.endDate.isBefore(LocalDate.now()) &&
                isSameSmsPickupEvent(existing, event, incomingFingerprint)
        }
        if (isDuplicate) return@withLock null

        scheduleCenter.addEvent(event)
        event
    }

    private fun isSameSmsPickupEvent(existing: Event, incoming: Event, incomingFingerprint: String?): Boolean {
        val existingFingerprint = SmsPickupFingerprint.fromEvent(existing)
        if (incomingFingerprint != null && existingFingerprint != null) {
            return incomingFingerprint == existingFingerprint
        }
        return existing.tag == incoming.tag && existing.description == incoming.description
    }

    override suspend fun ingestRecognizedEvents(
        events: List<RecognitionDraft>,
        sourceImagePath: String?
    ): List<Event> {
        if (events.isEmpty()) return emptyList()

        val settings = settingsQueryApi.settings.value
        val durationMinutes = settings.defaultEventDurationMinutes
        val added = mutableListOf<Event>()
        val knownEvents = scheduleCenter.events.value.toMutableList()
        events.forEach { eventData ->
            if (eventData.title.isBlank()) return@forEach

            val event = convertDraftToEvent(
                eventData,
                sourceImagePath,
                defaultDurationMinutes = durationMinutes,
                forceInstantCodeTimeToNow = settings.forceInstantCodeTimeToNow,
                eventColorPaletteHex = settings.eventColorPaletteHex
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

            val eventId = scheduleCenter.addEvent(event)
            attachmentManager.addRecognitionImageAttachment(eventId, sourceImagePath.orEmpty())
            val stored = event.copy(id = eventId)
            knownEvents.add(stored)
            added.add(stored)
        }

        return added
    }
}
