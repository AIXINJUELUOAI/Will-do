package com.antgskds.calendarassistant.data.node.store

import android.util.Log
import com.antgskds.calendarassistant.core.course.CourseEventMapper
import com.antgskds.calendarassistant.core.importer.ImportMode
import com.antgskds.calendarassistant.core.importer.WakeUpCourseImporter
import com.antgskds.calendarassistant.data.model.Course
import com.antgskds.calendarassistant.data.model.ImportResult
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.core.util.EventDeduplicator
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal class StoreBackupNode {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun exportCoursesData(courses: List<Course>, settings: MySettings): String {
        val courseEventsData = CourseEventsBackupData(
            courseEvents = CourseEventMapper.toTemplateEvents(courses, settings),
            semesterStartDate = settings.semesterStartDate,
            totalWeeks = settings.totalWeeks,
            timeTableJson = settings.timeTableJson,
            timeTableConfigJson = settings.timeTableConfigJson
        )
        return json.encodeToString(courseEventsData)
    }

    fun normalizeDateFormat(dateStr: String?): String? {
        if (dateStr.isNullOrBlank()) return null

        return try {
            LocalDate.parse(dateStr)
            dateStr
        } catch (_: DateTimeParseException) {
            try {
                val formatter = DateTimeFormatter.ofPattern("yyyy-M-d")
                val parsedDate = LocalDate.parse(dateStr, formatter)
                parsedDate.toString()
            } catch (e: Exception) {
                Log.e("StoreNode", "日期格式标准化失败: $dateStr", e)
                null
            }
        }
    }

    fun buildImportedSemesterSettings(
        currentSettings: MySettings,
        semesterStartDate: String?,
        totalWeeks: Int?
    ): Pair<MySettings, String?> {
        val normalizedDate = normalizeDateFormat(semesterStartDate)
        val newSettings = currentSettings.copy(
            semesterStartDate = normalizedDate ?: currentSettings.semesterStartDate,
            totalWeeks = totalWeeks ?: currentSettings.totalWeeks
        )
        return newSettings to normalizedDate
    }

    fun buildImportedCourseBackupSettings(
        currentSettings: MySettings,
        data: CoursesBackupData
    ): MySettings {
        return currentSettings.copy(
            semesterStartDate = data.semesterStartDate,
            totalWeeks = data.totalWeeks,
            timeTableJson = data.timeTableJson,
            timeTableConfigJson = data.timeTableConfigJson
        )
    }

    fun appendImportedActiveEvents(
        newActiveEvents: List<MyEvent>,
        currentActive: MutableList<MyEvent>
    ): List<MyEvent> {
        if (newActiveEvents.isEmpty()) return emptyList()
        val existingIds = currentActive.map { it.id }.toSet()
        val uniqueNewActive = newActiveEvents.filter { it.id !in existingIds }
        if (uniqueNewActive.size < newActiveEvents.size) {
            Log.w("Import", "跳过 ${newActiveEvents.size - uniqueNewActive.size} 个重复 ID 的活跃事件")
        }
        currentActive.addAll(uniqueNewActive)
        return uniqueNewActive
    }

    fun appendImportedArchivedEvents(
        newArchivedEvents: List<MyEvent>,
        currentArchived: MutableList<MyEvent>
    ) {
        if (newArchivedEvents.isEmpty()) return
        val existingIds = currentArchived.map { it.id }.toSet()
        val uniqueNewArchived = newArchivedEvents.filter { it.id !in existingIds }
        if (uniqueNewArchived.size < newArchivedEvents.size) {
            Log.w("Import", "跳过 ${newArchivedEvents.size - uniqueNewArchived.size} 个重复 ID 的归档事件")
        }
        currentArchived.addAll(uniqueNewArchived)
    }

    suspend fun importCoursesData(
        jsonString: String,
        saveCourses: suspend (List<Course>) -> Unit,
        applyImportedSemesterSettings: suspend (String?, Int?) -> Unit,
        applyImportedCourseBackupSettings: suspend (CoursesBackupData) -> Unit
    ): Result<Unit> {
        val wakeUpImporter = WakeUpCourseImporter()
        if (wakeUpImporter.supports(jsonString)) {
            Log.d("StoreNode", "检测到 WakeUp 课表格式，开始导入")
            return try {
                val result = wakeUpImporter.parse(jsonString)
                if (result.isSuccess) {
                    val importResult = result.getOrThrow()
                    saveCourses(importResult.courses)
                    applyImportedSemesterSettings(importResult.semesterStartDate, importResult.totalWeeks)
                    Log.d("StoreNode", "WakeUp 课表导入成功，共 ${importResult.courses.size} 门课程")
                    Result.success(Unit)
                } else {
                    Log.e("StoreNode", "WakeUp 课表解析失败: ${result.exceptionOrNull()?.message}")
                    Result.failure(result.exceptionOrNull() ?: Exception("解析失败"))
                }
            } catch (e: Exception) {
                Log.e("StoreNode", "WakeUp 课表导入异常", e)
                Result.failure(e)
            }
        }

        return try {
            val v2Data = runCatching { json.decodeFromString<CourseEventsBackupData>(jsonString) }.getOrNull()
            if (v2Data != null) {
                val settings = MySettings(
                    semesterStartDate = v2Data.semesterStartDate,
                    totalWeeks = v2Data.totalWeeks,
                    timeTableJson = v2Data.timeTableJson,
                    timeTableConfigJson = v2Data.timeTableConfigJson
                )
                val courses = CourseEventMapper.extractCourses(v2Data.courseEvents, settings)
                saveCourses(courses)
                applyImportedSemesterSettings(v2Data.semesterStartDate, v2Data.totalWeeks)
                return Result.success(Unit)
            }

            val v1Data = json.decodeFromString<CoursesBackupData>(jsonString)
            saveCourses(v1Data.courses)
            applyImportedCourseBackupSettings(v1Data)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("StoreNode", "导入课程数据失败", e)
            Result.failure(e)
        }
    }

    suspend fun importWakeUpFile(
        content: String,
        mode: ImportMode,
        importSettings: Boolean,
        currentCourses: () -> List<Course>,
        saveCourses: suspend (List<Course>) -> Unit,
        applyImportedSemesterSettings: suspend (String?, Int?) -> Unit
    ): Result<Int> {
        val importer = WakeUpCourseImporter()
        return try {
            val result = importer.parse(content)
            if (result.isSuccess) {
                val importResult = result.getOrThrow()
                val courses = importResult.courses

                if (mode == ImportMode.OVERWRITE) {
                    saveCourses(courses)
                    Log.d("StoreNode", "覆盖模式：清空后导入 ${courses.size} 门课程")
                } else {
                    val existing = currentCourses()
                    val mergedCourses = existing + courses
                    saveCourses(mergedCourses)
                    Log.d("StoreNode", "追加模式：从 ${existing.size} 门增加到 ${mergedCourses.size} 门课程")
                }

                if (importSettings) {
                    applyImportedSemesterSettings(importResult.semesterStartDate, importResult.totalWeeks)
                }

                Result.success(courses.size)
            } else {
                Log.e("StoreNode", "解析失败: ${result.exceptionOrNull()?.message}")
                Result.failure(result.exceptionOrNull() ?: Exception("解析失败"))
            }
        } catch (e: Exception) {
            Log.e("StoreNode", "导入异常", e)
            Result.failure(e)
        }
    }

    fun exportEventsData(events: List<MyEvent>, archivedEvents: List<MyEvent>): String {
        val eventsData = EventsBackupData(events = events, archivedEvents = archivedEvents)
        return json.encodeToString(eventsData)
    }

    fun decodeEventsBackupData(jsonString: String): EventsBackupData {
        return json.decodeFromString<EventsBackupData>(jsonString)
    }

    suspend fun importEventsData(
        jsonString: String,
        preserveArchivedStatus: Boolean,
        existingActiveEvents: () -> List<MyEvent>,
        existingArchivedEvents: () -> List<MyEvent>,
        appendImportedActiveEvents: suspend (List<MyEvent>) -> Unit,
        appendImportedArchivedEvents: suspend (List<MyEvent>) -> Unit,
        upsertRecurringEvents: suspend (List<MyEvent>) -> Unit,
        archiveEvent: suspend (String) -> Unit,
        restoreEvent: suspend (String) -> Unit,
        triggerAutoSync: suspend () -> Unit
    ): Result<ImportResult> {
        return try {
            val data = decodeEventsBackupData(jsonString)
            val allImportEvents = data.events + data.archivedEvents
            val normalizedImportEvents = normalizeImportedEvents(
                data = data,
                allImportEvents = allImportEvents,
                nowMillis = System.currentTimeMillis()
            )

            val deduplicationResult = EventDeduplicator.deduplicateForImport(
                importEvents = normalizedImportEvents,
                existingActiveEvents = existingActiveEvents(),
                existingArchivedEvents = existingArchivedEvents(),
                preserveArchivedStatus = preserveArchivedStatus
            )

            val eventsToAdd = deduplicationResult.toAdd
            if (eventsToAdd.isNotEmpty()) {
                val newActiveEvents = eventsToAdd.filter { it.archivedAt == null }
                val newArchivedEvents = eventsToAdd.filter { it.archivedAt != null }

                appendImportedActiveEvents(newActiveEvents)
                appendImportedArchivedEvents(newArchivedEvents)
                upsertRecurringEvents(eventsToAdd)
            }

            val archiveStatusUpdates = deduplicationResult.toUpdateArchiveStatus
            if (archiveStatusUpdates.isNotEmpty()) {
                for ((event, shouldBeArchived) in archiveStatusUpdates) {
                    if (shouldBeArchived) {
                        Log.d("StoreNode", "归档状态更新：归档事件 - ${event.title}")
                        archiveEvent(event.id)
                    } else {
                        Log.d("StoreNode", "归档状态更新：还原事件 - ${event.title}")
                        restoreEvent(event.id)
                    }
                }
            }

            if (eventsToAdd.isNotEmpty()) {
                triggerAutoSync()
            }

            val importResult = ImportResult(
                successCount = deduplicationResult.toAdd.size,
                skippedCount = deduplicationResult.toSkip.size,
                archiveStatusUpdateCount = deduplicationResult.toUpdateArchiveStatus.size
            )

            Log.d(
                "StoreNode",
                "导入完成: 新增 ${importResult.successCount}, 跳过 ${importResult.skippedCount}, 归档状态更新 ${importResult.archiveStatusUpdateCount}"
            )

            Result.success(importResult)
        } catch (e: Exception) {
            Log.e("StoreNode", "导入日程数据失败", e)
            Result.failure(e)
        }
    }

    fun normalizeImportedEvents(
        data: EventsBackupData,
        allImportEvents: List<MyEvent>,
        nowMillis: Long
    ): List<MyEvent> {
        val archivedIds = data.archivedEvents.map { it.id }.toSet()
        val activeIds = data.events.map { it.id }.toSet()
        return allImportEvents.map { event ->
            when {
                event.id in archivedIds -> {
                    if (event.archivedAt == null) {
                        Log.d("StoreNode", "修正归档事件缺少 archivedAt 字段: ${event.title}")
                        event.copy(archivedAt = nowMillis)
                    } else {
                        event
                    }
                }

                event.id in activeIds -> event.copy(archivedAt = null)
                else -> event
            }
        }
    }
}

@Serializable
internal data class CoursesBackupData(
    val courses: List<Course>,
    val semesterStartDate: String,
    val totalWeeks: Int,
    val timeTableJson: String,
    val timeTableConfigJson: String = ""
)

@Serializable
internal data class CourseEventsBackupData(
    val version: Int = 2,
    val courseEvents: List<MyEvent>,
    val semesterStartDate: String,
    val totalWeeks: Int,
    val timeTableJson: String,
    val timeTableConfigJson: String = ""
)

@Serializable
internal data class EventsBackupData(
    val events: List<MyEvent>,
    val archivedEvents: List<MyEvent> = emptyList()
)
