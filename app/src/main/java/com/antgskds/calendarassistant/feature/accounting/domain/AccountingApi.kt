package com.antgskds.calendarassistant.feature.accounting.domain

import com.antgskds.calendarassistant.feature.accounting.data.AccountingEntry
import kotlinx.coroutines.flow.Flow

/** 账单存储契约；导入调用方经 IngestCommandApi 写入，UI 只订阅查询。 */
interface AccountingApi {
    val entries: Flow<List<AccountingEntry>>
    val drafts: Flow<List<com.antgskds.calendarassistant.feature.accounting.data.AccountingDraft>>
    suspend fun stageDrafts(drafts: List<com.antgskds.calendarassistant.feature.accounting.data.AccountingDraft>)
    suspend fun ingestRecognizedBills(drafts: List<com.antgskds.calendarassistant.feature.accounting.data.AccountingDraft>, useCurrentTimeForMissing: Boolean): AccountingRecognitionResult
    suspend fun dismissDraft(id: String)
    suspend fun confirmDraft(id: String, input: AccountingEntryInput): AccountingConfirmationResult
    suspend fun saveEntry(input: AccountingEntryInput): AccountingEntry
    suspend fun deleteEntry(id: String)
    suspend fun importEntries(entries: List<AccountingEntry>): AccountingImportResult
    suspend fun restoreEntries(entries: List<AccountingEntry>): AccountingImportResult
}

data class AccountingImportResult(val inserted: Int, val duplicates: Int, val updated: Int = 0)
data class AccountingRecognitionResult(
    val saved: List<AccountingEntry> = emptyList(),
    val duplicates: Int = 0,
    val suspectedDuplicates: Int = 0,
    val pending: Int = 0,
)
enum class BillFileSource(val label: String) { WECHAT("微信支付"), ALIPAY("支付宝"), WILLDO("账单备份") }
data class AccountingImportIssue(val sheet: String, val row: Int, val reason: String)
data class AccountingImportPreview(
    val source: BillFileSource,
    val entries: List<AccountingEntry>,
    val issues: List<AccountingImportIssue>,
    val ignored: Int,
    val images: Map<String, ByteArray> = emptyMap(),
) {
    val pending: Int get() = entries.count { it.status != "CONFIRMED" }
    val neutral: Int get() = entries.count { it.direction == "TRANSFER" }
}
