package com.antgskds.calendarassistant.calendar.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.antgskds.calendarassistant.calendar.models.EventAttachment

@Dao
interface EventAttachmentsDao {
    @Query("SELECT * FROM event_attachments WHERE event_id = :eventId ORDER BY created_at ASC, id ASC")
    fun getAttachmentsForEvent(eventId: Long): List<EventAttachment>

    @Query("SELECT * FROM event_attachments WHERE event_key = :eventKey AND event_key != '' ORDER BY created_at ASC, id ASC")
    fun getAttachmentsForEventKey(eventKey: String): List<EventAttachment>

    @Query("UPDATE event_attachments SET event_id = :eventId, event_key = :eventKey WHERE event_key = :fromEventKey AND event_key != ''")
    fun bindAttachmentsByEventKey(fromEventKey: String, eventId: Long, eventKey: String)

    @Query("UPDATE event_attachments SET event_id = :eventId, event_key = :eventKey WHERE id IN (:ids)")
    fun bindAttachments(ids: List<Long>, eventId: Long, eventKey: String)

    @Query("UPDATE event_attachments SET event_key = :eventKey WHERE event_id = :eventId")
    fun updateEventKeyForEvent(eventId: Long, eventKey: String)

    @Query("SELECT * FROM event_attachments WHERE event_id IN (:eventIds) ORDER BY created_at ASC, id ASC")
    fun getAttachmentsForEvents(eventIds: List<Long>): List<EventAttachment>

    @Query("SELECT * FROM event_attachments ORDER BY created_at ASC, id ASC")
    fun getAllAttachments(): List<EventAttachment>

    @Query("SELECT COUNT(*) FROM event_attachments")
    fun getAttachmentCount(): Int

    @Query("SELECT COUNT(*) FROM event_attachments WHERE local_path = :localPath")
    fun countByLocalPath(localPath: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdate(attachment: EventAttachment): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(attachments: List<EventAttachment>): List<Long>

    @Query("DELETE FROM event_attachments WHERE id = :id")
    fun deleteAttachment(id: Long)

    @Query("DELETE FROM event_attachments WHERE event_id = :eventId")
    fun deleteAttachmentsForEvent(eventId: Long)

    @Query("DELETE FROM event_attachments WHERE event_key = :eventKey AND event_key != ''")
    fun deleteAttachmentsForEventKey(eventKey: String)
}
