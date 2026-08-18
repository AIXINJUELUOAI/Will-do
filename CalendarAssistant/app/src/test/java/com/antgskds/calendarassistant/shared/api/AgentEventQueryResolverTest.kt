package com.antgskds.calendarassistant.shared.api

import com.antgskds.calendarassistant.feature.schedule.domain.model.Event
import com.antgskds.calendarassistant.shared.operation.AgentEventQuery
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentEventQueryResolverTest {

    @Test
    fun rangeQueryExpandsRecurringEventAfterParentStartDate() {
        val parentStart = epochSecond(2026, 8, 10, 8, 0)
        val occurrenceStart = epochSecond(2026, 8, 17, 8, 0)
        val parent = Event(
            id = 42L,
            title = "Turn the lock one more time",
            startTS = parentStart,
            endTS = parentStart + 300L,
            rrule = "FREQ=DAILY",
            timeZone = "UTC"
        )
        val result = AgentEventQueryResolver.resolve(
            events = listOf(parent),
            query = AgentEventQuery(
                startTs = epochSecond(2026, 8, 17, 0, 0),
                endTs = epochSecond(2026, 8, 17, 23, 59)
            ),
            zoneId = ZoneOffset.UTC
        )

        assertEquals(1, result.size)
        assertEquals(42L, result.single().id)
        assertEquals(occurrenceStart, result.single().startTS)
        assertEquals(occurrenceStart + 300L, result.single().endTS)
        assertTrue(result.single().isRecurring)
    }

    private fun epochSecond(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDate.of(year, month, day)
            .atTime(LocalTime.of(hour, minute))
            .toEpochSecond(ZoneOffset.UTC)
}
