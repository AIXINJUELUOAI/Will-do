package com.antgskds.calendarassistant.core.center

import com.antgskds.calendarassistant.core.operation.IngestCommandApi
import com.antgskds.calendarassistant.data.model.CalendarEventData
import com.antgskds.calendarassistant.data.model.EventTags
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.ui.theme.EventColors
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class ImportCenter(
    private val scheduleCenter: ScheduleCenter
) : IngestCommandApi {

    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    override suspend fun ingestSmsPickup(eventData: CalendarEventData): MyEvent? {
        val existingEvents = scheduleCenter.events.value
        val isDuplicate = existingEvents.any { existing ->
            existing.tag == eventData.tag &&
                existing.description == eventData.description &&
                !existing.endDate.isBefore(LocalDate.now())
        }
        if (isDuplicate) {
            return null
        }

        val event = eventDataToMyEvent(eventData, existingEvents.size, sourceImagePath = null)
        scheduleCenter.addEvent(event)
        return event
    }

    override suspend fun ingestRecognizedEvents(
        events: List<CalendarEventData>,
        sourceImagePath: String?
    ): List<MyEvent> {
        if (events.isEmpty()) return emptyList()

        val added = mutableListOf<MyEvent>()
        val knownEvents = scheduleCenter.events.value.toMutableList()
        events.forEach { eventData ->
            if (eventData.title.isBlank()) return@forEach

            val event = eventDataToMyEvent(eventData, knownEvents.size, sourceImagePath)
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

    private fun eventDataToMyEvent(
        eventData: CalendarEventData,
        currentEventsCount: Int,
        sourceImagePath: String?
    ): MyEvent {
        val now = LocalDateTime.now()
        val startDateTime = try {
            if (eventData.startTime.isNotBlank()) {
                LocalDateTime.parse(eventData.startTime, dateTimeFormatter)
            } else {
                now
            }
        } catch (_: Exception) {
            now
        }

        val endDateTime = try {
            if (eventData.endTime.isNotBlank()) {
                LocalDateTime.parse(eventData.endTime, dateTimeFormatter)
            } else {
                startDateTime.plusHours(1)
            }
        } catch (_: Exception) {
            startDateTime.plusHours(1)
        }

        return MyEvent(
            id = UUID.randomUUID().toString(),
            title = eventData.title.trim(),
            startDate = startDateTime.toLocalDate(),
            endDate = endDateTime.toLocalDate(),
            startTime = startDateTime.format(timeFormatter),
            endTime = endDateTime.format(timeFormatter),
            location = eventData.location,
            description = eventData.description,
            color = EventColors[currentEventsCount % EventColors.size],
            sourceImagePath = sourceImagePath,
            tag = eventData.tag.ifBlank { EventTags.GENERAL }
        )
    }
}
