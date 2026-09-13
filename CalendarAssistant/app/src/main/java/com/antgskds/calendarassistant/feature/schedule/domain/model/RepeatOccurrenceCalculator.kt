package com.antgskds.calendarassistant.feature.schedule.domain.model

import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog

/**
 * Advances one occurrence while preserving the recurrence semantics shared by schedules and reminders.
 */
fun RepeatSpec.advanceOccurrence(
    current: ZonedDateTime,
    anchor: ZonedDateTime
): ZonedDateTime {
    val safeInterval = interval.coerceAtLeast(1).toLong()
    return when (frequency) {
        RepeatFrequency.DAILY -> current.plusDays(safeInterval)
        RepeatFrequency.WEEKLY -> {
            if (byDays.isEmpty()) {
                current.plusWeeks(safeInterval)
            } else {
                var next = current.plusDays(1)
                val nextActiveWeek = current
                    .with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                    .plusWeeks(safeInterval)
                while (next.isBefore(nextActiveWeek) || next == nextActiveWeek) {
                    if (next.dayOfWeek in byDays) return next
                    next = next.plusDays(1)
                }
                for (offset in 0..6) {
                    val candidate = nextActiveWeek.plusDays(offset.toLong())
                    if (candidate.dayOfWeek in byDays) return candidate
                }
                current.plusWeeks(safeInterval)
            }
        }
        RepeatFrequency.MONTHLY -> {
            val next = current.plusMonths(safeInterval)
            runCatching { next.withDayOfMonth(anchor.dayOfMonth) }
                .getOrElse { next.with(TemporalAdjusters.lastDayOfMonth()) }
        }
        RepeatFrequency.YEARLY -> current.plusYears(safeInterval)
    }
}

/**
 * Returns the first occurrence strictly after [after], respecting count and end-date constraints.
 * The [anchor] itself is the first occurrence.
 */
fun RepeatSpec.nextOccurrenceAfter(
    anchor: ZonedDateTime,
    after: ZonedDateTime
): ZonedDateTime? {
    var occurrence = anchor
    var occurrenceNumber = 1
    val maxCount = (end as? RepeatEnd.Count)?.count?.coerceAtLeast(1)
    val untilDate = (end as? RepeatEnd.Until)?.date

    if (untilDate != null && occurrence.toLocalDate().isAfter(untilDate)) return null
    if (occurrence.isAfter(after)) return occurrence

    while (occurrenceNumber < (maxCount ?: ConfigCatalog.REPEAT_OCCURRENCE_SEARCH_LIMIT)) {
        val next = advanceOccurrence(occurrence, anchor)
        if (!next.isAfter(occurrence)) return null
        occurrence = next
        occurrenceNumber++
        if (untilDate != null && occurrence.toLocalDate().isAfter(untilDate)) return null
        if (occurrence.isAfter(after)) return occurrence
    }
    return null
}
