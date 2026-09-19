package com.antgskds.calendarassistant.feature.notification.policy

import com.antgskds.calendarassistant.feature.notification.model.*
import org.junit.Assert.*
import org.junit.Test

class QuickMemoReminderDeliveryPolicyTest {
    @Test fun capsuleSwitchChoosesAnActualPublisherRoute() {
        assertEquals(NotificationRoute.LIVE, QuickMemoReminderDeliveryPolicy.route(true))
        assertEquals(NotificationRoute.NORMAL, QuickMemoReminderDeliveryPolicy.route(false))
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
