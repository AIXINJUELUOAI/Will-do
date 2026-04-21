package com.antgskds.calendarassistant.data.node.store

import android.util.Log
import com.antgskds.calendarassistant.data.model.EventTags
import com.antgskds.calendarassistant.data.model.MyEvent

internal object StoreEventStorageNode {
    fun shouldScheduleReminders(event: MyEvent): Boolean {
        return !event.isRecurringParent && event.tag != EventTags.NOTE && event.tag != EventTags.COURSE
    }

    fun normalizeEventForPersistence(event: MyEvent): MyEvent {
        return if (event.tag == EventTags.NOTE) {
            event.copy(
                skipCalendarSync = true,
                reminders = emptyList()
            )
        } else {
            event
        }
    }

    fun normalizeEventsById(events: List<MyEvent>): List<MyEvent> {
        return events.asReversed().distinctBy { it.id }.asReversed()
    }

    fun sanitizeRecurringEvents(events: List<MyEvent>): List<MyEvent> {
        return normalizeEventsById(events)
    }

    suspend fun updateEvents(
        newList: List<MyEvent>,
        onEventsUpdated: (List<MyEvent>) -> Unit,
        persistActiveEvents: suspend (List<MyEvent>) -> Unit
    ) {
        val normalizedList = normalizeEventsById(newList)
        if (normalizedList.size != newList.size) {
            Log.w("StoreNode", "检测到重复事件ID，已自动去重: ${newList.size} -> ${normalizedList.size}")
        }

        onEventsUpdated(normalizedList)
        persistActiveEvents(normalizedList)
    }

    suspend fun persistActiveEvents(
        events: List<MyEvent>,
        isRoomMainEnabled: () -> Boolean,
        saveEventsBackup: suspend (List<MyEvent>) -> Unit,
        saveEvents: suspend (List<MyEvent>) -> Unit,
        syncRoomShadowActive: suspend (List<MyEvent>) -> Unit
    ) {
        if (isRoomMainEnabled()) {
            saveEventsBackup(events)
        } else {
            saveEvents(events)
        }

        try {
            syncRoomShadowActive(events)
        } catch (e: Exception) {
            Log.e("StoreNode", "Room 影子写入失败", e)
        }
    }
}
