package com.antgskds.calendarassistant.feature.linkanalysis.domain

import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoEntity
import com.antgskds.calendarassistant.feature.quickmemo.domain.QuickMemoLinkParser
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog

object LinkSummaryTitlePolicy {
    fun validTitle(value: String): String = value.trim().takeIf { title ->
        title.isNotEmpty() && title.length <= ConfigCatalog.LINK_AI_TITLE_MAX_CHARS &&
            title.none { it.isISOControl() || it == '\u2028' || it == '\u2029' }
    }.orEmpty()

    fun replacement(started: QuickMemoEntity, current: QuickMemoEntity, previousAutoTitle: String, generated: String): String? {
        val title = validTitle(generated).takeIf(String::isNotBlank) ?: return null
        // updatedAt 也保护用户在分析期间改名后又改回原名的情况；其他编辑同样优先保留。
        if (current.title != started.title || current.updatedAt != started.updatedAt) return null
        val defaultTitle = current.sourceUrl?.let { QuickMemoLinkParser.parse(it)?.title }
        val automatic = previousAutoTitle.isNotBlank() && current.title == previousAutoTitle
        return title.takeIf { current.title.isBlank() || current.title == defaultTitle || automatic }
    }
}
