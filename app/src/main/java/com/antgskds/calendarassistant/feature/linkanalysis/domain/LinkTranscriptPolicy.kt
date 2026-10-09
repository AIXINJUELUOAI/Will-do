package com.antgskds.calendarassistant.feature.linkanalysis.domain

import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoEntity
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog

object LinkTranscriptPolicy {
    data class BodyUpdate(val body: String, val applied: String)

    fun withinStorageLimits(data: LinkSummaryData): Boolean =
        data.transcript.length <= ConfigCatalog.LINK_TEXT_MAX_CHARS &&
            data.bodyTranscript.length <= ConfigCatalog.LINK_TRANSCRIPT_BODY_MAX_CHARS

    fun paragraphs(transcript: String): String = transcript.trim().replace(Regex("""。\s*"""), "。\n\n").trimEnd()

    fun bodyUpdate(started: QuickMemoEntity, current: QuickMemoEntity, previous: String, transcript: String, enabled: Boolean): BodyUpdate? {
        if (!enabled || transcript.isBlank() || current.bodyText != started.bodyText || current.updatedAt != started.updatedAt) return null
        val block = "转写原文\n" + paragraphs(transcript)
        val body = current.bodyText
        if (previous.isNotBlank() && !body.endsWith(previous)) return null // 用户编辑过自动追加部分，保留并避免再次追加。
        if (previous.isBlank() && body.contains(paragraphs(transcript))) return null
        val prefix = if (previous.isBlank()) body.trimEnd() else body.removeSuffix(previous).trimEnd()
        return BodyUpdate(if (prefix.isBlank()) block else "$prefix\n\n$block", block)
    }
}
