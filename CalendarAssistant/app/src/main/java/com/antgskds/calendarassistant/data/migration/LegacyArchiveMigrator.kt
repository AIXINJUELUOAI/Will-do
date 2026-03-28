package com.antgskds.calendarassistant.data.migration

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
 * 归档事件迁移器
 * 负责将旧的归档事件（MyEvent.archivedAt != null）迁移到 Room 数据库
 */
class LegacyArchiveMigrator(
    private val database: AppDatabase,
    private val prefs: MigrationPrefs
) {
    private val json = Json { encodeDefaults = true }
    companion object {
        private const val TAG = "LegacyArchiveMigrator"
    }

    /**
     * 迁移归档事件（如果需要）
     * @param archivedEvents 归档事件列表
     */
    suspend fun migrateIfNeeded(archivedEvents: List<MyEvent>) {
        // 空列表直接返回
        if (archivedEvents.isEmpty()) {
            prefs.markArchivesMigrated(System.currentTimeMillis())
            Log.d(TAG, "No archived events to migrate")
            return
        }

        // 已迁移过则跳过
        if (prefs.isArchivesMigrated()) {
            Log.d(TAG, "Archives already migrated, skipping")
            return
        }

        val masterDao = database.eventMasterDao()
        val instanceDao = database.eventInstanceDao()

        // 获取已存在的 masterId
        val existingIds = try {
            masterDao.getAllMasterIds().toSet()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read existing master IDs", e)
            return
        }

        // 过滤出需要插入的事件（不重复插入）
        val toInsert = archivedEvents.filter { it.id !in existingIds }
        if (toInsert.isEmpty()) {
            Log.d(TAG, "All archived events already exist, marking as migrated")
            prefs.markArchivesMigrated(System.currentTimeMillis())
            return
        }

        val masters = ArrayList<EventMasterEntity>(toInsert.size)
        val instances = ArrayList<EventInstanceEntity>(toInsert.size)

        toInsert.forEach { event ->
            val ruleId = resolveRuleId(event)
            val startMillis = toMillis(event, event.startTime)
            val endMillis = toMillis(event, event.endTime)
            val completedAt = if (event.isCompleted) (event.completedAt ?: event.lastModified) else null
            // archivedAt 若为空，使用当前时间
            val archivedAt = event.archivedAt ?: System.currentTimeMillis()
            val stateSuffix = RuleActionDefaults.resolveStateSuffix(ruleId, event.isCompleted, event.isCheckedIn)
            val currentStateId = RuleActionDefaults.stateId(ruleId, stateSuffix)

            masters.add(
                EventMasterEntity(
                    masterId = event.id,
                    ruleId = ruleId,
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
                    source = "legacy_archive"
                )
            )

            instances.add(
                EventInstanceEntity(
                    instanceId = event.id,
                    masterId = event.id,
                    startTime = startMillis,
                    endTime = endMillis,
                    currentStateId = currentStateId,
                    completedAt = completedAt,
                    archivedAt = archivedAt,
                    syncFingerprint = buildSyncFingerprint(event.id, startMillis, endMillis),
                    isSynced = false,
                    isCancelled = false
                )
            )
        }

        try {
            database.withTransaction {
                masterDao.insertAll(masters)
                instanceDao.insertAll(instances)
            }
            prefs.markArchivesMigrated(System.currentTimeMillis())
            Log.i(TAG, "Migrated ${masters.size} archived events into Room")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to migrate archived events", e)
        }
    }

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

    private fun toMillis(event: MyEvent, timeStr: String): Long {
        return try {
            val date = if (timeStr == event.startTime) event.startDate else event.endDate
            val localDateTime = LocalDateTime.of(date, LocalTime.parse(timeStr))
            localDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }

    private fun buildSyncFingerprint(masterId: String, startMillis: Long, endMillis: Long): String {
        val source = "$masterId|$startMillis|$endMillis"
        val digest = MessageDigest.getInstance("SHA-1").digest(source.toByteArray())
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }
}
