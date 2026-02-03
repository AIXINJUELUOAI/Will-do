package com.antgskds.calendarassistant.ui.analyzer

import com.antgskds.calendarassistant.data.model.MyEvent
import java.time.LocalDateTime
import java.time.LocalTime

class ScheduleRecommendationAnalyzer {
    companion object {
        private const val TIME_RANGE_HOURS = 2
        private const val MIN_COUNT = 3
        private const val CACHE_VALIDITY_MS = 5 * 60 * 1000L
    }

    private var cache: Pair<String?, Long>? = null

    fun getRecommendation(
        events: List<MyEvent>,
        currentDateTime: LocalDateTime
    ): String? {
        cache?.let { (title, timestamp) ->
            if (System.currentTimeMillis() - timestamp < CACHE_VALIDITY_MS) {
                return title
            }
        }

        val result = analyze(events, currentDateTime)
        cache = result to System.currentTimeMillis()
        return result
    }

    fun clearCache() {
        cache = null
    }

    private fun analyze(events: List<MyEvent>, currentDateTime: LocalDateTime): String? {
        val currentTime = currentDateTime.toLocalTime()
        val currentDate = currentDateTime.toLocalDate()

        val timeRangeStart = currentTime.minusHours(TIME_RANGE_HOURS.toLong())
        val timeRangeEnd = currentTime.plusHours(TIME_RANGE_HOURS.toLong())

        val filteredEvents = events.filter { event ->
            val eventTime = LocalTime.parse(event.startTime)
            val eventDate = event.startDate

            val isSameDay = eventDate == currentDate
            val isInTimeRange = if (timeRangeStart <= timeRangeEnd) {
                eventTime >= timeRangeStart && eventTime <= timeRangeEnd
            } else {
                // 跨午夜的情况（如 23:00-01:00）
                eventTime >= timeRangeStart || eventTime <= timeRangeEnd
            }

            isSameDay && isInTimeRange
        }

        if (filteredEvents.isEmpty()) {
            return null
        }

        val titleCounts = filteredEvents
            .groupBy { it.title }
            .mapValues { (_, eventList) -> eventList.size }
            .filter { it.value >= MIN_COUNT }
            .maxByOrNull { it.value }

        return titleCounts?.key
    }
}
