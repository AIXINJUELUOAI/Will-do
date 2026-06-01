package com.antgskds.calendarassistant.calendar.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.antgskds.calendarassistant.calendar.helpers.TYPE_EVENT
import com.antgskds.calendarassistant.calendar.models.Event

@Dao
interface EventsDao {
    @Query("SELECT * FROM events WHERE archived_at IS NULL ORDER BY start_ts ASC")
    fun getAllEventsOrTasks(): List<Event>

    @Query("SELECT * FROM events WHERE type = $TYPE_EVENT AND archived_at IS NULL ORDER BY start_ts ASC")
    fun getAllEvents(): List<Event>

    @Query("SELECT * FROM events WHERE id = :id")
    fun getEventOrTaskWithId(id: Long): Event?

    @Query("SELECT * FROM events WHERE LOWER(TRIM(tag)) IN ('note', '便签')")
    fun getRetiredNoteEvents(): List<Event>

    @Query("DELETE FROM events WHERE LOWER(TRIM(tag)) IN ('note', '便签')")
    fun deleteRetiredNoteEvents(): Int

    @Query("SELECT * FROM events WHERE import_id = :importId")
    fun getEventOrTaskWithImportId(importId: String): Event?

    @Query("SELECT * FROM events WHERE source = :source AND type = $TYPE_EVENT")
    fun getEventsFromCalDAVCalendar(source: String): List<Event>

    @Query("SELECT * FROM events WHERE parent_id = :parentId ORDER BY start_ts ASC")
    fun getChildEvents(parentId: Long): List<Event>

    @Query("SELECT * FROM events WHERE parent_id = :parentId AND start_ts = :startTs LIMIT 1")
    fun getChildEventWithParentAndStart(parentId: Long, startTs: Long): Event?

    @Query("SELECT * FROM events WHERE parent_id = :parentId AND start_ts >= :fromTs ORDER BY start_ts ASC")
    fun getChildEventsFrom(parentId: Long, fromTs: Long): List<Event>

    @Query("DELETE FROM events WHERE parent_id = :parentId")
    fun deleteChildEvents(parentId: Long)

    @Query("DELETE FROM events WHERE parent_id = :parentId AND start_ts >= :fromTs")
    fun deleteChildEventsFrom(parentId: Long, fromTs: Long)

    @Query("SELECT * FROM events WHERE start_ts <= :toTS AND end_ts >= :fromTS AND archived_at IS NULL ORDER BY start_ts ASC")
    fun getEventsFromTo(fromTS: Long, toTS: Long): List<Event>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdate(event: Event): Long

    @Query("UPDATE events SET import_id = :importId, source = :source WHERE id = :id")
    fun updateEventImportIdAndSource(importId: String, source: String, id: Long)

    @Query("DELETE FROM events WHERE id = :id")
    fun deleteEvent(id: Long)

    @Query("DELETE FROM events WHERE id IN (:ids)")
    fun deleteEvents(ids: List<Long>)

    // ── 归档相关 ──────────────────────────────────────────────────────

    @Query("SELECT * FROM events WHERE archived_at IS NOT NULL ORDER BY archived_at DESC")
    fun getArchivedEvents(): List<Event>

    @Query("UPDATE events SET archived_at = :archivedAt WHERE id = :id")
    fun updateArchivedAt(id: Long, archivedAt: Long?)

    @Query("SELECT COUNT(*) FROM events WHERE archived_at IS NULL")
    fun getActiveEventCount(): Int

    @Query("SELECT COUNT(*) FROM events")
    fun getTotalEventCount(): Int
}
