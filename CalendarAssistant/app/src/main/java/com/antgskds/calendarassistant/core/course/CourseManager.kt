package com.antgskds.calendarassistant.core.course

import com.antgskds.calendarassistant.data.model.Course
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.data.model.TimeNode
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object CourseManager {

    fun getDailyCourses(
        targetDate: LocalDate,
        allCourses: List<Course>,
        settings: MySettings
    ): List<MyEvent> {
        val startDateStr = settings.semesterStartDate
        // 允许未设置开学日期时显示课程（视为第1周）
        val semesterStart = try {
            if (startDateStr.isNotBlank()) LocalDate.parse(startDateStr) else targetDate
        } catch (e: Exception) { targetDate }

        // 计算周次
        val daysDiff = ChronoUnit.DAYS.between(semesterStart, targetDate)
        // 如果未设置开学日期，默认显示所有课程，视为第1周
        val currentWeek = if (startDateStr.isBlank()) 1 else (daysDiff / 7).toInt() + 1

        val isOddWeek = currentWeek % 2 != 0
        val currentWeekType = if (isOddWeek) 1 else 2
        val dayOfWeek = targetDate.dayOfWeek.value
        val targetDateStr = targetDate.toString()

        val timeNodes = try {
            if (settings.timeTableJson.isNotBlank()) {
                Json { ignoreUnknownKeys = true }.decodeFromString<List<TimeNode>>(settings.timeTableJson)
            } else {
                getDefaultTimeNodes()
            }
        } catch (e: Exception) {
            getDefaultTimeNodes()
        }

        return allCourses.filter { course ->
            // 如果未设置周次范围（如0-0），默认全显，否则校验范围
            val weekMatch = (course.startWeek == 0 && course.endWeek == 0) || (currentWeek in course.startWeek..course.endWeek)
            val typeMatch = course.weekType == 0 || course.weekType == currentWeekType
            val dayMatch = course.dayOfWeek == dayOfWeek
            val notExcluded = !course.excludedDates.contains(targetDateStr)

            weekMatch && typeMatch && dayMatch && notExcluded
        }.mapNotNull { course ->
            val startNode = timeNodes.find { it.index == course.startNode }
            val endNode = timeNodes.find { it.index == course.endNode }

            if (startNode != null && endNode != null) {
                // 🔥 关键格式：course_{ID}_{DATE}
                val virtualId = "course_${course.id}_${targetDateStr}"

                MyEvent(
                    id = virtualId,
                    title = course.name,
                    startDate = targetDate,
                    endDate = targetDate,
                    startTime = startNode.startTime,
                    endTime = endNode.endTime,
                    location = course.location + (if (course.teacher.isNotBlank()) " | ${course.teacher}" else ""),
                    description = "第${course.startNode}-${course.endNode}节",
                    color = course.color,
                    eventType = "course"
                )
            } else {
                null
            }
        }
    }

    private fun getDefaultTimeNodes(): List<TimeNode> {
        // 兜底数据
        return (1..12).map {
            TimeNode(it, String.format("%02d:00", 8+it), String.format("%02d:45", 8+it))
        }
    }
}