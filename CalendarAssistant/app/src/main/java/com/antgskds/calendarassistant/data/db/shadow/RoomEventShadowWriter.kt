package com.antgskds.calendarassistant.data.db.shadow

import android.util.Log
import androidx.compose.ui.graphics.toArgb
import androidx.room.withTransaction
import com.antgskds.calendarassistant.core.rule.RuleActionDefaults
import com.antgskds.calendarassistant.core.rule.RuleMatchingEngine
import com.antgskds.calendarassistant.data.db.AppDatabase
import com.antgskds.calendarassistant.data.db.entity.EventInstanceEntity
import com.antgskds.calendarassistant.data.db.entity.EventMasterEntity
import com.antgskds.calendarassistant.data.model.EventTags
import com.antgskds.calendarassistant.data.model.MyEvent
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Room 影子写入器
 *
 * 职责：将 MyEvent 列表同步写入 Room 数据库（影子模式）
 * - 删除已不存在的事件（CASCADE 清理 instance）
 * - 插入/更新所有事件（master + instance）
 */
class RoomEventShadowWriter(
    private val database: AppDatabase
) {
    enum class SyncMode {
        ACTIVE,
        ARCHIVED
    }

    private val json = Json { encodeDefaults = true }
    private val masterDao = database.eventMasterDao()
    private val instanceDao = database.eventInstanceDao()
    private val tag = "RoomEventShadowWriter"

    /**
     * 同步事件到 Room 数据库
     *
     * @param events 要同步的事件列表
     */
    suspend fun syncEvents(events: List<MyEvent>, mode: SyncMode) {
        try {
            // 1. 构建 master 和 instance 列表
            val nonRecurringEvents = events.filter { !it.isRecurring && !it.isRecurringParent }
            val masters = nonRecurringEvents.map { toMaster(it) }
            val instances = nonRecurringEvents.map { toInstance(it) }
            val eventIds = events.map { it.id }.toSet()

            // 2. 事务写入
            database.withTransaction {
                masterDao.insertAll(masters)
                instanceDao.insertAll(instances)

                val staleIds = when (mode) {
                    SyncMode.ACTIVE -> instanceDao.getActiveInstanceIds().filter { it !in eventIds }
                    SyncMode.ARCHIVED -> instanceDao.getArchivedInstanceIds().filter { it !in eventIds }
                }
                if (staleIds.isNotEmpty()) {
                    val deletedInstances = when (mode) {
                        SyncMode.ACTIVE -> instanceDao.deleteActiveByIds(staleIds)
                        SyncMode.ARCHIVED -> instanceDao.deleteArchivedByIds(staleIds)
                    }
                    val orphanMasters = staleIds.filter { instanceDao.countByMasterId(it) == 0 }
                    if (orphanMasters.isNotEmpty()) {
                        masterDao.deleteAll(orphanMasters)
                    }
                    Log.d(
                        tag,
                        "清理 ${deletedInstances} 条${if (mode == SyncMode.ACTIVE) "活跃" else "归档"}实例, " +
                            "清理 ${orphanMasters.size} 条空 master"
                    )
                }
            }

            Log.d(tag, "同步完成: ${events.size} 个事件")

        } catch (e: Exception) {
            Log.e(tag, "同步事件到 Room 失败", e)
        }
    }

    suspend fun deleteEvents(ids: List<String>) {
        if (ids.isEmpty()) return
        try {
            masterDao.deleteAll(ids)
        } catch (e: Exception) {
            Log.e(tag, "删除 Room 事件失败", e)
        }
    }

    /**
     * 将 MyEvent 转换为 EventMasterEntity
     */
    private fun toMaster(event: MyEvent): EventMasterEntity {
        return EventMasterEntity(
            masterId = event.id,
            ruleId = resolveRuleId(event),
            title = event.title,
            description = event.description,
            location = event.location,
            colorArgb = event.color.toArgb(),
            rrule = null,
            syncId = null,
            eventType = event.eventType,
            remindersJson = json.encodeToString(event.reminders),
            isImportant = event.isImportant,
            sourceImagePath = event.sourceImagePath,
            skipCalendarSync = event.skipCalendarSync,
            createdAt = event.lastModified,
            updatedAt = event.lastModified,
            source = if (event.archivedAt != null) "legacy_archive" else "legacy_json"
        )
    }

    /**
     * 将 MyEvent 转换为 EventInstanceEntity
     */
    private fun toInstance(event: MyEvent): EventInstanceEntity {
        val startMillis = toMillis(event, event.startTime)
        val endMillis = toMillis(event, event.endTime)
        val ruleId = resolveRuleId(event)
        val stateSuffix = RuleActionDefaults.resolveStateSuffix(ruleId, event.isCompleted, event.isCheckedIn)
        val currentStateId = RuleActionDefaults.stateId(ruleId, stateSuffix)

        return EventInstanceEntity(
            instanceId = event.id,
            masterId = event.id,
            startTime = startMillis,
            endTime = endMillis,
            currentStateId = currentStateId,
            completedAt = if (event.isCompleted) (event.completedAt ?: event.lastModified) else null,
            archivedAt = event.archivedAt,
            syncFingerprint = buildSyncFingerprint(event.id, startMillis, endMillis),
            isSynced = false,
            isCancelled = false
        )
    }

    /**
     * 解析规则 ID（与 LegacyEventMigrator 保持一致）
     */
    private fun resolveRuleId(event: MyEvent): String {
        val parsed = RuleMatchingEngine.resolvePayload(event)?.ruleId
        if (!parsed.isNullOrBlank()) return parsed
        return when (event.tag) {
            EventTags.PICKUP -> RuleMatchingEngine.RULE_PICKUP
            EventTags.TRAIN -> RuleMatchingEngine.RULE_TRAIN
            EventTags.TAXI -> RuleMatchingEngine.RULE_TAXI
            EventTags.GENERAL -> RuleMatchingEngine.RULE_GENERAL
            else -> if (event.tag.isNotBlank()) event.tag else RuleMatchingEngine.RULE_GENERAL
        }
    }

    /**
     * 将时间字符串转换为毫秒时间戳
     */
    private fun toMillis(event: MyEvent, timeStr: String): Long {
        return try {
            val date = if (timeStr == event.startTime) event.startDate else event.endDate
            val localDateTime = LocalDateTime.of(date, LocalTime.parse(timeStr))
            localDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (e: Exception) {
            Log.w(tag, "时间解析失败: ${event.id} - $timeStr", e)
            System.currentTimeMillis()
        }
    }

    /**
     * 构建同步指纹（SHA1）
     */
    private fun buildSyncFingerprint(masterId: String, startMillis: Long, endMillis: Long): String {
        val source = "$masterId|$startMillis|$endMillis"
        val digest = MessageDigest.getInstance("SHA-1").digest(source.toByteArray())
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }
}
