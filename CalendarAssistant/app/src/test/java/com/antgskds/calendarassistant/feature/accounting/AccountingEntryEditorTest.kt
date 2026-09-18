package com.antgskds.calendarassistant.feature.accounting

import com.antgskds.calendarassistant.feature.accounting.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class AccountingEntryEditorTest {
    private fun input() = AccountingEntryInput(amount = "12.34", direction = "EXPENSE", merchant = " 午餐 ",
        category = "餐饮", note = "", date = LocalDate.of(2026, 9, 16), time = LocalTime.of(12, 30))

    @Test fun `amounts stay exact and invalid amounts cannot be saved`() {
        assertEquals(29L, AccountingEntryEditor.amountMinor("0.29"))
        assertEquals(1234L, AccountingEntryEditor.amountMinor(" 12.34 "))
        for (value in listOf("", "-1", "0", "0.00", "1.234", "NaN", "1e3", "92233720368547758.08")) {
            assertThrows(IllegalArgumentException::class.java) { AccountingEntryEditor.amountMinor(value) }
        }
    }

    @Test fun `manual entries have independent ids and no import dedup key`() {
        val first = AccountingEntryEditor.build(input(), null, 100L)
        val second = AccountingEntryEditor.build(input(), null, 101L)
        assertNotEquals(first.id, second.id)
        assertNull(first.dedupKey)
        assertEquals("MANUAL", first.source)
        assertEquals("手动记账", first.channel)
        assertEquals("CONFIRMED", first.status)
        assertEquals("午餐", first.merchant)
        assertEquals(1234L, first.amountMinor)
    }

    @Test fun `editing imported entry preserves provenance dedup currency and pending status`() {
        val original = AccountingFileParser(4096, 4096, 100, 10).read(
            ("交易时间,交易对方,收/支,金额(元),当前状态,交易单号\n" +
                "2026-09-16 12:30:15,商店,支出,35.20,已部分退款,tx1").byteInputStream(), BillFileSource.WECHAT).entries.single()
        val edited = AccountingEntryEditor.build(input().copy(id = original.id, amount = "20.01", direction = "INCOME",
            note = "补充备注", time = LocalTime.of(12, 30, 15)), original.copy(currency = "USD"), 123L)
        assertEquals(original.id, edited.id)
        assertEquals(original.dedupKey, edited.dedupKey)
        assertEquals(original.transactionId, edited.transactionId)
        assertEquals(original.source, edited.source)
        assertEquals(original.channel, edited.channel)
        assertEquals(original.createdAt, edited.createdAt)
        assertEquals("USD", edited.currency)
        assertEquals("PENDING", edited.status)
        assertEquals("manual-edit", edited.ruleId)
        assertEquals(2001L, edited.amountMinor)
        assertEquals("补充备注", edited.note)
        assertEquals(Instant.parse("2026-09-16T04:30:15Z").toEpochMilli(), edited.occurredAt)
    }

    @Test fun `editing missing or deleted entries never recreates them`() {
        assertThrows(IllegalArgumentException::class.java) {
            AccountingEntryEditor.build(input().copy(id = "missing"), null, 1L)
        }
        val deleted = AccountingEntryEditor.build(input(), null, 1L).copy(deletedAt = 2L)
        assertThrows(IllegalArgumentException::class.java) {
            AccountingEntryEditor.build(input().copy(id = deleted.id), deleted, 3L)
        }
    }

    @Test fun `blank title and unknown direction are rejected while blank category gets fallback`() {
        assertThrows(IllegalArgumentException::class.java) {
            AccountingEntryEditor.build(input().copy(merchant = "  "), null, 1L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AccountingEntryEditor.build(input().copy(direction = "OTHER"), null, 1L)
        }
        assertEquals("未分类", AccountingEntryEditor.build(input().copy(category = ""), null, 1L).category)
    }
}
