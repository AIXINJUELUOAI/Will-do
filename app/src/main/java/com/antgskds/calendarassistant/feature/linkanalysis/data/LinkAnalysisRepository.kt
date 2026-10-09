package com.antgskds.calendarassistant.feature.linkanalysis.data

import androidx.room.withTransaction
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoEntity
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

    /** 在摘要请求之前保存逐字稿；只有未发生用户编辑时才返回可更新的任务快照。 */
    suspend fun saveTranscript(id: Long, token: String, transcript: String, started: QuickMemoEntity, append: Boolean): QuickMemoEntity? = db.withTransaction {
        if (transcript.isBlank() || transcript.length > Limits.LINK_TEXT_MAX_CHARS) return@withTransaction null
        val record = current(id, token) ?: return@withTransaction null
        val currentMemo = memo(id) ?: return@withTransaction null
        val old = summary(record) ?: LinkSummaryData(sourceId = record.sourceId)
        val body = LinkTranscriptPolicy.bodyUpdate(started, currentMemo, old.bodyTranscript, transcript, append)
        val saved = old.copy(transcript = transcript, bodyTranscript = body?.applied ?: old.bodyTranscript)
        val now = System.currentTimeMillis()
        if (db.linkAnalysisDao().saveResult(id, token, LinkSourceProtocol.json.encodeToString(saved), now) != 1) return@withTransaction null
        if (body != null) db.quickMemoDao().updateBody(id, body.body, now)
        if (currentMemo.title != started.title || currentMemo.updatedAt != started.updatedAt || currentMemo.bodyText != started.bodyText)
            return@withTransaction null
        if (body == null) currentMemo else currentMemo.copy(bodyText = body.body, updatedAt = now)
    }

    suspend fun complete(id: Long, token: String, data: LinkSummaryData, startedMemo: QuickMemoEntity): Boolean = db.withTransaction {
        val record = current(id, token) ?: return@withTransaction false
        val currentMemo = memo(id) ?: return@withTransaction false
        if (data.summary.isBlank() || data.summary.length > Limits.LINK_SUMMARY_MAX_CHARS) return@withTransaction false
        val previousAutoTitle = summary(record)?.autoTitle.orEmpty()
        val nextTitle = LinkSummaryTitlePolicy.replacement(startedMemo, currentMemo, previousAutoTitle, data.aiTitle)
        val retainedAutoTitle = previousAutoTitle.takeIf {
            it == currentMemo.title && currentMemo.updatedAt == startedMemo.updatedAt
        }.orEmpty()
        val saved = data.copy(autoTitle = nextTitle ?: retainedAutoTitle, bodyTranscript = summary(record)?.bodyTranscript.orEmpty())
        val now = System.currentTimeMillis()
        val updated = db.linkAnalysisDao().complete(id, token, LinkSourceProtocol.json.encodeToString(saved), now) == 1
        if (updated) {
            if (nextTitle != null) db.quickMemoDao().updateTitle(id, nextTitle, now)
            else db.openHelper.writableDatabase.execSQL("UPDATE quick_memos SET updated_at = ? WHERE id = ?", arrayOf(now, id))
        }
        updated
    }
    suspend fun restore(id: Long, data: LinkSummaryData?) {
        if (data == null || (data.summary.isBlank() && data.transcript.isBlank()) || data.summary.length > Limits.LINK_SUMMARY_MAX_CHARS ||
            data.extractedText.length > Limits.LINK_TEXT_MAX_CHARS || !LinkTranscriptPolicy.withinStorageLimits(data) || data.warnings.size > Limits.LINK_MAX_MEDIA || data.warnings.any { it.length > 512 }) return
        db.withTransaction {
            val memo = memo(id) ?: return@withTransaction
            if (memo.sourceUrl == null) return@withTransaction
            db.linkAnalysisDao().put(LinkAnalysisEntity(id, sourceUrl = memo.sourceUrl, sourceId = data.sourceId,
                // 只有逐字稿的恢复记录保留素材，不重启付费任务，也不标成摘要完成。
                state = if (data.summary.isBlank()) "CANCELLED" else "DONE", resultJson = LinkSourceProtocol.json.encodeToString(data), updatedAt = data.completedAt))
        }
    }
    companion object {
        fun summary(record: LinkAnalysisEntity?): LinkSummaryData? =
            record?.resultJson?.takeIf(String::isNotBlank)?.let { runCatching { LinkSourceProtocol.json.decodeFromString<LinkSummaryData>(it) }.getOrNull() }
    }
}
