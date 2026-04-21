package com.antgskds.calendarassistant.core.calendar

import com.antgskds.calendarassistant.core.rule.RuleMatchingEngine
import com.antgskds.calendarassistant.core.util.EventDeduplicator
import com.antgskds.calendarassistant.data.db.entity.EventInstanceEntity
import com.antgskds.calendarassistant.data.db.entity.EventMasterEntity
import com.antgskds.calendarassistant.data.model.EventFingerprint
import com.antgskds.calendarassistant.data.model.EventTags
import com.antgskds.calendarassistant.data.model.MyEvent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.security.MessageDigest

internal object CalendarSyncManagerV2Hashing {

    fun computeEventHash(event: MyEvent, zoneId: ZoneId): Int {
        val startMillis = toEpochMillis(event.startDate, event.startTime, zoneId)
        val endMillis = toEpochMillis(event.endDate, event.endTime, zoneId)
        return computeHash(
            title = event.title,
            startMillis = startMillis,
            endMillis = endMillis,
            location = event.location,
            description = event.description,
            rrule = ""
        )
    }

    fun computeRecurringSyncHash(
        master: EventMasterEntity,
        instance: EventInstanceEntity,
        excludedStartTimes: List<Long>
    ): Int {
        val exDatePayload = excludedStartTimes.sorted().joinToString(",")
        val rrulePayload = listOf(master.rrule.orEmpty(), exDatePayload).joinToString("|")
        return computeHash(
            title = master.title,
            startMillis = instance.startTime,
            endMillis = instance.endTime,
            location = master.location,
            description = master.description,
            rrule = rrulePayload
        )
    }

    fun computeRecurringBaseHash(master: EventMasterEntity, instance: EventInstanceEntity): Int {
        return computeHash(
            title = master.title,
            startMillis = instance.startTime,
            endMillis = instance.endTime,
            location = master.location,
            description = master.description,
            rrule = master.rrule.orEmpty()
        )
    }

    fun computeSystemHash(systemEvent: CalendarManager.SystemEventInfo): Int {
        return computeHash(
            title = systemEvent.title,
            startMillis = systemEvent.startMillis,
            endMillis = systemEvent.endMillis,
            location = systemEvent.location,
            description = systemEvent.description,
            rrule = systemEvent.recurringRule
        )
    }

    fun resolveRuleIdFromSystem(event: CalendarManager.SystemEventInfo): String {
        val resolved = RuleMatchingEngine.resolvePayload(event.description, null)?.ruleId
        if (!resolved.isNullOrBlank()) return resolved
        return when (event.tag) {
            EventTags.COURSE -> EventTags.COURSE
            EventTags.PICKUP -> RuleMatchingEngine.RULE_PICKUP
            EventTags.TRAIN -> RuleMatchingEngine.RULE_TRAIN
            EventTags.TAXI -> RuleMatchingEngine.RULE_TAXI
            else -> RuleMatchingEngine.RULE_GENERAL
        }
    }

    fun buildSyncFingerprint(masterId: String, startMillis: Long, endMillis: Long): String {
        val source = "$masterId|$startMillis|$endMillis"
        val digest = MessageDigest.getInstance("SHA-1").digest(source.toByteArray())
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    fun normalizeTag(ruleId: String?): String {
        return when (ruleId) {
            null, "" -> EventTags.GENERAL
            RuleMatchingEngine.RULE_GENERAL -> EventTags.GENERAL
            RuleMatchingEngine.RULE_PICKUP -> EventTags.PICKUP
            RuleMatchingEngine.RULE_TRAIN -> EventTags.TRAIN
            RuleMatchingEngine.RULE_TAXI -> EventTags.TAXI
            EventTags.COURSE -> EventTags.COURSE
            else -> ruleId
        }
    }

    fun parseInstanceStartMillis(instanceKey: String, seriesKey: String): Long? {
        val prefix = "${seriesKey}_"
        if (!instanceKey.startsWith(prefix)) return null
        return instanceKey.removePrefix(prefix).toLongOrNull()
    }

    fun resolveSeedSingleEvent(
        systemEvent: CalendarManager.SystemEventInfo,
        activeEventsById: Map<String, MyEvent>,
        activeEventsByFingerprint: Map<EventFingerprint, MyEvent>
    ): MyEvent? {
        val appId = systemEvent.appId
        if (!appId.isNullOrBlank()) {
            val directMatch = activeEventsById[appId]
            if (directMatch != null) return directMatch
        }

        if (!systemEvent.isManaged) return null
        return activeEventsByFingerprint[EventDeduplicator.generateFingerprintFromSystemEvent(systemEvent)]
    }

    fun resolveSeedRecurringMaster(
        systemSeries: CalendarManager.SystemEventInfo,
        mastersById: Map<String, EventMasterEntity>,
        mastersByBaseHash: Map<Int, EventMasterEntity>
    ): EventMasterEntity? {
        val appId = systemSeries.appId
        if (!appId.isNullOrBlank()) {
            val directMatch = mastersById[appId]
            if (directMatch != null) return directMatch
        }

        if (!systemSeries.isManaged) return null
        return mastersByBaseHash[computeSystemHash(systemSeries)]
    }

    private fun computeHash(
        title: String,
        startMillis: Long,
        endMillis: Long,
        location: String?,
        description: String?,
        rrule: String?
    ): Int {
        val payload = listOf(
            title.trim(),
            startMillis.toString(),
            endMillis.toString(),
            location.orEmpty().trim(),
            description.orEmpty().trim(),
            rrule.orEmpty().trim()
        ).joinToString("|")
        return payload.hashCode()
    }

    private fun toEpochMillis(date: LocalDate, timeStr: String, zoneId: ZoneId): Long {
        return try {
            val localDateTime = LocalDateTime.of(date, LocalTime.parse(timeStr))
            localDateTime.atZone(zoneId).toInstant().toEpochMilli()
        } catch (_: Exception) {
            LocalDateTime.of(date, LocalTime.MIDNIGHT).atZone(zoneId).toInstant().toEpochMilli()
        }
    }
}
