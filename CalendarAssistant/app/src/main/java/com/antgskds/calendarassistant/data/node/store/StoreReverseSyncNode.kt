package com.antgskds.calendarassistant.data.node.store

import com.antgskds.calendarassistant.data.model.MyEvent

internal data class StoreCalendarSyncSnapshot(
    val activeEvents: List<MyEvent>,
    val archivedEvents: List<MyEvent>
)

internal object StoreReverseSyncNode {
    suspend fun syncFromCalendar(
        ensureArchivesLoaded: suspend () -> Unit,
        activeEvents: () -> List<MyEvent>,
        archivedEvents: () -> List<MyEvent>,
        isCalendarSyncV2Enabled: () -> Boolean,
        syncGatewayCall: suspend (
            Boolean,
            StoreCalendarSyncSnapshot,
            suspend (MyEvent) -> Unit,
            suspend (MyEvent) -> Unit,
            suspend (String) -> Unit
        ) -> Result<Int>,
        onEventUpsert: suspend (MyEvent, StoreCalendarSyncSnapshot) -> Unit,
        onEventDeleted: suspend (String, StoreCalendarSyncSnapshot) -> Unit
    ): Result<Int> {
        return try {
            ensureArchivesLoaded()

            val snapshot = StoreCalendarSyncSnapshot(
                activeEvents = activeEvents(),
                archivedEvents = archivedEvents()
            )

            syncGatewayCall(
                isCalendarSyncV2Enabled(),
                snapshot,
                { incoming -> onEventUpsert(incoming, snapshot) },
                { incoming -> onEventUpsert(incoming, snapshot) },
                { eventId -> onEventDeleted(eventId, snapshot) }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun handleCalendarAddedOrUpdated(
        incomingEvent: MyEvent,
        snapshot: StoreCalendarSyncSnapshot,
        mergeIncomingCalendarEvent: (MyEvent, MyEvent) -> MyEvent,
        isNoopCalendarMerge: (MyEvent, MyEvent) -> Boolean,
        updateEventWithoutSync: suspend (MyEvent) -> Unit,
        addEventWithoutSync: suspend (MyEvent) -> Unit,
        isDuplicateEvent: (MyEvent, List<MyEvent>, List<MyEvent>) -> Boolean,
        withRandomColorIfNeeded: (MyEvent) -> MyEvent
    ) {
        val existingById = snapshot.activeEvents.find { it.id == incomingEvent.id }
        if (existingById != null) {
            val finalEvent = mergeIncomingCalendarEvent(existingById, incomingEvent)
            if (!isNoopCalendarMerge(existingById, finalEvent)) {
                updateEventWithoutSync(finalEvent)
            }
            return
        }

        if (incomingEvent.isRecurring) {
            addEventWithoutSync(incomingEvent)
            return
        }

        val isDup = isDuplicateEvent(incomingEvent, snapshot.activeEvents, snapshot.archivedEvents)
        if (!isDup) {
            addEventWithoutSync(withRandomColorIfNeeded(incomingEvent))
        }
    }

    suspend fun handleCalendarDeleted(
        eventId: String,
        snapshot: StoreCalendarSyncSnapshot,
        deleteEventWithoutSync: suspend (String, Boolean) -> Unit
    ) {
        val target = snapshot.activeEvents.find { it.id == eventId }
            ?: snapshot.archivedEvents.find { it.id == eventId }
        val removeFromRoom = !(target?.isRecurring == true && !target.isRecurringParent)
        deleteEventWithoutSync(eventId, removeFromRoom)
    }
}
