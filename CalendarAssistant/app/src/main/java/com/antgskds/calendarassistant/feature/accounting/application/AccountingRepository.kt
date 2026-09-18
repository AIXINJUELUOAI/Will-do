package com.antgskds.calendarassistant.feature.accounting.application

import androidx.room.withTransaction
import com.antgskds.calendarassistant.feature.accounting.data.AccountingDraft
import com.antgskds.calendarassistant.feature.accounting.domain.AccountingRecognitionMapper
import com.antgskds.calendarassistant.feature.accounting.domain.AccountingConfirmationResult
import com.antgskds.calendarassistant.feature.accounting.data.AccountingEntry
import com.antgskds.calendarassistant.feature.accounting.domain.AccountingEntryInput
import com.antgskds.calendarassistant.feature.accounting.domain.AccountingEntryEditor
import com.antgskds.calendarassistant.feature.accounting.domain.AccountingApi
import com.antgskds.calendarassistant.feature.accounting.domain.AccountingImportResult
import com.antgskds.calendarassistant.feature.accounting.domain.AccountingRecognitionResult
import com.antgskds.calendarassistant.feature.schedule.data.db.EventsDatabase

/** 沿用现有账单表，批次写入要么全部成功，要么整体回滚。 */
class AccountingRepository(private val database: EventsDatabase) : AccountingApi {
    private val dao get() = database.accountingDao()
    override val entries = dao.observeEntries()
    override val drafts = dao.observeDrafts()

    override suspend fun ingestRecognizedBills(drafts: List<AccountingDraft>, useCurrentTimeForMissing: Boolean): AccountingRecognitionResult = database.withTransaction {
        val saved = mutableListOf<AccountingEntry>()
        var duplicates = 0
        var suspected = 0
        var pending = 0
        drafts.forEach { draft ->
            // 优先用稳定身份排重；即使本次识别缺少其他字段，也不重新建立已记录交易。
            val transactionKey = AccountingRecognitionMapper.transactionKey(draft.channel, draft.transactionId, draft.direction, draft.transactionIdType, draft.merchant)
            if (dao.findDuplicate(draft.id, transactionKey ?: draft.id) != null) {
                duplicates++
                dao.deleteDraft(draft.id)
                return@forEach
            }
            val input = AccountingRecognitionMapper.automaticInput(draft, useCurrentTimeForMissing)
            val entry = input?.let { runCatching { AccountingRecognitionMapper.build(draft, it, System.currentTimeMillis()) }.getOrNull() }
            if (entry == null) {
                dao.insertDraft(draft)
                pending++
                return@forEach
            }
            if (dao.findDuplicate(entry.id, entry.dedupKey ?: entry.id) != null) {
                duplicates++
                dao.deleteDraft(draft.id)
                return@forEach
            }
            val range = AccountingRecognitionMapper.duplicateTimeRange(entry.occurredAt)
            val possible = dao.duplicateCandidates(entry.amountMinor, entry.direction, entry.currency, range.first, range.last)
                .any { AccountingRecognitionMapper.possibleDuplicate(entry, it) }
            if (possible) {
                val actualTime = java.time.Instant.ofEpochMilli(entry.occurredAt).atZone(java.time.ZoneId.of(entry.zoneId)).toLocalDateTime()
                dao.insertDraft(draft.copy(occurredAt = actualTime.toString().replace('T', ' '),
                    note = listOf(draft.note, AccountingRecognitionMapper.POSSIBLE_DUPLICATE_NOTE).filter(String::isNotBlank).joinToString(" · ")))
                suspected++
            } else {
                dao.insert(entry)
                dao.deleteDraft(draft.id)
                saved += entry
            }
        }
        AccountingRecognitionResult(saved, duplicates, suspected, pending)
    }

    override suspend fun stageDrafts(drafts: List<AccountingDraft>) = database.withTransaction {
        drafts.forEach { dao.insertDraft(it) }
    }

    override suspend fun dismissDraft(id: String) = database.withTransaction { dao.deleteDraft(id) }

