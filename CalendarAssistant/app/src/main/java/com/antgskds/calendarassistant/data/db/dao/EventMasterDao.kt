package com.antgskds.calendarassistant.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.antgskds.calendarassistant.data.db.entity.EventMasterEntity

@Dao
interface EventMasterDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(master: EventMasterEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(masters: List<EventMasterEntity>)

    @Update
    suspend fun update(master: EventMasterEntity)

    @Query("SELECT * FROM event_masters WHERE masterId = :masterId")
    suspend fun getById(masterId: String): EventMasterEntity?

    @Query("SELECT * FROM event_masters ORDER BY createdAt DESC")
    suspend fun getAll(): List<EventMasterEntity>

    @Query("SELECT * FROM event_masters WHERE ruleId = :ruleId ORDER BY createdAt DESC")
    suspend fun getByRuleId(ruleId: String): List<EventMasterEntity>

    @Query("SELECT * FROM event_masters WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: Long): EventMasterEntity?

    @Query("DELETE FROM event_masters WHERE masterId = :masterId")
    suspend fun delete(masterId: String): Int

    @Query("DELETE FROM event_masters WHERE masterId IN (:ids)")
    suspend fun deleteAll(ids: List<String>): Int

    @Query("SELECT COUNT(*) FROM event_masters")
    suspend fun count(): Int

    @Query("SELECT masterId FROM event_masters")
    suspend fun getAllMasterIds(): List<String>

    @Query("SELECT * FROM event_masters WHERE rrule IS NOT NULL AND rrule != '' AND eventType = :eventType AND skipCalendarSync = 0")
    suspend fun getRecurringMasters(eventType: String): List<EventMasterEntity>

    @Query("SELECT * FROM event_masters WHERE source = :source")
    suspend fun getBySource(source: String): List<EventMasterEntity>
}
