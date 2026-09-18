package com.antgskds.calendarassistant.feature.accounting

import com.antgskds.calendarassistant.feature.accounting.ui.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class AccountingSuggestionMapperTest {
    private val today = LocalDate.of(2026, 9, 16)
    private fun bill(id: String, category: String = "其他", cents: Long = 3000, date: LocalDate = today,
        income: Boolean = false, name: String = "店铺", note: String = "") =
        PreviewBill(id, date, "12:00", name, category, cents, income, note = note)
    private fun suggestions(rows: List<PreviewBill>, period: PreviewPeriod = PreviewPeriod.WEEK) =
        AccountingSuggestionMapper.suggestions(rows, today, period, today)
    private fun ids(rows: List<PreviewBill>) = suggestions(rows).map { it.id }.toSet()

    @Test fun categoriesFrequencyAndLargeExpenseRestoreAllRelevantTemplates() {
        assertTrue("shopping_share" in ids((1..3).map { bill("$it", "购物", 10000) }))
        val dining = (1..5).map { bill("$it", "餐饮", 1500, note = "外卖订单") }
        assertTrue(ids(dining).containsAll(listOf("dining_share", "delivery", "small")))
        assertTrue("large" in ids(listOf(bill("large", cents = 50000))))
    }

    @Test fun realNamesCountsAndMoneyReplaceDemonstrationValues() {
        val rows = (1..3).map { bill("out$it", "转账", 2400, name = "张三") } +
            (1..3).map { bill("in$it", "转账", 1250, income = true, name = "李四") } +
            bill("reimbursement", "报销", 12345, income = true)
        val result = suggestions(rows).associateBy { it.id }
        assertTrue(result.getValue("transfer_out").text.contains("张三」转账较多，共 3 笔、¥72.00"))
        assertTrue(result.getValue("transfer_in").text.contains("李四」3 笔转账，共 ¥37.50"))
        assertTrue(result.getValue("reimbursement").text.contains("¥123.45"))
        assertFalse(result.values.any { "小林" in it.text })
    }

    @Test fun sameMerchantIsNotAutomaticallyConsideredATransfer() {
        val rows = (1..5).map { bill("$it", "购物", name = "超市") }
        assertFalse(ids(rows).any { it.startsWith("transfer_") })
        assertFalse("delivery" in ids(rows.map { it.copy(title = "美团单车") }))
    }

    @Test fun unrelatedLargeTransferDoesNotHideFrequentCounterparty() {
        val rows = (1..3).map { bill("$it", "转账", 100, name = "朋友") } +
            bill("single", "转账", 100000, name = "另一个人")
        assertTrue(suggestions(rows).first { it.id == "transfer_out" }.text.contains("朋友"))
    }

    @Test fun comparisonUsesSameElapsedDaysAndRequiresRecordsOnBothSides() {
        val current = (1..3).map { bill("now$it", "购物", 3000) }
        val prior = (1..3).map { bill("old$it", "购物", 1000, today.minusWeeks(1)) }
        val laterInPriorWeek = bill("not-comparable", "购物", 900000, today.minusDays(3))
        assertTrue("shopping_increase" in ids(current + prior + laterInPriorWeek))
        assertFalse("shopping_increase" in ids(current))
        assertTrue("decrease" in ids(current.map { it.copy(cents = 500) } + prior))
        assertTrue("stable" in ids(current.map { it.copy(cents = 1000) } + prior))
    }

    @Test fun pendingForeignNeutralAndFutureRecordsDoNotDriveSuggestions() {
        val rows = listOf(bill("pending", cents = 100000).copy(status = "PENDING"),
            bill("foreign", cents = 100000).copy(currency = "USD"),
            bill("neutral", cents = 100000).copy(direction = "TRANSFER"),
            bill("future", cents = 100000, date = today.plusDays(1)))
        assertEquals(setOf("summary"), ids(rows))
        assertEquals(setOf("empty"), ids(emptyList()))
        assertEquals("empty", suggestions(listOf(rows.last()), PreviewPeriod.DAY).single().id)
    }

    @Test fun shortPreviousMonthDoesNotCompareAgainstExtraDaysInCurrentMonth() {
        val end = LocalDate.of(2026, 3, 31)
        val rows = (1..3).map { bill("now$it", date = end.withDayOfMonth(it)) } +
            (1..3).map { bill("old$it", date = LocalDate.of(2026, 2, it)) } +
            bill("extra-day", cents = 900000, date = end)
        assertTrue(AccountingSuggestionMapper.suggestions(rows, end, PreviewPeriod.MONTH, end).any { it.id == "stable" })
    }

    @Test fun totalsLargerThanLongDoNotOverflowAndOutputIsDeterministic() {
        val rows = (1..3).map { bill("$it", "转账", Long.MAX_VALUE, name = "测试对方") }
        val result = suggestions(rows)
        assertTrue(result.first { it.id == "transfer_out" }.text.contains("¥276701161105643274.21"))
        assertEquals(result, suggestions(rows.reversed()))
    }
}
