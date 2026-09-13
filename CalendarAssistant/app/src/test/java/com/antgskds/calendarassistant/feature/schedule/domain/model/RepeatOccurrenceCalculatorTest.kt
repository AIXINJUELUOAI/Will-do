package com.antgskds.calendarassistant.feature.schedule.domain.model

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RepeatOccurrenceCalculatorTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun dailyRuleReturnsNextDayAtSameTime() {
        val anchor = LocalDateTime.of(2026, 9, 2, 8, 30).atZone(zone)

        val next = RepeatSpec.daily().nextOccurrenceAfter(anchor, anchor)

        assertEquals(LocalDateTime.of(2026, 9, 3, 8, 30), next?.toLocalDateTime())
    }

    @Test
    fun weekdayRuleSkipsWeekend() {
        val friday = LocalDateTime.of(2026, 9, 4, 8, 30).atZone(zone)

        val next = RepeatSpec.weekdays().nextOccurrenceAfter(friday, friday)

        assertEquals(DayOfWeek.MONDAY, next?.dayOfWeek)
        assertEquals(LocalDateTime.of(2026, 9, 7, 8, 30), next?.toLocalDateTime())
    }

    @Test
    fun customWeeklyRuleUsesSelectedDays() {
        val monday = LocalDateTime.of(2026, 9, 7, 9, 0).atZone(zone)
        val spec = RepeatSpec(
            frequency = RepeatFrequency.WEEKLY,
            byDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)
        )

        val next = spec.nextOccurrenceAfter(monday, monday)

        assertEquals(LocalDateTime.of(2026, 9, 9, 9, 0), next?.toLocalDateTime())
    }

    @Test
    fun untilRuleStopsAfterEndDate() {
        val anchor = LocalDateTime.of(2026, 9, 2, 8, 30).atZone(zone)
        val spec = RepeatSpec.daily().copy(end = RepeatEnd.Until(anchor.toLocalDate()))

        val next = spec.nextOccurrenceAfter(anchor, anchor)

        assertNull(next)
    }
}
