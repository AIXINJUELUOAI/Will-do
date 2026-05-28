package com.antgskds.calendarassistant.core.query

import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.data.model.WidgetScheduleSnapshot
import java.time.LocalDate

interface WidgetScheduleQueryApi {
    fun buildSnapshot(
        events: List<Event>,
        today: LocalDate = LocalDate.now(),
        lookaheadDays: Int = 7
    ): WidgetScheduleSnapshot
}
