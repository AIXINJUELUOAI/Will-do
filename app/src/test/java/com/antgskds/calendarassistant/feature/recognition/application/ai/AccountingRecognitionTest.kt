package com.antgskds.calendarassistant.feature.recognition.application.ai

import com.antgskds.calendarassistant.feature.accounting.data.AccountingDraft
import com.antgskds.calendarassistant.feature.accounting.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class AccountingRecognitionTest {
    @Test fun skippedPaymentReasonIsNotReplacedWithNoScheduleMessage() {
        val failure = com.antgskds.calendarassistant.shared.event.events.RecognitionFailedEvent(
            "image", "test", "EMPTY_RESULT", false, "第 1 条交易尚未完成，未生成账单")
        assertEquals(failure.message, RecognitionFailureMessageMapper.userMessage(failure))
    }

    private val bill = """{"amount":"35.20","direction":"EXPENSE","currency":"CNY","merchant":"午餐店","occurredAt":"2026-09-17 12:30:15","paymentStatus":"COMPLETED","channel":"微信支付","transactionId":"tx1"}"""
    private val event = """{"title":"开会","startTime":"2026-09-18 15:00","endTime":"2026-09-18 16:00","tag":"general"}"""

    @Test fun pureBillDoesNotCreatePhantomEvent() {
        val result = RecognitionJsonParser.parseContent("""{"bills":[$bill]}""")
        assertTrue(result.events.isEmpty())
        assertEquals("35.20", result.bills.single().amount)
    }

    @Test fun mixedAndMultipleRecordsAreParsedIndependently() {
        val result = RecognitionJsonParser.parseContent("""{"events":[$event,{"title":"取件","tag":"pickup"}],"bills":[$bill,null,$bill]}""")
        assertEquals(listOf("general", "pickup"), result.events.map { it.tag })
        assertEquals(2, result.bills.size)
        assertEquals(1, result.issues.size)
    }

    @Test fun oldResponsesAndMissingBillArrayRemainCompatible() {
        for (text in listOf("""{"events":[$event]}""", "[$event]", event)) {
            val result = RecognitionJsonParser.parseContent(text)
            assertEquals("开会", result.events.single().title)
            assertTrue(result.bills.isEmpty())
        }
    }

    @Test fun unpaidAndPendingRefundsAreNotStaged() {
        for (status in listOf("UNPAID", "FAILED", "CANCELLED", "REFUND_PENDING")) {
            val result = RecognitionJsonParser.parseContent("""{"events":[$event],"bills":[${bill.replace("COMPLETED", status)}]}""")
            assertEquals(1, result.events.size)
            assertTrue(result.bills.isEmpty())
            assertEquals(1, result.issues.size)
        }
    }

    @Test fun uncertainFieldsStayEmptyForUserCorrection() {
        val result = RecognitionJsonParser.parseContent("""{"bills":[{"merchant":"商店","amount":null,"direction":"unknown"}]}""")
        with(result.bills.single()) {
            assertEquals("", amount); assertEquals("", direction); assertEquals("", occurredAt)
            assertEquals("", currency); assertEquals("REVIEW", paymentStatus)
        }
    }

    @Test fun malformedBillArrayDoesNotLoseValidEvents() {
        val result = RecognitionJsonParser.parseContent("""{"events":[$event],"bills":{}}""")
        assertEquals(1, result.events.size)
        assertEquals(1, result.issues.size)
    }

    private fun input() = AccountingEntryInput(amount = "35.20", direction = "EXPENSE", merchant = "午餐店", category = "餐饮", note = "",
        date = LocalDate.of(2026, 9, 17), time = LocalTime.of(12, 30, 15), channel = "微信支付", transactionId = "tx1", paymentConfirmed = true)

    @Test fun confirmationUsesExactMoneyAndSameTransactionIdentityAsFileImport() {
        val saved = AccountingRecognitionMapper.build(AccountingDraft(id = "draft1", zoneId = "Asia/Shanghai"), input(), 1L)
        val imported = AccountingFileParser(4096, 4096, 100, 10).read(
            ("交易时间,交易对方,收/支,金额(元),当前状态,交易单号\n" +
                "2026-09-17 12:30:15,午餐店,支出,35.20,支付成功,tx1").byteInputStream(), BillFileSource.WECHAT).entries.single()
        assertEquals(3520L, saved.amountMinor)
        assertEquals(imported.dedupKey, saved.dedupKey)
        assertEquals(imported.occurredAt, saved.occurredAt)
        assertEquals("draft1", saved.id)
        assertEquals("RECOGNITION", saved.source)
    }

    @Test fun incompleteOrInvalidMoneyCannotBeConfirmed() {
        for (amount in listOf("", "-1", "0", "1.234", "92233720368547758.08")) {
            assertThrows(IllegalArgumentException::class.java) {
                AccountingRecognitionMapper.build(AccountingDraft(), input().copy(amount = amount), 1L)
            }
        }
        for (data in listOf(input().copy(paymentConfirmed = false), input().copy(currency = ""), input().copy(direction = ""))) {
            assertThrows(IllegalArgumentException::class.java) { AccountingRecognitionMapper.build(AccountingDraft(), data, 1L) }
        }
    }

    @Test fun refundsAndForeignCurrencyAreHandledConservatively() {
        val refund = AccountingDraft(paymentStatus = "REFUNDED")
        assertThrows(IllegalArgumentException::class.java) { AccountingRecognitionMapper.build(refund, input(), 1L) }
        val saved = AccountingRecognitionMapper.build(refund, input().copy(direction = "INCOME", currency = "USD"), 1L)
        assertEquals("PENDING", saved.status)
        assertNull(saved.refundOf)
    }

    @Test fun similarKnownDifferentTransactionsAreNotMerged() {
        val saved = AccountingRecognitionMapper.build(AccountingDraft(), input(), 1L)
        assertFalse(AccountingRecognitionMapper.possibleDuplicate(saved, saved.copy(transactionId = "tx2")))
        assertTrue(AccountingRecognitionMapper.possibleDuplicate(saved, saved.copy(transactionId = "", occurredAt = saved.occurredAt + 1_000)))
        assertTrue(AccountingRecognitionMapper.possibleDuplicate(saved, saved.copy(occurredAt = saved.occurredAt + 60_000)))
        assertFalse(AccountingRecognitionMapper.possibleDuplicate(saved, saved.copy(occurredAt = saved.occurredAt + 120_001)))
    }
}
