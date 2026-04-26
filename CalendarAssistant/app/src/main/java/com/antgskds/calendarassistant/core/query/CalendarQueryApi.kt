package com.antgskds.calendarassistant.core.query

import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.EventType

interface CalendarQueryApi {
    fun getEvents(): List<Event>
    fun getEventsInRange(fromTS: Long, toTS: Long): List<Event>
    fun getEvent(id: Long): Event?
    fun getEventTypes(): List<EventType>
    fun getScheduledReminderCount(eventId: Long): Int

    // 归档查询
    fun getArchivedEvents(): List<Event>
    fun getActiveEventCount(): Int
    fun getTotalEventCount(): Int
}
