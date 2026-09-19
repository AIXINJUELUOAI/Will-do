package com.antgskds.calendarassistant.shared.operation

import com.antgskds.calendarassistant.feature.recognition.domain.model.RecognitionDraft
import com.antgskds.calendarassistant.feature.schedule.domain.model.Event
import com.antgskds.calendarassistant.feature.schedule.domain.model.*

interface IngestCommandApi {
    suspend fun ingestRecognizedBills(drafts: List<com.antgskds.calendarassistant.feature.accounting.data.AccountingDraft>, useCurrentTimeForMissing: Boolean):
        com.antgskds.calendarassistant.feature.accounting.domain.AccountingRecognitionResult = error("此入库实现不支持自动记账")
    suspend fun stageAccountingDrafts(drafts: List<com.antgskds.calendarassistant.feature.accounting.data.AccountingDraft>): Unit =
        error("此入库实现不支持账单识别")
    suspend fun dismissAccountingDraft(id: String): Unit = error("此入库实现不支持账单识别")
    suspend fun confirmAccountingDraft(id: String, input: com.antgskds.calendarassistant.feature.accounting.domain.AccountingEntryInput):
        com.antgskds.calendarassistant.feature.accounting.domain.AccountingConfirmationResult = error("此入库实现不支持账单识别")

    suspend fun saveAccountingEntry(
        input: com.antgskds.calendarassistant.feature.accounting.domain.AccountingEntryInput,
    ): com.antgskds.calendarassistant.feature.accounting.data.AccountingEntry = error("此入库实现不支持账单编辑")

    suspend fun deleteAccountingEntry(id: String): Unit = error("此入库实现不支持账单删除")

    suspend fun restoreAccountingEntries(
        entries: List<com.antgskds.calendarassistant.feature.accounting.data.AccountingEntry>,
    ): com.antgskds.calendarassistant.feature.accounting.domain.AccountingImportResult =
        error("此入库实现不支持账单恢复")

    /** 文件导入由统一入口委派到独立账单存储；日程 writer 不负责账单实体。 */
    suspend fun ingestAccountingEntries(
        entries: List<com.antgskds.calendarassistant.feature.accounting.data.AccountingEntry>,
    ): com.antgskds.calendarassistant.feature.accounting.domain.AccountingImportResult =
        error("此入库实现不支持账单导入")

    suspend fun ingestSmsPickup(eventData: RecognitionDraft): Event?
    suspend fun ingestInstantCode(eventData: RecognitionDraft, sourceType: String = "instant_code"): Event?
    suspend fun ingestRecognizedEvents(events: List<RecognitionDraft>, sourceImagePath: String?): List<Event>
}
