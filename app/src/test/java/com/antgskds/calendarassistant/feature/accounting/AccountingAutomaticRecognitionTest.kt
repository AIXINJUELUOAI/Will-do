package com.antgskds.calendarassistant.feature.accounting

import com.antgskds.calendarassistant.feature.accounting.data.AccountingDraft
import com.antgskds.calendarassistant.feature.accounting.domain.*
import com.antgskds.calendarassistant.shared.management.resource.notification.display.live.template.AccountingRecognitionDisplay
import com.antgskds.calendarassistant.shared.management.resource.notification.display.live.vendor.xiaomi.XiaomiLiveNotificationTemplate
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class AccountingAutomaticRecognitionTest {
    private fun draft() = AccountingDraft(amount = "35.00", direction = "EXPENSE", currency = "CNY", merchant = "午饭",
        paymentStatus = "COMPLETED", zoneId = "Asia/Shanghai", createdAt = Instant.parse("2026-09-17T04:30:15Z").toEpochMilli())

    private fun entry(amount: String, direction: String = "EXPENSE") = draft().let { draft ->
        AccountingRecognitionMapper.build(draft, requireNotNull(AccountingRecognitionMapper.automaticInput(draft, true))
            .copy(amount = amount, direction = direction), draft.createdAt)
    }

    @Test fun transactionIdentityNormalizesChannelAliasesBeforeValidation() {
        assertEquals(AccountingRecognitionMapper.transactionKey("微信支付", "tx1", "EXPENSE"),
            AccountingRecognitionMapper.transactionKey(" 微信 ", " tx1 ", "EXPENSE"))
        assertEquals(AccountingRecognitionMapper.transactionKey("支付宝", "tx1", "EXPENSE"),
            AccountingRecognitionMapper.transactionKey("Alipay", "tx1", "EXPENSE"))
        assertNull(AccountingRecognitionMapper.transactionKey("微信", " / ", "EXPENSE"))
    }

    @Test fun missingTextTimeUsesRecognitionTimeButImageWaitsForReview() {
        val draft = draft()
        val input = requireNotNull(AccountingRecognitionMapper.automaticInput(draft, true))
        assertEquals(draft.createdAt, AccountingRecognitionMapper.build(draft, input, draft.createdAt).occurredAt)
        assertNull(AccountingRecognitionMapper.automaticInput(draft, false))
    }

    @Test fun explicitTimeIsPreservedAndInvalidTimeIsNotReplacedWithNow() {
        val input = requireNotNull(AccountingRecognitionMapper.automaticInput(draft().copy(occurredAt = "2026-09-16 13:10:00"), true))
        assertEquals("2026-09-16", input.date.toString())
        assertEquals("13:10", input.time.toString())
        assertNull(AccountingRecognitionMapper.automaticInput(draft().copy(occurredAt = "昨天不确定"), true))
    }

    @Test fun uncertainPaymentsAndForeignCurrencyDoNotAutomaticallyEnterLedger() {
        for (status in listOf("REVIEW", "UNPAID", "CANCELLED", "REFUND_PENDING", "FAILED")) {
            assertNull(AccountingRecognitionMapper.automaticInput(draft().copy(paymentStatus = status), true))
        }
        assertNull(AccountingRecognitionMapper.automaticInput(draft().copy(currency = "USD"), true))
    }

    @Test fun compactAmountAndExpandedTotalsOnlyIncludeSavedRecords() {
        val result = AccountingRecognitionResult(saved = listOf(entry("35.00"), entry("100.00", "INCOME")),
            duplicates = 1, suspectedDuplicates = 1, pending = 1)
        val display = AccountingRecognitionDisplay.create(result)
        assertEquals("¥135.00", display.shortText)
        assertEquals("已记 2 笔", display.primaryText)
        assertTrue(display.expandedText!!.contains("支出 ¥35.00 · 收入 ¥100.00"))
        assertTrue(display.expandedText.contains("重复 1 笔，暂不入库"))
        assertTrue(display.expandedText.contains("疑似重复 1 笔，暂不入库"))
        assertTrue(display.expandedText.contains("1 笔待核对"))
        val island = XiaomiLiveNotificationTemplate.create(display, true, false, true, "已完成", 1L, 2L)
        assertEquals("", island.summaryStatus)
        assertEquals("¥135.00", island.summaryTitle)
        assertEquals(display.expandedText, island.content)
        assertEquals("已记 2 笔", island.title)
    }

    @Test fun allDuplicatesDisplayReasonInsteadOfZeroMoneyOrSavedMessage() {
        val display = AccountingRecognitionDisplay.create(AccountingRecognitionResult(duplicates = 3))
        assertEquals("账单重复", display.shortText)
        assertEquals("重复 3 笔，暂不入库", display.expandedText)
        assertFalse(display.primaryText.contains("已记"))
        assertEquals("疑似重复", AccountingRecognitionDisplay.create(AccountingRecognitionResult(suspectedDuplicates = 1)).shortText)
    }

    @Test fun transfersAreSeparateAndLargeSumsAreNotTruncatedOrOverflowed() {
        val result = AccountingRecognitionResult(saved = listOf(entry("35.00"), entry("100.00", "TRANSFER")))
        val display = AccountingRecognitionDisplay.create(result)
        assertEquals("¥35.00", display.shortText)
        assertTrue(display.expandedText!!.contains("不计收支 ¥100.00"))
        val huge = entry("92233720368547758.07")
        assertEquals("¥184467440737095516.14", AccountingRecognitionDisplay.create(AccountingRecognitionResult(saved = listOf(huge, huge))).shortText)
    }
}
