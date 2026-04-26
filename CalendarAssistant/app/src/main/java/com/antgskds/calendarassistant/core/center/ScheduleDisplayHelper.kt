package com.antgskds.calendarassistant.core.center

import com.antgskds.calendarassistant.data.model.ScheduleDisplayItem
import com.antgskds.calendarassistant.data.model.ScheduleDisplayItem.ActionTarget
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.EventTags
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/**
 * 将数据库中的 Event 转换为 UI 展示用的 ScheduleDisplayItem。
 *
 * 职责：
 * - 普通事件 → Single 展示项
 * - 重复母事件 → 按日期范围展开为 RecurringOccurrence 展示项
 * - 已有子事件（exception instance）→ Single 展示项（它有自己的 DB id）
 * - Event 对象不会传给 UI，只在此处读取
 */
object ScheduleDisplayHelper {

    /**
     * 将事件列表转换为展示列表。
     * 重复母事件会被展开为 [from, to] 范围内的实例。
     */
    fun buildDisplayItems(
        events: List<Event>,
        from: LocalDate,
        to: LocalDate
    ): List<ScheduleDisplayItem> {
        val result = mutableListOf<ScheduleDisplayItem>()
        val activeEvents = events.filter { it.archivedAt == null }

        // 按 parentId 收集已有子事件，用于避免展开时重复
        val childrenByParent = activeEvents
            .filter { it.parentId != 0L && !it.isRecurring }
            .groupBy { it.parentId }

        for (event in activeEvents) {
            val id = event.id ?: continue

            when {
                // 重复母事件：展开为多个 RecurringOccurrence
                event.isRecurring -> {
                    val children = childrenByParent[id] ?: emptyList()
                    val occurrences = expandRecurring(event, from, to, children)
                    result.addAll(occurrences)
                }

                // 子事件（异常实例）：当作 Single 展示，它有自己的真实 id
                event.parentId != 0L -> {
                    if (overlapsRange(event, from, to)) {
                        result.add(eventToSingleItem(event))
                    }
                }

                // 普通单次事件
                else -> {
                    if (overlapsRange(event, from, to)) {
                        result.add(eventToSingleItem(event))
                    }
                }
            }
        }

        return result
    }

