package com.antgskds.calendarassistant.shared.management.resource.notification.display.live.template

import com.antgskds.calendarassistant.service.capsule.CapsuleActionSpec
import com.antgskds.calendarassistant.service.capsule.CapsuleDisplayModel

object RecognitionLiveDisplay {
    private const val PRIMARY_TITLE_MAX_CHARS = 11
    private const val SHORT_TITLE_MAX_CHARS = 6

    fun progress(title: String, content: String): CapsuleDisplayModel {
        val short = "正在分析"
        val secondary = cleanTitle(content)
        val detail = compactOcrDetail(title, secondary, short)
        return CapsuleDisplayModel(
            shortText = short,
            primaryText = compactPrimaryTitle(short, detail),
            secondaryText = secondary,
            expandedText = secondary
        )
    }

    fun statusResult(title: String, content: String): CapsuleDisplayModel {
        val short = compactShortTitle(cleanTitle(title) ?: "分析完成")
        val secondary = cleanTitle(content)
        val detail = compactOcrDetail(content, null, short)
        return CapsuleDisplayModel(
            shortText = short,
            primaryText = compactPrimaryTitle(short, detail),
            secondaryText = secondary,
            expandedText = secondary
        )
    }

    fun eventResult(
        shortText: String,
        title: String,
        contentLines: List<String>,
        action: CapsuleActionSpec? = null
    ): CapsuleDisplayModel {
        return CapsuleDisplayModel(
            shortText = compactResultText(shortText, SHORT_TITLE_MAX_CHARS),
            primaryText = title,
            secondaryText = contentLines.getOrNull(0),
            tertiaryText = contentLines.getOrNull(1),
            expandedText = contentLines.joinToString("\n").ifBlank { null },
            action = action
        )
    }

    private fun compactPrimaryTitle(shortTitle: String, detailTitle: String?): String {
        val short = compactShortTitle(shortTitle)
        val detail = compactTitleDetail(detailTitle, PRIMARY_TITLE_MAX_CHARS - short.length - 1)
        if (detail == null || detail == short) return short.take(PRIMARY_TITLE_MAX_CHARS)
        val title = "$short|$detail"
        return if (title.length <= PRIMARY_TITLE_MAX_CHARS) title else short.take(PRIMARY_TITLE_MAX_CHARS)
    }

    private fun compactShortTitle(value: String): String {
        val clean = cleanTitle(value) ?: "提醒"
        return if (clean.length <= SHORT_TITLE_MAX_CHARS) clean else clean.take(SHORT_TITLE_MAX_CHARS)
    }

    private fun compactResultText(value: String, maxChars: Int): String {
        val clean = value.trim()
        if (clean.length <= maxChars) return clean
        return if (maxChars <= 3) clean.take(maxChars) else clean.take(maxChars - 3) + "..."
    }

    private fun compactTitleDetail(value: String?, maxChars: Int): String? {
        if (maxChars <= 0) return null
        val clean = cleanTitle(value) ?: return null
        val candidates = listOf(
            clean,
            clean.replace("未识别到有效日程", "无有效日程"),
            clean.replace("没有可入库的新事件", "无新事件"),
            clean.replace("正在分析屏幕内容", "屏幕内容"),
            clean.removePrefix("正在分析"),
            clean.removeSuffix("加载中"),
            clean.removeSuffix("准备中")
        ).mapNotNull(::cleanTitle).distinct()
        return candidates.firstOrNull { it.length <= maxChars } ?: clean.take(maxChars)
    }

    private fun compactOcrDetail(title: String, content: String?, shortTitle: String): String? {
        val titleDetail = cleanTitle(title)
            ?.removePrefix(shortTitle)
            ?.let(::cleanTitle)
        return titleDetail ?: cleanTitle(content)
    }

    private fun cleanTitle(value: String?): String? {
        return value
            ?.trim()
            ?.trim('。', '.', '…')
            ?.replace("...", "")
            ?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
    }
}
