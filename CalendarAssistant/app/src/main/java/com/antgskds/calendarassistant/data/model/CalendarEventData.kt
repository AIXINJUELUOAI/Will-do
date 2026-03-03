package com.antgskds.calendarassistant.data.model

import kotlinx.serialization.Serializable

@Serializable
data class CalendarEventData(
    val hasEvent: Boolean = false,
    val title: String = "",
    val startTime: String = "", // 格式: yyyy-MM-dd HH:mm
    val endTime: String = "",   // 格式: yyyy-MM-dd HH:mm
    val location: String = "",
    val description: String = "",
    val type: String = EventType.EVENT,
    val tag: String = EventTags.GENERAL
)