package com.antgskds.calendarassistant.core.util

import android.util.Log
import com.antgskds.calendarassistant.data.model.Course
import com.antgskds.calendarassistant.data.model.MyEvent

object DataSanitizer {
    private const val TAG = "DataSanitizer"

    data class SanitizeResult<T>(
        val data: List<T>,
        val removedTitles: List<String>,
        val isFromBackup: Boolean = false
    )

    fun sanitizeEvents(events: List<MyEvent>): SanitizeResult<MyEvent> {
        if (events.isEmpty()) {
            return SanitizeResult(emptyList(), emptyList())
        }

        val removedTitles = mutableListOf<String>()
        
        val sanitized = events
            .mapIndexed { index, event ->
                var sanitizedEvent = event
                var shouldRemove = false
                var removeReason = ""

                when {
                    event.id.isBlank() -> {
                        shouldRemove = true
                        removeReason = "空ID"
                    }
                    event.title.isBlank() -> {
                        shouldRemove = true
                        removeReason = "空标题"
                    }
                    event.startDate == null -> {
                        shouldRemove = true
                        removeReason = "空开始日期"
                    }
                    event.endDate == null -> {
                        shouldRemove = true
                        removeReason = "空结束日期"
                    }
                    event.startTime.isBlank() -> {
                        shouldRemove = true
                        removeReason = "空开始时间"
                    }
                    event.endTime.isBlank() -> {
                        shouldRemove = true
                        removeReason = "空结束时间"
                    }
                }

                if (shouldRemove) {
                    removedTitles.add("${event.title}($removeReason)")
                    null
                } else {
                    sanitizedEvent
                }
            }
            .filterNotNull()

        val uniqueById = sanitized
            .groupBy { it.id }
            .map { (_, events) ->
                if (events.size > 1) {
                    removedTitles.addAll(events.drop(1).map { "${it.title}(重复ID)" })
                }
                events.first()
            }

        if (removedTitles.isNotEmpty()) {
            Log.w(TAG, "数据自愈: 清理了 ${removedTitles.size} 条异常日程: $removedTitles")
        }

        return SanitizeResult(uniqueById, removedTitles)
    }

    fun sanitizeCourses(courses: List<Course>): SanitizeResult<Course> {
        if (courses.isEmpty()) {
            return SanitizeResult(emptyList(), emptyList())
        }

        val removedTitles = mutableListOf<String>()

        val sanitized = courses
            .map { course ->
                var shouldRemove = false
                var removeReason = ""

                when {
                    course.id.isBlank() -> {
                        shouldRemove = true
                        removeReason = "空ID"
                    }
                    course.name.isBlank() -> {
                        shouldRemove = true
                        removeReason = "空课程名"
                    }
                    course.startWeek <= 0 || course.startWeek > 20 -> {
                        shouldRemove = true
                        removeReason = "无效起始周"
                    }
                    course.endWeek <= 0 || course.endWeek > 20 -> {
                        shouldRemove = true
                        removeReason = "无效结束周"
                    }
                    course.dayOfWeek < 1 || course.dayOfWeek > 7 -> {
                        shouldRemove = true
                        removeReason = "无效星期"
                    }
                }

                if (shouldRemove) {
                    removedTitles.add("${course.name}($removeReason)")
                    null
                } else {
                    course
                }
            }
            .filterNotNull()

        val uniqueById = sanitized
            .groupBy { it.id }
            .map { (_, courses) ->
                if (courses.size > 1) {
                    removedTitles.addAll(courses.drop(1).map { "${it.name}(重复ID)" })
                }
                courses.first()
            }

        if (removedTitles.isNotEmpty()) {
            Log.w(TAG, "数据自愈: 清理了 ${removedTitles.size} 条异常课程: $removedTitles")
        }

        return SanitizeResult(uniqueById, removedTitles)
    }

    fun buildCleanupSummary(
        eventRemoved: List<String>,
        courseRemoved: List<String>
    ): String {
        val parts = mutableListOf<String>()

        if (eventRemoved.isNotEmpty()) {
            val titles = eventRemoved.take(5).joinToString("、")
            val suffix = if (eventRemoved.size > 5) "等${eventRemoved.size}个" else ""
            parts.add("日程: $titles$suffix")
        }

        if (courseRemoved.isNotEmpty()) {
            val titles = courseRemoved.take(5).joinToString("、")
            val suffix = if (courseRemoved.size > 5) "等${courseRemoved.size}个" else ""
            parts.add("课程: $titles$suffix")
        }

        return if (parts.isEmpty()) {
            ""
        } else {
            parts.joinToString("，")
        }
    }
}
