package com.antgskds.calendarassistant.core.util

import com.antgskds.calendarassistant.data.model.MyEvent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

object DateCalculator {
    fun getToday(): LocalDate = LocalDate.now()

    fun calculateWeek(semesterStart: LocalDate, targetDate: LocalDate): Int {
        val days = ChronoUnit.DAYS.between(semesterStart, targetDate)
        return (days / 7).toInt() + 1
    }

    fun getDayOfWeek(date: LocalDate): Int {
        return date.dayOfWeek.value
    }

    /**
     * 判断事件是否过期
     * 逻辑移植自原 MainActivity.kt
     */
    fun isEventExpired(event: MyEvent): Boolean {
        return try {
            val timeParts = event.endTime.split(":")
            val hour = timeParts.getOrElse(0) { "23" }.toIntOrNull() ?: 23
            val minute = timeParts.getOrElse(1) { "59" }.toIntOrNull() ?: 59

            val endDateTime = LocalDateTime.of(event.endDate, LocalTime.of(hour, minute))
            endDateTime.isBefore(LocalDateTime.now())
        } catch (e: Exception) {
            false
        }
    }
}