package com.antgskds.calendarassistant.core.center

import com.antgskds.calendarassistant.core.course.CourseEventMapper
import com.antgskds.calendarassistant.core.migration.LegacyDataMigrationCoordinator
import com.antgskds.calendarassistant.core.operation.SettingsOperationApi
import com.antgskds.calendarassistant.core.query.SettingsQueryApi
import com.antgskds.calendarassistant.data.model.Course
import com.antgskds.calendarassistant.data.model.ImportResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
    private val httpClient by lazy { HttpClient(Android) }

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

    suspend fun parseExternalCourseImport(content: String): Result<ParsedCourseImport> = runCatching {
        val parsed = CourseImportParser.parseExternalContent(content)
        if (parsed.courses.isEmpty()) error("未解析到有效课程")
        parsed
    }

    suspend fun fetchWakeUpShareImport(shareText: String): Result<ParsedCourseImport> = runCatching {
        val key = CourseImportParser.extractWakeUpKey(shareText) ?: error("剪贴板中未识别到 WakeUp 分享口令")
        val response = httpClient.get {
            url("https://i.wakeup.fun/share_schedule/get")
            parameter("key", key)
            header("User-Agent", "WillDo/2.0")
        }
        if (!response.status.isSuccess()) {
            error("WakeUp 请求失败：HTTP ${response.status.value}")
        }

        val body = response.bodyAsText()
        val root = json.parseToJsonElement(body).jsonObject
        val status = root["status"]?.jsonPrimitive?.content?.toIntOrNull()
        if (status != 1) {
            error(root["message"]?.jsonPrimitive?.content ?: "WakeUp 返回错误状态")
        }
        val data = root["data"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: error("WakeUp 返回数据为空")
        CourseImportParser.parseWakeUpShareData(data)
    }

    suspend fun importParsedCourseImport(
        parsed: ParsedCourseImport,
        mode: ImportMode,
        importSettings: Boolean
    ): Result<Int> = runCatching {
        importParsed(parsed, mode, importSettings)
    }

    suspend fun importWakeUpFile(content: String, mode: ImportMode, importSettings: Boolean): Result<Int> = runCatching {
        val parsed = CourseImportParser.parseExternalContent(content)
        importParsed(parsed, mode, importSettings)
    }

    private suspend fun importParsed(parsed: ParsedCourseImport, mode: ImportMode, importSettings: Boolean): Int {
        if (parsed.courses.isEmpty()) error("未解析到有效课程")

        val effectiveSettings = if (importSettings) {
            val current = settingsQueryApi.settings.value
            val updated = current.copy(
                semesterStartDate = parsed.semesterStartDate ?: current.semesterStartDate,
                totalWeeks = parsed.totalWeeks ?: current.totalWeeks,
                timeTableJson = parsed.timeTableJson ?: current.timeTableJson,
                timeTableConfigJson = parsed.timeTableConfigJson ?: current.timeTableConfigJson
            )
            if (updated != current) settingsOperationApi.updateSettings(updated)
            updated
        } else {
            settingsQueryApi.settings.value
        }

        if (mode == ImportMode.OVERWRITE) {
            CourseEventMapper.extractParentCourses(scheduleCenter.events.value, effectiveSettings)
                .forEach { course ->
                    CourseEventMapper.findParentByCourseId(scheduleCenter.events.value, course.id)
                        ?.id
                        ?.let { scheduleCenter.deleteEvent(it) }
                }
        }

        var imported = 0
        parsed.courses.forEach { course ->
            val existingParent = if (mode == ImportMode.OVERWRITE) {
                null
            } else {
                CourseEventMapper.findParentByCourseId(scheduleCenter.events.value, course.id)
            }
            if (existingParent != null && mode == ImportMode.APPEND) return@forEach
            val event = CourseEventMapper.toParentEvent(course, effectiveSettings, existingParent)
            if (existingParent == null) scheduleCenter.addEvent(event) else scheduleCenter.updateEvent(event)
            imported++
        }
        return imported
    }
}
