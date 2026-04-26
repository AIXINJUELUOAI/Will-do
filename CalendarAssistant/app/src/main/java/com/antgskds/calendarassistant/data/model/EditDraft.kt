package com.antgskds.calendarassistant.data.model

import java.time.LocalDate
import java.time.LocalTime

/**
 * 编辑草稿 —— 编辑弹窗的初始填充数据。
 *
 * 由 ScheduleCenter.prepareEdit*() 生成。
 * 刻意不包含 importId / source / rrule / parentId 等同步字段，
 * 防止 UI 层误操作导致同步身份污染。
 */
data class EditDraft(
    val title: String = "",
    val startDate: LocalDate = LocalDate.now(),
    val startTime: LocalTime = LocalTime.now().withSecond(0).withNano(0),
    val endDate: LocalDate = LocalDate.now(),
    val endTime: LocalTime = LocalTime.now().plusHours(1).withSecond(0).withNano(0),
    val location: String = "",
    val description: String = "",
    val tag: String = "general",
    val color: Int = 0,
    val rrule: String = "",
    val reminders: List<Int> = emptyList(),
    val isRecurring: Boolean = false,
    val editHint: String? = null  // 例如："本次修改将应用到当前实例，并脱离重复系列"
)
