package com.antgskds.calendarassistant.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.antgskds.calendarassistant.data.db.entity.EventExcludedDateEntity

@Dao
interface EventExcludedDateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(excludedDate: EventExcludedDateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(excludedDates: List<EventExcludedDateEntity>)

    @Query("SELECT * FROM event_excluded_dates WHERE masterId = :masterId ORDER BY excludedStartTime ASC")
    suspend fun getByMasterId(masterId: String): List<EventExcludedDateEntity>

    @Query("SELECT * FROM event_excluded_dates WHERE masterId = :masterId AND excludedStartTime = :startTime LIMIT 1")
    suspend fun get(masterId: String, startTime: Long): EventExcludedDateEntity?

    @Query("SELECT excludedStartTime FROM event_excluded_dates WHERE masterId = :masterId")
    suspend fun getStartTimesByMasterId(masterId: String): List<Long>

    @Query("DELETE FROM event_excluded_dates WHERE masterId = :masterId AND excludedStartTime = :startTime")
    suspend fun delete(masterId: String, startTime: Long): Int

    @Query("DELETE FROM event_excluded_dates WHERE masterId = :masterId")
    suspend fun deleteByMasterId(masterId: String): Int

    @Query("DELETE FROM event_excluded_dates WHERE masterId IN (:masterIds)")
    suspend fun deleteByMasterIds(masterIds: List<String>): Int
}
