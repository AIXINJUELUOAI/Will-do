package com.antgskds.calendarassistant.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.antgskds.calendarassistant.data.db.entity.EventRuleEntity

@Dao
interface EventRuleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: EventRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<EventRuleEntity>)

    @Update
    suspend fun update(rule: EventRuleEntity)

    @Query("SELECT * FROM event_rules WHERE ruleId = :ruleId")
    suspend fun getById(ruleId: String): EventRuleEntity?

    @Query("SELECT * FROM event_rules ORDER BY name ASC")
    suspend fun getAll(): List<EventRuleEntity>

    @Query("SELECT * FROM event_rules WHERE isEnabled = 1 ORDER BY name ASC")
    suspend fun getEnabled(): List<EventRuleEntity>

    @Query("SELECT * FROM event_rules WHERE isEnabled = 1 AND appliesToSchedule = 1 ORDER BY name ASC")
    suspend fun getEnabledForSchedule(): List<EventRuleEntity>

    @Query("UPDATE event_rules SET isEnabled = :enabled WHERE ruleId = :ruleId")
    suspend fun updateEnabled(ruleId: String, enabled: Boolean): Int

    @Query("DELETE FROM event_rules WHERE ruleId = :ruleId")
    suspend fun delete(ruleId: String): Int
}
