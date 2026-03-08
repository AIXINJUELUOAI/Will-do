package com.antgskds.calendarassistant.core.calendar

import com.antgskds.calendarassistant.data.model.MyEvent
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object RecurringEventUtils {
    private val DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun buildSeriesKey(calendarId: Long, eventId: Long): String {
        return "${calendarId}_${eventId}"
    }

    fun buildParentId(seriesKey: String): String {
        return "recurring_parent_$seriesKey"
    }

    fun buildInstanceKey(seriesKey: String, beginMillis: Long): String {
        return "${seriesKey}_$beginMillis"
    }

    fun buildInstanceId(instanceKey: String): String {
        return "recurring_instance_$instanceKey"
    }

    fun formatMillis(millis: Long?): String? {
        if (millis == null) return null
        return Instant.ofEpochMilli(millis)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
            .format(DATE_TIME_FORMATTER)
    }

    fun eventStartMillis(event: MyEvent): Long? {
        return runCatching {
            LocalDateTime.of(event.startDate, java.time.LocalTime.parse(event.startTime))
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
    }

    fun eventEndMillis(event: MyEvent): Long? {
        return runCatching {
            LocalDateTime.of(event.endDate, java.time.LocalTime.parse(event.endTime))
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
    }
}
