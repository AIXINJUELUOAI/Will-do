package com.antgskds.calendarassistant.core.center

import com.antgskds.calendarassistant.core.operation.ScheduleOperationApi
import com.antgskds.calendarassistant.core.query.ScheduleQueryApi
import com.antgskds.calendarassistant.core.query.SettingsQueryApi
import com.antgskds.calendarassistant.core.rule.RuleMatchingEngine
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.data.model.MySettings
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class ScheduleCenter(
    private val scheduleOperationApi: ScheduleOperationApi,
    private val scheduleQueryApi: ScheduleQueryApi,
    private val settingsQueryApi: SettingsQueryApi
) {
    companion object {
        private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
    }

    val events: StateFlow<List<MyEvent>>
        get() = scheduleQueryApi.events

    val archivedEvents: StateFlow<List<MyEvent>>
        get() = scheduleQueryApi.archivedEvents

    fun refreshAndScheduleAll() {
        scheduleOperationApi.refreshAndScheduleAll()
    }

    suspend fun addEvent(event: MyEvent) {
        scheduleOperationApi.addEvent(event)
    }

    suspend fun updateEvent(event: MyEvent) {
        scheduleOperationApi.updateEvent(event)
    }

    suspend fun deleteEvent(eventId: String) {
        scheduleOperationApi.deleteEvent(eventId)
    }

    suspend fun detachRecurringInstance(
        parentEventId: String,
        sourceInstanceId: String,
        sourceInstanceKey: String,
        detachedEvent: MyEvent
    ) {
        scheduleOperationApi.detachRecurringInstance(
            parentEventId = parentEventId,
            sourceInstanceId = sourceInstanceId,
            sourceInstanceKey = sourceInstanceKey,
            detachedEvent = detachedEvent
        )
    }

    suspend fun performPrimaryRuleAction(eventId: String): Boolean {
        return scheduleOperationApi.performPrimaryRuleAction(eventId)
    }

    suspend fun completeScheduleEvent(eventId: String) {
        scheduleOperationApi.completeScheduleEvent(eventId)
    }

    suspend fun archiveEvent(eventId: String) {
        scheduleOperationApi.archiveEvent(eventId)
    }

    suspend fun restoreEvent(archivedEventId: String) {
        scheduleOperationApi.restoreEvent(archivedEventId)
    }

    suspend fun deleteArchivedEvent(archivedEventId: String) {
        scheduleOperationApi.deleteArchivedEvent(archivedEventId)
    }

    suspend fun clearAllArchives() {
        scheduleOperationApi.clearAllArchives()
    }

    suspend fun autoArchiveExpiredEvents(): Int {
        return scheduleOperationApi.autoArchiveExpiredEvents()
    }

    fun fetchArchivedEvents() {
        scheduleQueryApi.fetchArchivedEvents()
    }

    fun getEventsCount(): Int {
        return scheduleQueryApi.getEventsCount()
    }

    fun getTotalEventsCount(): Int {
        return scheduleQueryApi.getTotalEventsCount()
    }

    suspend fun completeAllActivePickups(now: LocalDateTime = LocalDateTime.now()): Int {
        val settings = settingsQueryApi.settings.value
        val activePickups = scheduleQueryApi.events.value.filter { event ->
            isAggregateActivePickup(event = event, settings = settings, now = now)
        }

        activePickups.forEach { event ->
            scheduleOperationApi.completeScheduleEvent(event.id)
        }

        return activePickups.size
    }

    private fun isAggregateActivePickup(
        event: MyEvent,
        settings: MySettings,
        now: LocalDateTime
    ): Boolean {
        val ruleId = RuleMatchingEngine.resolvePayload(event)?.ruleId
            ?: RuleMatchingEngine.RULE_GENERAL
        if (ruleId != RuleMatchingEngine.RULE_PICKUP || event.isCompleted || event.isRecurringParent) {
            return false
        }

        return try {
            val startDateTime = LocalDateTime.of(
                event.startDate,
                LocalTime.parse(event.startTime, TIME_FORMATTER)
            )
            val endDateTime = LocalDateTime.of(
                event.endDate,
                LocalTime.parse(event.endTime, TIME_FORMATTER)
            )
            val effectiveStartTime = if (settings.isAdvanceReminderEnabled && settings.advanceReminderMinutes > 0) {
                startDateTime.minusMinutes(settings.advanceReminderMinutes.toLong())
            } else {
                startDateTime.minusMinutes(1)
            }

            now.isBefore(endDateTime) && !now.isBefore(effectiveStartTime)
        } catch (_: Exception) {
            false
        }
    }
}
