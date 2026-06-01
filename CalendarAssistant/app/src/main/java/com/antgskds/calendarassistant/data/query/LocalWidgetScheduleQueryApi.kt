package com.antgskds.calendarassistant.data.query

import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.core.center.ScheduleDisplayHelper
import com.antgskds.calendarassistant.core.query.WidgetScheduleQueryApi
import com.antgskds.calendarassistant.data.model.ScheduleDisplayItem
import com.antgskds.calendarassistant.data.model.WidgetScheduleEntry
import com.antgskds.calendarassistant.data.model.WidgetScheduleGroup
import com.antgskds.calendarassistant.data.model.WidgetScheduleSnapshot
import java.time.LocalDate
import java.time.LocalDateTime

class LocalWidgetScheduleQueryApi : WidgetScheduleQueryApi {
    override fun buildSnapshot(
        events: List<Event>,
        today: LocalDate,
        lookaheadDays: Int
    ): WidgetScheduleSnapshot {
        val windowEnd = today.plusDays(lookaheadDays.coerceAtLeast(1).toLong())
        val now = LocalDateTime.now()
        val scheduleEvents = events.filter { it.archivedAt == null }
        val items = ScheduleDisplayHelper.buildDisplayItems(scheduleEvents, today, windowEnd)
            .distinctBy { it.stableKey }

        val visibleEntries = sortForWidget(items.filter { it.isPending && !it.isExpiredForWidget(now) })
            .map { item -> WidgetScheduleEntry(item.widgetDate(today), item) }
        val todayItems = visibleEntries.filter { it.date == today }.map { it.item }
        val upcomingItems = visibleEntries.map { it.item }
        val groups = visibleEntries
            .groupBy { it.date }
            .map { (date, entries) -> WidgetScheduleGroup(date, entries.map { it.item }) }

        return WidgetScheduleSnapshot(
            today = today,
            visibleEntries = visibleEntries,
            todayItems = todayItems,
            upcomingItems = upcomingItems,
            upcomingGroups = groups
        )
    }

    private fun sortForWidget(items: List<ScheduleDisplayItem>): List<ScheduleDisplayItem> {
        val now = LocalDateTime.now()
        return items.sortedWith(
            compareBy(
                { it.isExpiredForWidget(now) },
                { it.startTS },
                { it.title }
            )
        )
    }

    private fun ScheduleDisplayItem.overlapsDate(date: LocalDate): Boolean {
        return try {
            val start = LocalDateTime.of(startDate, startLocalTime)
            val end = LocalDateTime.of(endDate, endLocalTime)
            val dayStart = date.atStartOfDay()
            val dayEnd = date.plusDays(1).atStartOfDay()
            end > dayStart && start < dayEnd
        } catch (_: Exception) {
            date >= startDate && date <= endDate
        }
    }

    private fun ScheduleDisplayItem.widgetDate(today: LocalDate): LocalDate {
        return if (overlapsDate(today)) today else startDate
    }

    private fun ScheduleDisplayItem.isExpiredForWidget(
        now: LocalDateTime = LocalDateTime.now()
    ): Boolean {
        return try {
            LocalDateTime.of(endDate, endLocalTime).isBefore(now)
        } catch (_: Exception) {
            false
        }
    }
}
