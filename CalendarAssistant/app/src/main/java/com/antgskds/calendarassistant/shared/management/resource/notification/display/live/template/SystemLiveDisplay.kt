package com.antgskds.calendarassistant.shared.management.resource.notification.display.live.template

import com.antgskds.calendarassistant.service.capsule.CapsuleDisplayModel

object SystemLiveDisplay {
    private const val PRIMARY_TITLE_MAX_CHARS = 11
    private const val SHORT_TITLE_MAX_CHARS = 6

    fun networkSpeed(formattedSpeed: String): CapsuleDisplayModel {
        return CapsuleDisplayModel(
            shortText = formattedSpeed,
            primaryText = formattedSpeed,
            secondaryText = "下载速度",
            expandedText = "下载速度"
        )
    }

    fun modelLoading(title: String, content: String): CapsuleDisplayModel {
        val primary = title.trim().takeIf { it.isNotEmpty() } ?: "本地模型加载中"
        val secondary = content.trim().takeIf { it.isNotEmpty() }
        val short = "模型加载中"
        val detail = compactModelDetail(primary, secondary)
        return CapsuleDisplayModel(
            shortText = short,
            primaryText = compactPrimaryTitle(short, detail),
            secondaryText = secondary,
            expandedText = secondary
        )
    }

    fun voiceTranscription(title: String): CapsuleDisplayModel {
        val primary = cleanTitle(title) ?: "语音转写"
        return CapsuleDisplayModel(
            shortText = compactShortTitle(primary),
            primaryText = primary,
            secondaryText = "语音转写",
            expandedText = primary
        )
    }

    fun textQuickMemo(title: String): CapsuleDisplayModel {
        val primary = cleanTitle(title) ?: "随口记"
        return CapsuleDisplayModel(
            shortText = compactShortTitle(primary),
            primaryText = primary,
            secondaryText = "随口记",
            expandedText = primary
        )
    }

    fun quickMemoRecording(title: String, content: String): CapsuleDisplayModel {
        val primary = cleanTitle(title) ?: "录音中：00:00"
        val secondary = cleanTitle(content) ?: "松开保存"
        return CapsuleDisplayModel(
            shortText = primary,
            primaryText = primary,
            secondaryText = secondary,
            expandedText = secondary
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

    private fun compactModelDetail(title: String, content: String?): String? {
        val titleDetail = cleanTitle(title)
            ?.removeSuffix("加载中")
            ?.removeSuffix("准备中")
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
