package com.antgskds.calendarassistant.data.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")

/**
 * 日程展示模型 —— 纯展示用，不是数据库实体。
 *
 * UI 列表（首页/全部日程/胶囊）只使用此模型渲染。
 * 操作（编辑/删除/归档/完成）通过 [action] 路由到 ScheduleCenter。
 */
data class ScheduleDisplayItem(
    // ── 唯一标识 ──
    val stableKey: String,

    // ── 展示字段 ──
    val title: String,
    val startTS: Long,          // 秒级
    val endTS: Long,            // 秒级
    val location: String = "",
    val description: String = "",
    val tag: String = "general",
    val color: Int = 0,
    val state: Int = 0,         // STATE_PENDING / STATE_COMPLETED / STATE_CHECKED_IN
    val isAllDay: Boolean = false,
    val isRecurringInstance: Boolean = false,  // 展示用：是否属于重复系列
    val timeZone: String = "",

    // ── 操作路由 ──
    val action: ActionTarget
) {
    /**
     * 操作目标：告诉 ScheduleCenter 该怎么找到真实数据。
     */
    sealed class ActionTarget {
        /** 真实的单次事件（或已脱离系列的子事件） */
        data class Single(val eventId: Long) : ActionTarget()

        /** 重复系列中的一次实例（虚拟展开的） */
        data class RecurringOccurrence(
            val parentId: Long,
            val occurrenceTs: Long
        ) : ActionTarget()
    }

    // ── 便捷计算属性（供 UI 用，替代 EventExtensions）──

    private fun zone(): ZoneId = try {
        if (timeZone.isBlank()) ZoneId.systemDefault() else ZoneId.of(timeZone)
    } catch (_: Exception) { ZoneId.systemDefault() }

    private fun startZdt(): ZonedDateTime = Instant.ofEpochSecond(startTS).atZone(zone())
    private fun endZdt(): ZonedDateTime = Instant.ofEpochSecond(endTS).atZone(zone())

    val startDate: LocalDate get() = startZdt().toLocalDate()
    val endDate: LocalDate get() = endZdt().toLocalDate()
    val startTime: String get() = startZdt().toLocalTime().format(TIME_FMT)
    val endTime: String get() = endZdt().toLocalTime().format(TIME_FMT)
    val startLocalTime: LocalTime get() = startZdt().toLocalTime()
    val endLocalTime: LocalTime get() = endZdt().toLocalTime()
    val startMillis: Long get() = startTS * 1000L

    val isCompleted: Boolean get() = state == 1  // STATE_COMPLETED
    val isCheckedIn: Boolean get() = state == 2  // STATE_CHECKED_IN
    val isPending: Boolean get() = state == 0    // STATE_PENDING
    val isTransit: Boolean get() = tag == "flight" || tag == "train"

    val composeColor: androidx.compose.ui.graphics.Color
        get() = androidx.compose.ui.graphics.Color(color)

    /** 获取真实事件 id（仅 Single 类型有效） */
    val eventId: Long? get() = (action as? ActionTarget.Single)?.eventId

    /** 获取父事件 id（仅 RecurringOccurrence 类型有效） */
    val parentId: Long? get() = (action as? ActionTarget.RecurringOccurrence)?.parentId

    /** 获取实例时间戳（仅 RecurringOccurrence 类型有效） */
    val occurrenceTs: Long? get() = (action as? ActionTarget.RecurringOccurrence)?.occurrenceTs
}
