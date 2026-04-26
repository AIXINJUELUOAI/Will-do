package com.antgskds.calendarassistant.core.query

import com.antgskds.calendarassistant.data.model.ScheduleDisplayItem
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.data.model.MySettings
import java.time.LocalDate
import java.time.LocalDateTime

data class HomeSnapshot(
    val noteEvents: List<Event>,                          // 便签保持 Event
    val currentDateEvents: List<ScheduleDisplayItem>,     // 日程改用展示模型
    val tomorrowEvents: List<ScheduleDisplayItem>
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
