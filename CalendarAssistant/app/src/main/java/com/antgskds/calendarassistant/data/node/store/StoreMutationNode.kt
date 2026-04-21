package com.antgskds.calendarassistant.data.node.store

import com.antgskds.calendarassistant.data.model.Course
import com.antgskds.calendarassistant.data.model.MyEvent

internal object StoreMutationNode {
    suspend fun addEvent(
        event: MyEvent,
        triggerSync: Boolean,
        withEventLock: suspend (suspend () -> Unit) -> Unit,
        loadCurrentActiveMutableList: () -> MutableList<MyEvent>,
        normalizeEventForPersistence: (MyEvent) -> MyEvent,
        updateEvents: suspend (List<MyEvent>) -> Unit,
        scheduleRemindersIfNeeded: (MyEvent) -> Unit,
        triggerAutoSync: suspend () -> Unit
    ) {
        withEventLock {
            val currentList = loadCurrentActiveMutableList()
            val normalizedEvent = normalizeEventForPersistence(event)
            currentList.add(normalizedEvent)
            updateEvents(currentList)
            scheduleRemindersIfNeeded(normalizedEvent)
            if (triggerSync) {
                triggerAutoSync()
            }
        }
    }

    suspend fun updateEvent(
        event: MyEvent,
        triggerSync: Boolean,
        withEventLock: suspend (suspend () -> Unit) -> Unit,
        loadCurrentActiveMutableList: () -> MutableList<MyEvent>,
        normalizeEventForPersistence: (MyEvent) -> MyEvent,
        cancelReminders: (MyEvent) -> Unit,
        updateEvents: suspend (List<MyEvent>) -> Unit,
        onEventUpdated: (MyEvent) -> Unit,
        scheduleRemindersIfNeeded: (MyEvent) -> Unit,
        triggerAutoSync: suspend () -> Unit
    ) {
        withEventLock {
            val currentList = loadCurrentActiveMutableList()
            val normalizedEvent = normalizeEventForPersistence(event)
            val index = currentList.indexOfFirst { it.id == normalizedEvent.id }
            if (index == -1) return@withEventLock

            val oldEvent = currentList[index]
            cancelReminders(oldEvent)
            currentList[index] = normalizedEvent
            updateEvents(currentList)
            onEventUpdated(normalizedEvent)
            scheduleRemindersIfNeeded(normalizedEvent)
            if (triggerSync) {
                triggerAutoSync()
            }
        }
    }

    suspend fun deleteEvent(
        eventId: String,
        triggerSync: Boolean,
        removeFromRoom: Boolean,
        withEventLock: suspend (suspend () -> Unit) -> Unit,
        loadCurrentActiveMutableList: () -> MutableList<MyEvent>,
        onBeforeDelete: suspend (MyEvent, Boolean) -> Unit,
        cancelReminders: (MyEvent) -> Unit,
        updateEvents: suspend (List<MyEvent>) -> Unit,
        deleteRoomShadowEvents: suspend (List<String>) -> Unit,
        requestCapsuleRefresh: () -> Unit,
        triggerAutoSync: suspend () -> Unit
    ) {
        withEventLock {
            val currentList = loadCurrentActiveMutableList()
            val eventToDelete = currentList.find { it.id == eventId } ?: return@withEventLock

            onBeforeDelete(eventToDelete, removeFromRoom)
            cancelReminders(eventToDelete)
            currentList.remove(eventToDelete)
            updateEvents(currentList)
            if (removeFromRoom) {
                deleteRoomShadowEvents(listOf(eventId))
            }
            requestCapsuleRefresh()
            if (triggerSync) {
                triggerAutoSync()
            }
        }
    }

    suspend fun saveCourses(
        newCourses: List<Course>,
        triggerSync: Boolean,
        withCourseLock: suspend (suspend () -> Unit) -> Unit,
        updateCourses: suspend (List<Course>) -> Unit,
        triggerAutoSync: suspend () -> Unit
    ) {
        withCourseLock {
            updateCourses(newCourses)
            if (triggerSync) {
                triggerAutoSync()
            }
        }
    }
}
