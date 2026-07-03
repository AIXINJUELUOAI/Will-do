package com.antgskds.calendarassistant.shared.management.resource.notification.display.live.template

import com.antgskds.calendarassistant.service.capsule.CapsuleActionSpec
import com.antgskds.calendarassistant.service.capsule.CapsuleDisplayModel

internal object ScheduleLiveDisplaySupport {
    enum class Mode {
        COMPACT,
        FULL
    }

    fun general(
        title: String,
        time: String?,
        location: String?,
        description: String?,
        action: CapsuleActionSpec?,
        mode: Mode
    ): CapsuleDisplayModel {
        val headerTitle = clean(title) ?: "日程提醒"
        return compose(
            headerTitle = headerTitle,
            shortText = headerTitle,
            fullBodyLines = cleanLines(time, location, description),
            compactBodyLines = scheduleCompactLines(time, location, description),
            action = action,
            mode = mode
        )
    }

    fun daily(
        title: String,
        shortTitle: String,
        fullLines: List<String?>,
        compactLines: List<String?>,
        mode: Mode
    ): CapsuleDisplayModel {
        return compose(
            headerTitle = clean(title) ?: "日程提醒",
            shortText = clean(shortTitle) ?: clean(title) ?: "提醒",
            fullBodyLines = cleanLines(*fullLines.toTypedArray()),
            compactBodyLines = cleanLines(*compactLines.toTypedArray()),
            mode = mode
        )
    }

    private fun compose(
        headerTitle: String,
        shortText: String,
        fullBodyLines: List<String>,
        compactBodyLines: List<String>,
        mode: Mode,
        action: CapsuleActionSpec? = null
    ): CapsuleDisplayModel {
        val bodyLines = if (mode == Mode.COMPACT) compactBodyLines else fullBodyLines
        val filteredBodyLines = bodyLines.filterNot { it == headerTitle }
        val fallbackLines = if (filteredBodyLines.isNotEmpty()) filteredBodyLines else bodyLines
        return CapsuleDisplayModel(
            shortText = shortText,
            primaryText = headerTitle,
            secondaryText = fallbackLines.getOrNull(0),
            tertiaryText = if (mode == Mode.COMPACT) null else fallbackLines.getOrNull(1),
            expandedText = bodyLines.filterNot { it == headerTitle }.joinToString("\n").ifBlank { null },
            action = action
        )
    }

    private fun scheduleCompactLines(time: String?, location: String?, description: String?): List<String> {
        val cleanDescription = clean(description)
        val cleanLocation = clean(location)
        val cleanTime = clean(time)
        return when {
            cleanDescription != null && cleanLocation != null -> listOf(cleanDescription, cleanLocation)
            cleanDescription != null -> listOf(cleanDescription)
            cleanLocation != null -> listOf(cleanLocation)
            cleanTime != null -> listOf(cleanTime)
            else -> emptyList()
        }
    }

    private fun cleanLines(vararg values: String?): List<String> {
        return values.mapNotNull(::clean).distinct()
    }

    private fun clean(value: String?): String? {
        val clean = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return if (clean.equals("null", ignoreCase = true)) null else clean
    }
}
