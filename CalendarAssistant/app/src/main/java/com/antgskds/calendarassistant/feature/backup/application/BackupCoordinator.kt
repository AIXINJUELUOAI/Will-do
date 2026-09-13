package com.antgskds.calendarassistant.feature.backup.application

import android.content.Context
import android.net.Uri
import com.antgskds.calendarassistant.shared.util.AppLogger as Log
import com.antgskds.calendarassistant.feature.schedule.data.db.EventsDatabase
import com.antgskds.calendarassistant.feature.recognition.application.ai.AiPrompts
import com.antgskds.calendarassistant.feature.schedule.data.attachment.EventAttachmentManager
import com.antgskds.calendarassistant.feature.schedule.domain.course.CourseEventMapper
import com.antgskds.calendarassistant.feature.schedule.application.ScheduleFacade
import com.antgskds.calendarassistant.app.runtime.migration.LegacyDataMigrationCoordinator
import com.antgskds.calendarassistant.shared.operation.SettingsOperationApi
import com.antgskds.calendarassistant.shared.query.SettingsQueryApi
import com.antgskds.calendarassistant.feature.backup.data.model.AppBackupAttachmentDto
import com.antgskds.calendarassistant.feature.backup.data.model.AppBackupData
import com.antgskds.calendarassistant.feature.backup.data.model.AppBackupImportResult
import com.antgskds.calendarassistant.feature.backup.data.model.AppBackupManifest
import com.antgskds.calendarassistant.feature.backup.data.model.AppBackupOptions
import com.antgskds.calendarassistant.feature.backup.data.model.AppBackupQuickMemoDto
import com.antgskds.calendarassistant.feature.backup.data.model.AppBackupQuickMemoReminderDto
import com.antgskds.calendarassistant.feature.backup.data.model.AppBackupQuickMemoSuggestionDto
import com.antgskds.calendarassistant.feature.schedule.domain.course.Course
import com.antgskds.calendarassistant.feature.backup.data.model.ImportResult
import com.antgskds.calendarassistant.feature.backup.courseimport.CourseImportParser
import com.antgskds.calendarassistant.feature.backup.courseimport.ImportMode
import com.antgskds.calendarassistant.feature.backup.courseimport.ParsedCourseImport
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoEntity
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoReminderEntity
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoSuggestionEntity
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoSuggestionStatus
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
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupCoordinator(
    private val context: Context,
    private val scheduleCenter: ScheduleFacade,
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
        Log.i(TAG, "Export backup zip start uri=$uri options=$normalized")
        val exportItems = if (normalized.includeAttachments) buildAttachmentExportItems() else emptyList()
        val quickMemoAudioExportItems = if (normalized.includeQuickMemos) buildQuickMemoAudioExportItems() else emptyList()
        val quickMemoImageExportItems = if (normalized.includeQuickMemos) buildQuickMemoImageExportItems() else emptyList()
        val backgroundExportItem = if (normalized.includeSettings) buildAppBackgroundExportItem() else null
        Log.i(
            TAG,
            "Export backup zip items attachments=${exportItems.size}, quickMemoAudio=${quickMemoAudioExportItems.size}, " +
                "quickMemoImages=${quickMemoImageExportItems.size}, appBackground=${backgroundExportItem != null}"
        )
        val backupData = buildBackupData(
            options = normalized,
            attachmentDtos = exportItems.map { it.dto },
            quickMemoAudioFileNames = quickMemoAudioExportItems.associate { it.backupKey to it.fileName },
            quickMemoImageFileNames = quickMemoImageExportItems.associate { it.backupKey to it.fileName },
            appBackgroundImageFileName = backgroundExportItem?.fileName
        )
        Log.i(
            TAG,
            "Export backup zip data quickMemos=${backupData.quickMemos.size}, " +
                "quickMemoSuggestions=${backupData.quickMemoSuggestions.size}, " +
                "quickMemoAudioRefs=${backupData.quickMemos.count { !it.audioFileName.isNullOrBlank() }}, " +
                "quickMemoImageRefs=${backupData.quickMemos.count { !it.imageFileName.isNullOrBlank() }}"
        )
        try {
            val outputStream = openBackupOutputStream(uri) ?: error("无法打开导出文件")
            outputStream.use { output ->
                ZipOutputStream(output.buffered()).use { zip ->
                    zip.putJson("manifest.json", json.encodeToString(AppBackupManifest(createdAt = backupData.createdAt, options = normalized)))
                    zip.putJson("backup.json", json.encodeToString(backupData))
                    exportItems.forEach { item ->
                        zip.putFileEntry("attachments/${item.dto.fileName}", item.file)
                    }
                    quickMemoAudioExportItems.forEach { item ->
                        zip.putFileEntry("quick_memos/audio/${item.fileName}", item.file)
                    }
                    quickMemoImageExportItems.forEach { item ->
                        zip.putFileEntry("quick_memos/images/${item.fileName}", item.file)
                    }
                    backgroundExportItem?.let { item ->
                        zip.putFileEntry("settings/background/${item.fileName}", item.file)
                    }
                }
            }
            Log.i(TAG, "Export backup zip success uri=$uri")
        } catch (error: Throwable) {
            Log.e(TAG, "Export backup zip failed uri=$uri", error)
            throw error
        }
    }

    private fun openBackupOutputStream(uri: Uri): OutputStream? {
        val resolver = appContext.contentResolver
        val truncateResult = runCatching { resolver.openOutputStream(uri, "wt") }
        truncateResult.onSuccess { output ->
            if (output != null) {
                Log.i(TAG, "Open backup output stream success mode=wt uri=$uri")
                return output
            }
            Log.w(TAG, "Open backup output stream returned null mode=wt uri=$uri")
        }.onFailure { error ->
            Log.w(TAG, "Open backup output stream failed mode=wt uri=$uri", error)
        }

        val writeResult = runCatching { resolver.openOutputStream(uri, "w") }
        writeResult.onSuccess { output ->
            if (output != null) {
                Log.i(TAG, "Open backup output stream success mode=w uri=$uri")
                return output
            }
            Log.w(TAG, "Open backup output stream returned null mode=w uri=$uri")
        }.onFailure { error ->
            Log.w(TAG, "Open backup output stream failed mode=w uri=$uri", error)
        }

        val defaultResult = runCatching { resolver.openOutputStream(uri) }
        defaultResult.onSuccess { output ->
            if (output != null) {
                Log.i(TAG, "Open backup output stream success mode=default uri=$uri")
                return output
            }
            Log.w(TAG, "Open backup output stream returned null mode=default uri=$uri")
        }.onFailure { error ->
            Log.w(TAG, "Open backup output stream failed mode=default uri=$uri", error)
        }

        return null
    }

    suspend fun importBackupJson(jsonString: String, options: AppBackupOptions): Result<AppBackupImportResult> = runCatching {
        val normalized = normalizeBackupOptions(options)
        val appBackup = runCatching { json.decodeFromString<AppBackupData>(jsonString) }.getOrNull()
        if (appBackup != null && appBackup.version >= 1) {
            importAppBackupData(appBackup, normalized, null)
        } else {
            val eventsResult = if (normalized.includeEvents) importEventsData(jsonString).getOrThrow() else null
            AppBackupImportResult(eventsResult = eventsResult)
        }
    }

    suspend fun importBackupZip(uri: Uri, options: AppBackupOptions): Result<AppBackupImportResult> = runCatching {
        val normalized = normalizeBackupOptions(options)
        val tempDir = File(appContext.cacheDir, "backup_import_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            var backupJson = ""
            var attachmentEntryCount = 0
            var quickMemoAudioEntryCount = 0
            var quickMemoImageEntryCount = 0
            var backgroundEntryCount = 0
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input.buffered()).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val safeName = entry.name.replace('\\', '/')
                            when (safeName) {
                                "backup.json" -> backupJson = zip.readBytes().toString(Charsets.UTF_8)
                                else -> if (safeName.startsWith("attachments/")) {
                                    attachmentEntryCount++
                                    val file = File(tempDir, File(safeName).name)
                                    file.outputStream().use { output -> zip.copyTo(output) }
                                } else if (safeName.startsWith("quick_memos/audio/")) {
                                    quickMemoAudioEntryCount++
                                    val audioDir = File(tempDir, QUICK_MEMO_AUDIO_IMPORT_DIR).apply { mkdirs() }
                                    val file = File(audioDir, File(safeName).name)
                                    file.outputStream().use { output -> zip.copyTo(output) }
                                } else if (safeName.startsWith("quick_memos/images/")) {
                                    quickMemoImageEntryCount++
                                    val imageDir = File(tempDir, QUICK_MEMO_IMAGE_IMPORT_DIR).apply { mkdirs() }
                                    val file = File(imageDir, File(safeName).name)
                                    file.outputStream().use { output -> zip.copyTo(output) }
                                } else if (safeName.startsWith("settings/background/")) {
                                    backgroundEntryCount++
                                    val backgroundDir = File(tempDir, APP_BACKGROUND_IMPORT_DIR).apply { mkdirs() }
                                    val file = File(backgroundDir, File(safeName).name)
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
            Log.i(
                TAG,
                "Import backup zip parsed options=$normalized entries attachments=$attachmentEntryCount, " +
                    "quickMemoAudio=$quickMemoAudioEntryCount, quickMemoImages=$quickMemoImageEntryCount, " +
                    "background=$backgroundEntryCount, dataQuickMemos=${data.quickMemos.size}, " +
                    "dataQuickMemoAudioRefs=${data.quickMemos.count { !it.audioFileName.isNullOrBlank() }}, " +
                    "dataQuickMemoImageRefs=${data.quickMemos.count { !it.imageFileName.isNullOrBlank() }}"
            )
            importAppBackupData(data, normalized, tempDir)
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
        val includeEvents = options.includeEvents || options.includeAttachments
        return options.copy(
            includeEvents = includeEvents,
            includeAttachments = includeEvents
        )
    }

    private suspend fun buildBackupData(
        options: AppBackupOptions,
        attachmentDtos: List<AppBackupAttachmentDto> = if (options.includeAttachments) buildAttachmentDtos() else emptyList(),
        quickMemoAudioFileNames: Map<String, String> = emptyMap(),
        quickMemoImageFileNames: Map<String, String> = emptyMap(),
        appBackgroundImageFileName: String? = null
    ): AppBackupData {
        val eventsJson = if (options.includeEvents) legacyDataMigrationCoordinator.exportEventsData() else null
        val quickMemoData = if (options.includeQuickMemos) {
            buildQuickMemoDtos(quickMemoAudioFileNames, quickMemoImageFileNames)
        } else QuickMemoBackupData()
        return AppBackupData(
            createdAt = System.currentTimeMillis(),
            options = options,
            eventsJson = eventsJson,
            settings = if (options.includeSettings) settingsQueryApi.settings.value else null,
            promptsJson = if (options.includePrompts) AiPrompts.exportToJson(appContext) else null,
            appBackgroundImageFileName = appBackgroundImageFileName,
            attachments = attachmentDtos,
            quickMemos = quickMemoData.memos,
            quickMemoSuggestions = quickMemoData.suggestions
        )
    }

    private fun buildAttachmentDtos(): List<AppBackupAttachmentDto> {
        return buildAttachmentExportItems().map { it.dto }
    }

    private fun buildAppBackgroundExportItem(): AppBackgroundExportItem? {
        val path = settingsQueryApi.settings.value.appBackgroundImagePath.takeIf { it.isNotBlank() } ?: return null
        val file = File(path)
        if (!file.isReadableRegularFile("settings/background", "appBackgroundImagePath")) return null
        return AppBackgroundExportItem(
            fileName = "app_background_${file.name}",
            file = file
        )
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
            if (!file.isReadableRegularFile(
                    category = "attachments",
                    owner = "attachmentId=${attachment.id}, eventId=$eventId, displayName=${attachment.displayName}, source=${attachment.source}"
                )
            ) return@mapNotNull null
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
        val settingsImported = options.includeSettings && data.settings != null
        if (settingsImported) {
            // Restoring density/theme settings can recreate MainActivity and cancel the UI-owned
            // import coroutine. Apply settings only after every database and file import is done.
            settingsOperationApi.updateSettings(
                restoreAppBackgroundSetting(
                    data.settings ?: error("备份文件缺少设置数据"),
                    data.appBackgroundImageFileName,
                    attachmentsDir
                )
            )
        }
        Log.i(
            TAG,
            "Import app backup finished events=${eventsResult != null}, settings=$settingsImported, " +
                "prompts=$promptsImported, attachments=$importedAttachments, quickMemos=$importedQuickMemos"
        )
        return AppBackupImportResult(
            eventsResult = eventsResult,
            settingsImported = settingsImported,
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

    private suspend fun buildQuickMemoDtos(
        audioFileNames: Map<String, String>,
        imageFileNames: Map<String, String>
    ): QuickMemoBackupData {
        val memos = db.quickMemoDao().getAllQuickMemos()
        if (memos.isEmpty()) return QuickMemoBackupData()
        val remindersByMemo = db.quickMemoDao().getAllReminders().groupBy { it.quickMemoId }
        val memoKeys = memos.associate { memo -> (memo.id ?: 0L) to quickMemoBackupKey(memo) }
        val memoDtos = memos.map { memo ->
            val backupKey = quickMemoBackupKey(memo)
            val reminders = memo.id?.let(remindersByMemo::get).orEmpty()
            AppBackupQuickMemoDto(
                backupKey = backupKey,
                type = memo.type,
                bodyText = memo.bodyText,
                audioFileName = audioFileNames[backupKey],
                imageFileName = imageFileNames[backupKey],
                audioDurationMs = memo.audioDurationMs,
                transcriptionStatus = memo.transcriptionStatus,
                analysisStatus = memo.analysisStatus,
                createdAt = memo.createdAt,
                updatedAt = memo.updatedAt,
                sortRank = memo.sortRank,
                todoState = memo.todoState,
                todoPendingUntil = memo.todoPendingUntil,
                todoCompletedAt = memo.todoCompletedAt,
                reminderAt = reminders.firstOrNull()?.triggerAt,
                reminderRRule = reminders.firstOrNull()?.rrule.orEmpty(),
                reminders = reminders.map { reminder ->
                    AppBackupQuickMemoReminderDto(
                        triggerAt = reminder.triggerAt,
                        rrule = reminder.rrule,
                        createdAt = reminder.createdAt,
                        updatedAt = reminder.updatedAt
                    )
                }
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
            if (!file.isReadableRegularFile(
                    category = "quick_memos/audio",
                    owner = "memoId=${memo.id}, createdAt=${memo.createdAt}, type=${memo.type}"
                )
            ) return@mapNotNull null
            val backupKey = quickMemoBackupKey(memo)
            QuickMemoAudioExportItem(
                backupKey = backupKey,
                fileName = "${memo.id ?: memo.createdAt}_${file.name}",
                file = file
            )
        }
    }

    private suspend fun buildQuickMemoImageExportItems(): List<QuickMemoImageExportItem> {
        return db.quickMemoDao().getAllQuickMemos().mapNotNull { memo ->
            val imagePath = memo.imagePath?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val file = File(imagePath)
            if (!file.isReadableRegularFile(
                    category = "quick_memos/images",
                    owner = "memoId=${memo.id}, createdAt=${memo.createdAt}, type=${memo.type}"
                )
            ) return@mapNotNull null
            val backupKey = quickMemoBackupKey(memo)
            QuickMemoImageExportItem(
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
        val existingByKey = dao.getAllQuickMemos()
            .filter { it.id != null }
            .associateBy { quickMemoDuplicateKey(it) }
            .toMutableMap()
        val importedMemoIds = mutableMapOf<String, Long>()
        val audioTempDir = tempDir?.let { File(it, QUICK_MEMO_AUDIO_IMPORT_DIR) }
        val imageTempDir = tempDir?.let { File(it, QUICK_MEMO_IMAGE_IMPORT_DIR) }
        var imported = 0
        suspend fun importReminders(memoId: Long, dto: AppBackupQuickMemoDto) {
            val source = dto.reminders.ifEmpty {
                dto.reminderAt?.let { triggerAt ->
                    listOf(AppBackupQuickMemoReminderDto(triggerAt = triggerAt, rrule = dto.reminderRRule))
                }.orEmpty()
            }
            val existingKeys = dao.getRemindersForMemo(memoId)
                .map { it.triggerAt to it.rrule }
                .toMutableSet()
            source.filter { it.triggerAt > 0L }.forEach { reminder ->
                if (!existingKeys.add(reminder.triggerAt to reminder.rrule)) return@forEach
                dao.insertReminder(
                    QuickMemoReminderEntity(
                        quickMemoId = memoId,
                        triggerAt = reminder.triggerAt,
                        rrule = reminder.rrule,
                        createdAt = reminder.createdAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
                        updatedAt = reminder.updatedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
                    )
                )
            }
        }
        Log.i(
            TAG,
            "Import quick memos start memos=${memos.size}, suggestions=${suggestions.size}, " +
                "audioRefs=${memos.count { !it.audioFileName.isNullOrBlank() }}, " +
                "imageRefs=${memos.count { !it.imageFileName.isNullOrBlank() }}, " +
                "audioDir=${audioTempDir?.absolutePath}, audioDirExists=${audioTempDir?.isDirectory}, " +
                "imageDir=${imageTempDir?.absolutePath}, imageDirExists=${imageTempDir?.isDirectory}"
        )

        memos.forEach { dto ->
            val duplicateKey = quickMemoDuplicateKey(dto)
            val existingMemo = existingByKey[duplicateKey]
            if (existingMemo != null) {
                existingMemo.id?.let {
                    importedMemoIds[dto.backupKey] = it
                    importReminders(it, dto)
                }
                repairExistingQuickMemoFiles(existingMemo, dto, audioTempDir, imageTempDir)
                return@forEach
            }
            val audioPath = importQuickMemoAudio(dto.audioFileName, audioTempDir)
            val imagePath = importQuickMemoImage(dto.imageFileName, imageTempDir)
            val memoId = dao.insertQuickMemo(
                QuickMemoEntity(
                    type = dto.type,
                    bodyText = dto.bodyText,
                    audioPath = audioPath,
                    imagePath = imagePath,
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
            importReminders(memoId, dto)
            importedMemoIds[dto.backupKey] = memoId
            existingByKey[duplicateKey] = dao.getQuickMemo(memoId) ?: return@forEach
            imported++
        }

        val existingSuggestionKeys = dao.getAllSuggestions()
            .map { suggestion -> quickMemoSuggestionDuplicateKey(suggestion.quickMemoId, suggestion.type, suggestion.candidateJson) }
            .toMutableSet()
        suggestions.forEach { dto ->
            val memoId = importedMemoIds[dto.backupMemoKey] ?: return@forEach
            val suggestionKey = quickMemoSuggestionDuplicateKey(memoId, dto.type, dto.candidateJson)
            if (!existingSuggestionKeys.add(suggestionKey)) return@forEach
            dao.insertSuggestion(
                QuickMemoSuggestionEntity(
                    quickMemoId = memoId,
                    type = dto.type,
                    status = dto.status,
                    candidateJson = dto.candidateJson,
                    eventId = dto.eventId,
                    createdAt = dto.createdAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
                    updatedAt = dto.updatedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
                )
            )
        }
        Log.i(TAG, "Import quick memos finished inserted=$imported, mapped=${importedMemoIds.size}")
        return imported
    }

    private fun quickMemoSuggestionDuplicateKey(memoId: Long, type: String, candidateJson: String): String {
        return "$memoId|${type.trim()}|${candidateJson.trim()}"
    }

    private suspend fun repairExistingQuickMemoFiles(
        memo: QuickMemoEntity,
        dto: AppBackupQuickMemoDto,
        audioTempDir: File?,
        imageTempDir: File?
    ) {
        val memoId = memo.id ?: return
        val currentAudioReadable = memo.audioPath
            ?.takeIf { it.isNotBlank() }
            ?.let { File(it).isReadableRegularFile() } == true
        val currentImageReadable = memo.imagePath
            ?.takeIf { it.isNotBlank() }
            ?.let { File(it).isReadableRegularFile() } == true

        val repairedAudioPath = if (!currentAudioReadable) {
            importQuickMemoAudio(dto.audioFileName, audioTempDir)
        } else {
            null
        }
        val repairedImagePath = if (!currentImageReadable) {
            importQuickMemoImage(dto.imageFileName, imageTempDir)
        } else {
            null
        }

        if (repairedAudioPath != null || repairedImagePath != null) {
            db.quickMemoDao().updateQuickMemo(
                memo.copy(
                    audioPath = repairedAudioPath ?: memo.audioPath,
                    imagePath = repairedImagePath ?: memo.imagePath,
                    updatedAt = System.currentTimeMillis()
                )
            )
            Log.i(
                TAG,
                "Repaired existing quick memo files memoId=$memoId " +
                    "audioRepaired=${repairedAudioPath != null}, imageRepaired=${repairedImagePath != null}"
            )
        } else {
            Log.i(
                TAG,
                "Skip duplicate quick memo memoId=$memoId backupKey=${dto.backupKey} " +
                    "audioReadable=$currentAudioReadable imageReadable=$currentImageReadable"
            )
        }
    }

    private fun importQuickMemoAudio(fileName: String?, audioTempDir: File?): String? {
        if (fileName.isNullOrBlank() || audioTempDir == null) return null
        val sourceFile = File(audioTempDir, File(fileName).name)
        if (!sourceFile.exists() || !sourceFile.isFile) {
            Log.w(TAG, "Quick memo audio import source missing fileName=$fileName dir=${audioTempDir.absolutePath}")
            return null
        }
        val targetDir = File(appContext.filesDir, QUICK_MEMO_AUDIO_STORE_DIR).apply { mkdirs() }
        val targetFile = uniqueFile(targetDir, sourceFile.name)
        sourceFile.inputStream().use { input ->
            targetFile.outputStream().use { output -> input.copyTo(output) }
        }
        Log.i(TAG, "Quick memo audio imported fileName=$fileName target=${targetFile.absolutePath} bytes=${targetFile.length()}")
        return targetFile.absolutePath
    }

    private fun importQuickMemoImage(fileName: String?, imageTempDir: File?): String? {
        if (fileName.isNullOrBlank() || imageTempDir == null) return null
        val sourceFile = File(imageTempDir, File(fileName).name)
        if (!sourceFile.exists() || !sourceFile.isFile) {
            Log.w(TAG, "Quick memo image import source missing fileName=$fileName dir=${imageTempDir.absolutePath}")
            return null
        }
        val targetDir = File(appContext.filesDir, QUICK_MEMO_IMAGE_STORE_DIR).apply { mkdirs() }
        val targetFile = uniqueFile(targetDir, sourceFile.name)
        sourceFile.inputStream().use { input ->
            targetFile.outputStream().use { output -> input.copyTo(output) }
        }
        Log.i(TAG, "Quick memo image imported fileName=$fileName target=${targetFile.absolutePath} bytes=${targetFile.length()}")
        return targetFile.absolutePath
    }

    private fun restoreAppBackgroundSetting(
        settings: com.antgskds.calendarassistant.feature.settings.data.model.MySettings,
        fileName: String?,
        tempDir: File?
    ): com.antgskds.calendarassistant.feature.settings.data.model.MySettings {
        if (fileName.isNullOrBlank() || tempDir == null) {
            return settings.copy(appBackgroundImagePath = "", appBackgroundEnabled = false)
        }
        val sourceFile = File(File(tempDir, APP_BACKGROUND_IMPORT_DIR), File(fileName).name)
        if (!sourceFile.exists() || !sourceFile.isFile) {
            return settings.copy(appBackgroundImagePath = "", appBackgroundEnabled = false)
        }
        val targetDir = File(appContext.filesDir, APP_BACKGROUND_STORE_DIR).apply { mkdirs() }
        val targetFile = uniqueFile(targetDir, sourceFile.name)
        sourceFile.inputStream().use { input ->
            targetFile.outputStream().use { output -> input.copyTo(output) }
        }
        return settings.copy(
            appBackgroundEnabled = true,
            appBackgroundImagePath = targetFile.absolutePath
        )
    }

    private fun uniqueFile(directory: File, fileName: String): File {
        val cleanName = File(fileName).name.ifBlank { "quick_memo_file" }
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
        return "${memo.id ?: 0L}:${memo.createdAt}:${memo.type}:${memo.audioDurationMs}:${memo.bodyText.hashCode()}:${memo.imagePath?.hashCode() ?: 0}"
    }

    private fun quickMemoDuplicateKey(memo: QuickMemoEntity): String {
        return listOf(memo.type, memo.createdAt, memo.bodyText, memo.audioDurationMs, memo.imagePath?.let { File(it).name }.orEmpty()).joinToString("|")
    }

    private fun quickMemoDuplicateKey(dto: AppBackupQuickMemoDto): String {
        return listOf(dto.type, dto.createdAt, dto.bodyText, dto.audioDurationMs, dto.imageFileName.orEmpty()).joinToString("|")
    }

    private fun ZipOutputStream.putJson(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray())
        closeEntry()
    }

    private fun ZipOutputStream.putFileEntry(name: String, file: File) {
        if (!file.isReadableRegularFile("zip_entry", "entry=$name")) return
        val input = runCatching { file.inputStream() }.getOrElse { error ->
            Log.w(TAG, "Skip backup file open failed entry=$name path=${file.absolutePath}", error)
            return
        }
        try {
            input.use {
                putNextEntry(ZipEntry(name))
                it.copyTo(this)
                closeEntry()
            }
        } catch (error: Throwable) {
            Log.e(TAG, "Write backup entry failed entry=$name path=${file.absolutePath}", error)
            throw error
        }
    }

    private fun File.isReadableRegularFile(category: String? = null, owner: String = ""): Boolean {
        val exists = exists()
        val isRegularFile = isFile
        val readable = canRead()
        val result = exists && isRegularFile && readable
        if (!result && category != null) {
            Log.w(
                TAG,
                "Skip backup source category=$category owner=$owner path=$absolutePath " +
                    "exists=$exists isFile=$isRegularFile canRead=$readable length=${runCatching { length() }.getOrDefault(-1L)}"
            )
        }
        return result
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

    private data class QuickMemoImageExportItem(
        val backupKey: String,
        val fileName: String,
        val file: File
    )

    private data class AppBackgroundExportItem(
        val fileName: String,
        val file: File
    )

    private data class QuickMemoBackupData(
        val memos: List<AppBackupQuickMemoDto> = emptyList(),
        val suggestions: List<AppBackupQuickMemoSuggestionDto> = emptyList()
    )

    private companion object {
        const val TAG = "BackupCoordinator"
        const val QUICK_MEMO_AUDIO_IMPORT_DIR = "quick_memo_audio"
        const val QUICK_MEMO_IMAGE_IMPORT_DIR = "quick_memo_image"
        const val QUICK_MEMO_AUDIO_STORE_DIR = "quick_memos/audio"
        const val QUICK_MEMO_IMAGE_STORE_DIR = "quick_memos/images"
        const val APP_BACKGROUND_IMPORT_DIR = "app_background"
        const val APP_BACKGROUND_STORE_DIR = "theme/background"
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
