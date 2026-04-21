package com.antgskds.calendarassistant.data.query

import com.antgskds.calendarassistant.core.calendar.RecurringEventUtils
import com.antgskds.calendarassistant.core.query.ScheduleInsightsQueryApi
import com.antgskds.calendarassistant.data.model.MyEvent
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class LocalScheduleInsightsQueryApi : ScheduleInsightsQueryApi {
    override fun hasDuplicateAdvanceReminder(events: List<MyEvent>, minutes: Int): Boolean {
        return events.any { event ->
            event.reminders.any { it <= minutes }
        }
    }

    override fun findNextRecurringInstance(
        events: List<MyEvent>,
        parentEventId: String,
        nowMillis: Long
    ): MyEvent? {
        return events
            .filter { it.isRecurring && !it.isRecurringParent && it.parentRecurringId == parentEventId }
            .mapNotNull { child ->
                val startMillis = RecurringEventUtils.eventStartMillis(child) ?: return@mapNotNull null
                child to startMillis
            }
            .filter { (_, startMillis) -> startMillis >= nowMillis }
            .minByOrNull { (_, startMillis) -> startMillis }
            ?.first
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
