package com.antgskds.calendarassistant.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.antgskds.calendarassistant.data.db.entity.CalendarSyncMapEntity

@Dao
interface CalendarSyncMapDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(map: CalendarSyncMapEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(maps: List<CalendarSyncMapEntity>)

    @Update
    suspend fun update(map: CalendarSyncMapEntity)

    @Query("SELECT * FROM calendar_sync_map WHERE localMasterId = :localMasterId")
    suspend fun getByLocalMasterId(localMasterId: String): CalendarSyncMapEntity?

    @Query("SELECT * FROM calendar_sync_map WHERE calendarId = :calendarId AND systemEventId = :eventId")
    suspend fun getByCalendarEvent(calendarId: Long, eventId: Long): CalendarSyncMapEntity?

    @Query("SELECT * FROM calendar_sync_map WHERE calendarId = :calendarId")
    suspend fun getByCalendarId(calendarId: Long): List<CalendarSyncMapEntity>

    @Query("DELETE FROM calendar_sync_map WHERE localMasterId = :localMasterId")
    suspend fun deleteByLocalMasterId(localMasterId: String): Int

    @Query("DELETE FROM calendar_sync_map WHERE calendarId = :calendarId AND systemEventId = :eventId")
    suspend fun deleteByCalendarEvent(calendarId: Long, eventId: Long): Int
}
