package com.antgskds.calendarassistant.core.query

import com.antgskds.calendarassistant.data.model.Course
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.data.model.MySettings
import java.time.LocalDate
import java.time.LocalDateTime

data class HomeSnapshot(
    val noteEvents: List<MyEvent>,
    val currentDateEvents: List<MyEvent>,
    val tomorrowEvents: List<MyEvent>
)

interface HomeQueryApi {
    fun buildSnapshot(
        selectedDate: LocalDate,
        events: List<MyEvent>,
        courses: List<Course>,
        settings: MySettings
    ): HomeSnapshot

    fun calculateDelayToNextExpiration(
        events: List<MyEvent>,
        now: LocalDateTime = LocalDateTime.now()
    ): Long
}
