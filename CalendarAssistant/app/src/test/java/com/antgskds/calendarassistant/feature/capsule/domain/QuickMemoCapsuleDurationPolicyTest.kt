package com.antgskds.calendarassistant.feature.capsule.domain

import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class QuickMemoCapsuleDurationPolicyTest {
    @Test fun followsConfiguredMinutes() {
        assertEquals(60 * 60_000L, QuickMemoCapsuleDurationPolicy.durationMillis(60))
        assertEquals(30 * 60_000L, QuickMemoCapsuleDurationPolicy.durationMillis(30))
    }

    @Test fun endOfDayMatchesPinnedCapsuleAndKeepsMinimumNearMidnight() {
        val afternoon = ZonedDateTime.parse("2026-09-13T15:00:00+08:00[Asia/Shanghai]")
        assertEquals((8 * 60 + 59) * 60_000L, QuickMemoCapsuleDurationPolicy.durationMillis(-1, afternoon))
        assertEquals(60_000L, QuickMemoCapsuleDurationPolicy.durationMillis(-1, afternoon.withHour(23).withMinute(59).withSecond(30)))
    }
}
