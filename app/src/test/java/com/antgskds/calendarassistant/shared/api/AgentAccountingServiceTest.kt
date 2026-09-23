package com.antgskds.calendarassistant.shared.api

import com.antgskds.calendarassistant.feature.accounting.data.*
import com.antgskds.calendarassistant.feature.accounting.domain.*
import com.antgskds.calendarassistant.feature.recognition.domain.model.RecognitionDraft
import com.antgskds.calendarassistant.feature.schedule.domain.model.Event
import com.antgskds.calendarassistant.shared.operation.*
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AgentAccountingServiceTest {
    private val time = Instant.parse("2026-09-19T04:30:00Z").toEpochMilli()

    @Test fun queryAndSummaryRespectRangeStatusCurrencyAndPagination() = runBlocking {
        val fixture = Fixture()
        val base = fixture.service.create(draft()).bill!!
        val entry = fixture.rows.value.single()
        fixture.rows.value = (0 until 205).map { entry.copy(id = "expense-$it") } + listOf(
            entry.copy(id = "income", direction = "INCOME", amountMinor = 10000),
            entry.copy(id = "transfer", direction = "TRANSFER", amountMinor = 300),
            entry.copy(id = "usd", currency = "USD", amountMinor = 700),
            entry.copy(id = "pending", status = "PENDING"),
            entry.copy(id = "deleted", deletedAt = time),
            entry.copy(id = "end-boundary", occurredAt = time + 1),
        )
        val filter = AgentBillFilter(startMs = time, endMs = time + 1)
        val page = fixture.service.query(AgentBillQuery(filter, limit = 2))
        assertEquals(208, page.total)
        assertEquals(2, page.nextOffset)
        assertEquals(2, page.bills.size)
        assertEquals(209, fixture.service.query(AgentBillQuery(filter, includePending = true)).total)
        assertNull(fixture.service.query(AgentBillQuery(filter, offset = Int.MAX_VALUE)).nextOffset)
        val summary = fixture.service.summary(filter)
        val cny = summary.first { it.currency == "CNY" }
        assertEquals(207, cny.count)
        assertEquals(205 * base.amountMinor, cny.expenseMinor)
        assertEquals(10000L, cny.incomeMinor)
        assertEquals(300L, cny.transferMinor)
        assertEquals(10000L - cny.expenseMinor, cny.balanceMinor)
        assertEquals(700L, summary.first { it.currency == "USD" }.expenseMinor)
        assertEquals(0, fixture.service.summary(AgentBillFilter(startMs = time + 2)).size)
    }

    @Test fun creationUsesExistingDedupAndNeverForcesDuplicateIntoStorage() = runBlocking {
        val fixture = Fixture()
        val first = fixture.service.create(draft().copy(channel = "微信支付", transactionId = "tx-real-1", transactionIdType = "PAYMENT"))
        assertEquals("SAVED", first.status)
        assertEquals(time, first.bill!!.occurredAt)
        assertEquals(3520L, first.bill.amountMinor)
        assertEquals("餐饮", first.bill.merchant)
        val duplicate = fixture.service.create(draft().copy(channel = "微信支付", transactionId = "tx-real-1", transactionIdType = "PAYMENT"))
        assertEquals("DUPLICATE", duplicate.status)
        assertNull(duplicate.bill)
        val suspected = fixture.service.create(draft())
        assertEquals("SUSPECTED_DUPLICATE", suspected.status)
        assertNotNull(suspected.draftId)
        assertNull(suspected.bill)
        assertEquals(1, fixture.rows.value.size)
        assertEquals(0, fixture.manualSaves)
    }

    @Test fun partialUpdatePreservesProvenanceAndAttachmentThenDeleteRemovesItFromQueries() = runBlocking {
        val fixture = Fixture()
        val id = fixture.service.create(draft()).bill!!.id
        val original = fixture.rows.value.single().copy(
            source = "WECHAT", transactionId = "tx-1", dedupKey = "original-key",
            currency = "USD", sourceImagePath = "/private/image.jpg",
        )
        fixture.rows.value = listOf(original)
        val result = fixture.service.update(id, AgentBillPatch(note = "新备注", amount = "18.01"))
        assertEquals(1801L, result.amountMinor)
        assertEquals("新备注", result.note)
        assertEquals(time, result.occurredAt)
        assertEquals("USD", result.currency)
        assertEquals("tx-1", result.transactionId)
        assertTrue(result.hasAttachment)
        val stored = fixture.rows.value.single()
        assertEquals(original.sourceImagePath, stored.sourceImagePath)
        assertEquals(original.dedupKey, stored.dedupKey)
        assertEquals(original.createdAt, stored.createdAt)
        fixture.service.delete(id)
        assertTrue(fixture.service.query(AgentBillQuery()).bills.isEmpty())
        assertTrue(runCatching { fixture.service.get(id) }.exceptionOrNull() is NoSuchElementException)
        assertTrue(runCatching { fixture.service.update(id, AgentBillPatch(note = "不能复活")) }.isFailure)
    }

    @Test fun invalidInputsFailBeforeWriting() = runBlocking {
        val fixture = Fixture()
        for (bad in listOf("0", "-1", "1.001", "NaN", "92233720368547758.08")) {
            assertTrue(runCatching { fixture.service.create(draft().copy(amount = bad)) }.isFailure)
        }
        assertTrue(runCatching { fixture.service.create(draft().copy(currency = "USD")) }.isFailure)
        assertTrue(runCatching { fixture.service.create(draft().copy(direction = "REFUND")) }.isFailure)
        assertTrue(runCatching { fixture.service.create(draft().copy(zoneId = "invalid")) }.isFailure)
        assertTrue(runCatching { fixture.service.create(draft().copy(occurredAt = -1)) }.isFailure)
        assertTrue(runCatching { fixture.service.update("id", AgentBillPatch()) }.isFailure)
        assertTrue(runCatching { fixture.service.query(AgentBillQuery(limit = 201)) }.isFailure)
        assertTrue(runCatching { fixture.service.summary(AgentBillFilter(startMs = time, endMs = time)) }.isFailure)
        assertTrue(fixture.rows.value.isEmpty())
    }

    @Test fun omittedManualTimeUsesNowAndSpecifiedZoneIsPreserved() = runBlocking {
        val fixture = Fixture()
        val before = System.currentTimeMillis()
        val bill = fixture.service.create(draft().copy(occurredAt = null, zoneId = "America/New_York")).bill!!
        assertTrue(bill.occurredAt in before..System.currentTimeMillis())
        assertEquals("America/New_York", bill.zoneId)
    }

    @Test fun amountsAndTimestampsRoundTripThroughProtocolWithoutFloatConversion() {
        val json = AgentProtocolJson.json
        val draft = json.decodeFromString(AgentBillDraft.serializer(), """{"amount":"90071992547409.91","direction":"EXPENSE","occurredAt":1789792200000}""")
        assertEquals("90071992547409.91", draft.amount)
        assertEquals(1789792200000L, draft.occurredAt)
        assertEquals(9007199254740991L, AccountingEntryEditor.amountMinor(draft.amount))
        val encoded = json.encodeToString(AgentBillDraft.serializer(), draft)
        assertEquals(draft, json.decodeFromString(AgentBillDraft.serializer(), encoded))
    }

    private fun draft() = AgentBillDraft(
        amount = "35.20", direction = "EXPENSE", category = "餐饮",
        occurredAt = time, zoneId = "Asia/Shanghai",
    )

    private class Fixture : AccountingApi, IngestCommandApi {
        val rows = MutableStateFlow<List<AccountingEntry>>(emptyList())
        override val entries = rows
        override val drafts = MutableStateFlow<List<AccountingDraft>>(emptyList())
        var manualSaves = 0
        val service = AgentAccountingService(this, this)

        override suspend fun ingestRecognizedBills(drafts: List<AccountingDraft>, useCurrentTimeForMissing: Boolean): AccountingRecognitionResult {
            check(!useCurrentTimeForMissing)
            val draft = drafts.single()
            val input = AccountingRecognitionMapper.automaticInput(draft, false)!!
            val entry = AccountingRecognitionMapper.build(draft, input, System.currentTimeMillis())
            if (rows.value.any { it.id == entry.id || (entry.dedupKey != null && it.dedupKey == entry.dedupKey) }) {
                return AccountingRecognitionResult(duplicates = 1)
            }
            if (rows.value.any { AccountingRecognitionMapper.possibleDuplicate(entry, it) }) {
                return AccountingRecognitionResult(suspectedDuplicates = 1)
            }
            rows.value += entry
            return AccountingRecognitionResult(saved = listOf(entry))
        }

        override suspend fun saveAccountingEntry(input: AccountingEntryInput): AccountingEntry {
            manualSaves++
            val old = rows.value.firstOrNull { it.id == input.id }
            val updated = AccountingEntryEditor.build(input, old, System.currentTimeMillis())
            rows.value = rows.value.map { if (it.id == updated.id) updated else it }
            return updated
        }
        override suspend fun deleteAccountingEntry(id: String) {
            rows.value = rows.value.map { if (it.id == id) it.copy(deletedAt = System.currentTimeMillis()) else it }
        }
        override suspend fun stageDrafts(drafts: List<AccountingDraft>) = error("not used")
        override suspend fun dismissDraft(id: String) = error("not used")
        override suspend fun confirmDraft(id: String, input: AccountingEntryInput): AccountingConfirmationResult = error("not used")
        override suspend fun saveEntry(input: AccountingEntryInput): AccountingEntry = error("must use ingest")
        override suspend fun deleteEntry(id: String) = error("must use ingest")
        override suspend fun importEntries(entries: List<AccountingEntry>): AccountingImportResult = error("not used")
        override suspend fun restoreEntries(entries: List<AccountingEntry>): AccountingImportResult = error("not used")
        override suspend fun ingestSmsPickup(eventData: RecognitionDraft): Event? = error("not used")
        override suspend fun ingestInstantCode(eventData: RecognitionDraft, sourceType: String): Event? = error("not used")
        override suspend fun ingestRecognizedEvents(events: List<RecognitionDraft>, sourceImagePath: String?): List<Event> = error("not used")
    }
}
