package com.antgskds.calendarassistant.feature.quickmemo.domain

import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoEntity
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoTranscriptionStatus
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoType
import java.net.URI

data class QuickMemoFloatingPresentation(
    val compactText: String,
    val expandedText: String,
    val hasSummary: Boolean,
    val sourceUrl: String?,
    val showAudio: Boolean,
)

/** 悬浮卡只展示一项右侧操作：原链接优先，普通语音保留播放。 */
object QuickMemoFloatingPresentationPolicy {
    fun present(memo: QuickMemoEntity, summary: String? = null): QuickMemoFloatingPresentation {
        val url = sourceUrl(memo.sourceUrl)
        val body = memo.bodyText.ifBlank { url ?: fallbackText(memo) }
        val validSummary = summary?.trim()?.takeIf(String::isNotBlank)
        return QuickMemoFloatingPresentation(
            compactText = memo.title.trim().ifBlank { body },
            expandedText = validSummary ?: body,
            hasSummary = validSummary != null,
            sourceUrl = url,
            showAudio = url == null && memo.type == QuickMemoType.VOICE,
        )
    }

    /** 保留分享 token 和完整查询参数；只允许可交给浏览器的 HTTP(S) 原链接。 */
    fun sourceUrl(value: String?): String? {
        val url = value?.trim()?.takeIf(String::isNotBlank) ?: return null
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        return url.takeIf {
            (uri.scheme.equals("https", true) || uri.scheme.equals("http", true)) &&
                !uri.host.isNullOrBlank() && uri.rawUserInfo == null
        }
    }

    fun fallbackText(memo: QuickMemoEntity): String = when {
        memo.type == QuickMemoType.IMAGE -> "图片随口记"
        memo.type != QuickMemoType.VOICE -> "空白随口记"
        memo.transcriptionStatus == QuickMemoTranscriptionStatus.PENDING ||
            memo.transcriptionStatus == QuickMemoTranscriptionStatus.PROCESSING -> "转写中"
        memo.transcriptionStatus == QuickMemoTranscriptionStatus.FAILED -> "转写失败，可重试"
        else -> "仅音频"
    }
}
