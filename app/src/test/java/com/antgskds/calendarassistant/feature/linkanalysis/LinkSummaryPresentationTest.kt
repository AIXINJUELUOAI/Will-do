package com.antgskds.calendarassistant.feature.linkanalysis

import com.antgskds.calendarassistant.feature.linkanalysis.domain.*
import org.junit.Assert.*
import org.junit.Test

class LinkSummaryPresentationTest {
    @Test fun removesDuplicateWarningBlockButRetainsDifferentLimitations() {
        val warning = "源提供的正文不完整，摘要仅依据已取得素材"
        val data = LinkSummaryData(summary = "正文要点。\n\n**已知限制：** $warning；不能确认画面中的具体地点。", warnings = listOf(warning))
        val result = LinkSummaryPresentationPolicy.present(data)
        assertEquals("正文要点。", result.markdown)
        assertEquals(listOf(warning, "不能确认画面中的具体地点。"), result.warnings)
    }

    @Test fun keepsNormalContentAndHandlesHeadingWarnings() {
        val data = LinkSummaryData(summary = "已知限制的情况需要另行检查。\n\n### 已知限制\n\n- 没有完整音轨。\n\n## 建议\n继续查证。", warnings = listOf("没有完整音轨"))
        val result = LinkSummaryPresentationPolicy.present(data)
        assertTrue(result.markdown.contains("已知限制的情况需要另行检查。"))
        assertTrue(result.markdown.contains("## 建议"))
        assertTrue(result.markdown.contains("继续查证。"))
        assertEquals(listOf("没有完整音轨"), result.warnings)
    }
}
