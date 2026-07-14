package com.antgskds.calendarassistant.feature.schedule.ui.connector

import com.antgskds.calendarassistant.feature.schedule.presentation.model.ScheduleDisplayItem
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AllEventsConnectorTest {
    @Test
    fun `searches display fields and keeps first item for duplicate stable keys`() {
        val today = LocalDate.of(2026, 7, 14)
        val first = item("same", "Project review", today, LocalTime.of(9, 0), eventId = 1L)
        val duplicate = item("same", "Project duplicate", today, LocalTime.of(10, 0), eventId = 2L)
        val hidden = item("hidden", "Personal", today, LocalTime.of(11, 0), eventId = 3L)

        val connection = buildAllEventsConnection(
            items = listOf(first, duplicate, hidden),
            searchQuery = "project",
            today = today,
            now = today.atTime(8, 0),
            reverseOrderEnabled = false,
            revealedItemKey = null,
            timeRefreshToken = 1L,
            futureDays = 7,
            futureLimit = today.plusDays(7)
        )

        assertEquals(listOf("same"), connection.state.groups.single().items.map { it.stableKey })
        assertSame(first, connection.itemsByKey.getValue("same"))
    }

    @Test
    fun `reverse order changes only time order inside the same date group`() {
        val today = LocalDate.of(2026, 7, 14)
        val early = item("early", "Early", today, LocalTime.of(9, 0), eventId = 1L)
        val late = item("late", "Late", today, LocalTime.of(11, 0), eventId = 2L)

        val normal = buildConnection(listOf(late, early), today, reverse = false)
        val reversed = buildConnection(listOf(late, early), today, reverse = true)

        assertEquals(listOf("early", "late"), normal.state.groups.single().items.map { it.stableKey })
        assertEquals(listOf("late", "early"), reversed.state.groups.single().items.map { it.stableKey })
    }

    @Test
    fun `active events stay ahead of expired events`() {
        val today = LocalDate.of(2026, 7, 14)
        val expired = item("expired", "Expired", today.minusDays(1), LocalTime.of(9, 0), eventId = 1L)
        val active = item("active", "Active", today, LocalTime.of(9, 0), eventId = 2L)

        val connection = buildConnection(listOf(expired, active), today, reverse = false)

        assertEquals("active", connection.state.groups.first().items.first().stableKey)
        assertEquals("expired", connection.state.groups.last().items.first().stableKey)
    }

    private fun buildConnection(
        items: List<ScheduleDisplayItem>,
        today: LocalDate,
        reverse: Boolean
    ): AllEventsConnection = buildAllEventsConnection(
        items = items,
        searchQuery = "",
        today = today,
        now = today.atTime(8, 0),
        reverseOrderEnabled = reverse,
        revealedItemKey = null,
        timeRefreshToken = 1L,
        futureDays = 7,
        futureLimit = today.plusDays(7)
    )

    private fun item(
        key: String,
        title: String,
        date: LocalDate,
        time: LocalTime,
        eventId: Long
    ): ScheduleDisplayItem {
        val start = LocalDateTime.of(date, time)
        val end = start.plusHours(1)
        return ScheduleDisplayItem(
            stableKey = key,
            title = title,
            startTS = start.atZone(ZoneId.systemDefault()).toEpochSecond(),
            endTS = end.atZone(ZoneId.systemDefault()).toEpochSecond(),
            action = ScheduleDisplayItem.ActionTarget.Single(eventId)
        )
    }
}
