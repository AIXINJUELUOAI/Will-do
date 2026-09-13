package com.antgskds.calendarassistant.shared.api

import com.antgskds.calendarassistant.feature.schedule.domain.model.Event
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class AgentAttachmentTargetTest {
    private val start = Instant.parse("2026-09-09T09:00:00Z").epochSecond
    private val parent = Event(id = 1L, startTS = start, endTS = start + 3600,
        title = "每日会议", rrule = "FREQ=DAILY;COUNT=3", timeZone = "UTC")

    @Test fun virtualOccurrenceDoesNotUseParentAttachmentId() {
        for (ts in listOf(start, start + 86400)) {
            val target = AgentEventQueryResolver.resolveAttachmentTarget(listOf(parent), 1L, ts)
            assertNull(target.id)
            assertEquals(ts, target.startTS)
            assertEquals(ts + 3600, target.endTS)
        }
        assertEquals(1L, parent.id)
    }

    @Test fun existingChildIsReused() {
        val child = parent.copy(id = 2L, parentId = 1L, rrule = "", startTS = start + 86400,
            endTS = start + 90000)
        assertEquals(2L, AgentEventQueryResolver.resolveAttachmentTarget(listOf(parent, child), 1L, child.startTS).id)
        assertEquals(2L, AgentEventQueryResolver.resolveAttachmentTarget(listOf(parent, child), 2L, null).id)
    }

    @Test fun invalidAndExcludedInstancesAreRejected() {
        assertThrows(NoSuchElementException::class.java) {
            AgentEventQueryResolver.resolveAttachmentTarget(listOf(parent), 1L, start + 60)
        }
        assertThrows(NoSuchElementException::class.java) {
            AgentEventQueryResolver.resolveAttachmentTarget(listOf(parent), 1L, start + 86400 * 3)
        }
        assertThrows(NoSuchElementException::class.java) {
            AgentEventQueryResolver.resolveAttachmentTarget(listOf(parent.copy(exdates = listOf("20260909T090000Z"))), 1L, start)
        }
    }

    @Test fun listingSeriesWithoutOccurrenceRetainsExplicitSeriesTarget() {
        assertEquals(1L, AgentEventQueryResolver.resolveAttachmentTarget(listOf(parent), 1L, null).id)
    }
}
