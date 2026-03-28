package com.antgskds.calendarassistant.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.antgskds.calendarassistant.data.db.entity.EventInstanceEntity
import com.antgskds.calendarassistant.data.db.model.FullInstance

/**
 * 事件实例 DAO
 */
@Dao
interface EventInstanceDao {

    /**
     * 获取时间范围内的完整实例列表
     *
     * @param startMillis 开始时间戳 (毫秒)
     * @param endMillis 结束时间戳 (毫秒)
     * @return 完整实例列表（包含 master）
     */
    @Transaction
    @Query(
        """
        SELECT * FROM event_instances
        WHERE startTime >= :startMillis
        AND endTime <= :endMillis
        ORDER BY startTime ASC
        """
    )
    suspend fun getInstancesInRange(startMillis: Long, endMillis: Long): List<FullInstance>

    @Transaction
    @Query(
        """
        SELECT * FROM event_instances
        WHERE startTime >= :startMillis
        AND endTime <= :endMillis
        AND archivedAt IS NULL
        AND isCancelled = 0
        ORDER BY startTime ASC
        """
    )
    suspend fun getActiveInstancesInRange(startMillis: Long, endMillis: Long): List<FullInstance>

    /**
     * 获取指定主事件的所有实例
     */
    @Transaction
    @Query("SELECT * FROM event_instances WHERE masterId = :masterId ORDER BY startTime ASC")
    suspend fun getByMasterId(masterId: String): List<FullInstance>

    /**
     * 根据重复实例 Key 获取
     */
    @Transaction
    @Query("SELECT * FROM event_instances WHERE instanceId = :instanceId")
    suspend fun getById(instanceId: String): EventInstanceEntity?

    @Transaction
    @Query("SELECT * FROM event_instances WHERE syncFingerprint = :fingerprint LIMIT 1")
    suspend fun getBySyncFingerprint(fingerprint: String): FullInstance?

    /**
     * 插入单个实例
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(instance: EventInstanceEntity)

    /**
     * 批量插入实例
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(instances: List<EventInstanceEntity>)

    /**
     * 更新实例
     */
    @Update
    suspend fun update(instance: EventInstanceEntity)

    /**
     * 删除实例
     */
    @Query("DELETE FROM event_instances WHERE instanceId = :instanceId")
    suspend fun delete(instanceId: String): Int

    /**
     * 批量删除实例
     */
    @Query("DELETE FROM event_instances WHERE instanceId IN (:ids)")
    suspend fun deleteAll(ids: List<String>): Int

    /**
     * 删除主事件的所有实例
     */
    @Query("DELETE FROM event_instances WHERE masterId = :masterId")
    suspend fun deleteByMasterId(masterId: String): Int

    /**
     * 获取时间范围内的实例 ID 列表
     */
    @Query(
        """
        SELECT instanceId FROM event_instances
        WHERE startTime >= :startMillis
        AND endTime <= :endMillis
        ORDER BY startTime ASC
        """
    )
    suspend fun getInstanceIdsInRange(startMillis: Long, endMillis: Long): List<String>

    @Query("SELECT COUNT(*) FROM event_instances WHERE masterId = :masterId")
    suspend fun countByMasterId(masterId: String): Int

    @Query("DELETE FROM event_instances WHERE endTime < :beforeMillis")
    suspend fun deleteBefore(beforeMillis: Long): Int

    /**
     * 获取未完成实例列表（用于胶囊显示）
     */
    @Transaction
    @Query(
        """
        SELECT * FROM event_instances
        WHERE archivedAt IS NULL
        AND isCancelled = 0
        AND endTime >= :nowMillis
        ORDER BY startTime ASC
        LIMIT 10
        """
    )
    suspend fun getUpcomingInstances(nowMillis: Long): List<FullInstance>

    @Transaction
    @Query(
        """
        SELECT * FROM event_instances
        WHERE archivedAt IS NULL
        AND isCancelled = 0
        ORDER BY startTime ASC
        """
    )
    suspend fun getAllActive(): List<FullInstance>

    @Transaction
    @Query(
        """
        SELECT * FROM event_instances
        WHERE archivedAt IS NOT NULL
        AND isCancelled = 0
        ORDER BY archivedAt DESC
        """
    )
    suspend fun getAllArchived(): List<FullInstance>

    @Query("SELECT instanceId FROM event_instances WHERE archivedAt IS NULL AND isCancelled = 0")
    suspend fun getActiveInstanceIds(): List<String>

    @Query("SELECT instanceId FROM event_instances WHERE archivedAt IS NOT NULL AND isCancelled = 0")
    suspend fun getArchivedInstanceIds(): List<String>

    @Query("DELETE FROM event_instances WHERE instanceId IN (:ids) AND archivedAt IS NULL")
    suspend fun deleteActiveByIds(ids: List<String>): Int

    @Query("DELETE FROM event_instances WHERE instanceId IN (:ids) AND archivedAt IS NOT NULL")
    suspend fun deleteArchivedByIds(ids: List<String>): Int

    @Query("SELECT * FROM event_instances WHERE masterId = :masterId ORDER BY startTime ASC LIMIT 1")
    suspend fun getFirstInstanceByMasterId(masterId: String): EventInstanceEntity?

    @Query("SELECT instanceId FROM event_instances WHERE masterId = :masterId")
    suspend fun getInstanceIdsByMasterId(masterId: String): List<String>
}
