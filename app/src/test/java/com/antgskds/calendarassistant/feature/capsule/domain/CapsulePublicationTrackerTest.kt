package com.antgskds.calendarassistant.feature.capsule.domain

import com.antgskds.calendarassistant.feature.capsule.domain.model.CapsuleUiState.Active.CapsuleItem
import org.junit.Assert.*
import org.junit.Test

class CapsulePublicationTrackerTest {
    private fun item(id: String, notificationId: Int, text: String) = CapsuleItem(
        id, notificationId, 1, "event", text, text, "", 0,
        startMillis = 100, endMillis = 10000,
        display = CapsuleDisplayModel(shortText = text, primaryText = text),
    )

    @Test fun recognitionProgressResultAndRemovalDoNotRepublishExistingSchedule() {
        val tracker = CapsulePublicationTracker()
        val schedule = item("schedule", 1, "开会")
        tracker.commit(requireNotNull(tracker.prepare(schedule, 1000)))
        val progress = item("OCR_PROGRESS", 2, "识别中")
        tracker.commit(requireNotNull(tracker.prepare(progress, 2000)))
        assertNull(tracker.prepare(schedule, 2000))
        val result = item("OCR_RESULT", 2, "识别完成")
        val change = requireNotNull(tracker.prepare(result, 3000))
        assertEquals(2000L, change.firstPublishedAt)
        tracker.commit(change)
        assertNull(tracker.prepare(schedule, 3000))
        tracker.remove(2)
        assertNull(tracker.prepare(schedule, 4000))
        val changed = requireNotNull(tracker.prepare(schedule.copy(content = "改期"), 5000))
        assertEquals(1000L, changed.firstPublishedAt)
    }

    @Test fun failureIsRetriedAndSystemMissingNotificationCanBeRestoredWithoutChangingItsTime() {
        val tracker = CapsulePublicationTracker()
        val schedule = item("schedule", 1, "meeting")
        assertNotNull(tracker.prepare(schedule, 1000)) // Failed notify: no commit.
        val retry = requireNotNull(tracker.prepare(schedule, 2000))
        tracker.commit(retry)
        val restore = requireNotNull(tracker.prepare(schedule, 3000, force = true))
        assertEquals(2000L, restore.firstPublishedAt)
        tracker.remove(1)
        assertEquals(4000L, requireNotNull(tracker.prepare(schedule, 4000)).firstPublishedAt)
    }

    @Test fun aggregateRecalculationIsIgnoredButItsContentsAndScheduleTimesStillUpdate() {
        val tracker = CapsulePublicationTracker()
        val aggregate = item("AGGREGATE_PICKUP", 1, "2 个取件")
        tracker.commit(requireNotNull(tracker.prepare(aggregate, 1000)))
        assertNull(tracker.prepare(aggregate.copy(startMillis = 2000), 2000))
        assertNotNull(tracker.prepare(aggregate.copy(endMillis = 20000), 2000))
        val schedule = item("schedule", 2, "meeting")
        tracker.commit(requireNotNull(tracker.prepare(schedule, 1000)))
        assertNotNull(tracker.prepare(schedule.copy(startMillis = 3000), 3000))
        tracker.clear()
        assertNotNull(tracker.prepare(aggregate, 4000))
    }
}
