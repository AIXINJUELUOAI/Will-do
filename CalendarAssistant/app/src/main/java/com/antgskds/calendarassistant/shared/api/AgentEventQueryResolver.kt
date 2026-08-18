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
