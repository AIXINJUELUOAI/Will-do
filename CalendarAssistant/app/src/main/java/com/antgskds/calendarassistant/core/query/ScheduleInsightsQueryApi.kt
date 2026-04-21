package com.antgskds.calendarassistant.core.query

import com.antgskds.calendarassistant.data.model.MyEvent
import java.time.LocalDate

interface ScheduleInsightsQueryApi {
    fun hasDuplicateAdvanceReminder(events: List<MyEvent>, minutes: Int): Boolean

    fun findNextRecurringInstance(
        events: List<MyEvent>,
        parentEventId: String,
        nowMillis: Long = System.currentTimeMillis()
    ): MyEvent?

    fun calculateTargetWeek(
        semesterStartDate: String,
        targetDate: LocalDate,
        fallbackDate: LocalDate = LocalDate.now()
    ): Int
}
