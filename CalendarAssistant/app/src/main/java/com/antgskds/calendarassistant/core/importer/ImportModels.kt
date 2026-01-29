package com.antgskds.calendarassistant.core.importer

import com.antgskds.calendarassistant.data.model.Course
import com.antgskds.calendarassistant.data.model.TimeNode

/**
 * 导入结果封装
 * 包含解析出的课程列表、作息时间、以及学期设置
 */
data class ImportResult(
    val courses: List<Course>,
    val timeNodes: List<TimeNode>,
    val semesterStartDate: String?, // 可能为 null，表示文件中没包含
    val totalWeeks: Int?            // 可能为 null
)

/**
 * 导入模式
 */
enum class ImportMode {
    OVERWRITE, // 覆盖模式：清空现有课程，写入新课程
    APPEND     // 追加模式：保留现有课程，添加新课程
}
