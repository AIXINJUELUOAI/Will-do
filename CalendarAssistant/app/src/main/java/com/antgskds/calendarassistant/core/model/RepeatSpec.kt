package com.antgskds.calendarassistant.core.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class RepeatFrequency(val rruleValue: String, val unitLabel: String) {
    DAILY("DAILY", "天"),
    WEEKLY("WEEKLY", "周"),
    MONTHLY("MONTHLY", "月"),
    YEARLY("YEARLY", "年")
}

sealed class RepeatEnd {
    data object Never : RepeatEnd()
    data class Count(val count: Int) : RepeatEnd()
    data class Until(val date: LocalDate) : RepeatEnd()
}

data class RepeatSpec(
    val frequency: RepeatFrequency,
    val interval: Int = 1,
    val byDays: Set<DayOfWeek> = emptySet(),
    val end: RepeatEnd = RepeatEnd.Never
) {
    fun toRRule(): String {
        val parts = mutableListOf(
            "FREQ=${frequency.rruleValue}",
            "INTERVAL=${interval.coerceAtLeast(1)}"
        )
        if (frequency == RepeatFrequency.WEEKLY && byDays.isNotEmpty()) {
            parts += "BYDAY=${byDays.sortedBy { it.value }.joinToString(",") { it.toRRuleDay() }}"
        }
        when (val repeatEnd = end) {
            RepeatEnd.Never -> Unit
            is RepeatEnd.Count -> parts += "COUNT=${repeatEnd.count.coerceAtLeast(1)}"
            is RepeatEnd.Until -> parts += "UNTIL=${repeatEnd.date.format(DateTimeFormatter.BASIC_ISO_DATE)}T235959Z"
        }
        return parts.joinToString(";")
    }

    fun summary(): String {
        val base = when {
            frequency == RepeatFrequency.WEEKLY && byDays == WEEKDAYS -> "周一至周五"
            frequency == RepeatFrequency.WEEKLY && byDays.isNotEmpty() -> {
                val days = byDays.sortedBy { it.value }.joinToString("/") { it.shortCn() }
                if (interval <= 1) "每周 · $days" else "每 $interval 周 · $days"
            }
            interval <= 1 -> when (frequency) {
                RepeatFrequency.DAILY -> "每天"
                RepeatFrequency.WEEKLY -> "每周"
                RepeatFrequency.MONTHLY -> "每月"
                RepeatFrequency.YEARLY -> "每年"
            }
            else -> "每 $interval ${frequency.unitLabel}"
        }
        return when (val repeatEnd = end) {
            RepeatEnd.Never -> base
            is RepeatEnd.Count -> "$base · ${repeatEnd.count.coerceAtLeast(1)}次"
            is RepeatEnd.Until -> "$base · 至 ${repeatEnd.date}"
        }
    }

    companion object {
        val WEEKDAYS: Set<DayOfWeek> = setOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY
        )

        fun daily(): RepeatSpec = RepeatSpec(RepeatFrequency.DAILY)
        fun weekly(): RepeatSpec = RepeatSpec(RepeatFrequency.WEEKLY)
        fun weekdays(): RepeatSpec = RepeatSpec(RepeatFrequency.WEEKLY, byDays = WEEKDAYS)

        fun fromRRule(rrule: String): RepeatSpec? {
            if (rrule.isBlank()) return null
            val parts = rrule.split(';').mapNotNull { token ->
                val index = token.indexOf('=')
                if (index <= 0) null else token.substring(0, index).uppercase() to token.substring(index + 1)
            }.toMap()
            val frequency = when (parts["FREQ"]?.uppercase()) {
                "DAILY" -> RepeatFrequency.DAILY
                "WEEKLY" -> RepeatFrequency.WEEKLY
                "MONTHLY" -> RepeatFrequency.MONTHLY
                "YEARLY" -> RepeatFrequency.YEARLY
                else -> return null
            }
            val interval = parts["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
            val byDays = parts["BYDAY"]?.split(',')?.mapNotNull { it.toDayOfWeekOrNull() }?.toSet().orEmpty()
            val end = when {
                parts["COUNT"]?.toIntOrNull() != null -> RepeatEnd.Count(parts["COUNT"]!!.toInt().coerceAtLeast(1))
                parts["UNTIL"] != null -> {
                    val date = runCatching {
                        LocalDate.parse(parts["UNTIL"]!!.take(8), DateTimeFormatter.BASIC_ISO_DATE)
                    }.getOrNull()
                    if (date != null) RepeatEnd.Until(date) else RepeatEnd.Never
                }
                else -> RepeatEnd.Never
            }
            return RepeatSpec(frequency, interval, byDays, end)
        }
    }
}

fun DayOfWeek.toRRuleDay(): String = when (this) {
    DayOfWeek.MONDAY -> "MO"
    DayOfWeek.TUESDAY -> "TU"
    DayOfWeek.WEDNESDAY -> "WE"
    DayOfWeek.THURSDAY -> "TH"
    DayOfWeek.FRIDAY -> "FR"
    DayOfWeek.SATURDAY -> "SA"
    DayOfWeek.SUNDAY -> "SU"
}

fun DayOfWeek.shortCn(): String = when (this) {
    DayOfWeek.MONDAY -> "周一"
    DayOfWeek.TUESDAY -> "周二"
    DayOfWeek.WEDNESDAY -> "周三"
    DayOfWeek.THURSDAY -> "周四"
    DayOfWeek.FRIDAY -> "周五"
    DayOfWeek.SATURDAY -> "周六"
    DayOfWeek.SUNDAY -> "周日"
}

private fun String.toDayOfWeekOrNull(): DayOfWeek? = when (trim().takeLast(2).uppercase()) {
    "MO" -> DayOfWeek.MONDAY
    "TU" -> DayOfWeek.TUESDAY
    "WE" -> DayOfWeek.WEDNESDAY
    "TH" -> DayOfWeek.THURSDAY
    "FR" -> DayOfWeek.FRIDAY
    "SA" -> DayOfWeek.SATURDAY
    "SU" -> DayOfWeek.SUNDAY
    else -> null
}
