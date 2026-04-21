package com.antgskds.calendarassistant.core.calendar

import com.antgskds.calendarassistant.data.model.MyEvent

internal object CalendarSyncManagerV2RecurringUtils {
    fun mergeRecurringEvent(existingEvent: MyEvent, incomingEvent: MyEvent): MyEvent {
        return existingEvent.copy(
            title = incomingEvent.title,
            startDate = incomingEvent.startDate,
            endDate = incomingEvent.endDate,
            startTime = incomingEvent.startTime,
            endTime = incomingEvent.endTime,
            location = incomingEvent.location,
            description = incomingEvent.description,
            tag = incomingEvent.tag,
            isRecurring = incomingEvent.isRecurring,
            isRecurringParent = incomingEvent.isRecurringParent,
            recurringSeriesKey = incomingEvent.recurringSeriesKey,
            recurringInstanceKey = incomingEvent.recurringInstanceKey,
            parentRecurringId = incomingEvent.parentRecurringId,
            nextOccurrenceStartMillis = incomingEvent.nextOccurrenceStartMillis,
            excludedRecurringInstances = if (existingEvent.isRecurringParent) {
                existingEvent.excludedRecurringInstances
            } else {
                incomingEvent.excludedRecurringInstances
            },
            skipCalendarSync = true
        )
    }

    fun isWithinSyncWindow(event: MyEvent, syncWindowStart: Long, syncWindowEnd: Long): Boolean {
        val effectiveStart = if (event.isRecurringParent) {
            event.nextOccurrenceStartMillis ?: RecurringEventUtils.eventStartMillis(event)
        } else {
            RecurringEventUtils.eventStartMillis(event)
        }
        val effectiveEnd = if (event.isRecurringParent) {
            effectiveStart ?: RecurringEventUtils.eventEndMillis(event)
        } else {
            RecurringEventUtils.eventEndMillis(event)
        }

        if (effectiveStart == null || effectiveEnd == null) return false
        return effectiveEnd > syncWindowStart && effectiveStart < syncWindowEnd
    }
}
