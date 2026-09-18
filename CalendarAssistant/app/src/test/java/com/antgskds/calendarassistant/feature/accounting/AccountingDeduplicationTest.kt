package com.antgskds.calendarassistant.feature.accounting

import com.antgskds.calendarassistant.feature.accounting.data.AccountingDraft
import com.antgskds.calendarassistant.feature.accounting.domain.AccountingRecognitionMapper as Mapper
import com.antgskds.calendarassistant.feature.recognition.application.ai.RecognitionJsonParser
import org.junit.Assert.*
import org.junit.Test

class AccountingDeduplicationTest {
    @Test fun countAnywayKeepsTransactionEvidenceAndImageButRemovesPendingNote() {
        val draft = AccountingDraft(amount = "35.00", direction = "EXPENSE", currency = "CNY", merchant = "餐饮",
            occurredAt = "2026-09-18 12:30:00", paymentStatus = "COMPLETED", channel = "微信支付",
            transactionId = "tx", transactionIdType = "PAYMENT", sourceImagePath = "/screenshot.jpg",
            note = "午饭 · ${Mapper.POSSIBLE_DUPLICATE_NOTE}")
        val input = requireNotNull(Mapper.possibleDuplicateInput(draft))
        assertTrue(input.allowPossibleDuplicate)
        assertEquals("午饭", input.note)
        val saved = Mapper.build(draft, input, 1L)
        assertEquals("tx", saved.transactionId)
        assertNotNull(saved.dedupKey)
        assertEquals(draft.sourceImagePath, saved.sourceImagePath)
        assertNull(Mapper.possibleDuplicateInput(draft.copy(occurredAt = "")))
        assertNull(Mapper.possibleDuplicateInput(draft.copy(note = "普通待确认账单")))
        assertNull(Mapper.possibleDuplicateInput(draft.copy(paymentStatus = "REVIEW")))
    }

    private fun entry() = AccountingDraft(amount = "35.00", direction = "EXPENSE", currency = "CNY",
        merchant = "麦当劳", occurredAt = "2026-09-17 12:30:59", zoneId = "Asia/Shanghai", paymentStatus = "COMPLETED",
        channel = "微信支付", transactionId = "payment1", transactionIdType = "PAYMENT").let {
        Mapper.build(it, requireNotNull(Mapper.automaticInput(it, false)), 1L)
    }

    @Test fun actualTimeDifferenceIncludesBothBoundariesAndCrossesMinuteBuckets() {
        val a = entry()
        for (delta in listOf(-120_000L, -1_000L, 1_000L, 60_000L, 120_000L)) {
            assertTrue("delta=$delta", Mapper.possibleDuplicate(a, a.copy(occurredAt = a.occurredAt + delta)))
        }
        for (delta in listOf(-120_001L, 120_001L)) {
            assertFalse(Mapper.possibleDuplicate(a, a.copy(occurredAt = a.occurredAt + delta)))
        }
        assertEquals(Long.MIN_VALUE, Mapper.duplicateTimeRange(Long.MIN_VALUE).first)
        assertEquals(Long.MAX_VALUE, Mapper.duplicateTimeRange(Long.MAX_VALUE).last)
    }

    @Test fun amountCurrencyAndDirectionMustStillMatch() {
        val a = entry()
        for (b in listOf(a.copy(amountMinor = 3501), a.copy(currency = "USD"), a.copy(direction = "INCOME"))) {
            assertFalse(Mapper.possibleDuplicate(a, b))
        }
    }

    @Test fun nameNormalizationAndBranchSuffixesAreOnlySuspectedDuplicates() {
        val a = entry()
        // 跨分钟才能独立验证名称规则；同一分钟已由金额与时间直接拦截。
        val later = a.copy(occurredAt = a.occurredAt + 60_000)
        for (name in listOf(" 麦 当 劳 ", "麦当劳（人民路店）", "人民路麦当劳")) {
            assertTrue(name, Mapper.possibleDuplicate(a, later.copy(merchant = name)))
        }
        assertTrue(Mapper.possibleDuplicate(a.copy(merchant = "ＫＦＣ·人民路店"), later.copy(merchant = "kfc人民路店")))
        assertTrue(Mapper.possibleDuplicate(a.copy(merchant = "星巴克人民路咖啡店"), later.copy(merchant = "星巴客人民路咖啡店")))
        assertFalse(Mapper.possibleDuplicate(a, later.copy(merchant = "肯德基")))
        assertFalse(Mapper.possibleDuplicate(a.copy(merchant = "张三"), later.copy(merchant = "张三丰")))
        assertFalse(Mapper.possibleDuplicate(a.copy(merchant = "未提供交易对方"), later.copy(merchant = "未提供交易对方")))
    }

