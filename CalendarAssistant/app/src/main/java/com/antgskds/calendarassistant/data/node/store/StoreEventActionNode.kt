package com.antgskds.calendarassistant.data.node.store

import android.util.Log
import com.antgskds.calendarassistant.core.rule.EventActionResolver
import com.antgskds.calendarassistant.core.rule.RuleActionType
import com.antgskds.calendarassistant.core.rule.RuleMatchingEngine
import com.antgskds.calendarassistant.data.model.EventTags
import com.antgskds.calendarassistant.data.model.MyEvent
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

internal object StoreEventActionNode {
    data class DetachRecurringResult(
        val updatedList: MutableList<MyEvent>,
        val detachedLocalEvent: MyEvent,
        val removedSourceInstance: MyEvent?
    )

    fun detachRecurringInstance(
        currentList: MutableList<MyEvent>,
        parentEventId: String,
        sourceInstanceId: String,
        sourceInstanceKey: String,
        detachedEvent: MyEvent
    ): DetachRecurringResult? {
        val parentIndex = currentList.indexOfFirst { it.id == parentEventId && it.isRecurringParent }
        if (parentIndex == -1) return null

        val parentEvent = currentList[parentIndex]
        val updatedParent = parentEvent.copy(
            excludedRecurringInstances = (parentEvent.excludedRecurringInstances + sourceInstanceKey).distinct(),
            lastModified = System.currentTimeMillis()
        )
        currentList[parentIndex] = updatedParent

        val sourceInstance = currentList.find { it.id == sourceInstanceId }
        if (sourceInstance != null) {
            currentList.remove(sourceInstance)
        }

        val detachedLocalEvent = detachedEvent.copy(
            id = UUID.randomUUID().toString(),
            isRecurring = false,
            isRecurringParent = false,
            recurringSeriesKey = null,
            recurringInstanceKey = null,
            parentRecurringId = null,
            excludedRecurringInstances = emptyList(),
            nextOccurrenceStartMillis = null,
            skipCalendarSync = detachedEvent.skipCalendarSync,
            lastModified = System.currentTimeMillis()
        )

        currentList.add(detachedLocalEvent)

        return DetachRecurringResult(
            updatedList = currentList,
            detachedLocalEvent = detachedLocalEvent,
            removedSourceInstance = sourceInstance
        )
    }

    suspend fun completeScheduleEvent(
        id: String,
        findActiveEventById: (String) -> MyEvent?,
        updateEventWithoutSync: suspend (MyEvent) -> Unit,
        requestCapsuleRefresh: () -> Unit,
        syncSingleEventToCalendar: suspend (MyEvent) -> Unit
    ) {
        val event = findActiveEventById(id)
        if (event != null && event.tag != EventTags.NOTE && !event.isCompleted) {
            val now = LocalDateTime.now()
            val formatter = DateTimeFormatter.ofPattern("HH:mm")

            val updatedEvent = event.copy(
                endDate = now.toLocalDate(),
                endTime = now.format(formatter),
                isCompleted = true,
                completedAt = System.currentTimeMillis(),
                originalEndDate = event.endDate,
                originalEndTime = event.endTime
            )

            updateEventWithoutSync(updatedEvent)
            requestCapsuleRefresh()
            syncSingleEventToCalendar(updatedEvent)
        }
    }

    suspend fun checkInTransport(
        id: String,
        findActiveEventById: (String) -> MyEvent?,
        updateEventWithoutSync: suspend (MyEvent) -> Unit,
        requestCapsuleRefresh: () -> Unit,
        syncSingleEventToCalendar: suspend (MyEvent) -> Unit
    ) {
        Log.d("Undo", "checkInTransport: id=$id")
        val event = findActiveEventById(id)
        Log.d("Undo", "checkInTransport: event=$event, isCheckedIn=${event?.isCheckedIn}")
        if (event != null && !event.isCheckedIn) {
            val updatedEvent = event.copy(
                isCheckedIn = true,
                completedAt = System.currentTimeMillis()
            )
            updateEventWithoutSync(updatedEvent)
            requestCapsuleRefresh()
            syncSingleEventToCalendar(updatedEvent)
        }
    }

    suspend fun undoCompleteEvent(
        id: String,
        findActiveEventById: (String) -> MyEvent?,
        updateEvent: suspend (MyEvent) -> Unit,
        requestCapsuleRefresh: () -> Unit
    ): Boolean {
        val event = findActiveEventById(id)
        if (event != null && event.isCompleted && event.completedAt != null) {
            val elapsed = System.currentTimeMillis() - event.completedAt
            if (elapsed <= 60000) {
                val restoredEvent = event.copy(
                    endDate = event.originalEndDate ?: event.endDate,
                    endTime = event.originalEndTime ?: event.endTime,
                    isCompleted = false,
                    completedAt = null,
                    originalEndDate = null,
                    originalEndTime = null
                )
                updateEvent(restoredEvent)
                requestCapsuleRefresh()
                return true
            }
        }
        return false
    }

    suspend fun undoCheckInTransport(
        id: String,
        findActiveEventById: (String) -> MyEvent?,
        updateEventWithoutSync: suspend (MyEvent) -> Unit,
        requestCapsuleRefresh: () -> Unit
    ): Boolean {
        Log.d("Undo", "undoCheckInTransport: id=$id")
        val event = findActiveEventById(id)
        Log.d("Undo", "event=$event, isCheckedIn=${event?.isCheckedIn}, completedAt=${event?.completedAt}")
        if (event != null && event.isCheckedIn && event.completedAt != null) {
            val elapsed = System.currentTimeMillis() - event.completedAt
            Log.d("Undo", "elapsed=$elapsed, 1分钟内=${elapsed <= 60000}")
            if (elapsed <= 60000) {
                val restoredEvent = event.copy(
                    isCheckedIn = false,
                    completedAt = null
                )
                updateEventWithoutSync(restoredEvent)
                requestCapsuleRefresh()
                return true
            }
        }
        return false
    }

    suspend fun performPrimaryRuleAction(
        eventId: String,
        findActiveEventById: (String) -> MyEvent?,
        undoCheckInTransport: suspend (String) -> Boolean,
        undoCompleteEvent: suspend (String) -> Boolean,
        checkInTransport: suspend (String) -> Unit,
        completeScheduleEvent: suspend (String) -> Unit
    ): Boolean {
        val event = findActiveEventById(eventId) ?: return false
        if (event.isRecurringParent) return false
        val decision = EventActionResolver.resolve(event) ?: return false
        when (decision.actionType) {
            RuleActionType.UNDO -> {
                if (decision.ruleId == RuleMatchingEngine.RULE_TRAIN) {
                    undoCheckInTransport(eventId)
                } else {
                    undoCompleteEvent(eventId)
                }
            }

            RuleActionType.CHECKIN -> checkInTransport(eventId)
            RuleActionType.COMPLETE -> completeScheduleEvent(eventId)
        }
        return true
    }
}
