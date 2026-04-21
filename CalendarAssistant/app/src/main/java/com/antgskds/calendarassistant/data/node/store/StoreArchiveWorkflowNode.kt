package com.antgskds.calendarassistant.data.node.store

import android.util.Log
import com.antgskds.calendarassistant.core.util.DateCalculator
import com.antgskds.calendarassistant.data.model.EventTags
import com.antgskds.calendarassistant.data.model.MyEvent

internal object StoreArchiveWorkflowNode {
    suspend fun archiveEvent(
        eventId: String,
        findActiveEventById: (String) -> MyEvent?,
        withArchiveLock: suspend (suspend () -> Unit) -> Unit,
        loadCurrentArchivedMutableList: suspend () -> MutableList<MyEvent>,
        updateArchivedEvents: suspend (List<MyEvent>) -> Unit,
        deleteEventWithoutSyncAndKeepRoom: suspend (String) -> Unit
    ) {
        val event = findActiveEventById(eventId) ?: return

        if (event.tag == EventTags.COURSE) {
            Log.w("StoreNode", "Attempted to archive course event: ${event.id}")
            return
        }
        if (event.isRecurring) {
            Log.w("StoreNode", "Attempted to archive imported recurring event: ${event.id}")
            return
        }

        val archivedEvent = event.copy(archivedAt = System.currentTimeMillis())

        try {
            withArchiveLock {
                val currentArchived = loadCurrentArchivedMutableList()
                currentArchived.add(archivedEvent)
                updateArchivedEvents(currentArchived)
            }
        } catch (e: Exception) {
            Log.e("StoreNode", "归档失败: 写入归档文件错误", e)
            return
        }

        deleteEventWithoutSyncAndKeepRoom(eventId)
        Log.d("StoreNode", "Event archived: ${event.title}")
    }

    suspend fun restoreEvent(
        archivedEventId: String,
        withArchiveLock: suspend (suspend () -> Unit) -> Unit,
        findArchivedEventById: (String) -> MyEvent?,
        addEventWithSync: suspend (MyEvent) -> Unit,
        loadCurrentArchivedMutableList: suspend () -> MutableList<MyEvent>,
        updateArchivedEvents: suspend (List<MyEvent>) -> Unit
    ) {
        var archivedEvent: MyEvent? = null
        withArchiveLock {
            archivedEvent = findArchivedEventById(archivedEventId)
        }

        val event = archivedEvent ?: return
        val activeEvent = event.copy(archivedAt = null)
        addEventWithSync(activeEvent)

        withArchiveLock {
            val currentArchived = loadCurrentArchivedMutableList()
            currentArchived.remove(event)
            updateArchivedEvents(currentArchived)
        }

        Log.d("StoreNode", "Event restored: ${activeEvent.title}")
    }

    suspend fun deleteArchivedEvent(
        archivedEventId: String,
        loadCurrentArchivedMutableList: suspend () -> MutableList<MyEvent>,
        updateArchivedEvents: suspend (List<MyEvent>) -> Unit,
        deleteRoomShadowEvents: suspend (List<String>) -> Unit,
        removeCalendarMappingsForEvents: suspend (List<MyEvent>, String) -> Unit
    ) {
        val currentArchived = loadCurrentArchivedMutableList()
        val event = currentArchived.find { it.id == archivedEventId }
        if (event != null) {
            currentArchived.remove(event)
            updateArchivedEvents(currentArchived)
            deleteRoomShadowEvents(listOf(event.id))
            removeCalendarMappingsForEvents(listOf(event), "deleteArchivedEvent")
        }
    }

    suspend fun clearAllArchives(
        loadCurrentArchivedMutableList: suspend () -> MutableList<MyEvent>,
        removeCalendarMappingsForEvents: suspend (List<MyEvent>, String) -> Unit,
        updateArchivedEvents: suspend (List<MyEvent>) -> Unit,
        deleteRoomShadowEvents: suspend (List<String>) -> Unit
    ) {
        val currentArchived = loadCurrentArchivedMutableList()
        if (currentArchived.isEmpty()) return

        removeCalendarMappingsForEvents(currentArchived, "clearAllArchives")
        updateArchivedEvents(emptyList())
        deleteRoomShadowEvents(currentArchived.map { it.id })
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
        if (!autoArchiveEnabled) return 0

        val eventsSnapshot = activeEvents()
        val toArchiveEvents = eventsSnapshot.filter { event ->
            event.tag != EventTags.COURSE &&
                event.tag != EventTags.NOTE &&
                !event.isRecurring &&
                DateCalculator.isEventExpired(event)
        }
        if (toArchiveEvents.isEmpty()) return 0

        Log.d("StoreNode", "Auto-archiving ${toArchiveEvents.size} events...")

        withArchiveLock {
            val currentArchived = loadCurrentArchivedMutableList()
            val existingIds = currentArchived.map { it.id }.toSet()
            val newItems = toArchiveEvents.filter { it.id !in existingIds }
            val newArchivedItems = newItems.map { it.copy(archivedAt = System.currentTimeMillis()) }
            currentArchived.addAll(newArchivedItems)
            updateArchivedEvents(currentArchived)
        }

        withEventLock {
            val currentEvents = loadCurrentActiveMutableList()
            toArchiveEvents.forEach(cancelReminders)
            currentEvents.removeAll { event -> toArchiveEvents.any { it.id == event.id } }
            updateEvents(currentEvents)
            triggerAutoSync()
        }

        return toArchiveEvents.size
    }
}
