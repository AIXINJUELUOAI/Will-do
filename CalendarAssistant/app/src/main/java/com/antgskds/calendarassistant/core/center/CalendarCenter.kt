package com.antgskds.calendarassistant.core.center

import android.content.Context
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.EventType
import com.antgskds.calendarassistant.core.event.DomainEventBus
import com.antgskds.calendarassistant.core.event.DomainEventType
import com.antgskds.calendarassistant.core.event.events.ScheduleChangeOrigin
import com.antgskds.calendarassistant.core.event.events.ScheduleChangeType
import com.antgskds.calendarassistant.core.event.events.ScheduleChangedEvent
import com.antgskds.calendarassistant.core.model.RecognitionDraft
import com.antgskds.calendarassistant.core.model.RecurringMode
import com.antgskds.calendarassistant.core.operation.CalendarOperationApi
import com.antgskds.calendarassistant.core.operation.OperationResult
import com.antgskds.calendarassistant.core.query.CalendarQueryApi
import com.antgskds.calendarassistant.store.StoreDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

class CalendarCenter private constructor(context: Context) : CalendarOperationApi, CalendarQueryApi {
    private val dispatcher = StoreDispatcher.getInstance(context.applicationContext)
    private val eventScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val entityVersion = AtomicLong(System.currentTimeMillis())

    @Volatile
    private var domainEventBus: DomainEventBus? = null

    fun attachDomainEventBus(bus: DomainEventBus) {
        domainEventBus = bus
    }

    // ── CalendarOperationApi ──

    override fun createFromRecognition(draft: RecognitionDraft, syncToSystem: Boolean): Long =
        dispatcher.createFromRecognition(draft, syncToSystem).also { id ->
            emitScheduleChanged(ScheduleChangeType.CREATE, listOf(id), ScheduleChangeOrigin.INGEST)
        }

    override fun createEvent(event: Event, syncToSystem: Boolean): Long =
        dispatcher.createEvent(event, syncToSystem).also { id ->
            emitScheduleChanged(ScheduleChangeType.CREATE, listOf(id), ScheduleChangeOrigin.MANUAL)
        }

    override fun completeEvent(eventId: Long, occurrenceTs: Long?, syncToSystem: Boolean): OperationResult<Long> =
        dispatcher.completeEvent(eventId, occurrenceTs, syncToSystem).also { result ->
            if (result is OperationResult.Success) {
                emitScheduleChanged(ScheduleChangeType.UPDATE, listOf(result.data), ScheduleChangeOrigin.MANUAL)
            }
        }

    override fun checkInEvent(eventId: Long, occurrenceTs: Long?, syncToSystem: Boolean): OperationResult<Long> =
        dispatcher.checkInEvent(eventId, occurrenceTs, syncToSystem).also { result ->
            if (result is OperationResult.Success) {
                emitScheduleChanged(ScheduleChangeType.UPDATE, listOf(result.data), ScheduleChangeOrigin.MANUAL)
            }
        }

    override fun markPending(eventId: Long, occurrenceTs: Long?, syncToSystem: Boolean): OperationResult<Long> =
        dispatcher.markPending(eventId, occurrenceTs, syncToSystem).also { result ->
            if (result is OperationResult.Success) {
                emitScheduleChanged(ScheduleChangeType.UPDATE, listOf(result.data), ScheduleChangeOrigin.MANUAL)
            }
        }

    override fun updateEvent(event: Event, syncToSystem: Boolean) =
        dispatcher.updateEvent(event, syncToSystem)
            .also { event.id?.let { id -> emitScheduleChanged(ScheduleChangeType.UPDATE, listOf(id), ScheduleChangeOrigin.MANUAL) } }

    override fun deleteEvent(id: Long, deleteFromSystem: Boolean) =
        dispatcher.deleteEvent(id, deleteFromSystem)
            .also { emitScheduleChanged(ScheduleChangeType.DELETE, listOf(id), ScheduleChangeOrigin.MANUAL) }

    override fun editRecurringEvent(parentEventId: Long, editedOccurrence: Event, mode: RecurringMode, occurrenceTs: Long, syncToSystem: Boolean): Long? =
        dispatcher.editRecurringEvent(parentEventId, editedOccurrence, mode, occurrenceTs, syncToSystem)
            .also { id -> emitScheduleChanged(ScheduleChangeType.UPDATE, listOfNotNull(parentEventId, id), ScheduleChangeOrigin.MANUAL) }

    override fun deleteRecurringEvent(parentEventId: Long, mode: RecurringMode, occurrenceTs: Long, deleteFromSystem: Boolean) =
        dispatcher.deleteRecurringEvent(parentEventId, mode, occurrenceTs, deleteFromSystem)
            .also { emitScheduleChanged(ScheduleChangeType.BULK, emptyList(), ScheduleChangeOrigin.MANUAL) }

