package com.antgskds.calendarassistant.data.query

import com.antgskds.calendarassistant.core.query.ScheduleInsightsQueryApi
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class LocalScheduleInsightsQueryApi : ScheduleInsightsQueryApi {
    override fun hasDuplicateAdvanceReminder(events: List<Event>, minutes: Int): Boolean {
        return events.any { event ->
            event.reminderMinutes.any { it <= minutes }
        }
    }

    override fun findNextRecurringInstance(
        events: List<Event>,
        parentEventId: Long,
        nowMillis: Long
    ): Event? {
        return events
            .filter { it.parentId == parentEventId }
            .filter { it.startMillis >= nowMillis }
            .minByOrNull { it.startMillis }
    }

    override fun calculateTargetWeek(
        semesterStartDate: String,
        targetDate: LocalDate,
        fallbackDate: LocalDate
    ): Int {
        val semesterStart = try {
            if (semesterStartDate.isNotBlank()) LocalDate.parse(semesterStartDate) else fallbackDate
        } catch (_: Exception) {
            fallbackDate
        }

        val daysDiff = ChronoUnit.DAYS.between(semesterStart, targetDate)
        return (daysDiff / 7).toInt() + 1
    }
}
