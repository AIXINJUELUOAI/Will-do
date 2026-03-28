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

class LegacyEventMigrator(
    private val database: AppDatabase,
    private val prefs: MigrationPrefs
) {
    private val json = Json { encodeDefaults = true }

    suspend fun migrateIfNeeded(events: List<MyEvent>) {
        if (events.isEmpty()) {
            prefs.markEventsMigrated(System.currentTimeMillis())
            return
        }
        if (prefs.isEventsMigrated()) return

        val masterDao = database.eventMasterDao()
        val instanceDao = database.eventInstanceDao()

        val existingCount = try {
            masterDao.count()
        } catch (e: Exception) {
            Log.e("LegacyEventMigrator", "Failed to read Room state", e)
            return
        }
        if (existingCount > 0) {
            prefs.markEventsMigrated(System.currentTimeMillis())
            return
        }

        val masters = ArrayList<EventMasterEntity>(events.size)
        val instances = ArrayList<EventInstanceEntity>(events.size)

        events.forEach { event ->
            val ruleId = resolveRuleId(event)
            val startMillis = toMillis(event, event.startTime)
            val endMillis = toMillis(event, event.endTime)
            val completedAt = if (event.isCompleted) (event.completedAt ?: event.lastModified) else null
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
                    source = "legacy_json"
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
                    archivedAt = event.archivedAt,
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
            prefs.markEventsMigrated(System.currentTimeMillis())
            Log.i("LegacyEventMigrator", "Migrated ${masters.size} legacy events into Room")
        } catch (e: Exception) {
            Log.e("LegacyEventMigrator", "Failed to migrate legacy events", e)
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
