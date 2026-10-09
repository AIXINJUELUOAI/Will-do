package com.antgskds.calendarassistant.feature.linkanalysis

import com.antgskds.calendarassistant.feature.linkanalysis.application.LinkSummaryResponseParser
import com.antgskds.calendarassistant.feature.linkanalysis.domain.*
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoEntity
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class LinkSummaryTitleTest {
    private val memo = QuickMemoEntity(id = 7, title = "抖音收藏", sourceUrl = "https://v.douyin.com/example/", updatedAt = 10)

    @Test fun finalJsonSeparatesAiTitleAndMarkdownSummary() {
        val result = LinkSummaryResponseParser.parse("""
            ```json
            {"title":"  摄影构图技巧  ","summary":"- 保留主体\n- 注意光线"}
            ```
        """.trimIndent())
        assertEquals("摄影构图技巧", result.title)
        assertEquals("- 保留主体\n- 注意光线", result.summary)
    }

    @Test fun missingInvalidOrOversizedTitleKeepsValidSummary() {
        val titles = listOf("", "标题\n第二行", "a".repeat(ConfigCatalog.LINK_AI_TITLE_MAX_CHARS + 1))
        for (title in titles) {
            val raw = LinkSourceProtocol.json.encodeToString(mapOf("title" to title, "summary" to "有效摘要"))
            assertEquals("", LinkSummaryResponseParser.parse(raw).title)
            assertEquals("有效摘要", LinkSummaryResponseParser.parse(raw).summary)
        }
        assertEquals("", LinkSummaryResponseParser.parse("{\"summary\":\"有效摘要\"}").title)
        assertEquals("", LinkSummaryResponseParser.parse("{\"title\":123,\"summary\":\"有效摘要\"}").title)
    }

    @Test fun legacyMarkdownIsSavedWithoutGuessingTitle() {
        val result = LinkSummaryResponseParser.parse("# 旧模型的摘要\n保留这段正文")
        assertEquals("", result.title)
        assertEquals("# 旧模型的摘要\n保留这段正文", result.summary)
        val withExample = "配置举例：{\"title\":\"素材里的标题\"}，这也是摘要正文。"
        assertEquals(withExample, LinkSummaryResponseParser.parse(withExample).summary)
        assertEquals("", LinkSummaryResponseParser.parse(withExample).title)
    }

    @Test fun emptyOrMalformedSummaryFails() {
        for (raw in listOf("", "{\"title\":\"标题\",\"summary\":\" \"}", "{\"title\":\"标题\",\"summary\":false}", "{\"title\":\"标题\",")) {
            val failure = assertThrows(LinkAnalysisFailure::class.java) { LinkSummaryResponseParser.parse(raw) }
            assertEquals(LinkFailureCode.AI_EMPTY, failure.code)
        }
    }

    @Test fun replacesDefaultEmptyAndUneditedAutomaticTitles() {
        assertEquals("摄影技巧", LinkSummaryTitlePolicy.replacement(memo, memo, "", "摄影技巧"))
        val empty = memo.copy(title = "")
        assertEquals("摄影技巧", LinkSummaryTitlePolicy.replacement(empty, empty, "", "摄影技巧"))
        val automatic = memo.copy(title = "旧的 AI 标题")
        assertEquals("新的 AI 标题", LinkSummaryTitlePolicy.replacement(automatic, automatic, automatic.title, "新的 AI 标题"))
        assertNull(LinkSummaryTitlePolicy.replacement(memo, memo, "", ""))
    }

    @Test fun preservesCustomTitlesAndEditsDuringAnalysis() {
        val custom = memo.copy(title = "我的收藏")
        assertNull(LinkSummaryTitlePolicy.replacement(custom, custom, "", "新的 AI 标题"))
        assertNull(LinkSummaryTitlePolicy.replacement(memo, custom, "", "新的 AI 标题"))
        assertNull(LinkSummaryTitlePolicy.replacement(memo, memo.copy(updatedAt = 11), "", "新的 AI 标题"))
        val modifiedAutomatic = memo.copy(title = "编辑过的 AI 标题")
        assertNull(LinkSummaryTitlePolicy.replacement(modifiedAutomatic, modifiedAutomatic, "原 AI 标题", "新的 AI 标题"))
    }

    @Test fun oldSavedSummariesHaveNoAutomaticTitleAndNewOnesRoundTrip() {
        val old = LinkSourceProtocol.json.decodeFromString<LinkSummaryData>("{\"title\":\"来源标题\",\"summary\":\"旧摘要\"}")
        assertEquals("", old.aiTitle)
        assertEquals("", old.autoTitle)
        val saved = old.copy(aiTitle = "AI 标题", autoTitle = "AI 标题")
        assertEquals(saved, LinkSourceProtocol.json.decodeFromString<LinkSummaryData>(LinkSourceProtocol.json.encodeToString(saved)))
    }
}
