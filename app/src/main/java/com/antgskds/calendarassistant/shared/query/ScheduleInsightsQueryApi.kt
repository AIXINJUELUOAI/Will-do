package com.antgskds.calendarassistant.shared.query

import com.antgskds.calendarassistant.feature.schedule.domain.model.Event
import com.antgskds.calendarassistant.feature.schedule.domain.model.*
import java.time.LocalDate

interface ScheduleInsightsQueryApi {
    fun hasDuplicateAdvanceReminder(events: List<Event>, minutes: Int): Boolean

    fun findNextRecurringInstance(
        events: List<Event>,
        parentEventId: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): Event?

    fun calculateTargetWeek(
        semesterStartDate: String,
        targetDate: LocalDate,
        fallbackDate: LocalDate = LocalDate.now()
    ): Int
}
