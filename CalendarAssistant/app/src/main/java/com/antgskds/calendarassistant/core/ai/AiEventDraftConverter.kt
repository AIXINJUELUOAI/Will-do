package com.antgskds.calendarassistant.core.ai

import androidx.compose.ui.graphics.toArgb
import com.antgskds.calendarassistant.core.model.RecognitionDraft
import com.antgskds.calendarassistant.calendar.models.EventTags
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.inferEventTagFromDescription
import com.antgskds.calendarassistant.core.util.mergeSourceImageMarker
import com.antgskds.calendarassistant.ui.theme.AppEventColors
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * 将 AI 识别草稿转为 Event 对象（尚未持久化，id = null）。
 * tag 在识别阶段已注入；识别导入事件统一分配 APP 内色盘颜色。
 */
fun convertDraftToEvent(
    draft: RecognitionDraft,
    sourceImagePath: String? = null,
    defaultDurationMinutes: Int = 60,
    forceInstantCodeTimeToNow: Boolean = false
): Event {
    val resolvedTag = inferEventTagFromDescription(draft.description, draft.tag.ifBlank { EventTags.GENERAL })
    val resolvedStartTs = resolveStartTs(draft.startTS, resolvedTag, forceInstantCodeTimeToNow)
    val resolvedEndTs = resolveEndTs(resolvedStartTs, defaultDurationMinutes)

    return Event(
        id = null,
        title = draft.title.trim(),
        startTS = resolvedStartTs,
        endTS = resolvedEndTs,
        location = draft.location,
        description = mergeSourceImageMarker(draft.description, sourceImagePath),
        timeZone = draft.timeZone,
        tag = resolvedTag,
        color = randomRecognizedEventColor()
    )
}

private fun resolveStartTs(startTs: Long, tag: String, forceInstantCodeTimeToNow: Boolean): Long {
    return if (forceInstantCodeTimeToNow && isInstantCodeTag(tag)) {
        Instant.now().epochSecond
    } else {
        startTs
    }
}

private fun isInstantCodeTag(tag: String): Boolean {
    return when (tag.trim().lowercase()) {
        EventTags.PICKUP,
        EventTags.FOOD,
        EventTags.TICKET,
        EventTags.SENDER -> true
        else -> false
    }
}

private fun resolveEndTs(startTs: Long, defaultDurationMinutes: Int): Long {
    if (startTs <= 0L) return startTs
    return if (defaultDurationMinutes == END_OF_DAY_DURATION) {
        val zoneId = ZoneId.systemDefault()
        Instant.ofEpochSecond(startTs)
            .atZone(zoneId)
            .toLocalDate()
            .atTime(LocalTime.of(23, 59))
            .atZone(zoneId)
            .toEpochSecond()
    } else {
        startTs + defaultDurationMinutes.coerceAtLeast(1).toLong() * 60L
    }
}

private fun randomRecognizedEventColor(): Int = AppEventColors.random().toArgb()

const val END_OF_DAY_DURATION = -1
