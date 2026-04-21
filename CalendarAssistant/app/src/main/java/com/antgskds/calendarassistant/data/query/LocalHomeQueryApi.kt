package com.antgskds.calendarassistant.data.query

import com.antgskds.calendarassistant.core.course.CourseManager
import com.antgskds.calendarassistant.core.query.HomeQueryApi
import com.antgskds.calendarassistant.core.query.HomeSnapshot
import com.antgskds.calendarassistant.core.util.DateCalculator
import com.antgskds.calendarassistant.data.model.Course
import com.antgskds.calendarassistant.data.model.EventTags
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.data.model.MySettings
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

class LocalHomeQueryApi : HomeQueryApi {
    override fun buildSnapshot(
        selectedDate: LocalDate,
        events: List<MyEvent>,
        courses: List<Course>,
        settings: MySettings
    ): HomeSnapshot {
        val scheduleEvents = events.filter { it.tag != EventTags.NOTE && it.tag != EventTags.COURSE }
        val noteEvents = events.filter { it.tag == EventTags.NOTE }

        val todayNormal = scheduleEvents.filter { event ->
            !event.isRecurringParent && DateCalculator.overlapsDate(event, selectedDate)
        }.distinctBy { it.id }
        val todayCourses = CourseManager.getDailyCourses(selectedDate, courses, settings)
        val todayMerged = sortEventsByDisplayPriority(todayNormal + todayCourses)

        val tomorrowMerged = if (settings.showTomorrowEvents) {
            val tomorrow = selectedDate.plusDays(1)
            val todayEventIds = todayMerged.map { it.id }.toSet()
            val tomorrowNormal = scheduleEvents.filter { event ->
                !event.isRecurringParent && DateCalculator.overlapsDate(event, tomorrow)
            }.distinctBy { it.id }
            val tomorrowCourses = CourseManager.getDailyCourses(tomorrow, courses, settings)
            sortEventsByDisplayPriority(tomorrowNormal + tomorrowCourses)
                .filter { it.id !in todayEventIds }
        } else {
            emptyList()
        }

        return HomeSnapshot(
            noteEvents = noteEvents,
            currentDateEvents = todayMerged,
            tomorrowEvents = tomorrowMerged
        )
    }

    override fun calculateDelayToNextExpiration(events: List<MyEvent>, now: LocalDateTime): Long {
        var nearestEndMillis = Long.MAX_VALUE

        for (event in events) {
            if (event.isRecurringParent || event.tag == EventTags.NOTE || event.tag == EventTags.COURSE) continue
            try {
                val timeParts = event.endTime.split(":")
                val hour = timeParts.getOrElse(0) { "23" }.toIntOrNull() ?: 23
                val minute = timeParts.getOrElse(1) { "59" }.toIntOrNull() ?: 59
                val endDateTime = LocalDateTime.of(event.endDate, LocalTime.of(hour, minute))
                if (endDateTime.isAfter(now)) {
                    val diff = ChronoUnit.MILLIS.between(now, endDateTime)
                    if (diff in 1 until nearestEndMillis) {
                        nearestEndMillis = diff
                    }
                }
            } catch (_: Exception) {
                continue
            }
        }

        return if (nearestEndMillis == Long.MAX_VALUE) -1L else nearestEndMillis
    }

    private fun sortEventsByDisplayPriority(events: List<MyEvent>): List<MyEvent> {
        return events.sortedWith(
            compareBy(
                { event ->
                    val isExpired = DateCalculator.isEventExpired(event)
                    val isImportant = event.isImportant
                    val isMultiDay = event.startDate != event.endDate
                    when {
                        !isExpired && isImportant && isMultiDay -> 0
                        !isExpired && isImportant && !isMultiDay -> 1
                        !isExpired && !isImportant && isMultiDay -> 2
                        !isExpired && !isImportant && !isMultiDay -> 3
                        isExpired && isImportant && isMultiDay -> 4
                        isExpired && isImportant && !isMultiDay -> 5
                        isExpired && !isImportant && isMultiDay -> 6
                        else -> 7
                    }
                },
                { it.startTime }
            )
        )
    }
}
