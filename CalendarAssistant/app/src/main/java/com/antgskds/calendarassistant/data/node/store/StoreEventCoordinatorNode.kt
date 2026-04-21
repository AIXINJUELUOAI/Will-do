package com.antgskds.calendarassistant.data.node.store

import android.util.Log
import com.antgskds.calendarassistant.data.db.entity.EventInstanceEntity
import com.antgskds.calendarassistant.data.model.MyEvent

internal object StoreEventCoordinatorNode {
    suspend fun detachRecurringInstance(
        parentEventId: String,
        sourceInstanceId: String,
        sourceInstanceKey: String,
        detachedEvent: MyEvent,
        withEventLock: suspend (suspend () -> Unit) -> Unit,
        loadCurrentActiveMutableList: () -> MutableList<MyEvent>,
        cancelReminders: (MyEvent) -> Unit,
        updateEvents: suspend (List<MyEvent>) -> Unit,
        scheduleRemindersIfNeeded: (MyEvent) -> Unit,
        requestCapsuleRefresh: () -> Unit
    ) {
        withEventLock {
            val currentList = loadCurrentActiveMutableList()
            val detachResult = StoreEventActionNode.detachRecurringInstance(
                currentList = currentList,
                parentEventId = parentEventId,
                sourceInstanceId = sourceInstanceId,
                sourceInstanceKey = sourceInstanceKey,
                detachedEvent = detachedEvent
            ) ?: return@withEventLock

            detachResult.removedSourceInstance?.let(cancelReminders)
            updateEvents(detachResult.updatedList)
            scheduleRemindersIfNeeded(detachResult.detachedLocalEvent)
            requestCapsuleRefresh()
        }
    }

    suspend fun completeScheduleEvent(
        id: String,
        findActiveEventById: (String) -> MyEvent?,
        updateEventWithoutSync: suspend (MyEvent) -> Unit,
        requestCapsuleRefresh: () -> Unit,
        syncSingleEventToCalendar: suspend (MyEvent) -> Unit
    ) {
        StoreEventActionNode.completeScheduleEvent(
            id = id,
            findActiveEventById = findActiveEventById,
            updateEventWithoutSync = updateEventWithoutSync,
            requestCapsuleRefresh = requestCapsuleRefresh,
            syncSingleEventToCalendar = syncSingleEventToCalendar
        )
    }

    suspend fun checkInTransport(
        id: String,
        findActiveEventById: (String) -> MyEvent?,
        updateEventWithoutSync: suspend (MyEvent) -> Unit,
        requestCapsuleRefresh: () -> Unit,
        syncSingleEventToCalendar: suspend (MyEvent) -> Unit
    ) {
        StoreEventActionNode.checkInTransport(
            id = id,
            findActiveEventById = findActiveEventById,
            updateEventWithoutSync = updateEventWithoutSync,
            requestCapsuleRefresh = requestCapsuleRefresh,
            syncSingleEventToCalendar = syncSingleEventToCalendar
        )
    }

    suspend fun undoCompleteEvent(
        id: String,
        findActiveEventById: (String) -> MyEvent?,
        updateEvent: suspend (MyEvent) -> Unit,
        requestCapsuleRefresh: () -> Unit
    ): Boolean {
        return StoreEventActionNode.undoCompleteEvent(
            id = id,
            findActiveEventById = findActiveEventById,
            updateEvent = updateEvent,
            requestCapsuleRefresh = requestCapsuleRefresh
        )
    }

    suspend fun undoCheckInTransport(
        id: String,
        findActiveEventById: (String) -> MyEvent?,
        updateEventWithoutSync: suspend (MyEvent) -> Unit,
        requestCapsuleRefresh: () -> Unit
    ): Boolean {
        return StoreEventActionNode.undoCheckInTransport(
            id = id,
            findActiveEventById = findActiveEventById,
            updateEventWithoutSync = updateEventWithoutSync,
            requestCapsuleRefresh = requestCapsuleRefresh
        )
    }

    suspend fun performPrimaryRuleAction(
        eventId: String,
        findActiveEventById: (String) -> MyEvent?,
        undoCheckInTransport: suspend (String) -> Boolean,
        undoCompleteEvent: suspend (String) -> Boolean,
        checkInTransport: suspend (String) -> Unit,
        completeScheduleEvent: suspend (String) -> Unit
    ): Boolean {
        return StoreEventActionNode.performPrimaryRuleAction(
            eventId = eventId,
            findActiveEventById = findActiveEventById,
            undoCheckInTransport = undoCheckInTransport,
            undoCompleteEvent = undoCompleteEvent,
            checkInTransport = checkInTransport,
            completeScheduleEvent = completeScheduleEvent
        )
    }

    suspend fun markRecurringInstanceCancelled(
        eventId: String,
        loadInstanceById: suspend (String) -> EventInstanceEntity?,
        updateInstance: suspend (EventInstanceEntity) -> Unit
    ) {
        try {
            val instance = loadInstanceById(eventId) ?: return
            if (!instance.isCancelled) {
                updateInstance(instance.copy(isCancelled = true))
            }
        } catch (e: Exception) {
            Log.e("StoreNode", "标记重复实例取消失败: $eventId", e)
        }
    }
}
