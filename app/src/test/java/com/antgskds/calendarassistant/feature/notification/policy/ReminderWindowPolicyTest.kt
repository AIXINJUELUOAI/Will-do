package com.antgskds.calendarassistant.feature.notification.policy

import com.antgskds.calendarassistant.feature.schedule.domain.model.RepeatSpec
import com.antgskds.calendarassistant.feature.schedule.domain.model.nextOccurrenceAfter
import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test

class ReminderWindowPolicyTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private fun at(value: String) = ZonedDateTime.parse(value + "+08:00").toInstant().toEpochMilli()

    @Test fun sevenCalendarDaysIncludeTodayAndAdvanceOnlyWithNewForegroundWindow() {
        val firstEnd = ReminderWindowPolicy.endExclusive(at("2026-10-06T10:35:00"), zone)
        assertEquals(at("2026-10-13T00:00:00"), firstEnd)
        val nextNow = at("2026-10-08T09:00:00")
        assertFalse(ReminderWindowPolicy.allowsRegistration(at("2026-10-13T10:30:00"), nextNow, firstEnd, false, true))
        val newEnd = ReminderWindowPolicy.endExclusive(nextNow, zone)
        assertEquals(at("2026-10-15T00:00:00"), newEnd)
        assertTrue(ReminderWindowPolicy.allowsRegistration(at("2026-10-14T10:30:00"), nextNow, newEnd, true, false))
    }

    @Test fun backgroundDoesNotRegisterAndBoundsExcludeExpiredAndMidnightCutoff() {
        val now = at("2026-10-06T10:35:00")
        val end = ReminderWindowPolicy.endExclusive(now, zone)
        assertFalse(ReminderWindowPolicy.contains(now, now, end))
        assertFalse(ReminderWindowPolicy.contains(now - 1L, now, end))
        assertFalse(ReminderWindowPolicy.contains(end, now, end))
        assertTrue(ReminderWindowPolicy.contains(end - 1L, now, end))
        assertFalse(ReminderWindowPolicy.allowsRegistration(now + 1L, now, end, false, false))
        assertTrue(ReminderWindowPolicy.allowsRegistration(now + 1L, now, end, false, true))
        assertFalse(ReminderWindowPolicy.allowsRegistration(now + 1L, now, 0L, false, true))
    }

    @Test fun dailyMemoPreRegistersEachFutureOccurrenceAtTheSameLocalTime() {
        val first = at("2026-10-06T10:30:00")
        val now = at("2026-10-06T10:35:00")
        val occurrences = occurrences(first, now, RepeatSpec.daily())
        assertEquals((7..12).map { at("2026-10-${it.toString().padStart(2, '0')}T10:30:00") }, occurrences)
        val before = at("2026-10-06T10:29:00")
        assertEquals(7, occurrences(first, before, RepeatSpec.daily()).size)
    }

    @Test fun weeklyAndWeekdayMemoOccurrencesRespectTheSavedWindow() {
        val first = at("2026-09-29T10:30:00")
        val now = at("2026-10-06T09:00:00")
        assertEquals(listOf(at("2026-10-06T10:30:00")), occurrences(first, now, RepeatSpec.weekly()))
        val weekdays = occurrences(at("2026-10-05T10:30:00"), now, RepeatSpec.weekdays())
        assertEquals(listOf(6, 7, 8, 9, 12).map { at("2026-10-${it.toString().padStart(2, '0')}T10:30:00") }, weekdays)
    }

    @Test fun nonIncreasingOrEndedRecurrenceStopsAndCapsuleModePreservesBraceletAlarms() {
        assertEquals(listOf(11L), ReminderWindowPolicy.occurrences(11L, 10L, 20L) { it })
        assertTrue(ReminderWindowPolicy.occurrences(9L, 10L, 20L) { null }.isEmpty())
        assertFalse(ReminderWindowPolicy.needsNormalReminder(MySettings(isLiveCapsuleEnabled = true)))
        assertTrue(ReminderWindowPolicy.needsNormalReminder(MySettings(isLiveCapsuleEnabled = false)))
        assertTrue(ReminderWindowPolicy.needsNormalReminder(MySettings(isLiveCapsuleEnabled = true, braceletModeEnabled = true)))
    }

    @Test fun calendarWindowFollowsMidnightAcrossDaylightSavingRatherThanFixedHours() {
        val dstZone = ZoneId.of("Europe/Berlin")
        val now = ZonedDateTime.of(2026, 10, 20, 12, 0, 0, 0, dstZone)
        val expected = ZonedDateTime.of(2026, 10, 27, 0, 0, 0, 0, dstZone)
        assertEquals(expected.toInstant().toEpochMilli(), ReminderWindowPolicy.endExclusive(now.toInstant().toEpochMilli(), dstZone))
    }

    private fun occurrences(first: Long, now: Long, spec: RepeatSpec): List<Long> =
        ReminderWindowPolicy.occurrences(first, now, ReminderWindowPolicy.endExclusive(now, zone)) { after ->
            spec.nextOccurrenceAfter(Instant.ofEpochMilli(first).atZone(zone), Instant.ofEpochMilli(after).atZone(zone))
                ?.toInstant()?.toEpochMilli()
        }
}