    override suspend fun confirmDraft(id: String, input: AccountingEntryInput): AccountingConfirmationResult = database.withTransaction {
        val draft = dao.findDraft(id)
            ?: return@withTransaction AccountingConfirmationResult(dao.findById(id), duplicate = true)
        val entry = AccountingRecognitionMapper.build(draft, input, System.currentTimeMillis())
        val exact = dao.findDuplicate(entry.id, entry.dedupKey ?: entry.id)
        if (exact != null) {
            dao.deleteDraft(id)
            return@withTransaction AccountingConfirmationResult(exact.takeIf { it.deletedAt == null }, duplicate = true)
        }
        val range = AccountingRecognitionMapper.duplicateTimeRange(entry.occurredAt)
        val possible = dao.duplicateCandidates(entry.amountMinor, entry.direction, entry.currency, range.first, range.last)
            .any { AccountingRecognitionMapper.possibleDuplicate(entry, it) }
        require(!possible || input.allowPossibleDuplicate) { "存在疑似重复账单，请核对金额、时间和交易对方后选择仍然保存" }
        dao.insert(entry)
        dao.deleteDraft(id)
        AccountingConfirmationResult(entry, duplicate = false)
    }

    override suspend fun saveEntry(input: AccountingEntryInput): AccountingEntry = database.withTransaction {
        val old = input.id?.let { dao.findById(it) }
        val entry = AccountingEntryEditor.build(input, old, System.currentTimeMillis())
        if (old == null) dao.insert(entry) else dao.update(entry)
        entry
    }

    override suspend fun deleteEntry(id: String) = database.withTransaction {
        val old = dao.findById(id) ?: return@withTransaction
        if (old.deletedAt == null) {
            val now = System.currentTimeMillis()
            dao.update(old.copy(deletedAt = now, updatedAt = now))
        }
    }

    override suspend fun restoreEntries(entries: List<AccountingEntry>): AccountingImportResult = database.withTransaction {
        com.antgskds.calendarassistant.feature.accounting.domain.AccountingBackupCodec.validate(entries)
        var inserted = 0
        var duplicates = 0
        entries.forEach { entry ->
            if (dao.findDuplicate(entry.id, entry.dedupKey ?: entry.id) != null) {
                duplicates++
            } else {
                // 备份保留稳定 ID；用户确认过的同金额同时间独立账单也应原样恢复。
                dao.insert(entry)
                inserted++
            }
        }
        AccountingImportResult(inserted, duplicates)
    }

    override suspend fun importEntries(entries: List<AccountingEntry>): AccountingImportResult = database.withTransaction {
        var inserted = 0
        var duplicates = 0
        var updated = 0
        entries.forEach { entry ->
            require(entry.amountMinor > 0 && entry.source == "FILE" && entry.deletedAt == null) { "账单数据无效" }
            require(entry.direction in setOf("EXPENSE", "INCOME", "TRANSFER")) { "账单方向无效" }
            require(entry.status in setOf("CONFIRMED", "PENDING")) { "账单状态无效" }
            val key = requireNotNull(entry.dedupKey?.takeIf { it.isNotBlank() }) { "缺少账单去重标识" }
            val range = AccountingRecognitionMapper.duplicateTimeRange(entry.occurredAt)
            val old = dao.findDuplicate(entry.id, key)
            if (old == null) {
                val possible = dao.duplicateCandidates(entry.amountMinor, entry.direction, entry.currency, range.first, range.last)
                    .any { it.source in setOf("RECOGNITION", "MANUAL") && AccountingRecognitionMapper.possibleDuplicate(entry, it) }
                // 无交易号的相似记录不能直接合并；先标为待核对，避免重复计入收支。
                dao.insert(if (possible) entry.copy(status = "PENDING", note = entry.note + " · 疑似与已有记录重复，请核对") else entry)
                inserted++
            } else if (old.deletedAt == null && old.source == "FILE" && old.ruleId != "manual-edit" && old.status == "CONFIRMED" && entry.status == "PENDING") {
                // 同一交易后续出现退款/冲突时，保守转待核对；不覆盖用户字段或复活删除记录。
                dao.update(old.copy(status = "PENDING", note = entry.note, updatedAt = entry.updatedAt))
                updated++
            } else {
                duplicates++
            }
        }
        AccountingImportResult(inserted, duplicates, updated)
    }
}
