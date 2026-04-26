package com.antgskds.calendarassistant.core.center

import android.content.Context
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.EventType
import com.antgskds.calendarassistant.core.model.RecognitionDraft
import com.antgskds.calendarassistant.core.model.RecurringMode
import com.antgskds.calendarassistant.core.operation.CalendarOperationApi
import com.antgskds.calendarassistant.core.operation.OperationResult
import com.antgskds.calendarassistant.core.query.CalendarQueryApi
import com.antgskds.calendarassistant.store.StoreDispatcher

class CalendarCenter private constructor(context: Context) : CalendarOperationApi, CalendarQueryApi {
    private val dispatcher = StoreDispatcher.getInstance(context.applicationContext)

    // ── CalendarOperationApi ──

    override fun createFromRecognition(draft: RecognitionDraft, syncToSystem: Boolean): Long =
        dispatcher.createFromRecognition(draft, syncToSystem)

    override fun createEvent(event: Event, syncToSystem: Boolean): Long =
        dispatcher.createEvent(event, syncToSystem)

    override fun completeEvent(eventId: Long, occurrenceTs: Long?, syncToSystem: Boolean): OperationResult<Long> =
        dispatcher.completeEvent(eventId, occurrenceTs, syncToSystem)

    override fun checkInEvent(eventId: Long, occurrenceTs: Long?, syncToSystem: Boolean): OperationResult<Long> =
        dispatcher.checkInEvent(eventId, occurrenceTs, syncToSystem)

    override fun markPending(eventId: Long, occurrenceTs: Long?, syncToSystem: Boolean): OperationResult<Long> =
        dispatcher.markPending(eventId, occurrenceTs, syncToSystem)

    override fun updateEvent(event: Event, syncToSystem: Boolean) =
        dispatcher.updateEvent(event, syncToSystem)

    override fun deleteEvent(id: Long, deleteFromSystem: Boolean) =
        dispatcher.deleteEvent(id, deleteFromSystem)

    override fun editRecurringEvent(parentEventId: Long, editedOccurrence: Event, mode: RecurringMode, occurrenceTs: Long, syncToSystem: Boolean): Long? =
        dispatcher.editRecurringEvent(parentEventId, editedOccurrence, mode, occurrenceTs, syncToSystem)

    override fun deleteRecurringEvent(parentEventId: Long, mode: RecurringMode, occurrenceTs: Long, deleteFromSystem: Boolean) =
        dispatcher.deleteRecurringEvent(parentEventId, mode, occurrenceTs, deleteFromSystem)

    override fun deleteRecurringOccurrence(parentEventId: Long, occurrenceTs: Long, syncToSystem: Boolean): Event? =
        dispatcher.deleteRecurringOccurrence(parentEventId, occurrenceTs, syncToSystem)

    override fun deleteRecurringOccurrenceByExdate(parentEventId: Long, exdateUtc: String, syncToSystem: Boolean): Event? =
        dispatcher.deleteRecurringOccurrenceByExdate(parentEventId, exdateUtc, syncToSystem)

    override fun createOrUpdateEventType(eventType: EventType): Long =
        dispatcher.createOrUpdateEventType(eventType)

    override fun archiveEvent(eventId: Long) = dispatcher.archiveEvent(eventId)
    override fun archiveOccurrence(parentId: Long, occurrenceTs: Long) = dispatcher.archiveOccurrence(parentId, occurrenceTs)
    override fun restoreEvent(eventId: Long) = dispatcher.restoreEvent(eventId)
    override fun deleteArchivedEvent(eventId: Long, deleteFromSystem: Boolean) = dispatcher.deleteArchivedEvent(eventId, deleteFromSystem)
    override fun clearAllArchives(deleteFromSystem: Boolean) = dispatcher.clearAllArchives(deleteFromSystem)
    override fun autoArchiveExpiredEvents(beforeTs: Long): Int = dispatcher.autoArchiveExpiredEvents(beforeTs)

    override fun manualSyncNow() = dispatcher.manualSyncNow()
    override fun setSyncEnabled(enabled: Boolean) = dispatcher.setSyncEnabled(enabled)
    override fun setSyncedCalendarIds(ids: String) = dispatcher.setSyncedCalendarIds(ids)
    override fun onScheduledSyncTick() = dispatcher.onScheduledSyncTick()
    override fun onSystemCalendarChanged() = dispatcher.onSystemCalendarChanged()
    override fun refreshNotificationsForWindow(items: List<com.antgskds.calendarassistant.data.model.ScheduleDisplayItem>) = dispatcher.refreshNotificationsForWindow(items)

    // ── CalendarQueryApi ──

    override fun getEvents(): List<Event> = dispatcher.getEvents()
    override fun getEventsInRange(fromTS: Long, toTS: Long): List<Event> = dispatcher.getEventsInRange(fromTS, toTS)
    override fun getEvent(id: Long): Event? = dispatcher.getEvent(id)
    override fun getEventTypes(): List<EventType> = dispatcher.getEventTypes()
    override fun getScheduledReminderCount(eventId: Long): Int = dispatcher.getScheduledReminderCount(eventId)

    override fun getArchivedEvents(): List<Event> = dispatcher.getArchivedEvents()
    override fun getActiveEventCount(): Int = dispatcher.getActiveEventCount()
    override fun getTotalEventCount(): Int = dispatcher.getTotalEventCount()

    companion object {
        @Volatile
        private var instance: CalendarCenter? = null

        fun getInstance(context: Context): CalendarCenter {
            return instance ?: synchronized(this) {
                instance ?: CalendarCenter(context).also { instance = it }
            }
        }
    }
}
