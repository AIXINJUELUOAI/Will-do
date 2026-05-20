package com.antgskds.calendarassistant.core.migration

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.core.content.ContextCompat
import com.antgskds.calendarassistant.calendar.data.EventsDatabase
import com.antgskds.calendarassistant.calendar.helpers.CALDAV
import com.antgskds.calendarassistant.calendar.helpers.CalendarConfig
import com.antgskds.calendarassistant.calendar.helpers.REMINDER_NOTIFICATION
import com.antgskds.calendarassistant.calendar.helpers.REMINDER_OFF
import com.antgskds.calendarassistant.calendar.helpers.SOURCE_SIMPLE_CALENDAR
import com.antgskds.calendarassistant.calendar.helpers.STATE_CHECKED_IN
import com.antgskds.calendarassistant.calendar.helpers.STATE_COMPLETED
import com.antgskds.calendarassistant.calendar.helpers.STATE_PENDING
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.EventTags
import com.antgskds.calendarassistant.calendar.models.isNoteTag
import com.antgskds.calendarassistant.calendar.sync.SystemCalendarSyncManager
import com.antgskds.calendarassistant.core.center.CalendarCenter
import com.antgskds.calendarassistant.core.course.CourseEventMapper
import com.antgskds.calendarassistant.data.model.Course
import com.antgskds.calendarassistant.data.model.ImportResult
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.data.repository.SettingsRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import kotlin.math.max

