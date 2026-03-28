package com.antgskds.calendarassistant.data.db.reader

import androidx.compose.ui.graphics.Color
import com.antgskds.calendarassistant.core.calendar.RecurringEventUtils
import com.antgskds.calendarassistant.core.rule.RuleActionDefaults
import com.antgskds.calendarassistant.core.rule.RuleMatchingEngine
import com.antgskds.calendarassistant.data.db.AppDatabase
import com.antgskds.calendarassistant.data.db.model.FullInstance
import com.antgskds.calendarassistant.data.model.EventTags
import com.antgskds.calendarassistant.data.model.EventType
import com.antgskds.calendarassistant.data.model.MyEvent
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class RoomEventReader(private val database: AppDatabase) {
    private val json = Json { ignoreUnknownKeys = true }
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val zoneId = ZoneId.systemDefault()

    suspend fun loadActiveEvents(): List<MyEvent> {
        val instances = database.eventInstanceDao().getAllActive()
        return buildActiveEvents(instances)
    }

    suspend fun loadArchivedEvents(): List<MyEvent> {
        val instances = database.eventInstanceDao().getAllArchived()
        return instances.mapNotNull { toMyEvent(it, isRecurring = false, isRecurringParent = false, seriesKey = null, instanceKey = null, parentId = null, excludedKeys = emptyList(), nextOccurrenceStartMillis = null) }
    }

    private suspend fun buildActiveEvents(instances: List<FullInstance>): List<MyEvent> {
        if (instances.isEmpty()) return emptyList()

        val excludedDateDao = database.eventExcludedDateDao()
        val grouped = instances.groupBy { it.masterWithRule.master.masterId }
        val result = mutableListOf<MyEvent>()
        val now = System.currentTimeMillis()

        grouped.forEach { (_, group) ->
            val master = group.first().masterWithRule.master
            if (master.rrule.isNullOrBlank()) {
                group.mapNotNullTo(result) { toMyEvent(it, isRecurring = false, isRecurringParent = false, seriesKey = null, instanceKey = null, parentId = null, excludedKeys = emptyList(), nextOccurrenceStartMillis = null) }
                return@forEach
            }

            val seriesKey = master.masterId
            val excludedTimes = excludedDateDao.getStartTimesByMasterId(seriesKey)
            val excludedKeys = excludedTimes.map { RecurringEventUtils.buildInstanceKey(seriesKey, it) }.toSet()
            val parentId = RecurringEventUtils.buildParentId(seriesKey)

            val childEvents = group
                .sortedBy { it.instance.startTime }
                .mapNotNull { full ->
                    val instanceKey = RecurringEventUtils.buildInstanceKey(seriesKey, full.instance.startTime)
                    if (excludedKeys.contains(instanceKey)) return@mapNotNull null
                    toMyEvent(
                        full,
                        isRecurring = true,
                        isRecurringParent = false,
                        seriesKey = seriesKey,
                        instanceKey = instanceKey,
                        parentId = parentId,
                        excludedKeys = emptyList(),
                        nextOccurrenceStartMillis = full.instance.startTime
                    )
                }

            if (childEvents.isEmpty()) return@forEach

            val nextChild = childEvents.firstOrNull { child ->
                val endMillis = RecurringEventUtils.eventEndMillis(child) ?: return@firstOrNull false
                endMillis > now
            } ?: childEvents.first()

            val parentEvent = nextChild.copy(
                id = parentId,
                isRecurring = true,
                isRecurringParent = true,
                recurringSeriesKey = seriesKey,
                recurringInstanceKey = nextChild.recurringInstanceKey,
                parentRecurringId = null,
                excludedRecurringInstances = excludedKeys.toList(),
                nextOccurrenceStartMillis = nextChild.nextOccurrenceStartMillis,
                reminders = emptyList(),
                isCompleted = false,
                completedAt = null,
                isCheckedIn = false,
                skipCalendarSync = true
            )

            result.add(parentEvent)
            result.addAll(childEvents)
        }

        return result
    }

    private fun toMyEvent(
        full: FullInstance,
        isRecurring: Boolean,
        isRecurringParent: Boolean,
        seriesKey: String?,
        instanceKey: String?,
        parentId: String?,
        excludedKeys: List<String>,
        nextOccurrenceStartMillis: Long?
    ): MyEvent? {
        val master = full.masterWithRule.master
        val instance = full.instance
        val startInstant = Instant.ofEpochMilli(instance.startTime)
        val endInstant = Instant.ofEpochMilli(instance.endTime)
        val startZoned = startInstant.atZone(zoneId)
        val endZoned = endInstant.atZone(zoneId)
        val reminders = parseReminders(master.remindersJson)
        val tag = normalizeTag(master.ruleId, master.eventType)
        val description = master.description
        val resolvedRuleId = master.ruleId?.ifBlank { RuleMatchingEngine.RULE_GENERAL } ?: RuleMatchingEngine.RULE_GENERAL
        val checkedInStateId = RuleActionDefaults.stateId(resolvedRuleId, RuleActionDefaults.STATE_CHECKED_IN)
        val doneStateId = RuleActionDefaults.stateId(resolvedRuleId, RuleActionDefaults.STATE_DONE)
        val isCheckedIn = !isRecurringParent && instance.currentStateId == checkedInStateId
        val isCompleted = !isRecurringParent && (instance.completedAt != null || instance.currentStateId == doneStateId)

        return MyEvent(
            id = instance.instanceId,
            title = master.title,
            startDate = startZoned.toLocalDate(),
            endDate = endZoned.toLocalDate(),
            startTime = startZoned.toLocalTime().format(timeFormatter),
            endTime = endZoned.toLocalTime().format(timeFormatter),
            location = master.location,
            description = description,
            color = Color(master.colorArgb),
            isImportant = master.isImportant,
            sourceImagePath = master.sourceImagePath,
            reminders = if (isRecurringParent) emptyList() else reminders,
            eventType = master.eventType,
            tag = tag,
            isCompleted = isCompleted,
            completedAt = if (isRecurringParent) null else instance.completedAt,
            originalEndDate = null,
            originalEndTime = null,
            isCheckedIn = isCheckedIn,
            archivedAt = instance.archivedAt,
            lastModified = master.updatedAt,
            isRecurring = isRecurring,
            isRecurringParent = isRecurringParent,
            recurringSeriesKey = seriesKey,
            recurringInstanceKey = instanceKey,
            parentRecurringId = parentId,
            excludedRecurringInstances = excludedKeys,
            nextOccurrenceStartMillis = nextOccurrenceStartMillis,
            skipCalendarSync = if (isRecurringParent) true else master.skipCalendarSync
        )
    }

    private fun parseReminders(remindersJson: String): List<Int> {
        if (remindersJson.isBlank()) return emptyList()
        return try {
            json.decodeFromString<List<Int>>(remindersJson)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun normalizeTag(ruleId: String?, eventType: String): String {
        if (eventType == EventType.COURSE) return EventTags.GENERAL
        return when (ruleId) {
            null, "" -> EventTags.GENERAL
            RuleMatchingEngine.RULE_GENERAL -> EventTags.GENERAL
            RuleMatchingEngine.RULE_PICKUP -> EventTags.PICKUP
            RuleMatchingEngine.RULE_TRAIN -> EventTags.TRAIN
            RuleMatchingEngine.RULE_TAXI -> EventTags.TAXI
            else -> ruleId
        }
    }
}
