package com.antgskds.calendarassistant.store.local

import android.content.Context
import com.antgskds.calendarassistant.calendar.data.EventsDatabase
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.EventType

class LocalEventStoreNode(context: Context) {
    private val db = EventsDatabase.getInstance(context.applicationContext)

    fun getEvents(): List<Event> = db.eventsDao().getAllEvents()

    fun getEventsInRange(fromTS: Long, toTS: Long): List<Event> = db.eventsDao().getEventsFromTo(fromTS, toTS)

    fun getEvent(id: Long): Event? = db.eventsDao().getEventOrTaskWithId(id)

    fun getEventByImportId(importId: String): Event? = db.eventsDao().getEventOrTaskWithImportId(importId)

    fun getChildEvents(parentId: Long): List<Event> = db.eventsDao().getChildEvents(parentId)

    fun getChildEventWithParentAndStart(parentId: Long, startTs: Long): Event? {
        return db.eventsDao().getChildEventWithParentAndStart(parentId, startTs)
    }

    fun getChildEventsFrom(parentId: Long, fromTs: Long): List<Event> = db.eventsDao().getChildEventsFrom(parentId, fromTs)

    fun upsertEvent(event: Event): Long = db.eventsDao().insertOrUpdate(event)

    fun updateEventImportIdAndSource(importId: String, source: String, id: Long) {
        db.eventsDao().updateEventImportIdAndSource(importId, source, id)
    }

    fun deleteEvent(id: Long) {
        db.eventsDao().deleteEvent(id)
    }

    fun deleteChildEvents(parentId: Long) {
        db.eventsDao().deleteChildEvents(parentId)
    }

    fun deleteChildEventsFrom(parentId: Long, fromTs: Long) {
        db.eventsDao().deleteChildEventsFrom(parentId, fromTs)
    }

    fun getEventTypes(): List<EventType> = db.eventTypesDao().getEventTypes()

    fun upsertEventType(eventType: EventType): Long = db.eventTypesDao().insertOrUpdate(eventType)

    // ── 归档操作 ──────────────────────────────────────────────────────

    fun getArchivedEvents(): List<Event> = db.eventsDao().getArchivedEvents()

    fun archiveEvent(id: Long, archivedAt: Long) {
        db.eventsDao().updateArchivedAt(id, archivedAt)
    }

    fun restoreEvent(id: Long) {
        db.eventsDao().updateArchivedAt(id, null)
    }

    fun getActiveEventCount(): Int = db.eventsDao().getActiveEventCount()

    fun getTotalEventCount(): Int = db.eventsDao().getTotalEventCount()

    // ── 事务支持 ──────────────────────────────────────────────────────

    /**
     * 在数据库事务内执行操作，保证原子性。
     * 物化 occurrence 等多步操作必须用此方法包裹。
     */
    fun <T> runInTransaction(body: () -> T): T {
        var result: T? = null
        db.runInTransaction {
            result = body()
        }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }
}
