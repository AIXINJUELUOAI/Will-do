package com.antgskds.calendarassistant.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.antgskds.calendarassistant.data.db.entity.EventTransitionEntity

@Dao
interface EventTransitionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transition: EventTransitionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transitions: List<EventTransitionEntity>)

    @Update
    suspend fun update(transition: EventTransitionEntity)

    @Query("SELECT * FROM event_transitions WHERE transitionId = :transitionId")
    suspend fun getById(transitionId: String): EventTransitionEntity?

    @Query("SELECT * FROM event_transitions WHERE ruleId = :ruleId ORDER BY fromStateId ASC")
    suspend fun getByRuleId(ruleId: String): List<EventTransitionEntity>

    @Query("SELECT * FROM event_transitions")
    suspend fun getAll(): List<EventTransitionEntity>

    @Query("SELECT * FROM event_transitions WHERE ruleId = :ruleId AND fromStateId = :fromStateId")
    suspend fun getByFromState(ruleId: String, fromStateId: String): List<EventTransitionEntity>

    @Query("DELETE FROM event_transitions WHERE ruleId = :ruleId")
    suspend fun deleteByRuleId(ruleId: String): Int
}
