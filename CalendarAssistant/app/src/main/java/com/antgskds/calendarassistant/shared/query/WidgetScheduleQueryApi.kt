package com.antgskds.calendarassistant.shared.query

import com.antgskds.calendarassistant.feature.schedule.domain.model.Event
import com.antgskds.calendarassistant.platform.widget.model.WidgetScheduleSnapshot
import java.time.LocalDate

interface WidgetScheduleQueryApi {
    fun buildSnapshot(
        events: List<Event>,
        today: LocalDate = LocalDate.now(),
        lookaheadDays: Int = 7
    ): WidgetScheduleSnapshot
}
