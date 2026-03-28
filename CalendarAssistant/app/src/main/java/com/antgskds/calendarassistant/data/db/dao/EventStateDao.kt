package com.antgskds.calendarassistant.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.antgskds.calendarassistant.data.db.entity.EventStateEntity

@Dao
interface EventStateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(state: EventStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(states: List<EventStateEntity>)

    @Update
    suspend fun update(state: EventStateEntity)

    @Query("SELECT * FROM event_states WHERE stateId = :stateId")
    suspend fun getById(stateId: String): EventStateEntity?

    @Query("SELECT * FROM event_states WHERE ruleId = :ruleId ORDER BY stateId ASC")
    suspend fun getByRuleId(ruleId: String): List<EventStateEntity>

    @Query("SELECT * FROM event_states")
    suspend fun getAll(): List<EventStateEntity>

    @Query("DELETE FROM event_states WHERE ruleId = :ruleId")
    suspend fun deleteByRuleId(ruleId: String): Int
}
