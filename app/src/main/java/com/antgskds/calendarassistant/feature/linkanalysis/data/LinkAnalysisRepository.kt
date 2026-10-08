package com.antgskds.calendarassistant.feature.linkanalysis.data

import androidx.room.withTransaction
import com.antgskds.calendarassistant.feature.linkanalysis.domain.*
import com.antgskds.calendarassistant.feature.schedule.data.db.EventsDatabase
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog as Limits
import kotlinx.serialization.encodeToString

class LinkAnalysisRepository(private val db: EventsDatabase) {
    val records = db.linkAnalysisDao().observe()
    suspend fun memo(id: Long) = db.quickMemoDao().getQuickMemo(id)
    suspend fun all() = db.linkAnalysisDao().all()
    suspend fun get(id: Long) = db.linkAnalysisDao().get(id)
    suspend fun queue(record: LinkAnalysisEntity) = db.withTransaction {
        if (db.quickMemoDao().getQuickMemo(record.memoId)?.sourceUrl != record.sourceUrl) false else {
            val old = get(record.memoId)
            db.linkAnalysisDao().put(record.copy(resultJson = old?.resultJson.orEmpty()))
            true
        }
    }
    suspend fun current(id: Long, token: String): LinkAnalysisEntity? =
        get(id)?.takeIf { it.token == token && it.state != "CANCELLED" && memo(id)?.sourceUrl == it.sourceUrl }

    suspend fun state(id: Long, token: String, state: String, error: String = "") =
        db.linkAnalysisDao().state(id, token, state, error.take(512), System.currentTimeMillis())

    suspend fun complete(id: Long, token: String, data: LinkSummaryData): Boolean = db.withTransaction {
        if (current(id, token) == null || data.summary.isBlank() || data.summary.length > Limits.LINK_SUMMARY_MAX_CHARS) false
        else {
            val now = System.currentTimeMillis()
            val updated = db.linkAnalysisDao().complete(id, token, LinkSourceProtocol.json.encodeToString(data), now) == 1
            if (updated) db.openHelper.writableDatabase.execSQL("UPDATE quick_memos SET updated_at = ? WHERE id = ?", arrayOf(now, id))
            updated
        }
    }
    suspend fun restore(id: Long, data: LinkSummaryData?) {
        if (data == null || data.summary.isBlank() || data.summary.length > Limits.LINK_SUMMARY_MAX_CHARS ||
            data.extractedText.length > Limits.LINK_TEXT_MAX_CHARS || data.warnings.size > Limits.LINK_MAX_MEDIA || data.warnings.any { it.length > 512 }) return
        db.withTransaction {
            val memo = memo(id) ?: return@withTransaction
            if (memo.sourceUrl == null) return@withTransaction
            db.linkAnalysisDao().put(LinkAnalysisEntity(id, sourceUrl = memo.sourceUrl, sourceId = data.sourceId,
                state = "DONE", resultJson = LinkSourceProtocol.json.encodeToString(data), updatedAt = data.completedAt))
        }
    }
    companion object {
        fun summary(record: LinkAnalysisEntity?): LinkSummaryData? =
            record?.resultJson?.takeIf(String::isNotBlank)?.let { runCatching { LinkSourceProtocol.json.decodeFromString<LinkSummaryData>(it) }.getOrNull() }
    }
}
