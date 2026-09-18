package com.antgskds.calendarassistant.feature.accounting

import com.antgskds.calendarassistant.feature.accounting.ui.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class AccountingPreviewDataTest {
    private val today = LocalDate.of(2026, 9, 16)

    @Test fun `weekly range crosses month boundaries and starts on Monday`() {
        val range = AccountingPreviewData.range(LocalDate.of(2026, 10, 1), PreviewPeriod.WEEK)
        assertEquals(LocalDate.of(2026, 9, 28), range.start)
        assertEquals(LocalDate.of(2026, 10, 4), range.end)
    }

    @Test fun `daily average uses elapsed days including zero expense days`() {
        val rows = listOf(PreviewBill("1", today, "12:00", "午餐", "餐饮", 4800))
        val month = AccountingPreviewData.range(today, PreviewPeriod.MONTH)
        assertEquals(300L, AccountingPreviewData.dailyAverage(rows, month, today))
        assertEquals(1600L, AccountingPreviewData.dailyAverage(rows, AccountingPreviewData.range(today, PreviewPeriod.WEEK), today))
    }

    @Test fun `net expense can be negative and income is excluded from expense trend`() {
        val rows = listOf(PreviewBill("1", today, "12:00", "午餐", "餐饮", 1000),
            PreviewBill("2", today, "13:00", "报销", "报销", 2000, true))
        val range = AccountingPreviewData.range(today, PreviewPeriod.DAY)
        assertEquals(-1000L, AccountingPreviewData.sum(rows, range) - AccountingPreviewData.sum(rows, range, true))
        assertEquals("−¥10.00", AccountingPreviewData.money(-1000L))
        assertEquals(1000L, AccountingPreviewData.series(rows, range, today).single().second)
    }

    @Test fun `future days are missing values rather than zero expense predictions`() {
        val series = AccountingPreviewData.series(emptyList(), AccountingPreviewData.range(today, PreviewPeriod.WEEK), today)
        assertEquals(0L, series.first().second)
        assertNull(series.last().second)
    }

    @Test fun `real records keep source time zone and exclude pending neutral foreign and deleted entries`() {
        val base = com.antgskds.calendarassistant.feature.accounting.data.AccountingEntry(
            id = "real", amountMinor = 1234L, direction = "EXPENSE", currency = "CNY", merchant = "餐厅",
            category = "餐饮", note = "", occurredAt = java.time.Instant.parse("2026-09-15T17:00:00Z").toEpochMilli(),
            zoneId = "Asia/Shanghai", source = "FILE", channel = "微信支付", transactionId = "tx1",
            status = "CONFIRMED", ruleId = "bill-file-v2", dedupKey = "key1", refundOf = null,
            createdAt = 0L, updatedAt = 0L, deletedAt = null)
        val rows = AccountingPreviewData.bills(listOf(base,
            base.copy(id = "pending", status = "PENDING"),
            base.copy(id = "neutral", direction = "TRANSFER"),
            base.copy(id = "foreign", currency = "USD"),
            base.copy(id = "deleted", deletedAt = 1L)))
        assertEquals(4, rows.size)
        assertTrue(rows.all { it.date == today && it.time == "01:00" })
        assertEquals(1, rows.count { it.counted })
        assertEquals(1234L, AccountingPreviewData.sum(rows, AccountingPreviewData.range(today, PreviewPeriod.DAY)))
    }
}
