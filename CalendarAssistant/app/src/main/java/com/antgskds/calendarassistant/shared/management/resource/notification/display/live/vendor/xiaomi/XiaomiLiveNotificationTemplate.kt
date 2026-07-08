package com.antgskds.calendarassistant.shared.management.resource.notification.display.live.vendor.xiaomi

import com.antgskds.calendarassistant.service.capsule.CapsuleDisplayModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class XiaomiLiveNotificationContent(
    val title: String,
    val content: String,
    val templateKind: XiaomiLiveTemplateKind,
    val tagText: String?,
    val hintTitle: String?,
    val summaryStatus: String?,
    val summaryTitle: String?
)

enum class XiaomiLiveTemplateKind {
    TEXT_ICON,
    TEXT_ICON_ACTION
}

object XiaomiLiveNotificationTemplate {
    private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun create(
        display: CapsuleDisplayModel,
        useShortTitle: Boolean,
        hasActions: Boolean,
        forceTextIcon: Boolean,
        summaryStatus: String?,
        startMillis: Long,
        endMillis: Long
    ): XiaomiLiveNotificationContent {
        val templateKind = resolveTemplateKind(display, hasActions, forceTextIcon)
        return XiaomiLiveNotificationContent(
            title = buildIslandTitle(display, useShortTitle),
            content = buildContent(display, templateKind, useShortTitle),
            templateKind = templateKind,
            tagText = null,
            hintTitle = if (templateKind == XiaomiLiveTemplateKind.TEXT_ICON_ACTION) {
                buildHintTitle(startMillis, endMillis, display)
            } else {
                null
            },
            summaryStatus = summaryStatus,
            summaryTitle = buildSummaryTitle(display)
        )
    }

    private fun resolveTemplateKind(
        display: CapsuleDisplayModel,
        hasActions: Boolean,
        forceTextIcon: Boolean
    ): XiaomiLiveTemplateKind {
        if (forceTextIcon) return XiaomiLiveTemplateKind.TEXT_ICON
        return if (display.effectiveActions.isNotEmpty() && hasActions) {
            XiaomiLiveTemplateKind.TEXT_ICON_ACTION
        } else {
            XiaomiLiveTemplateKind.TEXT_ICON
        }
    }

    private fun buildIslandTitle(display: CapsuleDisplayModel, useShortTitle: Boolean): String {
        return if (useShortTitle) {
            display.shortText.ifBlank { display.primaryText }
        } else {
            display.primaryText.ifBlank { display.shortText }
        }
    }

    private fun buildContent(
        display: CapsuleDisplayModel,
        templateKind: XiaomiLiveTemplateKind,
        usePrimaryDetailFallback: Boolean
    ): String {
        val candidates = listOf(
            display.secondaryText,
            display.tertiaryText,
            display.expandedText?.lineSequence()?.firstOrNull()
        )
        val filtered = candidates.mapNotNull { sanitizeLine(it) }
            .let { values ->
                if (templateKind == XiaomiLiveTemplateKind.TEXT_ICON_ACTION) {
                    values.filterNot { isTimeRange(it) }
                } else {
                    values
                }
            }
        val content = filtered
            .distinct()
            .joinToString(" · ")
            .ifBlank {
                if (usePrimaryDetailFallback) {
                    primaryDetail(display.primaryText) ?: display.primaryText
                } else {
                    display.primaryText
                }
            }
        return truncate(content, 42)
    }

    private fun primaryDetail(primaryText: String): String? {
        return sanitizeLine(primaryText.substringAfter('|', missingDelimiterValue = ""))
    }

    private fun buildHintTitle(
        startMillis: Long,
        endMillis: Long,
        display: CapsuleDisplayModel
    ): String {
        val timeRange = formatTimeRange(startMillis, endMillis)
        if (timeRange != null) return timeRange
        val candidate = display.tertiaryText
            ?: display.secondaryText
            ?: display.primaryText
        return truncate(sanitizeLine(candidate) ?: display.primaryText, 18)
    }

    private fun formatTimeRange(startMillis: Long, endMillis: Long): String? {
        if (startMillis <= 0 || endMillis <= 0) return null
        return try {
            val zone = ZoneId.systemDefault()
            val start = Instant.ofEpochMilli(startMillis).atZone(zone).toLocalTime()
            val end = Instant.ofEpochMilli(endMillis).atZone(zone).toLocalTime()
            "${start.format(TIME_FORMATTER)}-${end.format(TIME_FORMATTER)}"
        } catch (_: Exception) {
            null
        }
    }

    private fun buildSummaryTitle(display: CapsuleDisplayModel): String {
        val raw = display.shortText.ifBlank { display.primaryText }
        val clean = sanitizeLine(raw) ?: raw
        return truncate(clean, 18)
    }

    private fun isTimeRange(value: String): Boolean {
        val text = value.trim()
        return Regex("\\d{1,2}:\\d{2}(-\\d{1,2}:\\d{2})?").containsMatchIn(text)
    }

    private fun sanitizeLine(value: String?): String? {
        val clean = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return clean.replace("\n", " ").replace("\r", " ")
    }

    private fun truncate(text: String, max: Int): String {
        if (text.length <= max) return text
        return text.take(max - 3) + "..."
    }
}