    /**
     * 单个事件转展示项
     */
    fun eventToSingleItem(event: Event): ScheduleDisplayItem {
        return ScheduleDisplayItem(
            stableKey = "single:${event.id}",
            title = event.title,
            startTS = event.startTS,
            endTS = event.endTS,
            location = event.location,
            description = event.description,
            tag = event.tag,
            color = event.color,
            state = event.state,
            isAllDay = event.getIsAllDay(),
            isRecurringInstance = event.parentId != 0L,
            timeZone = event.getTimeZoneString(),
            action = ActionTarget.Single(event.id!!)
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    // RRULE 展开
    // ══════════════════════════════════════════════════════════════════════

    private fun expandRecurring(
        parent: Event,
        from: LocalDate,
        to: LocalDate,
        existingChildren: List<Event>
    ): List<ScheduleDisplayItem> {
        val rrule = parseRRule(parent.rrule)
        if (rrule.freq == null) return emptyList()

        val zone = try { ZoneId.of(parent.getTimeZoneString()) } catch (_: Exception) { ZoneId.systemDefault() }
        val eventStart = Instant.ofEpochSecond(parent.startTS).atZone(zone)
        val duration = parent.endTS - parent.startTS

        val exdateSet = parent.exdates.mapNotNull { parseExdateToEpochSecond(it) }.toHashSet()
        val childStartTsSet = existingChildren.map { it.startTS }.toHashSet()

        val items = mutableListOf<ScheduleDisplayItem>()
        var current = eventStart
        var emittedCount = 0
        val maxOccurrences = rrule.count ?: 3650

        while (emittedCount < maxOccurrences) {
            val occDate = current.toLocalDate()
            if (occDate.isAfter(to)) break
            if (rrule.until != null && occDate.isAfter(rrule.until)) break

            val matchesByDay = rrule.byDay.isEmpty() || current.dayOfWeek in rrule.byDay
            if (matchesByDay) {
                val occTs = current.toEpochSecond()
                if (!occDate.isBefore(from)) {
                    if (occTs !in exdateSet && occTs !in childStartTsSet) {
                        items.add(
                            ScheduleDisplayItem(
                                stableKey = "rec:${parent.id}:${occTs}",
                                title = parent.title,
                                startTS = occTs,
                                endTS = occTs + duration,
                                location = parent.location,
                                description = parent.description,
                                tag = parent.tag,
                                color = parent.color,
                                state = parent.state,
                                isAllDay = parent.getIsAllDay(),
                                isRecurringInstance = true,
                                timeZone = parent.getTimeZoneString(),
                                action = ActionTarget.RecurringOccurrence(
                                    parentId = parent.id!!,
                                    occurrenceTs = occTs
                                )
                            )
                        )
                    }
                }
                emittedCount++
            }

            current = advanceCursor(current, rrule, eventStart)
            if (current.toLocalDate() == occDate) break
        }

        return items
    }

    private fun overlapsRange(event: Event, from: LocalDate, to: LocalDate): Boolean {
        return try {
            val zone = try { ZoneId.of(event.getTimeZoneString()) } catch (_: Exception) { ZoneId.systemDefault() }
            val start = Instant.ofEpochSecond(event.startTS).atZone(zone).toLocalDate()
            val end = Instant.ofEpochSecond(event.endTS).atZone(zone).toLocalDate()
            !end.isBefore(from) && !start.isAfter(to)
        } catch (_: Exception) { false }
    }

    // ── RRULE 解析 ──

    private data class RRule(
        val freq: String? = null,
        val interval: Int = 1,
        val count: Int? = null,
        val until: LocalDate? = null,
        val byDay: Set<DayOfWeek> = emptySet()
    )

    private fun parseRRule(rrule: String): RRule {
        if (rrule.isBlank()) return RRule()
        val parts = rrule.split(';').associate {
            val kv = it.split('=', limit = 2)
            if (kv.size == 2) kv[0].uppercase() to kv[1] else kv[0].uppercase() to ""
        }
        return RRule(
            freq = parts["FREQ"],
            interval = parts["INTERVAL"]?.toIntOrNull() ?: 1,
            count = parts["COUNT"]?.toIntOrNull(),
            until = parts["UNTIL"]?.let { parseUntilDate(it) },
            byDay = parts["BYDAY"]?.let { parseDays(it) } ?: emptySet()
        )
    }

    private fun advanceCursor(current: ZonedDateTime, rrule: RRule, original: ZonedDateTime): ZonedDateTime {
        val interval = rrule.interval
        return when (rrule.freq) {
            "DAILY" -> current.plusDays(interval.toLong())
            "WEEKLY" -> {
                if (rrule.byDay.isEmpty()) {
                    current.plusWeeks(interval.toLong())
                } else {
                    var next = current.plusDays(1)
                    val weekEnd = current.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                        .plusWeeks(interval.toLong())
                    while (next.isBefore(weekEnd) || next == weekEnd) {
                        if (next.dayOfWeek in rrule.byDay) return next
                        next = next.plusDays(1)
                    }
                    for (i in 0..6) {
                        val c = weekEnd.plusDays(i.toLong())
                        if (c.dayOfWeek in rrule.byDay) return c
                    }
                    current.plusWeeks(interval.toLong())
                }
            }
            "MONTHLY" -> {
                val next = current.plusMonths(interval.toLong())
                try { next.withDayOfMonth(original.dayOfMonth) }
                catch (_: Exception) { next.with(TemporalAdjusters.lastDayOfMonth()) }
            }
            "YEARLY" -> current.plusYears(interval.toLong())
            else -> current.plusDays(1)
        }
    }

    private fun parseDays(raw: String): Set<DayOfWeek> =
        raw.split(',').mapNotNull { parseSingleDay(it.trim()) }.toSet()

    private fun parseSingleDay(d: String): DayOfWeek? = when (d.takeLast(2).uppercase()) {
        "MO" -> DayOfWeek.MONDAY; "TU" -> DayOfWeek.TUESDAY
        "WE" -> DayOfWeek.WEDNESDAY; "TH" -> DayOfWeek.THURSDAY
        "FR" -> DayOfWeek.FRIDAY; "SA" -> DayOfWeek.SATURDAY
        "SU" -> DayOfWeek.SUNDAY; else -> null
    }

    private fun parseUntilDate(raw: String): LocalDate? = try {
        LocalDate.parse(raw.take(8), DateTimeFormatter.BASIC_ISO_DATE)
    } catch (_: Exception) { null }

    private fun parseExdateToEpochSecond(exdate: String): Long? = try {
        Instant.from(
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
                .withZone(java.time.ZoneOffset.UTC)
                .parse(exdate.trim())
        ).epochSecond
    } catch (_: Exception) { null }
}
