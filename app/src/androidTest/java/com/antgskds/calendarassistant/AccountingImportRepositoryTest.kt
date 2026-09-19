package com.antgskds.calendarassistant

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.antgskds.calendarassistant.feature.accounting.application.AccountingRepository
import com.antgskds.calendarassistant.feature.accounting.domain.AccountingEntryInput
import com.antgskds.calendarassistant.feature.accounting.domain.AccountingFileParser
import com.antgskds.calendarassistant.feature.accounting.domain.BillFileSource
import com.antgskds.calendarassistant.feature.accounting.data.AccountingDraft
import com.antgskds.calendarassistant.feature.schedule.data.db.EventsDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 使用独立内存数据库，不触碰测试机上的用户账单。 */
@RunWith(AndroidJUnit4::class)
class AccountingImportRepositoryTest {
    private lateinit var database: EventsDatabase
    private lateinit var repository: AccountingRepository

    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
            EventsDatabase::class.java).build()
        repository = AccountingRepository(database)
    }

    @After fun close() { database.close() }

    @Test fun restoringBackupKeepsManualIdentitySkipsRepeatsAndDoesNotReviveDeletedBills() = runBlocking {
        val original = com.antgskds.calendarassistant.feature.accounting.domain.AccountingEntryEditor.build(recognizedInput(), null, 1L)
        val second = original.copy(id = "separate-confirmed-bill")
        assertEquals(2, repository.restoreEntries(listOf(original, second)).inserted)
        assertEquals(2, repository.restoreEntries(listOf(original, second)).duplicates)
        repository.deleteEntry(original.id)
        assertEquals(2, repository.restoreEntries(listOf(original, second)).duplicates)
        assertEquals(listOf(second.id), repository.entries.first().map { it.id })
    }

    @Test fun countAnywaySavesSuspectedDuplicateOnceAndRetainsItsScreenshot() = runBlocking {
        val a = automaticDraft("original", "").copy(transactionIdType = "UNKNOWN")
        val b = a.copy(id = "suspected", sourceImagePath = "/accounting_images/b.jpg")
        repository.ingestRecognizedBills(listOf(a, b), false)
        val draft = repository.drafts.first().single()
        val input = requireNotNull(com.antgskds.calendarassistant.feature.accounting.domain.AccountingRecognitionMapper.possibleDuplicateInput(draft))
        val confirmed = repository.confirmDraft(draft.id, input)
        assertFalse(confirmed.duplicate)
        assertEquals(b.sourceImagePath, confirmed.entry?.sourceImagePath)
        assertTrue(repository.confirmDraft(draft.id, input).duplicate)
        assertEquals(2, repository.entries.first().size)
        assertTrue(repository.drafts.first().isEmpty())
    }

    private fun entry() = AccountingFileParser(4096, 4096, 100, 10).read(
        ("交易时间,交易对方,收/支,金额(元),当前状态,交易单号\n" +
            "2026-09-16 12:00:00,午餐店,支出,35.20,支付成功,tx1").byteInputStream(),
        BillFileSource.WECHAT).entries.single()

    private fun recognizedInput() = AccountingEntryInput(amount = "35.20", direction = "EXPENSE", merchant = "午餐店",
        category = "餐饮", note = "", date = java.time.LocalDate.of(2026, 9, 16), time = java.time.LocalTime.NOON,
        channel = "微信支付", transactionId = "tx1", paymentConfirmed = true)

    private fun automaticDraft(id: String, transaction: String = "tx1") = AccountingDraft(id = id, amount = "35.20",
        direction = "EXPENSE", currency = "CNY", merchant = "午餐店", zoneId = "Asia/Shanghai",
        occurredAt = "2026-09-16 12:00:00", paymentStatus = "COMPLETED", channel = "微信支付", transactionId = transaction,
        transactionIdType = "PAYMENT")

    @Test fun crossPageDifferencesWaitForReviewButKnownDifferentPaymentIsSaved() = runBlocking {
        repository.ingestRecognizedBills(listOf(automaticDraft("original").copy(merchant = "麦当劳")), true)
        val result = repository.ingestRecognizedBills(listOf(
            automaticDraft("suspected", "merchant-order").copy(merchant = "麦当劳（人民路店）",
                occurredAt = "2026-09-16 12:01:59", transactionIdType = "MERCHANT_ORDER"),
            automaticDraft("real-second", "tx2").copy(merchant = "麦当劳（人民路店）", occurredAt = "2026-09-16 12:01:59")
        ), true)
        assertEquals(1, result.suspectedDuplicates)
        assertEquals(listOf("real-second"), result.saved.map { it.id })
        assertEquals("suspected", repository.drafts.first().single().id)
        assertEquals(2, repository.entries.first().size)
    }

    @Test fun income125WithEmojiAndTextNamesOnlySavesOneEntry() = runBlocking {
        val original = automaticDraft("income-original", "").copy(amount = "125.00", direction = "INCOME",
            merchant = "*🔨", category = "收款", occurredAt = "2026-09-17 09:35:00", transactionIdType = "UNKNOWN")
        val secondPage = original.copy(id = "income-second-page", merchant = "*锤", category = "转账",
            occurredAt = "2026-09-17 09:35:17")
        val result = repository.ingestRecognizedBills(listOf(original, secondPage), false)
        assertEquals(listOf(original.id), result.saved.map { it.id })
        assertEquals(1, result.suspectedDuplicates)
        assertEquals(1, repository.entries.first().size)
        assertEquals(12500L, repository.entries.first().single().amountMinor)
        assertEquals(secondPage.id, repository.drafts.first().single().id)
        val separateRequest = repository.ingestRecognizedBills(listOf(secondPage.copy(id = "another-request")), false)
        assertTrue(separateRequest.saved.isEmpty())
        assertEquals(1, separateRequest.suspectedDuplicates)
        assertEquals(1, repository.entries.first().size)
    }

    @Test fun sameMinuteDifferentNamesAndDifferentPaymentIdsAreTwoEntries() = runBlocking {
        val first = automaticDraft("one", "payment-one").copy(merchant = "*🔨")
        val second = first.copy(id = "two", merchant = "*锤", transactionId = "payment-two")
        val result = repository.ingestRecognizedBills(listOf(first, second), false)
        assertEquals(2, result.saved.size)
        assertEquals(0, result.suspectedDuplicates)
        assertTrue(repository.drafts.first().isEmpty())
    }

    @Test fun samePaymentIdOverridesAmountTimeAndNameDifferences() = runBlocking {
        repository.ingestRecognizedBills(listOf(automaticDraft("original")), true)
        val result = repository.ingestRecognizedBills(listOf(automaticDraft("different-page")
            .copy(amount = "99.00", merchant = "完全不同名称", occurredAt = "2026-09-17 15:00:00")), false)
        assertEquals(1, result.duplicates)
        assertTrue(result.saved.isEmpty())
        assertEquals(1, repository.entries.first().size)
    }

    @Test fun legacyUnknownIdDoesNotRuleOutCrossMinuteDuplicate() = runBlocking {
        val old = entry().copy(transactionIdType = "UNKNOWN")
        repository.importEntries(listOf(old))
        val result = repository.ingestRecognizedBills(listOf(automaticDraft("new-page", "another-kind-of-id")
            .copy(merchant = "午餐店（人民路店）", occurredAt = "2026-09-16 12:01:00")), true)
        assertEquals(1, result.suspectedDuplicates)
        assertEquals(1, repository.entries.first().size)
    }

    @Test fun confirmationAndFileImportShareCrossMinuteSimilarityCheck() = runBlocking {
        repository.ingestRecognizedBills(listOf(automaticDraft("original", "").copy(merchant = "麦当劳")), true)
        val candidate = automaticDraft("review", "").copy(merchant = "麦当劳人民路店", occurredAt = "2026-09-16 12:01:00")
        repository.stageDrafts(listOf(candidate))
        try {
            repository.confirmDraft(candidate.id, recognizedInput().copy(transactionId = "", merchant = candidate.merchant,
                time = java.time.LocalTime.of(12, 1)))
            fail("cross-page candidate must require review")
        } catch (_: IllegalArgumentException) { assertEquals(1, repository.entries.first().size) }
        repository.importEntries(listOf(entry().copy(merchant = candidate.merchant, occurredAt = entry().occurredAt + 60_000)))
        assertEquals("PENDING", repository.entries.first().first { it.source == "FILE" }.status)
    }

    @Test fun automaticBatchCommitsValidRecordsAndSkipsKnownDuplicates() = runBlocking {
        repository.importEntries(listOf(entry()))
        val result = repository.ingestRecognizedBills(listOf(automaticDraft("duplicate"), automaticDraft("new", "tx2")), true)
        assertEquals(1, result.saved.size)
        assertEquals(1, result.duplicates)
        assertEquals(0, result.pending)
        assertTrue(repository.drafts.first().isEmpty())
        assertEquals(2, repository.entries.first().size)
        val retry = repository.ingestRecognizedBills(listOf(automaticDraft("new", "tx2")), true)
        assertTrue(retry.saved.isEmpty()); assertEquals(1, retry.duplicates)
    }

    @Test fun knownTransactionWithChannelAliasAndMissingFieldsIsStillDuplicate() = runBlocking {
        repository.importEntries(listOf(entry()))
        val result = repository.ingestRecognizedBills(listOf(automaticDraft("duplicate-alias")
            .copy(channel = "微信", amount = "", occurredAt = "")), false)
        assertEquals(1, result.duplicates)
        assertEquals(0, result.pending)
        assertTrue(repository.drafts.first().isEmpty())
        assertEquals(1, repository.entries.first().size)
    }

    @Test fun suspectedAndInvalidBillsWaitWithoutBlockingValidBill() = runBlocking {
        repository.importEntries(listOf(entry()))
        val result = repository.ingestRecognizedBills(listOf(automaticDraft("possible", ""),
            automaticDraft("bad", "bad").copy(amount = "unknown"), automaticDraft("good", "tx3")), true)
        assertEquals(1, result.saved.size)
        assertEquals(1, result.suspectedDuplicates)
        assertEquals(1, result.pending)
        assertEquals(2, repository.drafts.first().size)
        assertEquals(2, repository.entries.first().size)
    }

    @Test fun duplicatesWithinBatchAndDeletedTransactionsAreNeverRecreated() = runBlocking {
        val result = repository.ingestRecognizedBills(listOf(automaticDraft("one"), automaticDraft("two")), true)
        assertEquals(1, result.saved.size); assertEquals(1, result.duplicates)
        repository.deleteEntry("one")
        assertEquals(1, repository.ingestRecognizedBills(listOf(automaticDraft("three")), true).duplicates)
        assertTrue(repository.entries.first().isEmpty())
    }

    @Test fun recognizedDraftIsNotCountedUntilConfirmedAndRetriesAreSafe() = runBlocking {
        val draft = AccountingDraft(id = "recognized1", amount = "35.20", merchant = "午餐店", zoneId = "Asia/Shanghai")
        repository.stageDrafts(listOf(draft, draft))
        assertEquals(1, repository.drafts.first().size)
        assertTrue(repository.entries.first().isEmpty())
        assertFalse(repository.confirmDraft(draft.id, recognizedInput()).duplicate)
        assertTrue(repository.drafts.first().isEmpty())
        assertTrue(repository.confirmDraft(draft.id, recognizedInput()).duplicate)
        assertEquals(1, repository.entries.first().size)
        assertEquals(1, repository.importEntries(listOf(entry())).duplicates)
    }

    @Test fun fileImportBeforeRecognitionAndDeletedEntriesPreventDuplicateConfirmation() = runBlocking {
        val imported = entry()
        repository.importEntries(listOf(imported))
        for (id in listOf("before-delete", "after-delete")) {
            repository.stageDrafts(listOf(AccountingDraft(id = id, zoneId = "Asia/Shanghai")))
            assertTrue(repository.confirmDraft(id, recognizedInput()).duplicate)
            repository.deleteEntry(imported.id)
        }
        assertTrue(repository.entries.first().isEmpty())
        assertTrue(repository.drafts.first().isEmpty())
    }

    @Test fun invalidConfirmationKeepsDraftAndDoesNotWriteLedger() = runBlocking {
        val draft = AccountingDraft(id = "invalid", zoneId = "Asia/Shanghai")
        repository.stageDrafts(listOf(draft))
        try {
            repository.confirmDraft(draft.id, recognizedInput().copy(amount = "bad"))
            fail("invalid amount must not be committed")
        } catch (_: IllegalArgumentException) {
            assertEquals(1, repository.drafts.first().size)
            assertTrue(repository.entries.first().isEmpty())
        }
        repository.dismissDraft(draft.id)
        assertTrue(repository.drafts.first().isEmpty())
    }

    @Test fun possibleDuplicateRequiresExplicitOverride() = runBlocking {
        repository.importEntries(listOf(entry()))
        repository.stageDrafts(listOf(AccountingDraft(id = "possible", zoneId = "Asia/Shanghai")))
        val input = recognizedInput().copy(transactionId = "")
        try {
            repository.confirmDraft("possible", input)
            fail("possible duplicate needs user confirmation")
        } catch (_: IllegalArgumentException) { assertEquals(1, repository.drafts.first().size) }
        assertFalse(repository.confirmDraft("possible", input.copy(allowPossibleDuplicate = true)).duplicate)
        assertEquals(2, repository.entries.first().size)
    }

    @Test fun repeatedImportDoesNotDuplicate() = runBlocking {
        val entry = entry()
        val first = repository.importEntries(listOf(entry, entry))
        assertEquals(1, first.inserted)
        assertEquals(1, first.duplicates)
        assertEquals(1, repository.importEntries(listOf(entry)).duplicates)
        assertEquals(1, repository.entries.first().size)
    }

    @Test fun deletedTransactionIsNotResurrected() = runBlocking {
        val entry = entry()
        repository.importEntries(listOf(entry))
        repository.deleteEntry(entry.id)
        assertEquals(1, repository.importEntries(listOf(entry)).duplicates)
        assertTrue(repository.entries.first().isEmpty())
    }

    @Test fun refundUpdateKeepsOriginalAmountAndBecomesPending() = runBlocking {
        val entry = entry()
        repository.importEntries(listOf(entry))
        val result = repository.importEntries(listOf(entry.copy(status = "PENDING", note = "原始状态：已部分退款")))
        assertEquals(1, result.updated)
        val saved = repository.entries.first().single()
        assertEquals("PENDING", saved.status)
        assertEquals(entry.amountMinor, saved.amountMinor)
        assertEquals(1, repository.importEntries(listOf(entry)).duplicates)
        assertEquals("PENDING", repository.entries.first().single().status)
    }

    @Test fun manualCreateEditDeleteUpdatesRealRecords() = runBlocking {
        val input = AccountingEntryInput(amount = "12.34", direction = "EXPENSE", merchant = "午餐", category = "餐饮",
            note = "", date = java.time.LocalDate.of(2026, 9, 16), time = java.time.LocalTime.NOON)
        val created = repository.saveEntry(input)
        assertEquals(created.id, repository.entries.first().single().id)
        repository.saveEntry(input.copy(id = created.id, amount = "20.00", direction = "INCOME", merchant = "报销"))
        val saved = repository.entries.first().single()
        assertEquals(2000L, saved.amountMinor)
        assertEquals("INCOME", saved.direction)
        assertEquals("报销", saved.merchant)
        repository.deleteEntry(saved.id)
        assertTrue(repository.entries.first().isEmpty())
    }

    @Test fun reimportDoesNotOverwriteUserEdits() = runBlocking {
        val original = entry()
        repository.importEntries(listOf(original))
        repository.saveEntry(AccountingEntryInput(id = original.id, amount = "20.00", direction = "EXPENSE", merchant = "修改名称",
            category = "餐饮", note = "我的备注", date = java.time.LocalDate.of(2026, 9, 16), time = java.time.LocalTime.NOON))
        assertEquals(1, repository.importEntries(listOf(original.copy(status = "PENDING"))).duplicates)
        val saved = repository.entries.first().single()
        assertEquals(2000L, saved.amountMinor)
        assertEquals("我的备注", saved.note)
        assertEquals(original.dedupKey, saved.dedupKey)
        assertEquals(original.transactionId, saved.transactionId)
    }

    @Test fun invalidEntryRollsBackWholeBatch() = runBlocking {
        val entry = entry()
        try {
            repository.importEntries(listOf(entry, entry.copy(id = "invalid", dedupKey = "invalid", amountMinor = 0)))
            fail("invalid batch must fail")
        } catch (_: IllegalArgumentException) {
            assertTrue(repository.entries.first().isEmpty())
        }
    }
}
