package com.antgskds.calendarassistant.feature.notification.policy

import com.antgskds.calendarassistant.feature.notification.model.*
import org.junit.Assert.*
import org.junit.Test

class QuickMemoReminderDeliveryPolicyTest {
    @Test fun capsuleSwitchChoosesAnActualPublisherRoute() {
        assertEquals(NotificationRoute.LIVE, QuickMemoReminderDeliveryPolicy.route(true))
        assertEquals(NotificationRoute.NORMAL, QuickMemoReminderDeliveryPolicy.route(false))
    }

    @Test fun occurrenceChecksAcceptRetriesAndFutureRepeatsButRejectOldOrEditedAlarms() {
        val nextAfter: (Long) -> Long? = { after -> listOf(100L, 200L, 300L).firstOrNull { it > after } }
        assertTrue(QuickMemoReminderDeliveryPolicy.acceptsOccurrence(null, 100L, nextAfter))
        assertTrue(QuickMemoReminderDeliveryPolicy.acceptsOccurrence(100L, 100L, nextAfter))
        assertTrue(QuickMemoReminderDeliveryPolicy.acceptsOccurrence(200L, 100L, nextAfter))
        assertFalse(QuickMemoReminderDeliveryPolicy.acceptsOccurrence(99L, 100L, nextAfter))
        assertFalse(QuickMemoReminderDeliveryPolicy.acceptsOccurrence(150L, 100L, nextAfter))
        assertFalse(QuickMemoReminderDeliveryPolicy.acceptsOccurrence(100L, 200L, nextAfter))
    }

    @Test fun readySuccessMustNotCompleteOrDeleteTheReminder() {
        val key = NotificationKey("quick-memo:reminder:3")
        for (state in NotificationState.entries) {
            assertEquals(state == NotificationState.POSTED, QuickMemoReminderDeliveryPolicy.isDelivered(NotificationResult.Success(key, state)))
        }
        assertFalse(QuickMemoReminderDeliveryPolicy.isDelivered(NotificationResult.Success(key)))
        assertFalse(QuickMemoReminderDeliveryPolicy.isDelivered(NotificationResult.Failure(key, NotificationFailureReason.PERMISSION_DENIED)))
    }
}
