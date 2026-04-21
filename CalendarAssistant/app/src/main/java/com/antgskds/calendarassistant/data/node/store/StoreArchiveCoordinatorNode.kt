package com.antgskds.calendarassistant.data.node.store

import com.antgskds.calendarassistant.data.model.MyEvent

internal object StoreArchiveCoordinatorNode {
    suspend fun archiveEvent(
        eventId: String,
        findActiveEventById: (String) -> MyEvent?,
        withArchiveLock: suspend (suspend () -> Unit) -> Unit,
        loadCurrentArchivedMutableList: suspend () -> MutableList<MyEvent>,
        updateArchivedEvents: suspend (List<MyEvent>) -> Unit,
        deleteEventWithoutSyncAndKeepRoom: suspend (String) -> Unit
    ) {
        StoreArchiveWorkflowNode.archiveEvent(
            eventId = eventId,
            findActiveEventById = findActiveEventById,
            withArchiveLock = withArchiveLock,
            loadCurrentArchivedMutableList = loadCurrentArchivedMutableList,
            updateArchivedEvents = updateArchivedEvents,
            deleteEventWithoutSyncAndKeepRoom = deleteEventWithoutSyncAndKeepRoom
        )
    }

    suspend fun restoreEvent(
        archivedEventId: String,
        withArchiveLock: suspend (suspend () -> Unit) -> Unit,
        findArchivedEventById: (String) -> MyEvent?,
        addEventWithSync: suspend (MyEvent) -> Unit,
        loadCurrentArchivedMutableList: suspend () -> MutableList<MyEvent>,
        updateArchivedEvents: suspend (List<MyEvent>) -> Unit
    ) {
        StoreArchiveWorkflowNode.restoreEvent(
            archivedEventId = archivedEventId,
            withArchiveLock = withArchiveLock,
            findArchivedEventById = findArchivedEventById,
            addEventWithSync = addEventWithSync,
            loadCurrentArchivedMutableList = loadCurrentArchivedMutableList,
            updateArchivedEvents = updateArchivedEvents
        )
    }

    suspend fun deleteArchivedEvent(
        archivedEventId: String,
        withArchiveLock: suspend (suspend () -> Unit) -> Unit,
        loadCurrentArchivedMutableList: suspend () -> MutableList<MyEvent>,
        updateArchivedEvents: suspend (List<MyEvent>) -> Unit,
        deleteRoomShadowEvents: suspend (List<String>) -> Unit,
        removeCalendarMappingsForEvents: suspend (List<MyEvent>, String) -> Unit
    ) {
        withArchiveLock {
            StoreArchiveWorkflowNode.deleteArchivedEvent(
                archivedEventId = archivedEventId,
                loadCurrentArchivedMutableList = loadCurrentArchivedMutableList,
                updateArchivedEvents = updateArchivedEvents,
                deleteRoomShadowEvents = deleteRoomShadowEvents,
                removeCalendarMappingsForEvents = removeCalendarMappingsForEvents
            )
        }
    }

    suspend fun clearAllArchives(
        withArchiveLock: suspend (suspend () -> Unit) -> Unit,
        loadCurrentArchivedMutableList: suspend () -> MutableList<MyEvent>,
        removeCalendarMappingsForEvents: suspend (List<MyEvent>, String) -> Unit,
        updateArchivedEvents: suspend (List<MyEvent>) -> Unit,
        deleteRoomShadowEvents: suspend (List<String>) -> Unit
    ) {
        withArchiveLock {
            StoreArchiveWorkflowNode.clearAllArchives(
                loadCurrentArchivedMutableList = loadCurrentArchivedMutableList,
                removeCalendarMappingsForEvents = removeCalendarMappingsForEvents,
                updateArchivedEvents = updateArchivedEvents,
                deleteRoomShadowEvents = deleteRoomShadowEvents
            )
        }
    }

    suspend fun autoArchiveExpiredEvents(
        autoArchiveEnabled: Boolean,
        activeEvents: () -> List<MyEvent>,
        withArchiveLock: suspend (suspend () -> Unit) -> Unit,
        withEventLock: suspend (suspend () -> Unit) -> Unit,
        loadCurrentArchivedMutableList: suspend () -> MutableList<MyEvent>,
        updateArchivedEvents: suspend (List<MyEvent>) -> Unit,
        loadCurrentActiveMutableList: () -> MutableList<MyEvent>,
        cancelReminders: (MyEvent) -> Unit,
        updateEvents: suspend (List<MyEvent>) -> Unit,
        triggerAutoSync: suspend () -> Unit
    ): Int {
        return StoreArchiveWorkflowNode.autoArchiveExpiredEvents(
            autoArchiveEnabled = autoArchiveEnabled,
            activeEvents = activeEvents,
            withArchiveLock = withArchiveLock,
            withEventLock = withEventLock,
            loadCurrentArchivedMutableList = loadCurrentArchivedMutableList,
            updateArchivedEvents = updateArchivedEvents,
            loadCurrentActiveMutableList = loadCurrentActiveMutableList,
            cancelReminders = cancelReminders,
            updateEvents = updateEvents,
            triggerAutoSync = triggerAutoSync
        )
    }

    suspend fun updateArchivedEvents(
        newList: List<MyEvent>,
        onArchivedEventsUpdated: (List<MyEvent>) -> Unit,
        persistArchivedEvents: suspend (List<MyEvent>) -> Unit
    ) {
        onArchivedEventsUpdated(newList)
        persistArchivedEvents(newList)
    }
}
