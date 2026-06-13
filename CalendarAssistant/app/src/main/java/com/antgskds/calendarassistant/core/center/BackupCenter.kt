package com.antgskds.calendarassistant.core.center

import android.content.Context
import android.net.Uri
import com.antgskds.calendarassistant.calendar.data.EventsDatabase
import com.antgskds.calendarassistant.core.ai.AiPrompts
import com.antgskds.calendarassistant.core.attachment.EventAttachmentManager
import com.antgskds.calendarassistant.core.course.CourseEventMapper
import com.antgskds.calendarassistant.core.migration.LegacyDataMigrationCoordinator
import com.antgskds.calendarassistant.core.operation.SettingsOperationApi
import com.antgskds.calendarassistant.core.query.SettingsQueryApi
import com.antgskds.calendarassistant.data.model.AppBackupAttachmentDto
import com.antgskds.calendarassistant.data.model.AppBackupData
import com.antgskds.calendarassistant.data.model.AppBackupImportResult
import com.antgskds.calendarassistant.data.model.AppBackupManifest
import com.antgskds.calendarassistant.data.model.AppBackupOptions
import com.antgskds.calendarassistant.data.model.AppBackupQuickMemoDto
import com.antgskds.calendarassistant.data.model.AppBackupQuickMemoSuggestionDto
import com.antgskds.calendarassistant.data.model.Course
import com.antgskds.calendarassistant.data.model.ImportResult
import com.antgskds.calendarassistant.core.quickmemo.QuickMemoEntity
import com.antgskds.calendarassistant.core.quickmemo.QuickMemoSuggestionEntity
import com.antgskds.calendarassistant.core.quickmemo.QuickMemoSuggestionStatus
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
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupCenter(
    private val context: Context,
    private val scheduleCenter: ScheduleCenter,
    private val settingsQueryApi: SettingsQueryApi,
    private val settingsOperationApi: SettingsOperationApi,
    private val attachmentManager: EventAttachmentManager,
    private val legacyDataMigrationCoordinator: LegacyDataMigrationCoordinator
) {
    private val appContext = context.applicationContext
    private val db = EventsDatabase.getInstance(appContext)
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

    fun getAttachmentCount(): Int = attachmentManager.getAttachmentCount()

    fun estimateAttachmentBytes(): Long = attachmentManager.estimateTotalSize()

    suspend fun exportBackupData(options: AppBackupOptions): String {
        val normalized = normalizeBackupOptions(options)
        if (normalized.includeAttachments) error("附件备份需要导出为 ZIP")
        if (normalized.includeQuickMemos) error("随口记备份需要导出为 ZIP")
        return json.encodeToString(buildBackupData(normalized))
    }

    suspend fun exportBackupZip(uri: Uri, options: AppBackupOptions) {
        val normalized = normalizeBackupOptions(options)
        val exportItems = if (normalized.includeAttachments) buildAttachmentExportItems() else emptyList()
        val quickMemoExportItems = if (normalized.includeQuickMemos) buildQuickMemoAudioExportItems() else emptyList()
        val backupData = buildBackupData(
            options = normalized,
            attachmentDtos = exportItems.map { it.dto },
            quickMemoAudioFileNames = quickMemoExportItems.associate { it.backupKey to it.fileName }
        )
        appContext.contentResolver.openOutputStream(uri)?.use { output ->
            ZipOutputStream(output.buffered()).use { zip ->
                zip.putJson("manifest.json", json.encodeToString(AppBackupManifest(createdAt = backupData.createdAt, options = normalized)))
                zip.putJson("backup.json", json.encodeToString(backupData))
                exportItems.forEach { item ->
                    if (item.file.exists()) {
                        zip.putNextEntry(ZipEntry("attachments/${item.dto.fileName}"))
                        item.file.inputStream().use { input -> input.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
                quickMemoExportItems.forEach { item ->
                    if (item.file.exists()) {
                        zip.putNextEntry(ZipEntry("quick_memos/audio/${item.fileName}"))
                        item.file.inputStream().use { input -> input.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
        } ?: error("无法打开导出文件")
    }

    suspend fun importBackupJson(jsonString: String, options: AppBackupOptions): Result<AppBackupImportResult> = runCatching {
        val appBackup = runCatching { json.decodeFromString<AppBackupData>(jsonString) }.getOrNull()
        if (appBackup != null && appBackup.version >= 1) {
            importAppBackupData(appBackup, options, null)
        } else {
            val eventsResult = if (options.includeEvents) importEventsData(jsonString).getOrThrow() else null
            AppBackupImportResult(eventsResult = eventsResult)
        }
    }

    suspend fun importBackupZip(uri: Uri, options: AppBackupOptions): Result<AppBackupImportResult> = runCatching {
        val tempDir = File(appContext.cacheDir, "backup_import_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            var backupJson = ""
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input.buffered()).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val safeName = entry.name.replace('\\', '/')
                            when (safeName) {
                                "backup.json" -> backupJson = zip.readBytes().toString(Charsets.UTF_8)
                                else -> if (safeName.startsWith("attachments/")) {
                                    val file = File(tempDir, File(safeName).name)
                                    file.outputStream().use { output -> zip.copyTo(output) }
                                } else if (safeName.startsWith("quick_memos/audio/")) {
                                    val audioDir = File(tempDir, QUICK_MEMO_AUDIO_IMPORT_DIR).apply { mkdirs() }
                                    val file = File(audioDir, File(safeName).name)
                                    file.outputStream().use { output -> zip.copyTo(output) }
                                }
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            } ?: error("无法读取备份文件")
            if (backupJson.isBlank()) error("备份文件缺少 backup.json")
            val data = json.decodeFromString<AppBackupData>(backupJson)
            importAppBackupData(data, options, tempDir)
        } finally {
            runCatching { tempDir.deleteRecursively() }
        }
    }

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

    private fun normalizeBackupOptions(options: AppBackupOptions): AppBackupOptions {
        return if (options.includeAttachments && !options.includeEvents) {
            options.copy(includeEvents = true)
        } else {
            options
        }
    }

    private suspend fun buildBackupData(
        options: AppBackupOptions,
        attachmentDtos: List<AppBackupAttachmentDto> = if (options.includeAttachments) buildAttachmentDtos() else emptyList(),
        quickMemoAudioFileNames: Map<String, String> = emptyMap()
    ): AppBackupData {
        val eventsJson = if (options.includeEvents) legacyDataMigrationCoordinator.exportEventsData() else null
        val quickMemoData = if (options.includeQuickMemos) buildQuickMemoDtos(quickMemoAudioFileNames) else QuickMemoBackupData()
        return AppBackupData(
            createdAt = System.currentTimeMillis(),
            options = options,
            eventsJson = eventsJson,
            settings = if (options.includeSettings) settingsQueryApi.settings.value else null,
            promptsJson = if (options.includePrompts) AiPrompts.exportToJson(appContext) else null,
            attachments = attachmentDtos,
            quickMemos = quickMemoData.memos,
            quickMemoSuggestions = quickMemoData.suggestions
        )
    }

    private fun buildAttachmentDtos(): List<AppBackupAttachmentDto> {
        return buildAttachmentExportItems().map { it.dto }
    }

    private fun allStoredEvents() = (db.eventsDao().getAllEventsOrTasks() + db.eventsDao().getArchivedEvents())
        .distinctBy { it.id }

    private fun buildAttachmentExportItems(): List<AttachmentExportItem> {
        val events = allStoredEvents()
            .filter { it.id != null }
            .associateBy { it.id!! }
        return attachmentManager.getAllAttachments().mapNotNull { attachment ->
            val eventId = attachment.eventId ?: return@mapNotNull null
            val event = events[eventId] ?: return@mapNotNull null
            val file = File(attachment.localPath)
            if (!file.exists()) return@mapNotNull null
            val exportName = "${attachment.id ?: eventId}_${file.name}"
            AttachmentExportItem(
                file = file,
                dto = AppBackupAttachmentDto(
                    backupEventKey = legacyDataMigrationCoordinator.eventBackupKey(event),
                    fileName = exportName,
                    displayName = attachment.displayName,
                    mimeType = attachment.mimeType,
                    sizeBytes = file.length(),
                    source = attachment.source
                )
            )
        }
    }

    private suspend fun importAppBackupData(
        data: AppBackupData,
        options: AppBackupOptions,
        attachmentsDir: File?
    ): AppBackupImportResult {
        val eventsResult = if (options.includeEvents && !data.eventsJson.isNullOrBlank()) {
            importEventsData(data.eventsJson).getOrThrow()
        } else {
            null
        }
        if (options.includeSettings && data.settings != null) {
            settingsOperationApi.updateSettings(data.settings)
        }
        val promptsImported = if (options.includePrompts && !data.promptsJson.isNullOrBlank()) {
            AiPrompts.importFromJson(appContext, data.promptsJson)
        } else {
            false
        }
        val importedAttachments = if (options.includeAttachments && attachmentsDir != null) {
            importAttachments(data.attachments, attachmentsDir)
        } else {
            0
        }
        val importedQuickMemos = if (options.includeQuickMemos) {
            importQuickMemos(data.quickMemos, data.quickMemoSuggestions, attachmentsDir)
        } else {
            0
        }
        return AppBackupImportResult(
            eventsResult = eventsResult,
            settingsImported = options.includeSettings && data.settings != null,
            promptsImported = promptsImported,
            attachmentsImported = importedAttachments,
            quickMemosImported = importedQuickMemos
        )
    }

    private fun importAttachments(attachments: List<AppBackupAttachmentDto>, tempDir: File): Int {
        if (attachments.isEmpty()) return 0
        val events = allStoredEvents()
            .filter { it.id != null }
            .associateBy { legacyDataMigrationCoordinator.eventBackupKey(it) }
        var imported = 0
        attachments.forEach { dto ->
            val event = events[dto.backupEventKey] ?: return@forEach
            val sourceFile = File(tempDir, File(dto.fileName).name)
            if (!sourceFile.exists()) return@forEach
            attachmentManager.addExistingAttachment(
                eventId = event.id ?: return@forEach,
                file = sourceFile,
                displayName = dto.displayName,
                mimeType = dto.mimeType,
                source = dto.source,
                copyIntoStore = true
            )
            imported++
        }
        return imported
    }

    private suspend fun buildQuickMemoDtos(audioFileNames: Map<String, String>): QuickMemoBackupData {
        val memos = db.quickMemoDao().getAllQuickMemos()
        if (memos.isEmpty()) return QuickMemoBackupData()
        val memoKeys = memos.associate { memo -> (memo.id ?: 0L) to quickMemoBackupKey(memo) }
        val memoDtos = memos.map { memo ->
            val backupKey = quickMemoBackupKey(memo)
            AppBackupQuickMemoDto(
                backupKey = backupKey,
                type = memo.type,
                bodyText = memo.bodyText,
                audioFileName = audioFileNames[backupKey],
                audioDurationMs = memo.audioDurationMs,
                transcriptionStatus = memo.transcriptionStatus,
                analysisStatus = memo.analysisStatus,
                createdAt = memo.createdAt,
                updatedAt = memo.updatedAt,
                sortRank = memo.sortRank,
                todoState = memo.todoState,
                todoPendingUntil = memo.todoPendingUntil,
                todoCompletedAt = memo.todoCompletedAt
            )
        }
        val suggestionDtos = db.quickMemoDao().getAllSuggestions().mapNotNull { suggestion ->
            val backupMemoKey = memoKeys[suggestion.quickMemoId] ?: return@mapNotNull null
            AppBackupQuickMemoSuggestionDto(
                backupMemoKey = backupMemoKey,
                type = suggestion.type,
                status = suggestion.status,
                candidateJson = suggestion.candidateJson,
                eventId = suggestion.eventId,
                createdAt = suggestion.createdAt,
                updatedAt = suggestion.updatedAt
            )
        }
        return QuickMemoBackupData(memoDtos, suggestionDtos)
    }

    private suspend fun buildQuickMemoAudioExportItems(): List<QuickMemoAudioExportItem> {
        return db.quickMemoDao().getAllQuickMemos().mapNotNull { memo ->
            val audioPath = memo.audioPath?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val file = File(audioPath)
            if (!file.exists() || !file.isFile) return@mapNotNull null
            val backupKey = quickMemoBackupKey(memo)
            QuickMemoAudioExportItem(
                backupKey = backupKey,
                fileName = "${memo.id ?: memo.createdAt}_${file.name}",
                file = file
            )
        }
    }

    private suspend fun importQuickMemos(
        memos: List<AppBackupQuickMemoDto>,
        suggestions: List<AppBackupQuickMemoSuggestionDto>,
        tempDir: File?
    ): Int {
        if (memos.isEmpty()) return 0
        val dao = db.quickMemoDao()
        val existingKeys = dao.getAllQuickMemos().map { quickMemoDuplicateKey(it) }.toMutableSet()
        val importedMemoIds = mutableMapOf<String, Long>()
        val audioTempDir = tempDir?.let { File(it, QUICK_MEMO_AUDIO_IMPORT_DIR) }
        var imported = 0

        memos.forEach { dto ->
            val duplicateKey = quickMemoDuplicateKey(dto)
            if (duplicateKey in existingKeys) return@forEach
            val audioPath = importQuickMemoAudio(dto.audioFileName, audioTempDir)
            val memoId = dao.insertQuickMemo(
                QuickMemoEntity(
                    type = dto.type,
                    bodyText = dto.bodyText,
                    audioPath = audioPath,
                    audioDurationMs = dto.audioDurationMs,
                    transcriptionStatus = dto.transcriptionStatus,
                    analysisStatus = dto.analysisStatus,
                    createdAt = dto.createdAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
                    updatedAt = dto.updatedAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
                    sortRank = dto.sortRank,
                    todoState = dto.todoState,
                    todoPendingUntil = dto.todoPendingUntil,
                    todoCompletedAt = dto.todoCompletedAt
                )
            )
            importedMemoIds[dto.backupKey] = memoId
            existingKeys += duplicateKey
            imported++
        }

        suggestions.forEach { dto ->
            val memoId = importedMemoIds[dto.backupMemoKey] ?: return@forEach
            dao.insertSuggestion(
                QuickMemoSuggestionEntity(
                    quickMemoId = memoId,
                    type = dto.type,
                    status = if (dto.eventId == null) dto.status else QuickMemoSuggestionStatus.PENDING,
                    candidateJson = dto.candidateJson,
                    eventId = null,
                    createdAt = dto.createdAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
                    updatedAt = dto.updatedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
                )
            )
        }
        return imported
    }

    private fun importQuickMemoAudio(fileName: String?, audioTempDir: File?): String? {
        if (fileName.isNullOrBlank() || audioTempDir == null) return null
        val sourceFile = File(audioTempDir, File(fileName).name)
        if (!sourceFile.exists() || !sourceFile.isFile) return null
        val targetDir = File(appContext.filesDir, QUICK_MEMO_AUDIO_STORE_DIR).apply { mkdirs() }
        val targetFile = uniqueFile(targetDir, sourceFile.name)
        sourceFile.inputStream().use { input ->
            targetFile.outputStream().use { output -> input.copyTo(output) }
        }
        return targetFile.absolutePath
    }

    private fun uniqueFile(directory: File, fileName: String): File {
        val cleanName = File(fileName).name.ifBlank { "quick_memo_audio" }
        val dotIndex = cleanName.lastIndexOf('.')
        val baseName = if (dotIndex > 0) cleanName.substring(0, dotIndex) else cleanName
        val extension = if (dotIndex > 0) cleanName.substring(dotIndex) else ""
        var candidate = File(directory, cleanName)
        var index = 1
        while (candidate.exists()) {
            candidate = File(directory, "${baseName}_$index$extension")
            index++
        }
        return candidate
    }

    private fun quickMemoBackupKey(memo: QuickMemoEntity): String {
        return "${memo.id ?: 0L}:${memo.createdAt}:${memo.type}:${memo.audioDurationMs}:${memo.bodyText.hashCode()}"
    }

    private fun quickMemoDuplicateKey(memo: QuickMemoEntity): String {
        return listOf(memo.type, memo.createdAt, memo.bodyText, memo.audioDurationMs).joinToString("|")
    }

    private fun quickMemoDuplicateKey(dto: AppBackupQuickMemoDto): String {
        return listOf(dto.type, dto.createdAt, dto.bodyText, dto.audioDurationMs).joinToString("|")
    }

    private fun ZipOutputStream.putJson(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray())
        closeEntry()
    }

    private data class AttachmentExportItem(
        val file: File,
        val dto: AppBackupAttachmentDto
    )

    private data class QuickMemoAudioExportItem(
        val backupKey: String,
        val fileName: String,
        val file: File
    )

    private data class QuickMemoBackupData(
        val memos: List<AppBackupQuickMemoDto> = emptyList(),
        val suggestions: List<AppBackupQuickMemoSuggestionDto> = emptyList()
    )

    private companion object {
        const val QUICK_MEMO_AUDIO_IMPORT_DIR = "quick_memo_audio"
        const val QUICK_MEMO_AUDIO_STORE_DIR = "quick_memos/audio"
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
