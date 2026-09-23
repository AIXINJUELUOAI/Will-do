package com.antgskds.calendarassistant.feature.home.ui.render.material

import com.antgskds.calendarassistant.feature.schedule.presentation.model.ScheduleDisplayItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class HomeWeekTimelineLayoutTest {
    @Test
    fun overlappingEventsShareWidthAndLaterGroupReturnsToFullWidth() {
        val monday = LocalDate.of(2026, 9, 21)
        val first = item("first", monday, LocalTime.of(9, 0), LocalTime.of(11, 0))
        val overlapping = item("overlapping", monday, LocalTime.of(9, 30), LocalTime.of(10, 30))
        val later = item("later", monday, LocalTime.of(11, 0), LocalTime.NOON)

        val blocks = buildWeekTimelineBlocks(listOf(first, overlapping, later), monday)
            .associateBy { it.item.stableKey }

        assertEquals(2, blocks.getValue("first").columnCount)
        assertEquals(2, blocks.getValue("overlapping").columnCount)
        assertNotEquals(blocks.getValue("first").column, blocks.getValue("overlapping").column)
        assertEquals(1, blocks.getValue("later").columnCount)
        assertEquals(0, blocks.getValue("later").column)
    }

    @Test
    fun shortAdjacentEventsKeepTheirTimeBoundaries() {
        val monday = LocalDate.of(2026, 9, 21)
        val short = item("short", monday, LocalTime.of(9, 0), LocalTime.of(9, 5))
        val adjacent = item("adjacent", monday, LocalTime.of(9, 5), LocalTime.of(9, 10))
        val blocks = buildWeekTimelineBlocks(listOf(short, adjacent), monday)
        assertEquals(listOf(5, 5), blocks.map { it.endMinute - it.startMinute })
        assertEquals(listOf(1, 1), blocks.map { it.columnCount })
    }

    private fun item(
        key: String,
        date: LocalDate,
        start: LocalTime,
        end: LocalTime,
    ): ScheduleDisplayItem {
        val zone = ZoneId.systemDefault()
        return ScheduleDisplayItem(
            stableKey = key,
            title = key,
            startTS = date.atTime(start).atZone(zone).toEpochSecond(),
            endTS = date.atTime(end).atZone(zone).toEpochSecond(),
            action = ScheduleDisplayItem.ActionTarget.Single(key.hashCode().toLong()),
        )
    }
}
