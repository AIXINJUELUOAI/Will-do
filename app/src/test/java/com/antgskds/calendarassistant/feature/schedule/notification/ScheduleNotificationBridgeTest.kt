package com.antgskds.calendarassistant.feature.schedule.notification

import com.antgskds.calendarassistant.feature.notification.api.NotificationApi
import com.antgskds.calendarassistant.feature.notification.model.*
import com.antgskds.calendarassistant.feature.notification.policy.ReminderWindowPolicy
import com.antgskds.calendarassistant.feature.schedule.domain.ScheduleDisplayHelper
import com.antgskds.calendarassistant.feature.schedule.domain.model.Event
import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ScheduleNotificationBridgeTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private fun at(value: String) = ZonedDateTime.parse(value + "+08:00").toInstant().toEpochMilli()
    private val now = at("2026-10-06T10:35:00")
    private val end = ReminderWindowPolicy.endExclusive(now, zone)
    private val ordinary = MySettings(isLiveCapsuleEnabled = false)

    private fun event(id: Long, start: Long) = Event(id = id, startTS = start / 1000L, endTS = start / 1000L + 3600L, title = "test", timeZone = zone.id)
    private fun bridge(api: RecordingApi, allowed: () -> Boolean = { true }, settings: () -> MySettings = { ordinary }, nowProvider: () -> Long = { now }, endProvider: () -> Long = { end }) =
        ScheduleNotificationBridge(api, settings, windowEndProvider = endProvider, registrationAllowedProvider = allowed, nowProvider = nowProvider)

    @Test fun singleAlarmsAreFilteredByActualTriggerRatherThanEventStart() = runBlocking {
        val api = RecordingApi()
        bridge(api).submitSingleEvents(listOf(
            event(1, at("2026-10-06T11:00:00")),
            event(2, at("2026-10-06T10:30:00")),
            event(3, end),
            event(4, end + 600_000L).copy(reminder1Minutes = 30),
        ))
        assertEquals(setOf("schedule:single:1:offset:0", "schedule:single:4:offset:30"), api.entries.keys)
        assertEquals(end - 1_200_000L, api.entries.getValue("schedule:single:4:offset:30").behavior.triggerAtEpochMillis)
    }

    @Test fun backgroundKeepsValidAlarmsAndOnlyCancelsExpiredOrEditedOnes() = runBlocking {
        val api = RecordingApi()
        val soon = event(1, now + 60_000L)
        val later = event(2, now + 3_600_000L)
        bridge(api).submitSingleEvents(listOf(soon, later))
        api.calls.clear()
        bridge(api, allowed = { false }, nowProvider = { now + 120_000L }).submitSingleEvents(listOf(soon, later, event(3, now + 7_200_000L)))
        assertEquals(listOf("cancel:schedule:single:1:offset:0"), api.calls)
        assertEquals(setOf("schedule:single:2:offset:0"), api.entries.keys)
        api.calls.clear()
        bridge(api, allowed = { false }).onEventUpdated(later.copy(startTS = later.startTS + 60L))
        assertEquals(listOf("cancel:schedule:single:2:offset:0"), api.calls)
        assertTrue(api.entries.isEmpty())
    }

    @Test fun foregroundWindowExtensionCancelsStaleAlarmsBeforeFillingTwoNewDays() = runBlocking {
        val api = RecordingApi()
        val firstDay = event(1, at("2026-10-06T12:00:00"))
        val retained = event(2, at("2026-10-09T12:00:00"))
        val day13 = event(3, at("2026-10-13T12:00:00"))
        val day14 = event(4, at("2026-10-14T12:00:00"))
        val day15 = event(5, at("2026-10-15T12:00:00"))
        val events = listOf(firstDay, retained, day13, day14, day15)
        bridge(api).submitSingleEvents(events)
        api.calls.clear()
        val nextNow = at("2026-10-08T09:00:00")
        bridge(api, nowProvider = { nextNow }, endProvider = { ReminderWindowPolicy.endExclusive(nextNow, zone) }).submitSingleEvents(events)
        assertEquals("cancel:schedule:single:1:offset:0", api.calls.first())
        assertEquals(setOf("schedule:single:2:offset:0", "schedule:single:3:offset:0", "schedule:single:4:offset:0"), api.entries.keys)
    }

    @Test fun capsuleOnlyModeRemovesOrdinaryAlarmsAndBraceletModeRestoresThem() = runBlocking {
        val api = RecordingApi()
        var settings = ordinary
        val bridge = bridge(api, settings = { settings })
        val events = listOf(event(1, now + 3_600_000L))
        bridge.submitSingleEvents(events)
        settings = settings.copy(isLiveCapsuleEnabled = true)
        bridge.submitSingleEvents(events)
        assertTrue(api.entries.isEmpty())
        settings = settings.copy(braceletModeEnabled = true)
        bridge.submitSingleEvents(events)
        assertEquals(1, api.entries.size)
    }

    @Test fun recurringAlarmsStayWithinTheWindowAndBackgroundDoesNotGrowIt() = runBlocking {
        val api = RecordingApi()
        val parent = event(9, at("2026-10-06T12:00:00")).copy(rrule = "FREQ=DAILY")
        val items = ScheduleDisplayHelper.buildDisplayItems(listOf(parent), LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 15))
        bridge(api).submitRecurringWindow(items, mapOf(9L to parent))
        assertEquals(7, api.entries.size)
        assertTrue(api.entries.values.all { it.behavior.triggerAtEpochMillis!! in (now + 1L) until end })
        api.calls.clear()
        val nextNow = at("2026-10-08T09:00:00")
        bridge(api, allowed = { false }, nowProvider = { nextNow }).submitRecurringWindow(items, mapOf(9L to parent))
        assertEquals(5, api.entries.size)
        assertTrue(api.calls.all { it.startsWith("cancel:") })
    }

    @Test fun editedAlarmsAreCancelledBeforeReplacementAndDeletionCancelsAllOffsets() = runBlocking {
        val api = RecordingApi()
        val bridge = bridge(api)
        val initial = event(1, now + 3_600_000L).copy(reminder1Minutes = 15)
        bridge.onEventCreated(initial)
        api.calls.clear()
        bridge.onEventTimeEdited(initial.copy(startTS = initial.startTS + 3600L))
        assertTrue(api.calls.take(2).all { it.startsWith("cancel:") })
        assertTrue(api.calls.drop(2).all { it.startsWith("create:") })
        bridge.onEventDeleted(1)
        assertTrue(api.entries.isEmpty())
    }

    @Test fun lostForegroundDuringReconcileStopsNewRegistrations() = runBlocking {
        val api = RecordingApi()
        var foreground = true
        api.onCreate = { foreground = false }
        bridge(api, allowed = { foreground }).submitSingleEvents(listOf(event(1, now + 60_000L), event(2, now + 120_000L)))
        assertEquals(1, api.entries.size)
    }

    private class RecordingApi : NotificationApi {
        val entries = linkedMapOf<String, NotificationSnapshot>()
        val calls = mutableListOf<String>()
        var onCreate: (() -> Unit)? = null

        private fun record(request: NotificationRequest, operation: String): NotificationResult {
            calls += "$operation:${request.key.value}"
            entries[request.key.value] = NotificationSnapshot(request.key, request.kind, NotificationState.SCHEDULED, request.route, request.display, behavior = request.behavior)
            return NotificationResult.Success(request.key, NotificationState.SCHEDULED)
        }
        override suspend fun create(request: NotificationRequest): NotificationResult = record(request, "create").also { onCreate?.invoke() }
        override suspend fun update(request: NotificationRequest) = record(request, "update")
        override suspend fun cancel(key: NotificationKey): NotificationResult {
            calls += "cancel:${key.value}"
            entries.remove(key.value)
            return NotificationResult.Success(key, NotificationState.CANCELLED)
        }
        override suspend fun get(key: NotificationKey) = entries[key.value]
        override suspend fun list(query: NotificationQuery) = entries.values.filter { query.kind == null || it.kind == query.kind }
        override suspend fun trigger(trigger: NotificationTrigger): NotificationResult = error("Unexpected immediate trigger")
    }
}
