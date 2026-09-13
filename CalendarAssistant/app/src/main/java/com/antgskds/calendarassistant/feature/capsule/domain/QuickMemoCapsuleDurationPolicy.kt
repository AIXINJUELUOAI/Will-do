package com.antgskds.calendarassistant.feature.capsule.domain

import java.time.LocalTime
import java.time.ZonedDateTime

/** 保持原挂起胶囊的时长语义，提醒胶囊复用同一计算。 */
object QuickMemoCapsuleDurationPolicy {
    fun durationMillis(durationMinutes: Int, now: ZonedDateTime = ZonedDateTime.now()): Long {
        if (durationMinutes == -1) {
            val endOfDay = now.toLocalDate().atTime(LocalTime.of(23, 59)).atZone(now.zone)
            return (endOfDay.toInstant().toEpochMilli() - now.toInstant().toEpochMilli())
                .coerceIn(60_000L, 24 * 60 * 60_000L)
        }
        return durationMinutes.toLong().coerceIn(1L, 24 * 60L) * 60_000L
    }
}
