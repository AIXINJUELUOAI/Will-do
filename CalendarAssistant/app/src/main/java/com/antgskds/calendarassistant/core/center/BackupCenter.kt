package com.antgskds.calendarassistant.core.center

import android.graphics.Color
import com.antgskds.calendarassistant.core.course.CourseEventMapper
import com.antgskds.calendarassistant.core.migration.LegacyDataMigrationCoordinator
import com.antgskds.calendarassistant.core.operation.SettingsOperationApi
import com.antgskds.calendarassistant.core.query.SettingsQueryApi
import com.antgskds.calendarassistant.data.model.Course
import com.antgskds.calendarassistant.data.model.ImportResult
import com.antgskds.calendarassistant.data.model.external.wakeup.WakeUpCourseBaseDTO
import com.antgskds.calendarassistant.data.model.external.wakeup.WakeUpScheduleDTO
import com.antgskds.calendarassistant.data.model.external.wakeup.WakeUpSettingsDTO
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class BackupCenter(
    private val scheduleCenter: ScheduleCenter,
    private val settingsQueryApi: SettingsQueryApi,
    private val settingsOperationApi: SettingsOperationApi,
    private val legacyDataMigrationCoordinator: LegacyDataMigrationCoordinator
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
        prettyPrint = true
        isLenient = true
    }

    suspend fun exportCoursesData(): String {
        val courses = CourseEventMapper.extractParentCourses(scheduleCenter.events.value, settingsQueryApi.settings.value)
        return json.encodeToString(courses)
    }

    suspend fun importCoursesData(jsonString: String): Result<Unit> = runCatching {
        val courses = json.decodeFromString<List<Course>>(jsonString)
        val settings = settingsQueryApi.settings.value
        val existing = scheduleCenter.events.value
        courses.forEach { course ->
            val parent = CourseEventMapper.findParentByCourseId(existing, course.id)
            val event = CourseEventMapper.toParentEvent(course, settings, parent)
            if (parent == null) scheduleCenter.addEvent(event) else scheduleCenter.updateEvent(event)
        }
    }

    suspend fun exportEventsData(): String = legacyDataMigrationCoordinator.exportEventsData()

    suspend fun importEventsData(jsonString: String): Result<ImportResult> {
        val result = legacyDataMigrationCoordinator.importEventsData(jsonString)
        if (result.isSuccess) {
            scheduleCenter.refreshAll()
        }
        return result
    }

    suspend fun importWakeUpFile(content: String, mode: ImportMode, importSettings: Boolean): Result<Int> = runCatching {
        val parsed = parseWakeUp(content)
        if (parsed.courses.isEmpty()) error("未解析到有效课程")

        if (importSettings) {
            val current = settingsQueryApi.settings.value
            settingsOperationApi.updateSettings(
                current.copy(
                    semesterStartDate = parsed.semesterStartDate ?: current.semesterStartDate,
                    totalWeeks = parsed.totalWeeks ?: current.totalWeeks
                )
            )
        }

        if (mode == ImportMode.OVERWRITE) {
            CourseEventMapper.extractParentCourses(scheduleCenter.events.value, settingsQueryApi.settings.value)
                .forEach { course ->
                    CourseEventMapper.findParentByCourseId(scheduleCenter.events.value, course.id)
                        ?.id
                        ?.let { scheduleCenter.deleteEvent(it) }
                }
        }

        val settings = settingsQueryApi.settings.value
        var imported = 0
        parsed.courses.forEach { course ->
            val existingParent = if (mode == ImportMode.OVERWRITE) {
                null
            } else {
                CourseEventMapper.findParentByCourseId(scheduleCenter.events.value, course.id)
            }
            if (existingParent != null && mode == ImportMode.APPEND) return@forEach
            val event = CourseEventMapper.toParentEvent(course, settings, existingParent)
            if (existingParent == null) scheduleCenter.addEvent(event) else scheduleCenter.updateEvent(event)
            imported++
        }
        imported
    }

    private fun parseWakeUp(content: String): ParsedWakeUpCourses {
        val lines = content.lines().map { it.trim() }.filter { it.isNotBlank() }
        val baseMap = mutableMapOf<Int, WakeUpCourseBaseDTO>()
        val courses = mutableListOf<Course>()
        var semesterStartDate: String? = null
        var totalWeeks: Int? = null

        lines.forEach { line ->
            when {
                line.startsWith("{") && line.contains("\"startDate\"") -> {
                    runCatching { json.decodeFromString<WakeUpSettingsDTO>(line) }.getOrNull()?.let { settings ->
                        semesterStartDate = settings.startDate.takeIf { it.isNotBlank() }
                        totalWeeks = settings.maxWeek.takeIf { it > 0 }
                    }
                }

                line.startsWith("[") && line.contains("\"courseName\"") -> {
                    runCatching { json.decodeFromString<List<WakeUpCourseBaseDTO>>(line) }.getOrNull()
                        ?.forEach { baseMap[it.id] = it }
                }

                line.startsWith("[") && line.contains("\"startNode\"") && line.contains("\"step\"") && line.contains("\"day\"") -> {
                    val schedules = runCatching { json.decodeFromString<List<WakeUpScheduleDTO>>(line) }.getOrNull().orEmpty()
                    schedules.forEachIndexed { index, schedule ->
                        val base = baseMap[schedule.id] ?: return@forEachIndexed
                        val teacher = schedule.teacher.ifBlank { base.teacher }
                        val room = schedule.room
                        val id = CourseEventMapper.stableImportId(
                            name = base.courseName,
                            teacher = teacher,
                            room = room,
                            dayOfWeek = schedule.day,
                            startNode = schedule.startNode,
                            endNode = schedule.startNode + schedule.step - 1,
                            startWeek = schedule.startWeek,
                            endWeek = schedule.endWeek,
                            weekType = schedule.type
                        )
                        courses += Course(
                            id = id,
                            name = base.courseName,
                            location = room,
                            teacher = teacher,
                            color = paletteColor(courses.size + index),
                            dayOfWeek = schedule.day,
                            startNode = schedule.startNode,
                            endNode = schedule.startNode + schedule.step - 1,
                            startWeek = schedule.startWeek,
                            endWeek = schedule.endWeek,
                            weekType = schedule.type.coerceIn(0, 2)
                        )
                    }
                }
            }
        }
        return ParsedWakeUpCourses(courses, semesterStartDate, totalWeeks)
    }

    private fun paletteColor(index: Int): Int {
        val colors = intArrayOf(
            Color.rgb(66, 133, 244),
            Color.rgb(52, 168, 83),
            Color.rgb(251, 188, 5),
            Color.rgb(234, 67, 53),
            Color.rgb(156, 39, 176),
            Color.rgb(0, 150, 136),
            Color.rgb(255, 112, 67),
            Color.rgb(63, 81, 181)
        )
        return colors[index % colors.size]
    }

    private data class ParsedWakeUpCourses(
        val courses: List<Course>,
        val semesterStartDate: String?,
        val totalWeeks: Int?
    )
}
