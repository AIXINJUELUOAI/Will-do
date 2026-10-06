package com.antgskds.calendarassistant.platform.notification.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickMemoReminderSchedulerTest {
    @Test
    fun quotaRejectionDoesNotCrashOrAttemptFallback() {
        val quotaError = IllegalStateException("Maximum limit of concurrent alarms 500 reached")
        var fallbackCalls = 0
        var rejected: RuntimeException? = null

        val scheduled = registerQuickMemoReminderAlarm(
            preferred = { throw quotaError },
            fallback = { fallbackCalls++ },
            onRejected = { rejected = it },
        )

        assertFalse(scheduled)
        assertEquals(0, fallbackCalls)
        assertSame(quotaError, rejected)
    }

    @Test
    fun permissionRejectionCanRecoverWithInexactAlarm() {
        var fallbackCalls = 0
        val scheduled = registerQuickMemoReminderAlarm(
            preferred = { throw SecurityException("Exact alarm permission revoked") },
            fallback = { fallbackCalls++ },
            onRejected = { throw AssertionError("Successful fallback must not be rejected", it) },
        )

        assertTrue(scheduled)
        assertEquals(1, fallbackCalls)
    }

    @Test
    fun fallbackQuotaAndPermissionRejectionsAlsoDoNotCrash() {
        val failures = listOf(
            IllegalStateException("Maximum limit of concurrent alarms 500 reached"),
            SecurityException("Inexact alarm permission unavailable"),
        )
        failures.forEach { failure ->
            var rejected: RuntimeException? = null
            val scheduled = registerQuickMemoReminderAlarm(
                preferred = { throw SecurityException("Exact alarm permission revoked") },
                fallback = { throw failure },
                onRejected = { rejected = it },
            )

            assertFalse(scheduled)
            assertSame(failure, rejected)
        }
    }

    @Test
    fun retryCanSucceedAfterQuotaBecomesAvailable() {
        var quotaReached = true
        var registrations = 0
        var rejections = 0
        val preferred = {
            if (quotaReached) throw IllegalStateException("Alarm quota reached")
            registrations++
            Unit
        }
        val fallback = { throw AssertionError("Quota rejection must wait for retry") }
        val onRejected: (RuntimeException) -> Unit = { rejections++ }

        assertFalse(registerQuickMemoReminderAlarm(preferred, fallback, onRejected))
        assertEquals(0, registrations)
        quotaReached = false
        assertTrue(registerQuickMemoReminderAlarm(preferred, fallback, onRejected))
        assertEquals(1, registrations)
        assertEquals(1, rejections)
    }
}
