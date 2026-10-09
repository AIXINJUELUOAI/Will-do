package com.antgskds.calendarassistant.feature.linkanalysis

import com.antgskds.calendarassistant.feature.linkanalysis.application.LinkSummaryResponseParser
import com.antgskds.calendarassistant.feature.linkanalysis.domain.*
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoEntity
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class LinkTranscriptPolicyTest {
    private val memo = QuickMemoEntity(id = 1, bodyText = "文案\nhttps://example.com/", updatedAt = 10)

    @Test fun appendsSentenceParagraphsWithoutChangingOriginalText() {
        val update = requireNotNull(LinkTranscriptPolicy.bodyUpdate(memo, memo, "", "第一句。 第二句。价格3.14元。", true))
        assertEquals(memo.bodyText + "\n\n转写原文\n第一句。\n\n第二句。\n\n价格3.14元。", update.body)
        assertNull(LinkTranscriptPolicy.bodyUpdate(memo, memo, "", "第一句。", false))
    }

    @Test fun repeatedAnalysisReplacesOnlyUneditedAutomaticBlock() {
        val first = requireNotNull(LinkTranscriptPolicy.bodyUpdate(memo, memo, "", "旧的转写。", true))
        val current = memo.copy(bodyText = first.body, updatedAt = 20)
        val next = requireNotNull(LinkTranscriptPolicy.bodyUpdate(current, current, first.applied, "新的转写。", true))
        assertEquals(memo.bodyText + "\n\n转写原文\n新的转写。", next.body)
        val edited = current.copy(bodyText = current.bodyText.replace("旧的", "用户修改的"))
        assertNull(LinkTranscriptPolicy.bodyUpdate(edited, edited, first.applied, "新结果。", true))
        assertNull(LinkTranscriptPolicy.bodyUpdate(current, current.copy(bodyText = "用户输入", updatedAt = 21), first.applied, "新结果。", true))
    }

    @Test fun storageLimitAcceptsParagraphExpansionAndRejectsOversizedRawText() {
        val raw = "句。".repeat(ConfigCatalog.LINK_TEXT_MAX_CHARS / 2)
        val body = requireNotNull(LinkTranscriptPolicy.bodyUpdate(memo, memo, "", raw, true))
        val saved = LinkSummaryData(transcript = raw, bodyTranscript = body.applied)
        assertTrue(saved.bodyTranscript.length > ConfigCatalog.LINK_TEXT_MAX_CHARS)
        assertTrue(LinkTranscriptPolicy.withinStorageLimits(saved))
        assertFalse(LinkTranscriptPolicy.withinStorageLimits(saved.copy(transcript = raw + "超")))
    }

    @Test fun transcriptParsingIsOptionalAndBoundedWithoutDroppingValidSummary() {
        assertEquals("逐字原话。", LinkSummaryResponseParser.parse("{\"summary\":\"摘要\",\"transcript\":\"逐字原话。\"}").transcript)
        assertEquals("", LinkSummaryResponseParser.parse("{\"summary\":\"摘要\",\"transcript\":123}").transcript)
        val large = LinkSourceProtocol.json.encodeToString(mapOf("summary" to "摘要", "transcript" to "a".repeat(ConfigCatalog.LINK_TEXT_MAX_CHARS + 1)))
        val parsed = LinkSummaryResponseParser.parse(large)
        assertEquals("摘要", parsed.summary)
        assertEquals("", parsed.transcript)
        val old = LinkSourceProtocol.json.decodeFromString<LinkSummaryData>("{\"summary\":\"旧摘要\"}")
        assertEquals("", old.transcript)
        assertEquals("", old.bodyTranscript)
    }
}
