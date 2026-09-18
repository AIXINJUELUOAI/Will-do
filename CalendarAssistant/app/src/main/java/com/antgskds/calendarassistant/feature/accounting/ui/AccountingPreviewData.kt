package com.antgskds.calendarassistant.feature.accounting.ui

import com.antgskds.calendarassistant.feature.accounting.data.AccountingEntry
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** 真实账单的只读展示模型。金额单位为分，不参与持久化。 */
internal data class PreviewBill(
    val id: String, val date: LocalDate, val time: String, val title: String,
    val category: String, val cents: Long, val income: Boolean = false,
    val counted: Boolean = true, val direction: String = if (income) "INCOME" else "EXPENSE",
    val status: String = "CONFIRMED", val currency: String = "CNY",
    val channel: String = "", val note: String = "", val transactionId: String = "",
    val sourceImagePath: String? = null,
)

internal enum class PreviewPeriod(val label: String) { DAY("日"), WEEK("周"), MONTH("月") }

internal data class PreviewRange(val start: LocalDate, val end: LocalDate) {
    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)
    val dates: List<LocalDate> get() = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()
}

internal object AccountingPreviewData {
    fun bills(entries: List<AccountingEntry>): List<PreviewBill> = entries.filter { it.deletedAt == null }.map { entry ->
        val zone = runCatching { ZoneId.of(entry.zoneId) }.getOrDefault(ZoneId.of("Asia/Shanghai"))
        val date = Instant.ofEpochMilli(entry.occurredAt).atZone(zone)
        PreviewBill(entry.id, date.toLocalDate(), date.format(DateTimeFormatter.ofPattern("HH:mm")),
            entry.merchant.ifBlank { entry.note.ifBlank { "账单" } }, entry.category.ifBlank { "未分类" }, entry.amountMinor,
            income = entry.direction == "INCOME",
            counted = entry.status == "CONFIRMED" && entry.currency == "CNY" && entry.direction in setOf("EXPENSE", "INCOME"),
            direction = entry.direction, status = entry.status, currency = entry.currency,
            channel = entry.channel, note = entry.note, transactionId = entry.transactionId, sourceImagePath = entry.sourceImagePath)
    }

    fun billTitle(date: LocalDate, period: PreviewPeriod, today: LocalDate): String {
        val range = range(date, period)
        if (today in range) return when (period) {
            PreviewPeriod.DAY -> "今日账单"
            PreviewPeriod.WEEK -> "本周账单"
            PreviewPeriod.MONTH -> "本月账单"
        }
        fun dayLabel(value: LocalDate): String =
            (if (value.year == today.year) "" else "${value.year}年") + "${value.monthValue}月${value.dayOfMonth}日"
        return when (period) {
            PreviewPeriod.DAY -> "${dayLabel(date)}账单"
            PreviewPeriod.WEEK -> {
                val endLabel = if (range.start.year == range.end.year && range.start.month == range.end.month)
                    "${range.end.dayOfMonth}日" else dayLabel(range.end)
                "${dayLabel(range.start)}—${endLabel}账单"
            }
            PreviewPeriod.MONTH -> (if (date.year == today.year) "" else "${date.year}年") + "${date.monthValue}月账单"
        }
    }

    fun range(date: LocalDate, period: PreviewPeriod): PreviewRange = when (period) {
        PreviewPeriod.DAY -> PreviewRange(date, date)
        PreviewPeriod.WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).let {
            PreviewRange(it, it.plusDays(6))
        }
        PreviewPeriod.MONTH -> PreviewRange(date.withDayOfMonth(1), date.withDayOfMonth(date.lengthOfMonth()))
    }

    fun shift(date: LocalDate, period: PreviewPeriod, step: Long): LocalDate = when (period) {
        PreviewPeriod.DAY -> date.plusDays(step)
        PreviewPeriod.WEEK -> date.plusWeeks(step)
        PreviewPeriod.MONTH -> date.plusMonths(step)
    }

    fun sum(bills: List<PreviewBill>, range: PreviewRange, income: Boolean = false): Long =
        bills.filter { it.date in range && it.income == income && it.counted }.sumOf { it.cents }

    fun dailyAverage(bills: List<PreviewBill>, range: PreviewRange, today: LocalDate): Long {
        val elapsed = range.dates.count { !it.isAfter(today) }
        return if (elapsed == 0) 0 else sum(bills, range) / elapsed
    }

    fun series(bills: List<PreviewBill>, range: PreviewRange, today: LocalDate): List<Pair<LocalDate, Long?>> =
        range.dates.map { date -> date to if (date.isAfter(today)) null else sum(bills, PreviewRange(date, date)) }

    fun money(cents: Long): String =
        (if (cents < 0) "−¥" else "¥") + String.format(Locale.CHINA, "%,.2f", BigDecimal.valueOf(cents, 2).abs())

    fun dateLabel(range: PreviewRange, period: PreviewPeriod): String = when (period) {
        PreviewPeriod.DAY -> "${range.start.year}年${range.start.monthValue}月${range.start.dayOfMonth}日"
        PreviewPeriod.WEEK -> "${range.start.year}年 ${range.start.monthValue}/${range.start.dayOfMonth} — ${range.end.monthValue}/${range.end.dayOfMonth}"
        PreviewPeriod.MONTH -> "${range.start.year}年${range.start.monthValue}月"
    }
}
