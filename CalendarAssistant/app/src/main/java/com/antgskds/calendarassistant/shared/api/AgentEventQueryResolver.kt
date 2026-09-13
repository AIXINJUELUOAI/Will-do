package com.antgskds.calendarassistant.shared.api

import com.antgskds.calendarassistant.feature.schedule.domain.ScheduleDisplayHelper
import com.antgskds.calendarassistant.feature.schedule.domain.model.Event
import com.antgskds.calendarassistant.feature.schedule.presentation.model.ScheduleDisplayItem.ActionTarget
import com.antgskds.calendarassistant.shared.operation.AgentEventQuery
import com.antgskds.calendarassistant.shared.operation.WillDoAgentContract
import java.time.Instant
import java.time.ZoneId

internal object AgentEventQueryResolver {
    private const val OPEN_RANGE_DAYS = 3650L

    /** 无独立记录的有效实例返回 id=null；查询附件时不能因此创建数据库记录。 */
    fun resolveAttachmentTarget(events: List<Event>, eventId: Long, occurrenceTs: Long?): Event {
        val event = events.firstOrNull { it.id == eventId }
            ?: throw NoSuchElementException("Event $eventId not found")
        if (occurrenceTs == null) return event
        require(occurrenceTs > 0L) { "occurrenceTs must be an epoch timestamp in seconds" }
        if (!event.isRecurring) {
            require(event.startTS == occurrenceTs) { "occurrenceTs does not match event $eventId" }
            return event
        }
        events.firstOrNull { it.parentId == eventId && it.startTS == occurrenceTs }?.let { return it }
        val occurrence = resolve(events.filter { it.id == eventId || it.parentId == eventId }, AgentEventQuery(startTs = occurrenceTs, endTs = occurrenceTs,
            limit = WillDoAgentContract.MAX_QUERY_LIMIT)).firstOrNull {
            it.id == eventId && it.startTS == occurrenceTs && it.archivedAt == null
        } ?: throw NoSuchElementException("Occurrence $eventId:$occurrenceTs not found; use the child eventId for edited instances")
        return occurrence.copy(id = null)
    }

    fun resolve(
        events: List<Event>,
        query: AgentEventQuery,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<Event> {
        if (query.startTs != null && query.endTs != null && query.startTs > query.endTs) {
            return emptyList()
        }

        val candidates = if (query.startTs == null && query.endTs == null) {
            events
        } else {
            expandRange(events, query, zoneId)
        }
        val needle = query.text?.trim()?.takeIf(String::isNotEmpty)

        return candidates.asSequence()
            .filter { query.startTs == null || it.endTS >= query.startTs }
            .filter { query.endTs == null || it.startTS <= query.endTs }
            .filter { query.tag == null || it.tag == query.tag }
            .filter { event ->
                needle == null || listOf(event.title, event.description, event.location)
                    .any { it.contains(needle, ignoreCase = true) }
            }
            .sortedBy(Event::startTS)
            .take(query.limit.coerceIn(1, WillDoAgentContract.MAX_QUERY_LIMIT))
            .toList()
    }

    private fun expandRange(
        events: List<Event>,
        query: AgentEventQuery,
        zoneId: ZoneId
    ): List<Event> {
        val active = events.filter { it.archivedAt == null }
        val archived = events.filter { it.archivedAt != null }
        if (active.isEmpty()) return archived

        val lowerBound = query.startTs
            ?: active.minOfOrNull(Event::startTS)
            ?: query.endTs
            ?: return archived
        val upperBound = query.endTs ?: openRangeEnd(lowerBound)
        val from = Instant.ofEpochSecond(lowerBound).atZone(zoneId).toLocalDate().minusDays(1)
        val to = Instant.ofEpochSecond(upperBound).atZone(zoneId).toLocalDate().plusDays(1)
        val eventsById = active.mapNotNull { event -> event.id?.let { it to event } }.toMap()

        val expanded = ScheduleDisplayHelper.buildDisplayItems(active, from, to).mapNotNull { item ->
            when (val action = item.action) {
                is ActionTarget.Single -> eventsById[action.eventId]
                is ActionTarget.RecurringOccurrence -> eventsById[action.parentId]?.copy(
                    startTS = item.startTS,
                    endTS = item.endTS
                )
            }
        }
        return expanded + archived
    }

    private fun openRangeEnd(startTs: Long): Long {
        val seconds = OPEN_RANGE_DAYS * 24L * 60L * 60L
        return if (startTs > Long.MAX_VALUE - seconds) Long.MAX_VALUE else startTs + seconds
    }
}
