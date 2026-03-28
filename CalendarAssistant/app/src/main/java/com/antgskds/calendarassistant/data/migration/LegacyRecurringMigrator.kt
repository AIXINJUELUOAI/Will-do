package com.antgskds.calendarassistant.data.migration

import android.util.Log
import androidx.compose.ui.graphics.toArgb
import androidx.room.withTransaction
import com.antgskds.calendarassistant.core.calendar.RecurringEventUtils
import com.antgskds.calendarassistant.core.rule.RuleActionDefaults
import com.antgskds.calendarassistant.core.rule.RuleMatchingEngine
import com.antgskds.calendarassistant.data.db.AppDatabase
import com.antgskds.calendarassistant.data.db.entity.EventExcludedDateEntity
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

class LegacyRecurringMigrator(
    private val database: AppDatabase,
    private val prefs: MigrationPrefs
) {
    private val json = Json { encodeDefaults = true }

    companion object {
        private const val TAG = "LegacyRecurringMigrator"
        private const val SOURCE = "legacy_json_recurring"
        private const val RRULE_PLACEHOLDER = "EXTERNAL"
        private const val PARENT_PREFIX = "recurring_parent_"
        private const val INSTANCE_PREFIX = "recurring_instance_"
    }

    suspend fun migrateIfNeeded(events: List<MyEvent>) {
        if (prefs.isRecurringMigrated()) return
        if (events.isEmpty()) {
            prefs.markRecurringMigrated(System.currentTimeMillis())
            return
        }

        val recurringEvents = events.filter { it.isRecurring || it.isRecurringParent }
        if (recurringEvents.isEmpty()) {
            prefs.markRecurringMigrated(System.currentTimeMillis())
            return
        }

        val masterDao = database.eventMasterDao()
        val instanceDao = database.eventInstanceDao()
        val excludedDao = database.eventExcludedDateDao()

        val existingMasterIds = masterDao.getAllMasterIds().toSet()
        val grouped = recurringEvents.mapNotNull { event ->
            resolveSeriesKey(event)?.let { it to event }
        }.groupBy({ it.first }, { it.second })

        if (grouped.isEmpty()) {
            prefs.markRecurringMigrated(System.currentTimeMillis())
            return
        }

        val masters = mutableListOf<EventMasterEntity>()
        val instances = mutableListOf<EventInstanceEntity>()
        val excludedDates = mutableListOf<EventExcludedDateEntity>()
        val now = System.currentTimeMillis()

        grouped.forEach { (seriesKey, group) ->
            if (existingMasterIds.contains(seriesKey)) return@forEach

            val parent = group.firstOrNull { it.isRecurringParent }
            val sample = parent ?: group.first()
            val ruleId = resolveRuleId(sample)
            val stateId = RuleActionDefaults.stateId(ruleId, RuleActionDefaults.STATE_PENDING)

            val children = group.filter { it.isRecurring && !it.isRecurringParent }
            val childEvents = if (children.isEmpty() && parent != null) {
                listOf(parent.copy(isRecurring = true, isRecurringParent = false))
            } else {
                children
            }

            if (childEvents.isEmpty()) return@forEach

            masters.add(
                EventMasterEntity(
                    masterId = seriesKey,
                    ruleId = ruleId,
                    title = sample.title,
                    description = sample.description,
                    location = sample.location,
                    colorArgb = sample.color.toArgb(),
                    rrule = RRULE_PLACEHOLDER,
                    syncId = null,
                    eventType = sample.eventType,
                    remindersJson = json.encodeToString(sample.reminders),
                    isImportant = sample.isImportant,
                    sourceImagePath = sample.sourceImagePath,
                    skipCalendarSync = true,
                    createdAt = sample.lastModified,
                    updatedAt = sample.lastModified,
                    source = SOURCE
                )
            )

            childEvents.forEach { event ->
                val startMillis = toMillis(event, event.startTime)
                val endMillis = toMillis(event, event.endTime)
                val completedAt = if (event.isCompleted) (event.completedAt ?: event.lastModified) else null
                val stateSuffix = RuleActionDefaults.resolveStateSuffix(ruleId, event.isCompleted, event.isCheckedIn)
                val currentStateId = RuleActionDefaults.stateId(ruleId, stateSuffix)

                instances.add(
                    EventInstanceEntity(
                        instanceId = event.id,
                        masterId = seriesKey,
                        startTime = startMillis,
                        endTime = endMillis,
                        currentStateId = currentStateId,
                        completedAt = completedAt,
                        archivedAt = event.archivedAt,
                        syncFingerprint = buildSyncFingerprint(seriesKey, startMillis, endMillis),
                        isSynced = false,
                        isCancelled = false
                    )
                )
            }

            val excludedKeys = parent?.excludedRecurringInstances.orEmpty()
            excludedKeys.forEach { key ->
                val startMillis = parseInstanceStartMillis(key, seriesKey) ?: return@forEach
                excludedDates.add(
                    EventExcludedDateEntity(
                        excludedId = "${seriesKey}_$startMillis",
                        masterId = seriesKey,
                        excludedStartTime = startMillis
                    )
                )
            }
        }

        if (masters.isEmpty()) {
            prefs.markRecurringMigrated(System.currentTimeMillis())
            return
        }

        try {
            database.withTransaction {
                masterDao.insertAll(masters)
                instanceDao.insertAll(instances)
                if (excludedDates.isNotEmpty()) {
                    excludedDao.insertAll(excludedDates)
                }
            }
            prefs.markRecurringMigrated(System.currentTimeMillis())
            Log.i(TAG, "Migrated ${masters.size} recurring series from JSON into Room")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to migrate recurring events", e)
        }
    }

    suspend fun upsertRecurringEvents(events: List<MyEvent>) {
        val recurringEvents = events.filter { it.isRecurring || it.isRecurringParent }
        if (recurringEvents.isEmpty()) return

        val masterDao = database.eventMasterDao()
        val instanceDao = database.eventInstanceDao()
        val excludedDao = database.eventExcludedDateDao()

        val grouped = recurringEvents.mapNotNull { event ->
            resolveSeriesKey(event)?.let { it to event }
        }.groupBy({ it.first }, { it.second })

        if (grouped.isEmpty()) return

        val masters = mutableListOf<EventMasterEntity>()
        val instances = mutableListOf<EventInstanceEntity>()
        val excludedDates = mutableListOf<EventExcludedDateEntity>()
        val now = System.currentTimeMillis()

        grouped.forEach { (seriesKey, group) ->
            val existingMaster = masterDao.getById(seriesKey)
            if (existingMaster != null && existingMaster.source != SOURCE) {
                return@forEach
            }

            val parent = group.firstOrNull { it.isRecurringParent }
            val sample = parent ?: group.first()
            val ruleId = resolveRuleId(sample)
            val stateId = RuleActionDefaults.stateId(ruleId, RuleActionDefaults.STATE_PENDING)

            val children = group.filter { it.isRecurring && !it.isRecurringParent }
            val childEvents = if (children.isEmpty() && parent != null) {
                listOf(parent.copy(isRecurring = true, isRecurringParent = false))
            } else {
                children
            }

            if (childEvents.isEmpty()) return@forEach

            masters.add(
                EventMasterEntity(
                    masterId = seriesKey,
                    ruleId = ruleId,
                    title = sample.title,
                    description = sample.description,
                    location = sample.location,
                    colorArgb = sample.color.toArgb(),
                    rrule = RRULE_PLACEHOLDER,
                    syncId = null,
                    eventType = sample.eventType,
                    remindersJson = json.encodeToString(sample.reminders),
                    isImportant = sample.isImportant,
                    sourceImagePath = sample.sourceImagePath,
                    skipCalendarSync = true,
                    createdAt = sample.lastModified,
                    updatedAt = now,
                    source = SOURCE
                )
            )

            childEvents.forEach { event ->
                val startMillis = toMillis(event, event.startTime)
                val endMillis = toMillis(event, event.endTime)
                val completedAt = if (event.isCompleted) (event.completedAt ?: event.lastModified) else null
                val stateSuffix = RuleActionDefaults.resolveStateSuffix(ruleId, event.isCompleted, event.isCheckedIn)
                val currentStateId = RuleActionDefaults.stateId(ruleId, stateSuffix)

                instances.add(
                    EventInstanceEntity(
                        instanceId = event.id,
                        masterId = seriesKey,
                        startTime = startMillis,
                        endTime = endMillis,
                        currentStateId = currentStateId,
                        completedAt = completedAt,
                        archivedAt = event.archivedAt,
                        syncFingerprint = buildSyncFingerprint(seriesKey, startMillis, endMillis),
                        isSynced = false,
                        isCancelled = false
                    )
                )
            }

            val excludedKeys = parent?.excludedRecurringInstances.orEmpty()
            excludedKeys.forEach { key ->
                val startMillis = parseInstanceStartMillis(key, seriesKey) ?: return@forEach
                excludedDates.add(
                    EventExcludedDateEntity(
                        excludedId = "${seriesKey}_$startMillis",
                        masterId = seriesKey,
                        excludedStartTime = startMillis
                    )
                )
            }
        }

        if (masters.isEmpty()) return

        try {
            database.withTransaction {
                masterDao.insertAll(masters)
                instanceDao.insertAll(instances)
                if (excludedDates.isNotEmpty()) {
                    excludedDao.insertAll(excludedDates.distinctBy { it.excludedId })
                }
            }
            Log.i(TAG, "Upserted ${masters.size} recurring series into Room")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upsert recurring events", e)
        }
    }

    private fun resolveSeriesKey(event: MyEvent): String? {
        val direct = event.recurringSeriesKey?.takeIf { it.isNotBlank() }
        if (direct != null) return direct

        val parentId = event.parentRecurringId
        if (!parentId.isNullOrBlank() && parentId.startsWith(PARENT_PREFIX)) {
            return parentId.removePrefix(PARENT_PREFIX)
        }

        if (event.isRecurringParent && event.id.startsWith(PARENT_PREFIX)) {
            return event.id.removePrefix(PARENT_PREFIX)
        }

        if (event.id.startsWith(INSTANCE_PREFIX)) {
            val instanceKey = event.id.removePrefix(INSTANCE_PREFIX)
            val seriesKey = instanceKey.substringBeforeLast("_")
            if (seriesKey.isNotBlank()) return seriesKey
        }

        return null
    }

    private fun parseInstanceStartMillis(instanceKey: String, seriesKey: String): Long? {
        val prefix = "${seriesKey}_"
        if (!instanceKey.startsWith(prefix)) return null
        return instanceKey.removePrefix(prefix).toLongOrNull()
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
