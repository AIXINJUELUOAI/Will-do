package com.antgskds.calendarassistant.data.model.external.wakeup

import kotlinx.serialization.Serializable

/**
 * 对应第 3 行：全局设置
 * 示例：{..."startDate":"2025-9-1","maxWeek":20...}
 */
@Serializable
data class WakeUpSettingsDTO(
    val startDate: String = "", // 开学日期
    val maxWeek: Int = 20,      // 总周数
    val nodes: Int = 12         // 每日节数 (可选)
)

/**
 * 对应第 4 行：课程基本信息（字典）
 * 示例：[{"id":0,"courseName":"高等数学","color":"#FF5722"}]
 */
@Serializable
data class WakeUpCourseBaseDTO(
    val id: Int,                // WakeUp内部ID，用于关联
    val courseName: String,
    val teacher: String = "",   // WakeUp有时候把老师放在这里，有时候放在排课里
    val color: String = "#808080"
)

/**
 * 对应第 5 行：具体排课信息
 * 示例：[{"id":0,"day":1,"startNode":1,"step":2,"startWeek":1,"endWeek":16,"type":0}]
 */
@Serializable
data class WakeUpScheduleDTO(
    val id: Int,          // 关联到 CourseBaseDTO.id
    val day: Int,         // 1=周一, 7=周日
    val startNode: Int,
    val step: Int,        // 持续节数
    val startWeek: Int,
    val endWeek: Int,
    val type: Int,        // 0=全周, 1=单周, 2=双周
    val teacher: String = "",
    val room: String = "" // 教室
)
