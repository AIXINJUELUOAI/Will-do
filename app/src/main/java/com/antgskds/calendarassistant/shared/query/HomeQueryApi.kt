package com.antgskds.calendarassistant.shared.query

import com.antgskds.calendarassistant.feature.schedule.presentation.model.ScheduleDisplayItem
import com.antgskds.calendarassistant.feature.schedule.domain.model.Event
import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import java.time.LocalDate
import java.time.LocalDateTime

data class HomeSnapshot(
    val currentDateEvents: List<ScheduleDisplayItem>,     // 日程改用展示模型
    val tomorrowEvents: List<ScheduleDisplayItem>,
    val datesWithEvents: Set<LocalDate> = emptySet(),
)

interface HomeQueryApi {
    fun buildSnapshot(
        selectedDate: LocalDate,
        events: List<Event>,
        settings: MySettings
    ): HomeSnapshot

    fun calculateDelayToNextExpiration(
        events: List<Event>,
        now: LocalDateTime = LocalDateTime.now()
    ): Long
}
