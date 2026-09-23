package com.antgskds.calendarassistant.shared.api

import com.antgskds.calendarassistant.feature.accounting.data.AccountingDraft
import com.antgskds.calendarassistant.feature.accounting.data.AccountingEntry
import com.antgskds.calendarassistant.feature.accounting.domain.AccountingApi
import com.antgskds.calendarassistant.feature.accounting.domain.AccountingEntryEditor
import com.antgskds.calendarassistant.feature.accounting.domain.AccountingEntryInput
import com.antgskds.calendarassistant.shared.operation.*
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.first

/** 权限由 AgentDataService 与传输入口核验；写入沿用应用统一入口。 */
class AgentAccountingService(
    private val accounting: AccountingApi,
    private val ingest: IngestCommandApi,
) {
    suspend fun create(draft: AgentBillDraft): AgentBillCreation {
        require(draft.currency == "CNY") { "Only CNY bill creation is supported" }
        AccountingEntryEditor.amountMinor(draft.amount)
        validateDirection(draft.direction)
        require(draft.transactionIdType in setOf("PAYMENT", "MERCHANT_ORDER", "UNKNOWN")) { "Invalid transactionIdType" }
        val zone = draft.zoneId?.let(ZoneId::of) ?: ZoneId.systemDefault()
        val occurredAt = draft.occurredAt ?: System.currentTimeMillis()
        require(occurredAt >= 0) { "occurredAt must be Unix milliseconds >= 0" }
        val candidate = AccountingDraft(
            amount = draft.amount,
            direction = draft.direction,
            currency = "CNY",
            merchant = draft.merchant.trim().ifBlank { draft.category.trim().ifBlank { "未分类" } },
            category = draft.category,
            note = draft.note,
            occurredAt = Instant.ofEpochMilli(occurredAt).atZone(zone).toLocalDateTime().toString(),
            zoneId = zone.id,
            channel = draft.channel,
            transactionId = draft.transactionId,
            transactionIdType = draft.transactionIdType,
            paymentStatus = "COMPLETED",
            sourceType = "agent",
        )
        val result = ingest.ingestRecognizedBills(listOf(candidate), useCurrentTimeForMissing = false)
        return when {
            result.saved.isNotEmpty() -> AgentBillCreation("SAVED", bill = result.saved.single().toAgentBill())
            result.duplicates > 0 -> AgentBillCreation("DUPLICATE")
            result.suspectedDuplicates > 0 -> AgentBillCreation("SUSPECTED_DUPLICATE", draftId = candidate.id)
            result.pending > 0 -> AgentBillCreation("PENDING", draftId = candidate.id)
            else -> error("Bill ingestion returned no result")
        }
    }

    private suspend fun find(id: String): AccountingEntry {
        require(id.isNotBlank()) { "id is required" }
        return accounting.entries.first().firstOrNull { it.id == id && it.deletedAt == null }
            ?: throw NoSuchElementException("Bill not found")
    }

    suspend fun get(id: String): AgentBill = find(id).toAgentBill()

    suspend fun query(query: AgentBillQuery): AgentBillPage {
        require(query.offset >= 0) { "offset must be >= 0" }
        require(query.limit in 1..WillDoAgentContract.MAX_QUERY_LIMIT) { "Invalid limit" }
        val entries = filtered(query.filter, query.includePending)
            .sortedWith(compareByDescending<AccountingEntry> { it.occurredAt }.thenBy { it.id })
        val bills = entries.drop(query.offset).take(query.limit).map { it.toAgentBill() }
        val next = if (query.offset < entries.size && bills.size < entries.size - query.offset) {
            query.offset + bills.size
        } else null
        return AgentBillPage(bills, entries.size, next)
    }

    suspend fun update(id: String, patch: AgentBillPatch): AgentBill {
        require(patch != AgentBillPatch()) { "At least one editable field is required" }
        patch.direction?.let(::validateDirection)
        patch.occurredAt?.let { require(it >= 0) { "occurredAt must be Unix milliseconds >= 0" } }
        val old = find(id)
        val time = Instant.ofEpochMilli(patch.occurredAt ?: old.occurredAt).atZone(ZoneId.of(old.zoneId))
        val input = AccountingEntryInput(
            id = id,
            amount = patch.amount ?: BigDecimal.valueOf(old.amountMinor, 2).toPlainString(),
            direction = patch.direction ?: old.direction,
            merchant = patch.merchant ?: old.merchant,
            category = patch.category ?: old.category,
            note = patch.note ?: old.note,
            date = time.toLocalDate(),
            time = time.toLocalTime(),
        )
        return ingest.saveAccountingEntry(input).toAgentBill()
    }

    suspend fun delete(id: String) {
        find(id)
        ingest.deleteAccountingEntry(id)
    }

    suspend fun summary(filter: AgentBillFilter): List<AgentBillSummary> =
        filtered(filter, includePending = false).groupBy { it.currency }.toSortedMap().map { (currency, bills) ->
            fun sum(direction: String) = bills.filter { it.direction == direction }
                .fold(0L) { total, bill -> Math.addExact(total, bill.amountMinor) }
            val expense = sum("EXPENSE")
            val income = sum("INCOME")
            AgentBillSummary(currency, bills.size, expense, income, sum("TRANSFER"), Math.subtractExact(income, expense))
        }

    private suspend fun filtered(filter: AgentBillFilter, includePending: Boolean): List<AccountingEntry> {
        require(filter.startMs == null || filter.startMs >= 0) { "Invalid startMs" }
        require(filter.endMs == null || filter.endMs >= 0) { "Invalid endMs" }
        require(filter.startMs == null || filter.endMs == null || filter.startMs < filter.endMs) { "Expected startMs < endMs" }
        filter.direction?.let(::validateDirection)
        filter.currency?.let { require(it.matches(Regex("[A-Z]{3}"))) { "Invalid currency" } }
        return accounting.entries.first().filter { bill ->
            bill.deletedAt == null &&
                (includePending || bill.status == "CONFIRMED") &&
                (filter.startMs == null || bill.occurredAt >= filter.startMs) &&
                (filter.endMs == null || bill.occurredAt < filter.endMs) &&
                (filter.direction == null || bill.direction == filter.direction) &&
                (filter.category == null || bill.category == filter.category) &&
                (filter.currency == null || bill.currency == filter.currency) &&
                (filter.text.isNullOrBlank() || listOf(bill.merchant, bill.note, bill.category, bill.transactionId)
                    .any { it.contains(filter.text, ignoreCase = true) })
        }
    }

    private fun validateDirection(value: String) {
        require(value in setOf("EXPENSE", "INCOME", "TRANSFER")) { "Invalid direction" }
    }

    private fun AccountingEntry.toAgentBill() = AgentBill(
        id, amountMinor, direction, currency, merchant, category, note, occurredAt, zoneId,
        channel, transactionId, transactionIdType, status, source, !sourceImagePath.isNullOrBlank(),
    )
}