    override fun deleteRecurringOccurrence(parentEventId: Long, occurrenceTs: Long, syncToSystem: Boolean): Event? =
        dispatcher.deleteRecurringOccurrence(parentEventId, occurrenceTs, syncToSystem)
            .also { emitScheduleChanged(ScheduleChangeType.UPDATE, listOf(parentEventId), ScheduleChangeOrigin.MANUAL) }

    override fun deleteRecurringOccurrenceByExdate(parentEventId: Long, exdateUtc: String, syncToSystem: Boolean): Event? =
        dispatcher.deleteRecurringOccurrenceByExdate(parentEventId, exdateUtc, syncToSystem)
            .also { emitScheduleChanged(ScheduleChangeType.UPDATE, listOf(parentEventId), ScheduleChangeOrigin.MANUAL) }

    override fun createOrUpdateEventType(eventType: EventType): Long =
        dispatcher.createOrUpdateEventType(eventType)

    override fun archiveEvent(eventId: Long) = dispatcher.archiveEvent(eventId)
        .also { emitScheduleChanged(ScheduleChangeType.ARCHIVE, listOf(eventId), ScheduleChangeOrigin.MANUAL) }
    override fun archiveOccurrence(parentId: Long, occurrenceTs: Long) = dispatcher.archiveOccurrence(parentId, occurrenceTs)
        .also { emitScheduleChanged(ScheduleChangeType.BULK, emptyList(), ScheduleChangeOrigin.MANUAL) }
    override fun restoreEvent(eventId: Long) = dispatcher.restoreEvent(eventId)
        .also { emitScheduleChanged(ScheduleChangeType.RESTORE, listOf(eventId), ScheduleChangeOrigin.MANUAL) }
    override fun deleteArchivedEvent(eventId: Long, deleteFromSystem: Boolean) = dispatcher.deleteArchivedEvent(eventId, deleteFromSystem)
        .also { emitScheduleChanged(ScheduleChangeType.DELETE, listOf(eventId), ScheduleChangeOrigin.MANUAL) }
    override fun clearAllArchives(deleteFromSystem: Boolean) = dispatcher.clearAllArchives(deleteFromSystem)
        .also { emitScheduleChanged(ScheduleChangeType.BULK, emptyList(), ScheduleChangeOrigin.MANUAL) }
    override fun autoArchiveExpiredEvents(beforeTs: Long): Int = dispatcher.autoArchiveExpiredEvents(beforeTs)
        .also { count -> if (count > 0) emitScheduleChanged(ScheduleChangeType.BULK, emptyList(), ScheduleChangeOrigin.SYSTEM) }

    override fun manualSyncNow() = dispatcher.manualSyncNow()
        .also { emitScheduleChanged(ScheduleChangeType.BULK, emptyList(), ScheduleChangeOrigin.SYNC) }
    override fun setSyncEnabled(enabled: Boolean) = dispatcher.setSyncEnabled(enabled)
    override fun setSyncedCalendarIds(ids: String) = dispatcher.setSyncedCalendarIds(ids)
    override fun onScheduledSyncTick() = dispatcher.onScheduledSyncTick()
        .also { emitScheduleChanged(ScheduleChangeType.BULK, emptyList(), ScheduleChangeOrigin.SYNC) }
    override fun onSystemCalendarChanged() = dispatcher.onSystemCalendarChanged()
        .also { emitScheduleChanged(ScheduleChangeType.BULK, emptyList(), ScheduleChangeOrigin.SYNC) }
    override fun refreshNotificationsForWindow(items: List<com.antgskds.calendarassistant.data.model.ScheduleDisplayItem>) = dispatcher.refreshNotificationsForWindow(items)
    fun reconcileNotificationsFromStore(windowDays: Long = 7L) = dispatcher.reconcileNotificationsFromStore(windowDays)

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

    private fun emitScheduleChanged(
        changeType: ScheduleChangeType,
        eventIds: List<Long>,
        origin: ScheduleChangeOrigin
    ) {
        val bus = domainEventBus ?: return
        val uniqueIds = eventIds.filter { it > 0L }.distinct().map { it.toString() }
        val updatedAt = System.currentTimeMillis()
        eventScope.launch {
            bus.emit(
                eventType = DomainEventType.SCHEDULE_CHANGED,
                traceId = "schedule_${updatedAt}",
                source = "calendar_center_write_port",
                entityKey = if (uniqueIds.isEmpty()) "schedule:bulk" else "schedule:${uniqueIds.joinToString(",")}",
                payload = ScheduleChangedEvent(
                    changeType = changeType,
                    eventIds = uniqueIds,
                    origin = origin,
                    entityVersion = entityVersion.incrementAndGet(),
                    updatedAt = updatedAt
                )
            )
        }
    }
}
