package com.antgskds.calendarassistant.feature.linkanalysis.application

import com.antgskds.calendarassistant.feature.linkanalysis.domain.*
import com.antgskds.calendarassistant.feature.recognition.application.ai.RecognitionJsonParser
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** title 来自最终综合分析；老模型返回纯 Markdown 时仍保存有效摘要。 */
data class LinkGeneratedSummary(val title: String, val summary: String, val transcript: String = "", val transcriptIncomplete: Boolean = false)

object LinkSummaryResponseParser {
    fun parse(response: String): LinkGeneratedSummary {
        val raw = response.trim().removePrefix("\uFEFF").trim()
        val cleaned = RecognitionJsonParser.cleanJsonString(raw)
        val obj = runCatching { LinkSourceProtocol.json.parseToJsonElement(cleaned) as? JsonObject }.getOrNull()
        fun field(name: String): String? = (obj?.get(name) as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()
        val structured = obj != null && (obj.containsKey("summary") || raw.startsWith("{") || raw.startsWith("```json"))
        val summary = if (structured) field("summary").orEmpty() else {
            if (raw.startsWith("{") || raw.startsWith("```json")) throw LinkAnalysisFailure(LinkFailureCode.AI_EMPTY)
            raw
        }
        if (summary.isBlank() || summary.length > ConfigCatalog.LINK_SUMMARY_MAX_CHARS)
            throw LinkAnalysisFailure(LinkFailureCode.AI_EMPTY)
        val transcript = if (structured) field("transcript").orEmpty().takeIf { it.length <= ConfigCatalog.LINK_TEXT_MAX_CHARS }.orEmpty() else ""
        return LinkGeneratedSummary(if (structured) LinkSummaryTitlePolicy.validTitle(field("title").orEmpty()) else "", summary, transcript)
    }
}