class LegacyDataMigrationCoordinator(
    context: Context,
    private val calendarCenter: CalendarCenter,
    private val settingsRepository: SettingsRepository
) {
    private val appContext = context.applicationContext
    private val db = EventsDatabase.getInstance(appContext)
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    fun runAutoMigrationIfNeeded(force: Boolean = false): LegacyMigrationReport {
        cleanupExpiredQuarantineFiles()

        val alreadyDone = prefs.getInt(KEY_MIGRATION_VERSION, 0) >= MIGRATION_VERSION
        if (!force && alreadyDone) {
            return LegacyMigrationReport(
                status = MigrationStatus.SKIPPED_ALREADY_MIGRATED,
                inserted = 0,
                updated = 0,
                skipped = 0,
                candidateCount = 0,
                movedLegacyFiles = 0,
                message = "legacy migration already completed"
            )
        }

        val legacyFiles = collectLegacyFiles()
        if (legacyFiles.isEmpty()) {
            markMigrationDone(
                LegacyMigrationReport(
                    status = MigrationStatus.SKIPPED_NO_LEGACY_DATA,
                    inserted = 0,
                    updated = 0,
                    skipped = 0,
                    candidateCount = 0,
                    movedLegacyFiles = 0,
                    message = "no legacy files found"
                )
            )
            return LegacyMigrationReport(
                status = MigrationStatus.SKIPPED_NO_LEGACY_DATA,
                inserted = 0,
                updated = 0,
                skipped = 0,
                candidateCount = 0,
                movedLegacyFiles = 0,
                message = "no legacy files found"
            )
        }

        val settings = settingsRepository.loadSettings()
        val wasSyncEnabled = runCatching {
            val config = com.antgskds.calendarassistant.calendar.helpers.CalendarConfig.newInstance(appContext)
            config.caldavSync
        }.getOrDefault(false)

        if (wasSyncEnabled) {
            runCatching { calendarCenter.setSyncEnabled(false) }
                .onFailure { Log.w(TAG, "Failed to disable sync before migration", it) }
        }

        val report = try {
            val candidates = loadLegacyCandidates(legacyFiles, settings)
            if (candidates.isEmpty()) {
                return LegacyMigrationReport(
                    status = MigrationStatus.SKIPPED_NO_VALID_CANDIDATES,
                    inserted = 0,
                    updated = 0,
                    skipped = 0,
                    candidateCount = 0,
                    movedLegacyFiles = 0,
                    message = "legacy files found but no valid events parsed"
                )
            }

            val mergeResult = mergeCandidatesIntoCurrentStore(candidates)
            val movedCount = quarantineLegacyFiles(legacyFiles)
            LegacyMigrationReport(
                status = MigrationStatus.SUCCESS,
                inserted = mergeResult.inserted,
                updated = mergeResult.updated,
                skipped = mergeResult.skipped,
                candidateCount = mergeResult.candidateCount,
                movedLegacyFiles = movedCount,
                message = "migration success"
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Legacy migration failed", t)
            LegacyMigrationReport(
                status = MigrationStatus.FAILED,
                inserted = 0,
                updated = 0,
                skipped = 0,
                candidateCount = 0,
                movedLegacyFiles = 0,
                message = t.message ?: "migration failed"
            )
        } finally {
            if (wasSyncEnabled) {
                runCatching { calendarCenter.setSyncEnabled(true) }
                    .onFailure { Log.w(TAG, "Failed to restore sync after migration", it) }
            }
            cleanupExpiredQuarantineFiles()
        }

        if (report.status == MigrationStatus.SUCCESS || report.status == MigrationStatus.SKIPPED_NO_LEGACY_DATA) {
            markMigrationDone(report)
        }

        Log.i(
            TAG,
            "Legacy migration result: status=${report.status}, candidates=${report.candidateCount}, " +
                "inserted=${report.inserted}, updated=${report.updated}, skipped=${report.skipped}, moved=${report.movedLegacyFiles}"
        )
        return report
    }

    fun exportEventsData(): String {
        val activeEvents = db.eventsDao().getAllEventsOrTasks()
        val archivedEvents = db.eventsDao().getArchivedEvents()
        return json.encodeToString(
            EventsBackupDataV3(
                version = 3,
                events = activeEvents.map { it.toBackupDto() },
                archivedEvents = archivedEvents.map { it.toBackupDto() }
            )
        )
    }

    fun importEventsData(jsonString: String): Result<ImportResult> = runCatching {
        val settings = settingsRepository.loadSettings()
        val rawCandidates = parseBackupContentCandidates(jsonString, settings)
        val hasCrossDeviceSystemBindings = rawCandidates.any(::hasSystemCalendarBinding)
        val candidates = deduplicateImportCandidatePool(
            rawCandidates.map(::normalizeManualImportCandidate),
            preferSignatureKey = hasCrossDeviceSystemBindings
        )
        val mergeResult = mergeImportCandidatesIntoCurrentStore(candidates)
        val pushedToSystem = pushImportedEventsToSystemCalendar(mergeResult.eventsForSystemSync)
        Log.i(
            TAG,
            "Manual event import result: candidates=${mergeResult.candidateCount}, inserted=${mergeResult.inserted}, " +
                "updated=${mergeResult.updated}, skipped=${mergeResult.skipped}, queuedForSystem=${mergeResult.eventsForSystemSync.size}, " +
                "pushedToSystem=$pushedToSystem"
        )
        ImportResult(
            successCount = mergeResult.inserted,
            skippedCount = mergeResult.skipped,
            archiveStatusUpdateCount = mergeResult.updated
        )
    }

    private fun collectLegacyFiles(): List<File> {
        val files = mutableListOf<File>()

        val legacyDb = appContext.getDatabasePath(LEGACY_DB_NAME)
        if (legacyDb.exists()) {
            files += legacyDb
            val wal = File(legacyDb.parentFile, "$LEGACY_DB_NAME-wal")
            val shm = File(legacyDb.parentFile, "$LEGACY_DB_NAME-shm")
            if (wal.exists()) files += wal
            if (shm.exists()) files += shm
        }

        LEGACY_JSON_FILES.forEach { name ->
            val file = File(appContext.filesDir, name)
            if (file.exists()) files += file
        }

        return files.distinctBy { it.absolutePath }
    }

    private fun loadLegacyCandidates(files: List<File>, settings: MySettings): List<Event> {
        val fromLegacyRoom = loadLegacyRoomCandidates(files, settings)
        val fromLegacyJson = loadLegacyJsonCandidates(files, settings)
        return deduplicateCandidatePool(fromLegacyRoom + fromLegacyJson)
    }

    private fun loadLegacyRoomCandidates(files: List<File>, settings: MySettings): List<Event> {
        val dbFile = files.firstOrNull { it.name == LEGACY_DB_NAME } ?: return emptyList()
        if (!dbFile.exists()) return emptyList()

        val candidates = mutableListOf<Event>()
        var sqlite: SQLiteDatabase? = null
        try {
            sqlite = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            if (!hasTable(sqlite, "event_masters") || !hasTable(sqlite, "event_instances")) {
                return emptyList()
            }

            val masters = loadLegacyMasters(sqlite)
            val instancesByMaster = loadLegacyInstances(sqlite).groupBy { it.masterId }
            val excludedByMaster = if (hasTable(sqlite, "event_excluded_dates")) {
                loadLegacyExcludedDates(sqlite)
            } else {
                emptyMap()
            }

            masters.forEach { master ->
                val masterInstances = instancesByMaster[master.masterId].orEmpty()
                    .sortedBy { it.startTimeMillis }
                if (masterInstances.isEmpty()) return@forEach

                val tag = normalizeTag(resolveTag(master.ruleId))

                if (tag == EventTags.COURSE) {
                    buildCourseCandidateFromLegacyMaster(master, settings)?.let { candidates += it }
                    return@forEach
                }

                val reminders = parseLegacyReminderMinutes(master.remindersJson)
                val source = resolveSource(master.source)
                val recurringRRule = master.rrule.orEmpty().trim()

                if (recurringRRule.isNotBlank() && !recurringRRule.equals("EXTERNAL", ignoreCase = true)) {
                    val anchor = masterInstances.firstOrNull { !it.isCancelled } ?: return@forEach
                    val startTS = normalizeEpochSeconds(anchor.startTimeMillis)
                    val endTS = max(startTS + 60L, normalizeEpochSeconds(anchor.endTimeMillis))
                    val exdates = buildLegacyExdates(
                        excludedByMaster[master.masterId].orEmpty(),
                        masterInstances.filter { it.isCancelled }.map { it.startTimeMillis }
                    )
                    candidates += Event(
                        id = null,
                        startTS = startTS,
                        endTS = endTS,
                        title = master.title,
                        location = master.location,
                        description = appendLegacyMeta(master.description, master.isImportant, master.sourceImagePath),
                        reminder1Minutes = reminders.getOrElse(0) { REMINDER_OFF },
                        reminder2Minutes = reminders.getOrElse(1) { REMINDER_OFF },
                        reminder3Minutes = reminders.getOrElse(2) { REMINDER_OFF },
                        reminder1Type = REMINDER_NOTIFICATION,
                        reminder2Type = REMINDER_NOTIFICATION,
                        reminder3Type = REMINDER_NOTIFICATION,
                        rrule = recurringRRule,
                        exdates = exdates,
                        attendees = emptyList(),
                        importId = buildLegacyImportId(source, master.syncId),
                        timeZone = ZoneId.systemDefault().id,
                        flags = 0,
                        eventType = 1L,
                        parentId = 0L,
                        lastUpdated = normalizeTimestampSeconds(master.updatedAt),
                        source = source,
                        availability = 0,
                        color = master.colorArgb,
                        type = 0,
                        state = STATE_PENDING,
                        tag = tag,
                        archivedAt = null
                    )
                    return@forEach
                }

                val validInstances = masterInstances.filter { !it.isCancelled }
                val uniqueImportId = if (validInstances.size == 1) {
                    buildLegacyImportId(source, master.syncId)
                } else {
                    ""
                }

                validInstances.forEach { instance ->
                    val startTS = normalizeEpochSeconds(instance.startTimeMillis)
                    val endTS = max(startTS + 60L, normalizeEpochSeconds(instance.endTimeMillis))
                    candidates += Event(
                        id = null,
                        startTS = startTS,
                        endTS = endTS,
                        title = master.title,
                        location = master.location,
                        description = appendLegacyMeta(master.description, master.isImportant, master.sourceImagePath),
                        reminder1Minutes = reminders.getOrElse(0) { REMINDER_OFF },
                        reminder2Minutes = reminders.getOrElse(1) { REMINDER_OFF },
                        reminder3Minutes = reminders.getOrElse(2) { REMINDER_OFF },
                        reminder1Type = REMINDER_NOTIFICATION,
                        reminder2Type = REMINDER_NOTIFICATION,
                        reminder3Type = REMINDER_NOTIFICATION,
                        rrule = "",
                        exdates = emptyList(),
                        attendees = emptyList(),
                        importId = uniqueImportId,
                        timeZone = ZoneId.systemDefault().id,
                        flags = 0,
                        eventType = 1L,
                        parentId = 0L,
                        lastUpdated = normalizeTimestampSeconds(max(master.updatedAt, instance.startTimeMillis)),
                        source = source,
                        availability = 0,
                        color = master.colorArgb,
                        type = 0,
                        state = resolveLegacyState(instance.currentStateId, instance.completedAtMillis),
                        tag = tag,
                        archivedAt = normalizeNullableTimestampSeconds(instance.archivedAtMillis)
                    )
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to parse legacy room db", t)
        } finally {
            runCatching { sqlite?.close() }
        }
        return candidates
    }

    private fun loadLegacyJsonCandidates(files: List<File>, settings: MySettings): List<Event> {
        val candidates = mutableListOf<Event>()
        val nowSeconds = System.currentTimeMillis() / 1000L

        files.forEach { file ->
            val name = file.name.lowercase()
            if (!name.endsWith(".json") && !name.endsWith(".bak")) return@forEach

            val content = runCatching { file.readText() }.getOrNull()?.trim().orEmpty()
            if (content.isBlank()) return@forEach

            if (name.startsWith("courses")) {
                val courses = runCatching { json.decodeFromString<List<Course>>(content) }.getOrNull().orEmpty()
                courses.forEach { course ->
                    candidates += CourseEventMapper.toParentEvent(course, settings)
                }
                return@forEach
            }

            val backupData = runCatching { json.decodeFromString<LegacyEventsBackupData>(content) }.getOrNull()
            if (backupData != null) {
                backupData.events.forEach { legacy ->
                    convertLegacyMyEventToCandidate(legacy, settings, forceArchived = false, nowSeconds = nowSeconds)
                        ?.let { candidates += it }
                }
                backupData.archivedEvents.forEach { legacy ->
                    convertLegacyMyEventToCandidate(legacy, settings, forceArchived = true, nowSeconds = nowSeconds)
                        ?.let { candidates += it }
                }
                return@forEach
            }

            val asList = runCatching { json.decodeFromString<List<LegacyMyEvent>>(content) }.getOrNull().orEmpty()
            if (asList.isNotEmpty()) {
                val forceArchived = name.startsWith("archives")
                asList.forEach { legacy ->
                    convertLegacyMyEventToCandidate(
                        legacy = legacy,
                        settings = settings,
                        forceArchived = forceArchived,
                        nowSeconds = nowSeconds
                    )?.let { candidates += it }
                }
            }
        }

        return candidates
    }

    private fun parseBackupContentCandidates(content: String, settings: MySettings): List<Event> {
        val trimmed = content.trim()
        if (trimmed.isBlank()) return emptyList()
        val nowSeconds = System.currentTimeMillis() / 1000L

        val v3Data = runCatching { json.decodeFromString<EventsBackupDataV3>(trimmed) }.getOrNull()
        if (v3Data != null && v3Data.version >= 3) {
            return v3Data.events.map { it.toEvent(forceArchived = false, nowSeconds = nowSeconds) } +
                v3Data.archivedEvents.map { it.toEvent(forceArchived = true, nowSeconds = nowSeconds) }
        }

        val legacyEventsData = runCatching { json.decodeFromString<LegacyEventsBackupData>(trimmed) }.getOrNull()
        if (legacyEventsData != null && (legacyEventsData.events.isNotEmpty() || legacyEventsData.archivedEvents.isNotEmpty())) {
            return legacyEventsData.events.mapNotNull { legacy ->
                convertLegacyMyEventToCandidate(legacy, settings, forceArchived = false, nowSeconds = nowSeconds)
            } + legacyEventsData.archivedEvents.mapNotNull { legacy ->
                convertLegacyMyEventToCandidate(legacy, settings, forceArchived = true, nowSeconds = nowSeconds)
            }
        }

        val courseEventsData = runCatching { json.decodeFromString<LegacyCourseEventsBackupData>(trimmed) }.getOrNull()
        if (courseEventsData != null && courseEventsData.courseEvents.isNotEmpty()) {
            val importedSettings = settings.copy(
                semesterStartDate = courseEventsData.semesterStartDate.ifBlank { settings.semesterStartDate },
                totalWeeks = courseEventsData.totalWeeks.takeIf { it > 0 } ?: settings.totalWeeks,
                timeTableJson = courseEventsData.timeTableJson.ifBlank { settings.timeTableJson },
                timeTableConfigJson = courseEventsData.timeTableConfigJson.ifBlank { settings.timeTableConfigJson }
            )
            return courseEventsData.courseEvents.mapNotNull { legacy ->
                convertLegacyMyEventToCandidate(legacy, importedSettings, forceArchived = false, nowSeconds = nowSeconds)
            }
        }

        val courses = runCatching { json.decodeFromString<List<Course>>(trimmed) }.getOrNull()
        if (!courses.isNullOrEmpty()) {
            return courses.map { course -> CourseEventMapper.toParentEvent(course, settings) }
        }

        val legacyList = runCatching { json.decodeFromString<List<LegacyMyEvent>>(trimmed) }.getOrNull()
        if (!legacyList.isNullOrEmpty()) {
            return legacyList.mapNotNull { legacy ->
                convertLegacyMyEventToCandidate(legacy, settings, forceArchived = false, nowSeconds = nowSeconds)
            }
        }

        error("无法识别的备份文件格式")
    }

    private fun convertLegacyMyEventToCandidate(
        legacy: LegacyMyEvent,
        settings: MySettings,
        forceArchived: Boolean,
        nowSeconds: Long
    ): Event? {
        val title = legacy.title.trim()
        if (title.isBlank()) return null

        val tag = normalizeTag(legacy.tag)
        if (tag == EventTags.COURSE) {
            if (!legacy.isRecurringParent) return null
            val meta = CourseEventMapper.parseMeta(legacy.description) ?: return null
            val course = Course(
                id = meta.uid.ifBlank { legacy.id.ifBlank { CourseEventMapper.stableImportId(title, meta.teacher, legacy.location, meta.dayOfWeek, meta.startNode, meta.endNode, meta.startWeek, meta.endWeek, meta.weekType) } },
                name = title,
                location = legacy.location,
                teacher = meta.teacher,
                color = legacy.color,
                dayOfWeek = meta.dayOfWeek,
                startNode = meta.startNode,
                endNode = meta.endNode,
                startWeek = meta.startWeek,
                endWeek = meta.endWeek,
                weekType = meta.weekType
            )
            return CourseEventMapper.toParentEvent(course, settings)
        }

        if (legacy.isRecurringParent) {
            // 旧模型里母事件通常与子实例并存；迁移时优先使用子实例避免重复。
            return null
        }

        val startTS = parseLegacyDateTimeToEpochSeconds(legacy.startDate, legacy.startTime) ?: return null
        val endTSRaw = parseLegacyDateTimeToEpochSeconds(legacy.endDate, legacy.endTime)
            ?: (startTS + 60L)
        val endTS = max(startTS + 60L, endTSRaw)
        val reminders = legacy.reminders.filter { it >= 0 }
        val archivedAt = normalizeNullableTimestampSeconds(legacy.archivedAt)
            ?: if (forceArchived) nowSeconds else null

        return Event(
            id = null,
            startTS = startTS,
            endTS = endTS,
            title = title,
            location = legacy.location,
            description = appendLegacyMeta(legacy.description, legacy.isImportant, legacy.sourceImagePath),
            reminder1Minutes = reminders.getOrElse(0) { REMINDER_OFF },
            reminder2Minutes = reminders.getOrElse(1) { REMINDER_OFF },
            reminder3Minutes = reminders.getOrElse(2) { REMINDER_OFF },
            reminder1Type = REMINDER_NOTIFICATION,
            reminder2Type = REMINDER_NOTIFICATION,
            reminder3Type = REMINDER_NOTIFICATION,
            rrule = "",
            exdates = emptyList(),
            attendees = emptyList(),
            importId = "",
            timeZone = ZoneId.systemDefault().id,
            flags = 0,
            eventType = 1L,
            parentId = 0L,
            lastUpdated = normalizeTimestampSeconds(legacy.lastModified),
            source = SOURCE_SIMPLE_CALENDAR,
            availability = 0,
            color = legacy.color,
            type = 0,
            state = resolveLegacyStateFromBooleans(
                isCompleted = legacy.isCompleted || legacy.completedAt != null,
                isCheckedIn = legacy.isCheckedIn
            ),
            tag = tag,
            archivedAt = archivedAt
        )
    }

    private fun deduplicateCandidatePool(candidates: List<Event>): List<Event> {
        return deduplicateImportCandidatePool(candidates.map { ImportCandidate(it) }).map { it.event }
    }

    private fun deduplicateImportCandidatePool(
        candidates: List<ImportCandidate>,
        preferSignatureKey: Boolean = false
    ): List<ImportCandidate> {
        if (candidates.isEmpty()) return emptyList()
        val sorted = candidates.sortedByDescending { normalizedComparisonMillis(it.event.lastUpdated) }
        val output = mutableListOf<ImportCandidate>()
        val seen = HashSet<String>()

        sorted.forEach { candidate ->
            val key = candidatePrimaryKey(candidate.event, preferSignatureKey)
            if (key in seen) return@forEach
            seen += key
            output += candidate
        }
        return output
    }

    private fun mergeCandidatesIntoCurrentStore(candidates: List<Event>): MergeResult {
        return mergeImportCandidatesIntoCurrentStore(candidates.map { ImportCandidate(it) })
    }

    private fun mergeImportCandidatesIntoCurrentStore(candidates: List<ImportCandidate>): MergeResult {
        if (candidates.isEmpty()) {
            return MergeResult(inserted = 0, updated = 0, skipped = 0, candidateCount = 0)
        }

        val dao = db.eventsDao()
        val existing = (dao.getAllEventsOrTasks() + dao.getArchivedEvents())
            .filter { it.id != null }
            .associateBy { it.id!! }
            .values
            .toMutableList()

        val index = ExistingIndex(existing)
        val inserts = mutableListOf<ImportCandidate>()
        val updates = mutableListOf<ImportCandidate>()
        var skipped = 0

        candidates.forEach { importCandidate ->
            val candidate = importCandidate.event
            val directConflict = index.findDirectConflict(candidate)
            if (directConflict != null) {
                if (shouldReplaceExisting(directConflict, candidate)) {
                    val merged = mergeEvent(
                        existing = directConflict,
                        candidate = candidate,
                        detachSystemBinding = importCandidate.detachedSystemBinding
                    )
                    updates += importCandidate.copy(event = merged)
                    index.replace(directConflict, merged)
                } else if (shouldDetachExistingSystemBinding(directConflict, importCandidate)) {
                    val detached = directConflict.copy(importId = "", source = SOURCE_SIMPLE_CALENDAR)
                    updates += importCandidate.copy(event = detached, detachedSystemBinding = true)
                    index.replace(directConflict, detached)
                } else if (shouldPreserveLegacyVisuals(directConflict, candidate)) {
                    val merged = directConflict.copy(color = candidate.color)
                    updates += importCandidate.copy(event = merged)
                    index.replace(directConflict, merged)
                } else {
                    skipped++
                }
                return@forEach
            }

            val recurringDuplicate = if (!candidate.isRecurring && candidate.parentId == 0L) {
                index.findRecurringOccurrenceDuplicate(candidate)
            } else {
                null
            }
            if (recurringDuplicate != null) {
                skipped++
                return@forEach
            }

            inserts += importCandidate
            index.add(candidate)
        }

        val eventsForSystemSync = mutableListOf<Event>()
        if (inserts.isNotEmpty() || updates.isNotEmpty()) {
            db.runInTransaction {
                updates.forEach { importCandidate ->
                    val event = importCandidate.event
                    dao.insertOrUpdate(event)
                    if (shouldQueueForSystemPushAfterImport(event)) {
                        eventsForSystemSync += event
                    }
                }
                inserts.forEach { importCandidate ->
                    val event = importCandidate.event.copy(id = null)
                    val id = dao.insertOrUpdate(event)
                    val stored = event.copy(id = id)
                    if (shouldQueueForSystemPushAfterImport(stored)) {
                        eventsForSystemSync += stored
                    }
                }
            }
        }

        return MergeResult(
            inserted = inserts.size,
            updated = updates.size,
            skipped = skipped,
            candidateCount = candidates.size,
            eventsForSystemSync = eventsForSystemSync
        )
    }

    private fun quarantineLegacyFiles(files: List<File>): Int {
        if (files.isEmpty()) return 0
        val sourceFiles = files.filter { it.exists() }
        if (sourceFiles.isEmpty()) return 0

        val quarantineRoot = File(appContext.filesDir, QUARANTINE_DIR_NAME).apply { mkdirs() }
        val sessionDir = File(quarantineRoot, "migrated_${System.currentTimeMillis()}").apply { mkdirs() }

        var moved = 0
        sourceFiles.forEach { file ->
            val target = uniqueTargetFile(sessionDir, file.name)
            if (moveFile(file, target)) {
                moved++
            }
        }
        return moved
    }

    private fun cleanupExpiredQuarantineFiles() {
        val root = File(appContext.filesDir, QUARANTINE_DIR_NAME)
        if (!root.exists()) return
        val deadline = System.currentTimeMillis() - QUARANTINE_RETENTION_MILLIS

        root.listFiles().orEmpty().forEach { child ->
            if (child.lastModified() < deadline) {
                runCatching { child.deleteRecursively() }
                    .onFailure { Log.w(TAG, "Failed to delete expired quarantine entry: ${child.absolutePath}", it) }
            }
        }
    }

    private fun markMigrationDone(report: LegacyMigrationReport) {
        prefs.edit()
            .putInt(KEY_MIGRATION_VERSION, MIGRATION_VERSION)
            .putLong(KEY_MIGRATION_COMPLETED_AT, System.currentTimeMillis())
            .putString(KEY_MIGRATION_STATUS, report.status.name)
            .putString(KEY_MIGRATION_SUMMARY, report.toSummary())
            .apply()
    }

    private fun hasTable(db: SQLiteDatabase, tableName: String): Boolean {
        val cursor = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
            arrayOf(tableName)
        )
        cursor.use {
            return it.moveToFirst()
        }
    }

    private fun loadLegacyMasters(db: SQLiteDatabase): List<LegacyMaster> {
        val masters = mutableListOf<LegacyMaster>()
        db.rawQuery(
            "SELECT masterId, ruleId, title, description, location, colorArgb, rrule, syncId, remindersJson, isImportant, sourceImagePath, updatedAt, source FROM event_masters",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                masters += LegacyMaster(
                    masterId = cursor.string("masterId"),
                    ruleId = cursor.stringOrNull("ruleId"),
                    title = cursor.string("title"),
                    description = cursor.string("description"),
                    location = cursor.string("location"),
                    colorArgb = cursor.int("colorArgb"),
                    rrule = cursor.stringOrNull("rrule"),
                    syncId = cursor.longOrNull("syncId"),
                    remindersJson = cursor.stringOrNull("remindersJson").orEmpty(),
                    isImportant = cursor.intOrNull("isImportant") == 1,
                    sourceImagePath = cursor.stringOrNull("sourceImagePath"),
                    updatedAt = cursor.longOrNull("updatedAt") ?: 0L,
                    source = cursor.stringOrNull("source").orEmpty()
                )
            }
        }
        return masters
    }

    private fun loadLegacyInstances(db: SQLiteDatabase): List<LegacyInstance> {
        val instances = mutableListOf<LegacyInstance>()
        db.rawQuery(
            "SELECT instanceId, masterId, startTime, endTime, currentStateId, completedAt, archivedAt, isCancelled FROM event_instances",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                instances += LegacyInstance(
                    instanceId = cursor.string("instanceId"),
                    masterId = cursor.string("masterId"),
                    startTimeMillis = cursor.long("startTime"),
                    endTimeMillis = cursor.long("endTime"),
                    currentStateId = cursor.stringOrNull("currentStateId").orEmpty(),
                    completedAtMillis = cursor.longOrNull("completedAt"),
                    archivedAtMillis = cursor.longOrNull("archivedAt"),
                    isCancelled = cursor.intOrNull("isCancelled") == 1
                )
            }
        }
        return instances
    }

    private fun loadLegacyExcludedDates(db: SQLiteDatabase): Map<String, List<Long>> {
        val result = mutableMapOf<String, MutableList<Long>>()
        db.rawQuery("SELECT masterId, excludedStartTime FROM event_excluded_dates", null).use { cursor ->
            while (cursor.moveToNext()) {
                val masterId = cursor.string("masterId")
                val excluded = cursor.long("excludedStartTime")
                result.getOrPut(masterId) { mutableListOf() } += excluded
            }
        }
        return result
    }

    private fun buildLegacyExdates(excludedMillis: List<Long>, cancelledStartMillis: List<Long>): List<String> {
        val allMillis = (excludedMillis + cancelledStartMillis)
            .map { normalizeEpochMillis(it) }
            .filter { it > 0L }
            .distinct()
        return allMillis.map { millis ->
            EXDATE_FORMATTER.format(Instant.ofEpochMilli(millis))
        }
    }

    private fun buildCourseCandidateFromLegacyMaster(master: LegacyMaster, settings: MySettings): Event? {
        val meta = CourseEventMapper.parseMeta(master.description) ?: return null
        val id = meta.uid.ifBlank {
            CourseEventMapper.stableImportId(
                name = master.title,
                teacher = meta.teacher,
                room = master.location,
                dayOfWeek = meta.dayOfWeek,
                startNode = meta.startNode,
                endNode = meta.endNode,
                startWeek = meta.startWeek,
                endWeek = meta.endWeek,
                weekType = meta.weekType
            )
        }
        val course = Course(
            id = id,
            name = master.title,
            location = master.location,
            teacher = meta.teacher,
            color = master.colorArgb,
            dayOfWeek = meta.dayOfWeek,
            startNode = meta.startNode,
            endNode = meta.endNode,
            startWeek = meta.startWeek,
            endWeek = meta.endWeek,
            weekType = meta.weekType
        )
        return CourseEventMapper.toParentEvent(course, settings)
    }

    private fun parseLegacyReminderMinutes(raw: String): List<Int> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<List<Int>>(raw)
        }.getOrElse { emptyList() }
            .filter { it >= 0 }
            .distinct()
            .sorted()
            .take(3)
    }

    private fun appendLegacyMeta(description: String, isImportant: Boolean, sourceImagePath: String?): String {
        if (!isImportant && sourceImagePath.isNullOrBlank()) return description
        val suffix = buildString {
            if (isImportant) append("[legacy-important]\n")
            if (!sourceImagePath.isNullOrBlank()) append("[img:$sourceImagePath]")
        }.trim()
        if (suffix.isBlank()) return description
        return if (description.isBlank()) suffix else "$description\n$suffix"
    }

    private fun resolveSource(raw: String): String {
        val source = raw.trim()
        return if (source.isBlank()) SOURCE_SIMPLE_CALENDAR else source
    }

    private fun buildLegacyImportId(source: String, syncId: Long?): String {
        if (syncId == null || syncId <= 0L) return ""
        return if (source.startsWith(CALDAV, ignoreCase = true)) "$source-$syncId" else ""
    }

    private fun normalizeManualImportCandidate(event: Event): ImportCandidate {
        val hadSystemBinding = hasSystemCalendarBinding(event)
        return ImportCandidate(
            event = if (hadSystemBinding) {
                event.copy(importId = "", source = SOURCE_SIMPLE_CALENDAR)
            } else {
                event.copy(source = event.source.ifBlank { SOURCE_SIMPLE_CALENDAR })
            },
            detachedSystemBinding = hadSystemBinding,
            originalImportId = event.importId,
            originalSource = event.source
        )
    }

    private fun hasSystemCalendarBinding(event: Event): Boolean {
        return event.source.startsWith(CALDAV, ignoreCase = true) ||
            event.importId.startsWith(CALDAV, ignoreCase = true)
    }

    private fun shouldQueueForSystemPushAfterImport(event: Event): Boolean {
        return event.archivedAt == null &&
            event.importId.isBlank() &&
            event.parentId == 0L &&
            !isNoteTag(event.tag)
    }

    private fun shouldDetachExistingSystemBinding(existing: Event, candidate: ImportCandidate): Boolean {
        if (!candidate.detachedSystemBinding || !hasSystemCalendarBinding(existing)) return false
        val originalImportId = candidate.originalImportId.trim()
        val originalSource = candidate.originalSource.trim()
        return (originalImportId.isNotBlank() && existing.importId == originalImportId) ||
            (originalSource.isNotBlank() && existing.source == originalSource && existing.importId.startsWith("$originalSource-"))
    }

    private fun pushImportedEventsToSystemCalendar(events: List<Event>): Int {
        if (events.isEmpty()) return 0
        val config = CalendarConfig.newInstance(appContext)
        if (!config.caldavSync) {
            Log.i(TAG, "Skipped pushing imported events to system calendar: sync disabled, count=${events.size}")
            return 0
        }
        if (!hasCalendarPermission()) {
            Log.i(TAG, "Skipped pushing imported events to system calendar: missing calendar permission, count=${events.size}")
            return 0
        }

        val syncManager = SystemCalendarSyncManager(appContext)
        var pushed = 0
        events.forEach { event ->
            val id = event.id ?: return@forEach
            val latest = db.eventsDao().getEventOrTaskWithId(id) ?: event
            if (!shouldQueueForSystemPushAfterImport(latest)) return@forEach
            runCatching {
                val synced = syncManager.insertCalDAVEvent(
                    latest.copy(
                        importId = "",
                        source = SOURCE_SIMPLE_CALENDAR,
                        lastUpdated = System.currentTimeMillis() / 1000L
                    )
                )
                if (hasSystemCalendarBinding(synced)) {
                    db.eventsDao().insertOrUpdate(synced.copy(id = id, lastUpdated = System.currentTimeMillis() / 1000L))
                    pushed++
                } else {
                    Log.w(TAG, "Imported event was not bound to system calendar: localId=$id title=${latest.title}")
                }
            }.onFailure {
                Log.w(TAG, "Failed to push imported event to system calendar: localId=$id title=${latest.title}", it)
            }
        }
        return pushed
    }

    private fun hasCalendarPermission(): Boolean {
        val read = ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CALENDAR)
        val write = ContextCompat.checkSelfPermission(appContext, Manifest.permission.WRITE_CALENDAR)
        return read == PackageManager.PERMISSION_GRANTED && write == PackageManager.PERMISSION_GRANTED
    }

    private fun resolveTag(ruleId: String?): String {
        val normalized = ruleId?.trim()?.lowercase().orEmpty()
        return when (normalized) {
            "", "general" -> EventTags.GENERAL
            "pickup" -> EventTags.PICKUP
            "food" -> EventTags.FOOD
            "train" -> EventTags.TRAIN
            "taxi" -> EventTags.TAXI
            "flight" -> EventTags.FLIGHT
            "ticket" -> EventTags.TICKET
            "sender" -> EventTags.SENDER
            "course", "__removed_course__" -> EventTags.COURSE
            "note" -> EventTags.NOTE
            else -> normalized
        }
    }

    private fun resolveLegacyState(stateId: String?, completedAtMillis: Long?): Int {
        if (completedAtMillis != null && completedAtMillis > 0L) return STATE_COMPLETED
        val raw = stateId?.trim()?.lowercase().orEmpty()
        return when {
            raw.contains("checked") -> STATE_CHECKED_IN
            raw.contains("done") || raw.contains("complete") -> STATE_COMPLETED
            else -> STATE_PENDING
        }
    }

    private fun resolveLegacyStateFromBooleans(isCompleted: Boolean, isCheckedIn: Boolean): Int {
        return when {
            isCheckedIn -> STATE_CHECKED_IN
            isCompleted -> STATE_COMPLETED
            else -> STATE_PENDING
        }
    }

    private fun parseLegacyDateTimeToEpochSeconds(dateRaw: String, timeRaw: String): Long? {
        val date = parseLegacyDate(dateRaw) ?: return null
        val time = parseLegacyTime(timeRaw) ?: return null
        return date.atTime(time).atZone(ZoneId.systemDefault()).toEpochSecond()
    }

    private fun parseLegacyDate(raw: String): LocalDate? {
        val value = raw.trim()
        if (value.isBlank()) return null
        return runCatching { LocalDate.parse(value) }.getOrElse {
            runCatching {
                val parts = value.split('-', '/', '.').mapNotNull { part -> part.toIntOrNull() }
                if (parts.size == 3) LocalDate.of(parts[0], parts[1], parts[2]) else null
            }.getOrNull()
        }
    }

    private fun parseLegacyTime(raw: String): LocalTime? {
        val normalized = raw.trim()
            .replace('\uFF1A', ':')
            .replace('.', ':')
        if (normalized.isBlank()) return null

        return runCatching { LocalTime.parse(normalized) }.getOrElse {
            runCatching {
                val parts = normalized.split(':')
                val hour = parts.getOrNull(0)?.toIntOrNull() ?: return@runCatching null
                val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
                LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
            }.getOrNull()
        }
    }

    private fun normalizeTag(tag: String?): String {
        return when (tag?.trim()?.lowercase().orEmpty()) {
            "", "general" -> EventTags.GENERAL
            "__removed_course__", "course" -> EventTags.COURSE
            else -> tag?.trim()?.lowercase().orEmpty()
        }
    }

    private fun normalizeEpochSeconds(value: Long): Long {
        if (value <= 0L) return 0L
        return if (value > 9_999_999_999L) value / 1000L else value
    }

    private fun normalizeEpochMillis(value: Long): Long {
        if (value <= 0L) return 0L
        return if (value > 9_999_999_999L) value else value * 1000L
    }

    private fun normalizeTimestampSeconds(value: Long): Long {
        if (value <= 0L) return System.currentTimeMillis() / 1000L
        return if (value > 9_999_999_999L) value / 1000L else value
    }

    private fun normalizeNullableTimestampSeconds(value: Long?): Long? {
        if (value == null || value <= 0L) return null
        return if (value > 9_999_999_999L) value / 1000L else value
    }

    private fun candidatePrimaryKey(event: Event, preferSignatureKey: Boolean = false): String {
        if (preferSignatureKey) {
            return if (event.isRecurring) {
                "rec:${recurringSignature(event)}"
            } else {
                "single:${singleSignature(event)}"
            }
        }
        if (event.importId.isNotBlank()) return "import:${event.importId}"
        if (event.isRecurring) return "rec:${recurringSignature(event)}"
        return "single:${singleSignature(event)}"
    }

    private fun singleSignature(event: Event): String {
        return listOf(
            normalizeText(event.title),
            normalizeText(event.location),
            normalizeTag(event.tag),
            event.startTS.toString(),
            event.endTS.toString(),
            normalizeText(event.description)
        ).joinToString("|")
    }

    private fun recurringSignature(event: Event): String {
        val duration = event.endTS - event.startTS
        return listOf(
            normalizeText(event.title),
            normalizeText(event.location),
            normalizeTag(event.tag),
            normalizeRRule(event.rrule),
            event.startTS.toString(),
            duration.toString()
        ).joinToString("|")
    }

    private fun normalizeText(text: String?): String = text?.trim()?.lowercase().orEmpty()

    private fun normalizeRRule(rrule: String?): String {
        if (rrule.isNullOrBlank()) return ""
        return rrule.split(';')
            .map { it.trim().uppercase() }
            .filter { it.isNotBlank() }
            .sorted()
            .joinToString(";")
    }

    private fun shouldReplaceExisting(existing: Event, candidate: Event): Boolean {
        return normalizedComparisonMillis(candidate.lastUpdated) > normalizedComparisonMillis(existing.lastUpdated)
    }

    private fun shouldPreserveLegacyVisuals(existing: Event, candidate: Event): Boolean {
        val existingFromSystemCalendar = existing.source.startsWith(CALDAV, ignoreCase = true)
        val candidateFromLegacyApp = candidate.source == SOURCE_SIMPLE_CALENDAR || candidate.source.isBlank()
        return existingFromSystemCalendar && candidateFromLegacyApp && candidate.color != 0 && existing.color != candidate.color
    }

    private fun normalizedComparisonMillis(value: Long): Long {
        if (value <= 0L) return 0L
        return if (value > 9_999_999_999L) value else value * 1000L
    }

    private fun mergeEvent(existing: Event, candidate: Event, detachSystemBinding: Boolean = false): Event {
        return candidate.copy(
            id = existing.id,
            importId = when {
                detachSystemBinding -> candidate.importId
                candidate.importId.isNotBlank() -> candidate.importId
                else -> existing.importId
            },
            source = when {
                detachSystemBinding -> candidate.source.ifBlank { SOURCE_SIMPLE_CALENDAR }
                candidate.source.isNotBlank() -> candidate.source
                else -> existing.source
            },
            attendees = if (candidate.attendees.isNotEmpty()) candidate.attendees else existing.attendees,
            eventType = if (existing.eventType > 0) existing.eventType else candidate.eventType,
            parentId = if (candidate.parentId != 0L) candidate.parentId else existing.parentId,
            rrule = if (candidate.rrule.isNotBlank()) candidate.rrule else existing.rrule,
            exdates = if (candidate.exdates.isNotEmpty()) candidate.exdates else existing.exdates
        )
    }

    private fun uniqueTargetFile(parent: File, fileName: String): File {
        var target = File(parent, fileName)
        if (!target.exists()) return target
        var index = 1
        while (target.exists()) {
            target = File(parent, "$fileName.$index")
            index++
        }
        return target
    }

    private fun moveFile(source: File, target: File): Boolean {
        return runCatching {
            if (source.renameTo(target)) {
                true
            } else {
                source.copyTo(target, overwrite = true)
                source.delete()
            }
        }.getOrElse {
            Log.w(TAG, "Failed to move legacy file: ${source.absolutePath}", it)
            false
        }
    }

    private fun Event.toBackupDto(): EventBackupDto {
        return EventBackupDto(
            startTS = startTS,
            endTS = endTS,
            title = title,
            location = location,
            description = description,
            reminder1Minutes = reminder1Minutes,
            reminder2Minutes = reminder2Minutes,
            reminder3Minutes = reminder3Minutes,
            reminder1Type = reminder1Type,
            reminder2Type = reminder2Type,
            reminder3Type = reminder3Type,
            rrule = rrule,
            exdates = exdates,
            importId = importId,
            timeZone = timeZone,
            flags = flags,
            eventType = eventType,
            parentId = parentId,
            lastUpdated = lastUpdated,
            source = source,
            availability = availability,
            color = color,
            type = type,
            state = state,
            tag = tag,
            archivedAt = archivedAt
        )
    }

    private fun EventBackupDto.toEvent(forceArchived: Boolean, nowSeconds: Long): Event {
        return Event(
            id = null,
            startTS = startTS,
            endTS = max(startTS + 60L, endTS),
            title = title,
            location = location,
            description = description,
            reminder1Minutes = reminder1Minutes,
            reminder2Minutes = reminder2Minutes,
            reminder3Minutes = reminder3Minutes,
            reminder1Type = reminder1Type,
            reminder2Type = reminder2Type,
            reminder3Type = reminder3Type,
            rrule = rrule,
            exdates = exdates,
            attendees = emptyList(),
            importId = importId,
            timeZone = timeZone.ifBlank { ZoneId.systemDefault().id },
            flags = flags,
            eventType = eventType,
            parentId = 0L,
            lastUpdated = normalizeTimestampSeconds(lastUpdated),
            source = source.ifBlank { SOURCE_SIMPLE_CALENDAR },
            availability = availability,
            color = color,
            type = type,
            state = state,
            tag = normalizeTag(tag),
            archivedAt = archivedAt ?: if (forceArchived) nowSeconds else null
        )
    }

    private inner class ExistingIndex(existingEvents: List<Event>) {
        private val existingByImportId = mutableMapOf<String, Event>()
        private val existingByRecurringSignature = mutableMapOf<String, Event>()
        private val existingBySingleSignature = mutableMapOf<String, Event>()
        private val recurringParentsByOccurrenceKey = mutableMapOf<String, MutableList<Event>>()

        init {
            existingEvents.forEach { add(it) }
        }

        fun add(event: Event) {
            if (event.importId.isNotBlank()) {
                existingByImportId[event.importId] = event
            }
            if (event.isRecurring) {
                existingByRecurringSignature[recurringSignature(event)] = event
                val key = recurringOccurrenceKey(event)
                recurringParentsByOccurrenceKey.getOrPut(key) { mutableListOf() } += event
            } else {
                existingBySingleSignature[singleSignature(event)] = event
            }
        }

        fun replace(oldEvent: Event, newEvent: Event) {
            remove(oldEvent)
            add(newEvent)
        }

        fun findDirectConflict(candidate: Event): Event? {
            if (candidate.importId.isNotBlank()) {
                existingByImportId[candidate.importId]?.let { return it }
            }
            return if (candidate.isRecurring) {
                existingByRecurringSignature[recurringSignature(candidate)]
            } else {
                existingBySingleSignature[singleSignature(candidate)]
            }
        }

        fun findRecurringOccurrenceDuplicate(candidate: Event): Event? {
            val key = recurringOccurrenceKey(candidate)
            val parents = recurringParentsByOccurrenceKey[key].orEmpty()
            if (parents.isEmpty()) return null

            return parents.firstOrNull { parent ->
                candidate.endTS - candidate.startTS == parent.endTS - parent.startTS &&
                    isOccurrenceOfParent(parent, candidate.startTS)
            }
        }

        private fun remove(event: Event) {
            if (event.importId.isNotBlank()) {
                existingByImportId.remove(event.importId)
            }
            if (event.isRecurring) {
                existingByRecurringSignature.remove(recurringSignature(event))
                val key = recurringOccurrenceKey(event)
                recurringParentsByOccurrenceKey[key]?.removeAll { stored ->
                    if (event.id != null) {
                        stored.id == event.id
                    } else {
                        stored === event
                    }
                }
            } else {
                existingBySingleSignature.remove(singleSignature(event))
            }
        }

        private fun recurringOccurrenceKey(event: Event): String {
            val zone = safeZone(event.timeZone)
            val zdt = Instant.ofEpochSecond(event.startTS).atZone(zone)
            return listOf(
                normalizeText(event.title),
                normalizeText(event.location),
                normalizeTag(event.tag),
                zdt.toLocalTime().toString()
            ).joinToString("|")
        }

        private fun isOccurrenceOfParent(parent: Event, occurrenceTs: Long): Boolean {
            val parsedRule = parseRRule(parent.rrule) ?: return false
            val zone = safeZone(parent.timeZone)
            val start = Instant.ofEpochSecond(parent.startTS).atZone(zone)
            val target = Instant.ofEpochSecond(occurrenceTs).atZone(zone)
            if (target.isBefore(start)) return false
            if (target.toLocalTime() != start.toLocalTime()) return false

            val occurrenceEpoch = target.toEpochSecond()
            if (parent.exdates.asSequence().mapNotNull { parseExdateToEpochSeconds(it) }.any { it == occurrenceEpoch }) {
                return false
            }

            var cursor = start
            var emitted = 0
            val maxCount = parsedRule.count ?: MAX_OCCURRENCE_SCAN
            while (emitted < maxCount && emitted < MAX_OCCURRENCE_SCAN) {
                val localDate = cursor.toLocalDate()
                if (parsedRule.until != null && localDate.isAfter(parsedRule.until)) break

                if (cursor.toEpochSecond() == occurrenceEpoch) return true
                if (cursor.isAfter(target)) return false

                val next = advanceCursor(cursor, parsedRule, start)
                if (!next.isAfter(cursor)) break
                cursor = next
                emitted++
            }
            return false
        }

        private fun parseRRule(rrule: String): ParsedRRule? {
            if (rrule.isBlank()) return null
            val pairs = rrule.split(';')
                .mapNotNull {
                    val idx = it.indexOf('=')
                    if (idx <= 0) null else it.substring(0, idx).uppercase() to it.substring(idx + 1)
                }
                .toMap()
            val freq = pairs["FREQ"]?.uppercase() ?: return null
            return ParsedRRule(
                freq = freq,
                interval = pairs["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                count = pairs["COUNT"]?.toIntOrNull()?.takeIf { it > 0 },
                until = pairs["UNTIL"]?.let(::parseUntilDate),
                byDay = pairs["BYDAY"]?.let(::parseByDay).orEmpty()
            )
        }

        private fun advanceCursor(current: ZonedDateTime, rule: ParsedRRule, original: ZonedDateTime): ZonedDateTime {
            return when (rule.freq) {
                "DAILY" -> current.plusDays(rule.interval.toLong())
                "WEEKLY" -> {
                    if (rule.byDay.isEmpty()) {
                        current.plusWeeks(rule.interval.toLong())
                    } else {
                        var probe = current.plusDays(1)
                        val weekBoundary = current.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                            .plusWeeks(rule.interval.toLong())
                        while (!probe.isAfter(weekBoundary)) {
                            if (probe.dayOfWeek in rule.byDay) return probe
                            probe = probe.plusDays(1)
                        }
                        for (i in 0..6) {
                            val candidate = weekBoundary.plusDays(i.toLong())
                            if (candidate.dayOfWeek in rule.byDay) return candidate
                        }
                        current.plusWeeks(rule.interval.toLong())
                    }
                }
                "MONTHLY" -> {
                    val next = current.plusMonths(rule.interval.toLong())
                    runCatching { next.withDayOfMonth(original.dayOfMonth) }
                        .getOrElse { next.with(TemporalAdjusters.lastDayOfMonth()) }
                }
                "YEARLY" -> current.plusYears(rule.interval.toLong())
                else -> current.plusDays(1)
            }
        }

        private fun parseByDay(raw: String): Set<DayOfWeek> {
            return raw.split(',').mapNotNull { token ->
                when (token.trim().takeLast(2).uppercase()) {
                    "MO" -> DayOfWeek.MONDAY
                    "TU" -> DayOfWeek.TUESDAY
                    "WE" -> DayOfWeek.WEDNESDAY
                    "TH" -> DayOfWeek.THURSDAY
                    "FR" -> DayOfWeek.FRIDAY
                    "SA" -> DayOfWeek.SATURDAY
                    "SU" -> DayOfWeek.SUNDAY
                    else -> null
                }
            }.toSet()
        }

        private fun parseUntilDate(raw: String): LocalDate? {
            return runCatching { LocalDate.parse(raw.take(8), DateTimeFormatter.BASIC_ISO_DATE) }.getOrNull()
        }

        private fun parseExdateToEpochSeconds(raw: String): Long? {
            val normalized = raw.substringAfterLast(';').trim()
            return runCatching {
                Instant.from(EXDATE_FORMATTER.parse(normalized)).epochSecond
            }.getOrNull()
        }

        private fun safeZone(timeZone: String?): ZoneId {
            return runCatching {
                if (timeZone.isNullOrBlank()) ZoneId.systemDefault() else ZoneId.of(timeZone)
            }.getOrDefault(ZoneId.systemDefault())
        }

    }

    private data class ParsedRRule(
        val freq: String,
        val interval: Int,
        val count: Int?,
        val until: LocalDate?,
        val byDay: Set<DayOfWeek>
    )

    private data class MergeResult(
        val inserted: Int,
        val updated: Int,
        val skipped: Int,
        val candidateCount: Int,
        val eventsForSystemSync: List<Event> = emptyList()
    )

    private data class ImportCandidate(
        val event: Event,
        val detachedSystemBinding: Boolean = false,
        val originalImportId: String = "",
        val originalSource: String = ""
    )

    private data class LegacyMaster(
        val masterId: String,
        val ruleId: String?,
        val title: String,
        val description: String,
        val location: String,
        val colorArgb: Int,
        val rrule: String?,
        val syncId: Long?,
        val remindersJson: String,
        val isImportant: Boolean,
        val sourceImagePath: String?,
        val updatedAt: Long,
        val source: String
    )

    private data class LegacyInstance(
        val instanceId: String,
        val masterId: String,
        val startTimeMillis: Long,
        val endTimeMillis: Long,
        val currentStateId: String,
        val completedAtMillis: Long?,
        val archivedAtMillis: Long?,
        val isCancelled: Boolean
    )

    @Serializable
    private data class EventsBackupDataV3(
        val version: Int = 0,
        val events: List<EventBackupDto> = emptyList(),
        val archivedEvents: List<EventBackupDto> = emptyList()
    )

    @Serializable
    private data class EventBackupDto(
        val startTS: Long = 0L,
        val endTS: Long = 0L,
        val title: String = "",
        val location: String = "",
        val description: String = "",
        val reminder1Minutes: Int = REMINDER_OFF,
        val reminder2Minutes: Int = REMINDER_OFF,
        val reminder3Minutes: Int = REMINDER_OFF,
        val reminder1Type: Int = REMINDER_NOTIFICATION,
        val reminder2Type: Int = REMINDER_NOTIFICATION,
        val reminder3Type: Int = REMINDER_NOTIFICATION,
        val rrule: String = "",
        val exdates: List<String> = emptyList(),
        val importId: String = "",
        val timeZone: String = "",
        val flags: Int = 0,
        val eventType: Long = 1L,
        val parentId: Long = 0L,
        val lastUpdated: Long = 0L,
        val source: String = SOURCE_SIMPLE_CALENDAR,
        val availability: Int = 0,
        val color: Int = 0,
        val type: Int = 0,
        val state: Int = STATE_PENDING,
        val tag: String = EventTags.GENERAL,
        val archivedAt: Long? = null
    )

    @Serializable
    private data class LegacyCourseEventsBackupData(
        val version: Int = 2,
        val courseEvents: List<LegacyMyEvent> = emptyList(),
        val semesterStartDate: String = "",
        val totalWeeks: Int = 20,
        val timeTableJson: String = "",
        val timeTableConfigJson: String = ""
    )

    @Serializable
    private data class LegacyEventsBackupData(
        val events: List<LegacyMyEvent> = emptyList(),
        val archivedEvents: List<LegacyMyEvent> = emptyList()
    )

    @Serializable
    private data class LegacyMyEvent(
        val id: String = "",
        val title: String = "",
        val startDate: String = "",
        val endDate: String = "",
        val startTime: String = "",
        val endTime: String = "",
        val location: String = "",
        val description: String = "",
        val color: Int = 0,
        val isImportant: Boolean = false,
        val sourceImagePath: String? = null,
        val reminders: List<Int> = emptyList(),
        val tag: String = EventTags.GENERAL,
        val isCompleted: Boolean = false,
        val completedAt: Long? = null,
        val isCheckedIn: Boolean = false,
        val archivedAt: Long? = null,
        val lastModified: Long = 0L,
        val isRecurring: Boolean = false,
        val isRecurringParent: Boolean = false
    )

    private fun Cursor.string(column: String): String {
        val index = getColumnIndex(column)
        return if (index < 0 || isNull(index)) "" else getString(index).orEmpty()
    }

    private fun Cursor.stringOrNull(column: String): String? {
        val index = getColumnIndex(column)
        return if (index < 0 || isNull(index)) null else getString(index)
    }

    private fun Cursor.long(column: String): Long {
        val index = getColumnIndex(column)
        return if (index < 0 || isNull(index)) 0L else getLong(index)
    }

    private fun Cursor.longOrNull(column: String): Long? {
        val index = getColumnIndex(column)
        return if (index < 0 || isNull(index)) null else getLong(index)
    }

    private fun Cursor.int(column: String): Int {
        val index = getColumnIndex(column)
        return if (index < 0 || isNull(index)) 0 else getInt(index)
    }

    private fun Cursor.intOrNull(column: String): Int? {
        val index = getColumnIndex(column)
        return if (index < 0 || isNull(index)) null else getInt(index)
    }

    companion object {
        private const val TAG = "LegacyMigration"
        private const val PREFS_NAME = "migration_state"
        private const val KEY_MIGRATION_VERSION = "legacy_to_events_migration_version"
        private const val KEY_MIGRATION_COMPLETED_AT = "legacy_to_events_migration_completed_at"
        private const val KEY_MIGRATION_STATUS = "legacy_to_events_migration_status"
        private const val KEY_MIGRATION_SUMMARY = "legacy_to_events_migration_summary"

        private const val MIGRATION_VERSION = 1
        private const val LEGACY_DB_NAME = "calendar_assistant.db"
        private const val QUARANTINE_DIR_NAME = "legacy_quarantine"
        private const val QUARANTINE_RETENTION_MILLIS = 3L * 24L * 60L * 60L * 1000L
        private const val MAX_OCCURRENCE_SCAN = 4000

        private val EXDATE_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

        private val LEGACY_JSON_FILES = listOf(
            "events.json",
            "events.json.bak",
            "events.room.bak",
            "archives.json",
            "archives.json.bak",
            "archives.room.bak",
            "courses.json",
            "courses.json.bak"
        )
    }
}

data class LegacyMigrationReport(
    val status: MigrationStatus,
    val inserted: Int,
    val updated: Int,
    val skipped: Int,
    val candidateCount: Int,
    val movedLegacyFiles: Int,
    val message: String
) {
    fun toSummary(): String {
        return "status=$status,candidates=$candidateCount,inserted=$inserted,updated=$updated,skipped=$skipped,moved=$movedLegacyFiles,msg=$message"
    }
}

enum class MigrationStatus {
    SUCCESS,
    SKIPPED_ALREADY_MIGRATED,
    SKIPPED_NO_LEGACY_DATA,
    SKIPPED_NO_VALID_CANDIDATES,
    FAILED
}
