package com.antgskds.calendarassistant.data.query

import com.antgskds.calendarassistant.core.center.ScheduleDisplayHelper
import com.antgskds.calendarassistant.core.query.HomeQueryApi
import com.antgskds.calendarassistant.core.query.HomeSnapshot
import com.antgskds.calendarassistant.data.model.ScheduleDisplayItem
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.*
import com.antgskds.calendarassistant.data.model.MySettings
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

class LocalHomeQueryApi : HomeQueryApi {
    override fun buildSnapshot(
        selectedDate: LocalDate,
        events: List<Event>,
        settings: MySettings
    ): HomeSnapshot {
        val activeEvents = events.filter { it.archivedAt == null }
        val scheduleEvents = activeEvents

        val expandTo = if (settings.showTomorrowEvents) selectedDate.plusDays(1) else selectedDate
        val displayItems = ScheduleDisplayHelper.buildDisplayItems(scheduleEvents, selectedDate, expandTo)

        val todayItems = displayItems.filter { item ->
            overlapsDate(item, selectedDate)
        }.distinctBy { it.stableKey }
        val todayMerged = sortByDisplayPriority(todayItems, settings.homeListReverseOrder)

        val tomorrowMerged = if (settings.showTomorrowEvents) {
            val tomorrow = selectedDate.plusDays(1)
            val todayKeys = todayMerged.map { it.stableKey }.toSet()
            val tomorrowItems = displayItems.filter { item ->
                overlapsDate(item, tomorrow)
            }.distinctBy { it.stableKey }
            sortByDisplayPriority(tomorrowItems, settings.homeListReverseOrder).filter { it.stableKey !in todayKeys }
        } else {
            emptyList()
        }

        return HomeSnapshot(
            currentDateEvents = todayMerged,
            tomorrowEvents = tomorrowMerged
        )
    }

    override fun calculateDelayToNextExpiration(events: List<Event>, now: LocalDateTime): Long {
        val today = now.toLocalDate()
        val scheduleEvents = events.filter { it.archivedAt == null }
        val displayItems = ScheduleDisplayHelper.buildDisplayItems(scheduleEvents, today, today)

        var nearestEndMillis = Long.MAX_VALUE
        for (item in displayItems) {
            try {
                val endDateTime = LocalDateTime.of(item.endDate, item.endLocalTime)
                if (endDateTime.isAfter(now)) {
                    val diff = ChronoUnit.MILLIS.between(now, endDateTime)
                    if (diff in 1 until nearestEndMillis) {
                        nearestEndMillis = diff
                    }
                }
            } catch (_: Exception) { continue }
        }
        return if (nearestEndMillis == Long.MAX_VALUE) -1L else nearestEndMillis
    }

    private fun overlapsDate(item: ScheduleDisplayItem, date: LocalDate): Boolean {
        return try {
            val startDt = LocalDateTime.of(item.startDate, item.startLocalTime)
            val endDt = LocalDateTime.of(item.endDate, item.endLocalTime)
            val dayStart = date.atStartOfDay()
            val dayEnd = date.plusDays(1).atStartOfDay()
            endDt > dayStart && startDt < dayEnd
        } catch (_: Exception) {
            date >= item.startDate && date <= item.endDate
        }
    }

    private fun sortByDisplayPriority(
        items: List<ScheduleDisplayItem>,
        reverse: Boolean = false
    ): List<ScheduleDisplayItem> {
        val now = LocalDateTime.now()
        // 外层优先级分类（未过期/通勤/多天等 0-7）始终保持；reverse 只反转同组内的时间次序。
        val priorityKey: (ScheduleDisplayItem) -> Int = { item ->
            val isExpired = try {
                LocalDateTime.of(item.endDate, item.endLocalTime).isBefore(now)
            } catch (_: Exception) { false }
            val isTransit = item.isTransit
            val isMultiDay = item.startDate != item.endDate
            when {
                !isExpired && isTransit && isMultiDay -> 0
                !isExpired && isTransit && !isMultiDay -> 1
                !isExpired && !isTransit && isMultiDay -> 2
                !isExpired && !isTransit && !isMultiDay -> 3
                isExpired && isTransit && isMultiDay -> 4
                isExpired && isTransit && !isMultiDay -> 5
                isExpired && !isTransit && isMultiDay -> 6
                else -> 7
            }
        }
        val comparator = if (reverse) {
            compareBy(priorityKey).thenByDescending { it.startTime }
        } else {
            compareBy(priorityKey).thenBy { it.startTime }
        }
        return items.sortedWith(comparator)
    }
}