    @Test fun sameMinuteIncome125DoesNotRequireEmojiAndTextNamesToMatch() {
        val a = entry().copy(amountMinor = 12500, direction = "INCOME", merchant = "*🔨",
            occurredAt = java.time.Instant.parse("2026-09-17T01:35:00Z").toEpochMilli(),
            transactionId = "", transactionIdType = "UNKNOWN", category = "收款")
        for (seconds in listOf(0L, 17L, 59L)) {
            val b = a.copy(merchant = "*锤", category = "转账", occurredAt = a.occurredAt + seconds * 1000)
            assertTrue(Mapper.possibleDuplicate(a, b))
            assertTrue(Mapper.possibleDuplicate(b, a))
        }
        // 时间只是接近时仍需名称证据；同一时刻但不同日期也不能当成重复。
        assertFalse(Mapper.possibleDuplicate(a, a.copy(merchant = "*锤", occurredAt = a.occurredAt + 60_000)))
        assertFalse(Mapper.possibleDuplicate(a, a.copy(merchant = "*锤", occurredAt = a.occurredAt + 86_400_000)))
    }

    @Test fun sameMinuteDifferentNamesStillRespectMoneyDirectionAndComparableIds() {
        val a = entry().copy(merchant = "*🔨")
        val b = a.copy(merchant = "*锤", transactionId = "")
        assertTrue(Mapper.possibleDuplicate(a, b))
        assertTrue(Mapper.possibleDuplicate(a, b.copy(merchant = "完全不同的名称")))
        assertFalse(Mapper.possibleDuplicate(a, b.copy(amountMinor = a.amountMinor + 1)))
        assertFalse(Mapper.possibleDuplicate(a, b.copy(currency = "USD")))
        assertFalse(Mapper.possibleDuplicate(a, b.copy(direction = "INCOME")))
        assertFalse(Mapper.possibleDuplicate(a, b.copy(transactionId = "payment2")))
        assertTrue(Mapper.possibleDuplicate(a, b.copy(transactionId = "merchant2", transactionIdType = "MERCHANT_ORDER")))
        assertTrue(Mapper.possibleDuplicate(a, b.copy(transactionId = "unknown2", transactionIdType = "UNKNOWN")))
    }

    @Test fun onlyComparableCompleteIdsCanProveDifferentTransactions() {
        val a = entry()
        assertFalse(Mapper.possibleDuplicate(a, a.copy(transactionId = "payment2", channel = "微信")))
        for (b in listOf(a.copy(transactionId = "merchant1", transactionIdType = "MERCHANT_ORDER"),
            a.copy(transactionId = "unknown1", transactionIdType = "UNKNOWN"),
            a.copy(transactionId = "payment2", channel = ""), a.copy(transactionId = "pay***2"),
            a.copy(transactionId = ""))) {
            assertTrue(Mapper.possibleDuplicate(a, b))
        }
        assertTrue(Mapper.possibleDuplicate(a.copy(channel = ""), a.copy(channel = "", transactionId = "payment2")))
    }

    @Test fun exactIdentitySeparatesNumberTypesAndMerchantOrderScopes() {
        val payment = Mapper.transactionKey("微信支付", "same-number", "EXPENSE", "PAYMENT")
        val order = Mapper.transactionKey("微信支付", "same-number", "EXPENSE", "MERCHANT_ORDER", "麦当劳")
        assertNotNull(payment); assertNotNull(order); assertNotEquals(payment, order)
        assertNotEquals(order, Mapper.transactionKey("微信支付", "same-number", "EXPENSE", "MERCHANT_ORDER", "肯德基"))
        assertNull(Mapper.transactionKey("微信支付", "same-number", "EXPENSE", "UNKNOWN"))
        assertNull(Mapper.transactionKey("微信支付", "pay***", "EXPENSE", "PAYMENT"))
    }

    @Test fun parserPreservesExplicitTypesAndNeverGuessesMissingType() {
        for (type in listOf("PAYMENT", "MERCHANT_ORDER", "UNKNOWN", "unexpected", "")) {
            val bill = RecognitionJsonParser.parseContent("""{"bills":[{"merchant":"午饭","transactionId":"123",
                "transactionIdType":"$type"}]}""").bills.single()
            assertEquals(if (type in setOf("PAYMENT", "MERCHANT_ORDER")) type else "UNKNOWN", bill.transactionIdType)
        }
        val legacy = RecognitionJsonParser.parseContent("""{"bills":[{"merchant":"午饭","transactionId":"123"}]}""").bills.single()
        assertEquals("UNKNOWN", legacy.transactionIdType)
    }
}
