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
    // --- 新增：用于区分事件类型 ---
    // "event" = 普通日程 (默认)
    // "pickup" = 取件码/取餐码等
    val type: String = "event"
)