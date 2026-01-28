package com.antgskds.calendarassistant.core.calendar

import android.util.Log
import com.antgskds.calendarassistant.core.calendar.CalendarManager.CourseEventInstance
import com.antgskds.calendarassistant.data.model.Course
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.data.model.TimeNode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.min

/**
 * 日历事件映射器
 * 负责应用数据模型与系统日历之间的数据转换
 */
object CalendarEventMapper {

    private const val TAG = "CalendarEventMapper"

    // 定义统一的同步日程颜色：青灰色 (索引 6)
    private const val SYNCED_EVENT_COLOR = 0xFFA2B5BB.toInt()

    /**
     * 将课程展开为单次事件实例列表
     *
     * 影子课程 (isTemp = true) 是用户手动调整的单次课程（如调课、停课），
     * 它们本质上是 startWeek == endWeek 的特殊课程，需要正常同步到系统日历。
     *
     * @param course 课程对象
     * @param semesterStart 学期开始日期
     * @param totalWeeks 总周数
     * @return 课程事件实例列表
     */
    fun expandCourseToInstances(
        course: Course,
        semesterStart: LocalDate,
        totalWeeks: Int
    ): List<CourseEventInstance> {
        val instances = mutableListOf<CourseEventInstance>()

        // 遍历每一周（包括影子课程）
        for (weekNum in course.startWeek..min(course.endWeek, totalWeeks)) {
            // 检查单双周限制
            if (!checkWeekType(weekNum, course.weekType)) {
                continue
            }

            // 计算该周课程的具体日期
            val daysOffset = (weekNum - 1) * 7L + (course.dayOfWeek - 1).toLong()
            val courseDate = semesterStart.plusDays(daysOffset)

            // 检查是否在排除日期列表中
            if (isDateExcluded(courseDate, course.excludedDates)) {
                Log.d(TAG, "日期被排除: $courseDate - ${course.name}")
                continue
            }

            // 创建课程实例
            instances.add(
                CourseEventInstance(
                    course = course,
                    date = courseDate
                )
            )
        }

        val tempTag = if (course.isTemp) "[影子课程]" else ""
        Log.d(TAG, "${tempTag}课程展开: ${course.name} -> ${instances.size} 个实例")
        return instances
    }

    /**
     * 展开所有课程为事件实例列表
     *
     * @param courses 课程列表
     * @param semesterStart 学期开始日期
     * @param totalWeeks 总周数
     * @return 所有课程事件实例列表
     */
    fun expandAllCourses(
        courses: List<Course>,
        semesterStart: LocalDate,
        totalWeeks: Int
    ): List<CourseEventInstance> {
        val allInstances = mutableListOf<CourseEventInstance>()

        courses.forEach { course ->
            val instances = expandCourseToInstances(course, semesterStart, totalWeeks)
            allInstances.addAll(instances)
        }

        Log.d(TAG, "总共展开 ${allInstances.size} 个课程实例")
        return allInstances
    }

    /**
     * 从系统日历事件信息转换为 MyEvent
     *
     * 🔥 修改：
     * 1. 适配全天事件：解析为 00:00 - 23:59，并使用 UTC 防止时区偏移
     * 2. 颜色处理：统一使用青灰色，清晰标识"外部同步"的日程
     */
    fun mapSystemEventToMyEvent(
        systemEvent: CalendarManager.SystemEventInfo,
        fixedId: String? = null
    ): MyEvent? {
        try {
            val startInstant = Instant.ofEpochMilli(systemEvent.startMillis)
            val endInstant = Instant.ofEpochMilli(systemEvent.endMillis)

            val startDate: LocalDate
            val endDate: LocalDate
            val startTimeStr: String
            val endTimeStr: String

            // 1. 处理全天日程与时区问题
            if (systemEvent.allDay) {
                // === 全天事件处理 ===
                // 系统日历的全天事件存储为 UTC 的 00:00
                // 必须使用 UTC 解析，防止加上时区偏移变成 08:00
                val utcZone = ZoneId.of("UTC")

                startDate = startInstant.atZone(utcZone).toLocalDate()

                // 系统日历的全天结束时间通常是"次日0点"，需要减去1纳秒退回当天
                endDate = endInstant.atZone(utcZone).minusNanos(1).toLocalDate()

                // 强制设置为全天范围 (00:00 - 23:59)
                startTimeStr = "00:00"
                endTimeStr = "23:59"
            } else {
                // === 普通事件处理 ===
                // 使用系统默认时区解析
                val systemZone = ZoneId.systemDefault()
                val startDateTime = startInstant.atZone(systemZone).toLocalDateTime()
                val endDateTime = endInstant.atZone(systemZone).toLocalDateTime()

                startDate = startDateTime.toLocalDate()
                endDate = endDateTime.toLocalDate()
                startTimeStr = startDateTime.toLocalTime().toString()
                endTimeStr = endDateTime.toLocalTime().toString()
            }

            // 2. 颜色处理：统一使用青灰色
            // 不再读取 systemEvent.color，直接使用固定颜色
            val colorInt = SYNCED_EVENT_COLOR

            // 优先使用 fixedId，否则生成新 ID
            val eventId = fixedId ?: "sync_calendar_${systemEvent.eventId}_${System.currentTimeMillis()}"

            return MyEvent(
                id = eventId,
                title = systemEvent.title,
                startDate = startDate,
                endDate = endDate,
                startTime = startTimeStr,
                endTime = endTimeStr,
                location = systemEvent.location,
                description = systemEvent.description,
                color = androidx.compose.ui.graphics.Color(colorInt),
                isImportant = false,
                eventType = "event"
            )
        } catch (e: Exception) {
            Log.e(TAG, "转换系统事件失败: eventId=${systemEvent.eventId}", e)
            return null
        }
    }

    /**
     * 生成学期哈希值
     * 用于判断学期配置是否发生变化，需要重新同步课程
     *
     * @param semesterStart 学期开始日期
     * @param totalWeeks 总周数
     * @return 学期哈希字符串
     */
    fun generateSemesterHash(semesterStart: LocalDate, totalWeeks: Int): String {
        return "${semesterStart.toEpochDay()}_$totalWeeks"
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 检查周次是否符合单双周要求
     *
     * @param weekNum 当前周次
     * @param weekType 周类型 (0=全部, 1=单周, 2=双周)
     * @return 是否符合
     */
    private fun checkWeekType(weekNum: Int, weekType: Int): Boolean {
        return when (weekType) {
            0 -> true // 全部
            1 -> weekNum % 2 == 1 // 单周
            2 -> weekNum % 2 == 0 // 双周
            else -> true
        }
    }

    /**
     * 检查日期是否在排除列表中
     *
     * @param date 待检查日期
     * @param excludedDates 排除日期列表 (字符串格式，如 "2024-01-15")
     * @return 是否被排除
     */
    private fun isDateExcluded(date: LocalDate, excludedDates: List<String>): Boolean {
        if (excludedDates.isEmpty()) return false

        val dateStr = date.toString()
        return excludedDates.contains(dateStr)
    }
}
