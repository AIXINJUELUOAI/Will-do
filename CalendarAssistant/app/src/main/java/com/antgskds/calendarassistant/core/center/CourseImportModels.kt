package com.antgskds.calendarassistant.core.center

import com.antgskds.calendarassistant.data.model.Course

enum class CourseImportSourceType {
    WAKEUP_FILE,
    WAKEUP_SHARE,
    ICS
}

data class ParsedCourseImport(
    val sourceType: CourseImportSourceType,
    val sourceName: String,
    val courses: List<Course>,
    val semesterStartDate: String? = null,
    val totalWeeks: Int? = null,
    val timeTableJson: String? = null,
    val timeTableConfigJson: String? = null,
    val timeNodeCount: Int = 0
) {
    val canImportSettings: Boolean
        get() = semesterStartDate != null || totalWeeks != null || timeTableJson != null

    val hasTimeTable: Boolean
        get() = !timeTableJson.isNullOrBlank() && timeNodeCount > 0
}
