package com.antgskds.calendarassistant.core.ai

import androidx.compose.ui.graphics.Color
import com.antgskds.calendarassistant.data.model.CalendarEventData
import com.antgskds.calendarassistant.data.model.EventTags
import com.antgskds.calendarassistant.data.model.EventType
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.ui.theme.EventColors
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

fun convertAiEventToMyEvent(
    eventData: CalendarEventData,
    currentEventsCount: Int,
    sourceImagePath: String?
): MyEvent {
    val now = LocalDateTime.now()
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    val startDateTime = try {
        if (eventData.startTime.isNotBlank()) LocalDateTime.parse(eventData.startTime, formatter) else now
    } catch (_: Exception) {
        now
    }

    val endDateTime = try {
        if (eventData.endTime.isNotBlank()) LocalDateTime.parse(eventData.endTime, formatter) else startDateTime.plusHours(1)
    } catch (_: Exception) {
        startDateTime.plusHours(1)
    }

    val resolvedTag = when {
        eventData.tag.isNotBlank() && eventData.tag != EventTags.GENERAL -> eventData.tag
        eventData.type == "pickup" -> EventTags.PICKUP
        else -> eventData.tag
    }.ifBlank { EventTags.GENERAL }

    val color = if (EventColors.isNotEmpty()) {
        EventColors[currentEventsCount % EventColors.size]
    } else {
        Color.Gray
    }

    return MyEvent(
        id = UUID.randomUUID().toString(),
        title = eventData.title.trim(),
        startDate = startDateTime.toLocalDate(),
        endDate = endDateTime.toLocalDate(),
        startTime = startDateTime.format(timeFormatter),
        endTime = endDateTime.format(timeFormatter),
        location = eventData.location,
        description = eventData.description,
        color = color,
        sourceImagePath = sourceImagePath,
        eventType = EventType.EVENT,
        tag = resolvedTag
    )
}
