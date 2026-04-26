package com.antgskds.calendarassistant.store

import android.content.Context
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.EventType
import com.antgskds.calendarassistant.core.model.RecognitionDraft
import com.antgskds.calendarassistant.core.model.RecurringMode
import com.antgskds.calendarassistant.core.operation.OperationResult
import com.antgskds.calendarassistant.store.StoreRootNode

class StoreDispatcher private constructor(context: Context) {
    private val rootNode = StoreRootNode(context.applicationContext)

    // ── 创建 ──
    fun createFromRecognition(draft: RecognitionDraft, syncToSystem: Boolean = true): Long =
        rootNode.createFromRecognition(draft, syncToSystem)

    fun createEvent(event: Event, syncToSystem: Boolean = true): Long =
        rootNode.createEvent(event, syncToSystem)

    // ── 状态变更 ──
    fun completeEvent(eventId: Long, occurrenceTs: Long? = null, syncToSystem: Boolean = true): OperationResult<Long> =
        rootNode.completeEvent(eventId, occurrenceTs, syncToSystem)

    fun checkInEvent(eventId: Long, occurrenceTs: Long? = null, syncToSystem: Boolean = true): OperationResult<Long> =
        rootNode.checkInEvent(eventId, occurrenceTs, syncToSystem)

    fun markPending(eventId: Long, occurrenceTs: Long? = null, syncToSystem: Boolean = true): OperationResult<Long> =
        rootNode.markPending(eventId, occurrenceTs, syncToSystem)

    // ── 更新 & 删除 ──
    fun updateEvent(event: Event, syncToSystem: Boolean = true) =
        rootNode.updateEvent(event, syncToSystem)

    fun deleteEvent(id: Long, deleteFromSystem: Boolean = true) =
        rootNode.deleteEvent(id, deleteFromSystem)

    // ── 重复事件 ──
    fun editRecurringEvent(parentEventId: Long, editedOccurrence: Event, mode: RecurringMode, occurrenceTs: Long, syncToSystem: Boolean = true): Long? =
        rootNode.editRecurringEvent(parentEventId, editedOccurrence, mode, occurrenceTs, syncToSystem)

    fun deleteRecurringEvent(parentEventId: Long, mode: RecurringMode, occurrenceTs: Long, deleteFromSystem: Boolean = true) =
        rootNode.deleteRecurringEvent(parentEventId, mode, occurrenceTs, deleteFromSystem)

    fun deleteRecurringOccurrence(parentEventId: Long, occurrenceTs: Long, syncToSystem: Boolean = true): Event? =
        rootNode.deleteRecurringOccurrence(parentEventId, occurrenceTs, syncToSystem)

    fun deleteRecurringOccurrenceByExdate(parentEventId: Long, exdateUtc: String, syncToSystem: Boolean = true): Event? =
        rootNode.deleteRecurringOccurrenceByExdate(parentEventId, exdateUtc, syncToSystem)

    // ── EventType ──
    fun createOrUpdateEventType(eventType: EventType): Long =
        rootNode.createOrUpdateEventType(eventType)

    // ── 查询 ──
    fun getEvents(): List<Event> = rootNode.getEvents()
    fun getEventsInRange(fromTS: Long, toTS: Long): List<Event> = rootNode.getEventsInRange(fromTS, toTS)
    fun getEvent(id: Long): Event? = rootNode.getEvent(id)
    fun getEventTypes(): List<EventType> = rootNode.getEventTypes()
    fun getScheduledReminderCount(eventId: Long): Int = rootNode.getScheduledReminderCount(eventId)

    // ── 归档 ──
    fun archiveEvent(eventId: Long) = rootNode.archiveEvent(eventId)
    fun archiveOccurrence(parentId: Long, occurrenceTs: Long) = rootNode.archiveOccurrence(parentId, occurrenceTs)
    fun restoreEvent(eventId: Long) = rootNode.restoreEvent(eventId)
    fun deleteArchivedEvent(eventId: Long, deleteFromSystem: Boolean = true) = rootNode.deleteArchivedEvent(eventId, deleteFromSystem)
    fun clearAllArchives(deleteFromSystem: Boolean = true) = rootNode.clearAllArchives(deleteFromSystem)
    fun autoArchiveExpiredEvents(beforeTs: Long): Int = rootNode.autoArchiveExpiredEvents(beforeTs)
    fun getArchivedEvents(): List<Event> = rootNode.getArchivedEvents()
    fun getActiveEventCount(): Int = rootNode.getActiveEventCount()
    fun getTotalEventCount(): Int = rootNode.getTotalEventCount()

    // ── 日历同步 ──
    fun manualSyncNow() = rootNode.manualSyncNow()
    fun setSyncEnabled(enabled: Boolean) = rootNode.setSyncEnabled(enabled)
    fun setSyncedCalendarIds(ids: String) = rootNode.setSyncedCalendarIds(ids)
    fun onScheduledSyncTick() = rootNode.onScheduledSyncTick()
    fun onSystemCalendarChanged() = rootNode.onSystemCalendarChanged()

    fun refreshNotificationsForWindow(items: List<com.antgskds.calendarassistant.data.model.ScheduleDisplayItem>) = rootNode.refreshNotificationsForWindow(items)

    companion object {
        @Volatile
        private var instance: StoreDispatcher? = null

        fun getInstance(context: Context): StoreDispatcher {
            return instance ?: synchronized(this) {
                instance ?: StoreDispatcher(context).also { instance = it }
            }
        }
    }
}
