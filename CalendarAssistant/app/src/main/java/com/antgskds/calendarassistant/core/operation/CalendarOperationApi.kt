package com.antgskds.calendarassistant.core.operation

import com.antgskds.calendarassistant.core.model.RecognitionDraft
import com.antgskds.calendarassistant.core.model.RecurringMode
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.EventType

interface CalendarOperationApi {
    fun createFromRecognition(draft: RecognitionDraft, syncToSystem: Boolean = true): Long
    fun createEvent(event: Event, syncToSystem: Boolean = true): Long
    fun completeEvent(eventId: Long, occurrenceTs: Long? = null, syncToSystem: Boolean = true): OperationResult<Long>
    fun checkInEvent(eventId: Long, occurrenceTs: Long? = null, syncToSystem: Boolean = true): OperationResult<Long>
    fun markPending(eventId: Long, occurrenceTs: Long? = null, syncToSystem: Boolean = true): OperationResult<Long>
    fun updateEvent(event: Event, syncToSystem: Boolean = true)
    fun deleteEvent(id: Long, deleteFromSystem: Boolean = true)
    fun editRecurringEvent(parentEventId: Long, editedOccurrence: Event, mode: RecurringMode, occurrenceTs: Long, syncToSystem: Boolean = true): Long?
    fun deleteRecurringEvent(parentEventId: Long, mode: RecurringMode, occurrenceTs: Long, deleteFromSystem: Boolean = true)
    fun deleteRecurringOccurrence(parentEventId: Long, occurrenceTs: Long, syncToSystem: Boolean = true): Event?
    fun deleteRecurringOccurrenceByExdate(parentEventId: Long, exdateUtc: String, syncToSystem: Boolean = true): Event?
    fun createOrUpdateEventType(eventType: EventType): Long

    // 归档操作
    fun archiveEvent(eventId: Long)
    fun archiveOccurrence(parentId: Long, occurrenceTs: Long)
    fun restoreEvent(eventId: Long)
    fun deleteArchivedEvent(eventId: Long, deleteFromSystem: Boolean = true)
    fun clearAllArchives(deleteFromSystem: Boolean = true)
    fun autoArchiveExpiredEvents(beforeTs: Long): Int

    // 日历同步
    fun manualSyncNow()
    fun setSyncEnabled(enabled: Boolean)
    fun setSyncedCalendarIds(ids: String)
    fun onScheduledSyncTick()
    fun onSystemCalendarChanged()
    fun refreshNotificationsForWindow(items: List<com.antgskds.calendarassistant.data.model.ScheduleDisplayItem>)
}
