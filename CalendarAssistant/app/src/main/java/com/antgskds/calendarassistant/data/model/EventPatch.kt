package com.antgskds.calendarassistant.data.model

/**
 * 事件修改补丁 —— 用户在编辑弹窗中实际修改的字段。
 *
 * 由 AddEventDialog 提交，ScheduleCenter 负责将其合并到真实 Event 上。
 * 只包含用户可编辑的字段，不包含任何同步/身份/层级字段。
 */
data class EventPatch(
    val title: String,
    val startTS: Long,          // 秒级时间戳
    val endTS: Long,            // 秒级时间戳
    val location: String = "",
    val description: String = "",
    val tag: String = "general",
    val color: Int = 0,
    val rrule: String = "",
    val reminder1Minutes: Int = -1,
    val reminder2Minutes: Int = -1,
    val reminder3Minutes: Int = -1
)
