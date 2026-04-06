package com.antgskds.calendarassistant.data.repository

import android.content.Context
import android.util.Log
import androidx.compose.ui.graphics.toArgb
import com.antgskds.calendarassistant.data.model.Course
import com.antgskds.calendarassistant.core.rule.EventActionResolver
import com.antgskds.calendarassistant.core.rule.RuleActionType
import com.antgskds.calendarassistant.core.rule.RuleMatchingEngine
import com.antgskds.calendarassistant.core.rule.RuleRegistry
import com.antgskds.calendarassistant.data.model.EventTags
import com.antgskds.calendarassistant.data.model.EventType
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.service.notification.NotificationScheduler
import com.antgskds.calendarassistant.core.calendar.CalendarSyncGateway
import com.antgskds.calendarassistant.core.util.CrashHandler
import com.antgskds.calendarassistant.core.util.EventDeduplicator
import com.antgskds.calendarassistant.data.model.ImportResult
import com.antgskds.calendarassistant.core.calendar.CalendarManager
import com.antgskds.calendarassistant.core.calendar.CalendarSyncManager
import com.antgskds.calendarassistant.core.calendar.CalendarSyncV2Prefs
import com.antgskds.calendarassistant.core.importer.WakeUpCourseImporter
import com.antgskds.calendarassistant.ui.theme.getRandomEventColor
import com.antgskds.calendarassistant.core.importer.ImportMode
import com.antgskds.calendarassistant.data.db.AppDatabase
import com.antgskds.calendarassistant.data.db.reader.RoomEventReader
import com.antgskds.calendarassistant.data.db.shadow.RoomEventShadowWriter
import com.antgskds.calendarassistant.data.migration.LegacyArchiveMigrator
import com.antgskds.calendarassistant.data.migration.LegacyEventMigrator
import com.antgskds.calendarassistant.data.migration.MigrationPrefs
import com.antgskds.calendarassistant.data.migration.LegacyRecurringMigrator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.antgskds.calendarassistant.core.capsule.CapsuleStateManager
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.UUID

class AppRepository private constructor(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    val appContext: Context = context.applicationContext

    // 数据源
    private val eventRepository = EventRepository(context)
    private val courseRepository = CourseRepository(context)
    private val settingsRepository = SettingsRepository(context)
    private val archiveRepository = ArchiveRepository(context)
    private val syncMappingRepository = SyncMappingRepository(context)

    private val database by lazy { AppDatabase.getInstance(context.applicationContext) }
    private val migrationPrefs by lazy { MigrationPrefs(context.applicationContext) }
    private val legacyEventMigrator by lazy { LegacyEventMigrator(database, migrationPrefs) }
    private val legacyArchiveMigrator by lazy { LegacyArchiveMigrator(database, migrationPrefs) }
    private val legacyRecurringMigrator by lazy { LegacyRecurringMigrator(database, migrationPrefs) }
    private val roomShadowWriter by lazy { RoomEventShadowWriter(database) }
    private val roomEventReader by lazy { RoomEventReader(database) }
    // StateFlows
    private val _events = MutableStateFlow<List<MyEvent>>(emptyList())
    val events: StateFlow<List<MyEvent>> = _events.asStateFlow()

    private val _courses = MutableStateFlow<List<Course>>(emptyList())
    val courses: StateFlow<List<Course>> = _courses.asStateFlow()

    private val _settings = MutableStateFlow(MySettings())
    val settings: StateFlow<MySettings> = _settings.asStateFlow()

    private val _archivedEvents = MutableStateFlow<List<MyEvent>>(emptyList())
    val archivedEvents: StateFlow<List<MyEvent>> = _archivedEvents.asStateFlow()

    // 【新增】胶囊状态管理器 - ✅ 直接初始化，避免 lazy 死锁
    val capsuleStateManager: CapsuleStateManager = CapsuleStateManager(this, scope, context.applicationContext)

    // 【新增】日历同步管理器
    private val syncGateway = CalendarSyncGateway(context.applicationContext)

    private val eventMutex = Mutex()
    private val courseMutex = Mutex()
    private val archiveMutex = Mutex()

    @Volatile
    private var syncSeedAttempted = false

    private data class ArchivedLoadResult(
        val mergedArchived: List<MyEvent>
    )

    private data class CalendarSyncSnapshot(
        val activeEvents: List<MyEvent>,
        val archivedEvents: List<MyEvent>
    )

    init {
        refreshData()
        migrateEventTypes()
        migrateEventTags()
        scope.launch(Dispatchers.IO) {
            RuleRegistry.refresh(appContext)
        }
    }

    private fun migrateEventTypes() {
        scope.launch {
            if (isRoomMainEnabled() && migrationPrefs.isEventsMigrated() && migrationPrefs.isRecurringMigrated()) {
                return@launch
            }
                val events = eventRepository.loadEvents()
            val needMigration = events.any { it.eventType == "temp" || it.eventType == "pickup" }
            if (needMigration) {
                val migratedEvents = events.map {
                    when (it.eventType) {
                        "temp", "pickup" -> it.copy(
                            eventType = EventType.EVENT,
                            tag = EventTags.PICKUP
                        )
                        else -> it
                    }
                }
                eventRepository.saveEvents(migratedEvents)
                _events.value = migratedEvents
                Log.i("Migration", "已迁移 ${events.size} 条旧数据: temp/pickup -> event + tag=pickup")
            }
        }
    }

    private fun migrateEventTags() {
        scope.launch {
            if (isRoomMainEnabled() && migrationPrefs.isEventsMigrated() && migrationPrefs.isRecurringMigrated()) {
                return@launch
            }
            val events = eventRepository.loadEvents()
            val needMigration = events.any { it.tag.isBlank() || it.tag.isEmpty() }

            // 第一步：只对 tag 为空的事件进行迁移
            val migratedEvents = if (needMigration) {
                events.map { event ->
                    if (event.tag.isBlank() || event.tag.isEmpty()) {
                        val resolved = RuleMatchingEngine.resolvePayload(event.description, null)?.ruleId
                        val newTag = resolved?.ifBlank { null } ?: EventTags.GENERAL
                        event.copy(tag = newTag)
                    } else {
                        event
                    }
                }
            } else {
                events
            }

            // 第二步：校准所有事件的 tag（根据 description 更正错误的 tag）
            val calibratedEvents = migratedEvents.map { event ->
                val correctTag = RuleMatchingEngine.resolvePayload(event.description, null)?.ruleId
                if (!correctTag.isNullOrBlank() && correctTag != event.tag) {
                    Log.d("Calibration", "校准事件 tag: ${event.title}, ${event.tag} -> $correctTag")
                    event.copy(tag = correctTag)
                } else {
                    event
                }
            }

            // 保存并更新
            eventRepository.saveEvents(calibratedEvents)
            _events.value = calibratedEvents

            if (needMigration) {
                Log.i("Migration", "已迁移 ${events.size} 条旧数据的 tag")
            }
        }
    }

    fun loadAndScheduleAll() {
        refreshData()
    }

    private fun refreshData() {
        scope.launch {
            val useRoomMain = isRoomMainEnabled()
            val roomHasData = if (useRoomMain) {
                runCatching { database.eventMasterDao().count() > 0 }.getOrElse { false }
            } else {
                false
            }
            val shouldLoadJson = !useRoomMain || !migrationPrefs.isEventsMigrated() || !migrationPrefs.isRecurringMigrated() || !roomHasData
            val rawEvents = if (shouldLoadJson) {
                eventRepository.loadEvents()
            } else {
                emptyList()
            }
            val loadedCourses = courseRepository.loadCourses()
            val loadedSettings = settingsRepository.loadSettings()
            
            val eventCleanupInfo = if (shouldLoadJson) {
                eventRepository.getAndClearCleanupInfo()
            } else {
                ""
            }
            val courseCleanupInfo = courseRepository.getAndClearCleanupInfo()
            
            val cleanupInfo = listOf(eventCleanupInfo, courseCleanupInfo)
                .filter { it.isNotEmpty() }
                .joinToString("，")
            
            if (cleanupInfo.isNotEmpty()) {
                CrashHandler.saveCleanupInfo(appContext, cleanupInfo)
                Log.i("AppRepository", "数据自愈: $cleanupInfo")
            }
            
            val loadedEvents = rawEvents
            // 🔥 修复：移除归档加载，改为懒加载（冷启动性能优化）
            // val loadedArchives = archiveRepository.loadArchivedEvents()

            if (rawEvents.isNotEmpty() && loadedEvents.size != rawEvents.size) {
                eventRepository.saveEvents(loadedEvents)
            }

            if (shouldLoadJson) {
                legacyEventMigrator.migrateIfNeeded(loadedEvents)
                legacyRecurringMigrator.migrateIfNeeded(rawEvents)
            }

            if (!migrationPrefs.isArchivesMigrated()) {
                val rawArchived = archiveRepository.loadArchivedEvents()
                val archivedForMigration = sanitizeRecurringEvents(rawArchived)
                if (archivedForMigration.size != rawArchived.size) {
                    archiveRepository.saveArchivedEvents(archivedForMigration)
                }
                legacyArchiveMigrator.migrateIfNeeded(archivedForMigration)
            }

            cleanupLegacyJsonIfStable()

            val useRoomRead = isRoomReadEnabled()
            val roomEvents = if (useRoomRead || useRoomMain) {
                roomEventReader.loadActiveEvents()
            } else {
                emptyList()
            }
            val sanitizedRoomEvents = sanitizeRecurringEvents(roomEvents)
            val roomRecurringPresent = sanitizedRoomEvents.any { it.isRecurring || it.isRecurringParent }
            val jsonRecurringEvents = loadedEvents.filter { it.isRecurring || it.isRecurringParent }
            val jsonRecurringById = jsonRecurringEvents.associateBy { it.id }
            val jsonRecurringIds = jsonRecurringById.keys
            val mergedRoomEvents = when {
                useRoomMain && roomRecurringPresent -> sanitizedRoomEvents
                useRoomMain && jsonRecurringEvents.isNotEmpty() -> {
                    Log.w("AppRepository", "Room 主存缺少重复事件，回退 JSON 递补")
                    val roomIds = sanitizedRoomEvents.map { it.id }.toSet()
                    val replaced = sanitizedRoomEvents.map { jsonRecurringById[it.id] ?: it }
                    val missingRecurring = jsonRecurringEvents.filter { it.id !in roomIds }
                    replaced + missingRecurring
                }
                else -> sanitizedRoomEvents
            }
            val activeEvents = when {
                useRoomMain && (mergedRoomEvents.isNotEmpty() || loadedEvents.isEmpty()) -> mergedRoomEvents
                useRoomMain -> {
                    Log.w("AppRepository", "Room 主存开启但未读取到数据，回退 JSON")
                    loadedEvents
                }
                useRoomRead -> sanitizedRoomEvents
                else -> loadedEvents
            }

            if ((useRoomRead || useRoomMain) && rawEvents.isNotEmpty()) {
                val diffJsonEvents = if (useRoomMain) {
                    loadedEvents.filter { !it.isRecurring && !it.isRecurringParent }
                } else {
                    loadedEvents
                }
                val diffRoomEvents = if (useRoomMain) {
                    if (roomRecurringPresent) {
                        sanitizedRoomEvents.filter { !it.isRecurring && !it.isRecurringParent }
                    } else {
                        sanitizedRoomEvents.filter { it.id !in jsonRecurringIds }
                    }
                } else {
                    sanitizedRoomEvents
                }
                logRoomDiff("active", diffJsonEvents, diffRoomEvents)
            }

            _events.value = activeEvents
            _courses.value = loadedCourses
            _settings.value = loadedSettings
            // _archivedEvents 保持初始为空，直到用户查看时加载

            activeEvents.forEach { event ->
                scheduleRemindersIfNeeded(event)
            }

            // 启动后尝试自动归档（建议延迟执行，不阻塞启动）
            launch {
                autoArchiveExpiredEvents()
            }
        }
    }

    /**
     * 🔥 修复：懒加载归档数据
     * 仅在进入归档页面时调用
     */
    fun fetchArchivedEvents() {
        scope.launch {
            archiveMutex.withLock {
                _archivedEvents.value = loadArchivedEventsForState().mergedArchived
            }
        }
    }

    /**
     * ✅ 核心修复：内部使用的确保归档已加载方法
     * 防止在导入或同步时因归档未加载导致去重失效
     */
    private suspend fun ensureArchivesLoaded() {
        // 如果内存中为空，则尝试加载
        if (_archivedEvents.value.isEmpty()) {
            archiveMutex.withLock {
                // 双重检查，防止并发加载
                if (_archivedEvents.value.isEmpty()) {
                    Log.d("AppRepository", "触发归档数据懒加载...")
                    _archivedEvents.value = loadArchivedEventsForState().mergedArchived
                }
            }
        }
    }

    private suspend fun loadArchivedEventsForState(): ArchivedLoadResult {
        val useRoomRead = isRoomReadEnabled()
        val useRoomMain = isRoomMainEnabled()
        val roomArchived = if (useRoomRead || useRoomMain) {
            roomEventReader.loadArchivedEvents()
        } else {
            emptyList()
        }
        val shouldLoadJson = !useRoomMain || !migrationPrefs.isArchivesMigrated()
        val rawLoaded = if (shouldLoadJson) {
            archiveRepository.loadArchivedEvents()
        } else {
            emptyList()
        }
        val loaded = sanitizeRecurringEvents(rawLoaded)
        if (shouldLoadJson && loaded.size != rawLoaded.size) {
            archiveRepository.saveArchivedEvents(loaded)
        }
        if (shouldLoadJson) {
            legacyArchiveMigrator.migrateIfNeeded(loaded)
        }
        val mergedArchived = when {
            useRoomMain && (roomArchived.isNotEmpty() || loaded.isEmpty()) -> roomArchived
            useRoomMain -> {
                Log.w("AppRepository", "Room 主存开启但未读取到归档数据，回退 JSON")
                loaded
            }
            useRoomRead -> roomArchived
            else -> loaded
        }
        if ((useRoomRead || useRoomMain) && shouldLoadJson) {
            logRoomDiff("archived", loaded, roomArchived)
        }
        return ArchivedLoadResult(mergedArchived = mergedArchived)
    }

    fun setRoomReadEnabled(enabled: Boolean) {
        settingsRepository.setRoomReadEnabled(enabled)
    }

    fun isRoomReadEnabled(): Boolean {
        return settingsRepository.isRoomReadEnabled()
    }

    fun setRoomMainEnabled(enabled: Boolean) {
        settingsRepository.setRoomMainEnabled(enabled)
    }

    fun isRoomMainEnabled(): Boolean {
        return settingsRepository.isRoomMainEnabled()
    }

    private fun isCalendarSyncV2Enabled(): Boolean {
        return CalendarSyncV2Prefs.isEnabled(appContext)
    }

    private fun logRoomDiff(label: String, jsonEvents: List<MyEvent>, roomEvents: List<MyEvent>) {
        val jsonMap = jsonEvents.associateBy { it.id }
        val roomMap = roomEvents.associateBy { it.id }
        val jsonIds = jsonMap.keys
        val roomIds = roomMap.keys
        val missingInRoom = jsonIds - roomIds
        val missingInJson = roomIds - jsonIds

        val mismatches = jsonIds.intersect(roomIds).mapNotNull { id ->
            val jsonSnapshot = EventCompareSnapshot.from(jsonMap[id] ?: return@mapNotNull null)
            val roomSnapshot = EventCompareSnapshot.from(roomMap[id] ?: return@mapNotNull null)
            val diffs = jsonSnapshot.diff(roomSnapshot)
            if (diffs.isEmpty()) null else id to diffs
        }

        if (missingInRoom.isEmpty() && missingInJson.isEmpty() && mismatches.isEmpty()) {
            Log.d("RoomRead", "$label list match: ${jsonEvents.size} items")
            return
        }

        val sampleMissingRoom = missingInRoom.take(5)
        val sampleMissingJson = missingInJson.take(5)
        val sampleMismatch = mismatches.take(5).joinToString(", ") { (id, diffs) ->
            "$id(${diffs.joinToString("/")})"
        }

        Log.w(
            "RoomRead",
            "$label list mismatch: json=${jsonEvents.size}, room=${roomEvents.size}, " +
                "missingInRoom=${missingInRoom.size} sample=$sampleMissingRoom, " +
                "missingInJson=${missingInJson.size} sample=$sampleMissingJson, " +
                "fieldMismatch=${mismatches.size} sample=$sampleMismatch"
        )
    }

    private data class EventCompareSnapshot(
        val title: String,
        val startDate: java.time.LocalDate,
        val endDate: java.time.LocalDate,
        val startTime: String,
        val endTime: String,
        val location: String,
        val description: String,
        val colorArgb: Int,
        val isImportant: Boolean,
        val sourceImagePath: String?,
        val reminders: List<Int>,
        val eventType: String,
        val tag: String,
        val isCompleted: Boolean,
        val completedAtPresent: Boolean,
        val isCheckedIn: Boolean,
        val archivedAt: Long?,
        val skipCalendarSync: Boolean,
        val isRecurring: Boolean,
        val isRecurringParent: Boolean
    ) {
        fun diff(other: EventCompareSnapshot): List<String> {
            val diffs = mutableListOf<String>()
            if (title != other.title) diffs.add("title")
            if (startDate != other.startDate) diffs.add("startDate")
            if (endDate != other.endDate) diffs.add("endDate")
            if (startTime != other.startTime) diffs.add("startTime")
            if (endTime != other.endTime) diffs.add("endTime")
            if (location != other.location) diffs.add("location")
            if (description != other.description) diffs.add("description")
            if (colorArgb != other.colorArgb) diffs.add("color")
            if (isImportant != other.isImportant) diffs.add("isImportant")
            if (sourceImagePath != other.sourceImagePath) diffs.add("sourceImagePath")
            if (reminders != other.reminders) diffs.add("reminders")
            if (eventType != other.eventType) diffs.add("eventType")
            if (tag != other.tag) diffs.add("tag")
            if (isCompleted != other.isCompleted) diffs.add("isCompleted")
            if (completedAtPresent != other.completedAtPresent) diffs.add("completedAt")
            if (isCheckedIn != other.isCheckedIn) diffs.add("isCheckedIn")
            if (archivedAt != other.archivedAt) diffs.add("archivedAt")
            if (skipCalendarSync != other.skipCalendarSync) diffs.add("skipCalendarSync")
            if (isRecurring != other.isRecurring) diffs.add("isRecurring")
            if (isRecurringParent != other.isRecurringParent) diffs.add("isRecurringParent")
            return diffs
        }

        companion object {
            fun from(event: MyEvent): EventCompareSnapshot {
                return EventCompareSnapshot(
                    title = event.title,
                    startDate = event.startDate,
                    endDate = event.endDate,
                    startTime = event.startTime,
                    endTime = event.endTime,
                    location = event.location,
                    description = event.description,
                    colorArgb = event.color.toArgb(),
                    isImportant = event.isImportant,
                    sourceImagePath = event.sourceImagePath,
                    reminders = event.reminders,
                    eventType = event.eventType,
                    tag = event.tag,
                    isCompleted = event.isCompleted,
                    completedAtPresent = event.completedAt != null,
                    isCheckedIn = event.isCheckedIn,
                    archivedAt = event.archivedAt,
                    skipCalendarSync = event.skipCalendarSync,
                    isRecurring = event.isRecurring,
                    isRecurringParent = event.isRecurringParent
                )
            }
        }
    }

    // --- Events 操作 ---

    /**
     * 添加事件
     *
     * @param event 要添加的事件
     * @param triggerSync 是否触发同步到系统日历（默认 true）
     * 🔥 修复：增加 triggerSync 参数，避免反向同步时触发死循环
     */
    suspend fun addEvent(event: MyEvent, triggerSync: Boolean = true) = eventMutex.withLock {
        val currentList = loadCurrentActiveMutableList()
        currentList.add(event)
        updateEvents(currentList)
        scheduleRemindersIfNeeded(event)
        if (triggerSync) {
            triggerAutoSync()
        }
    }

    /**
     * 更新事件
     *
     * @param event 要更新的事件
     * @param triggerSync 是否触发同步到系统日历（默认 true）
     * 🔥 修复：增加 triggerSync 参数，避免反向同步时触发死循环
     */
    suspend fun updateEvent(event: MyEvent, triggerSync: Boolean = true) = eventMutex.withLock {
        val currentList = loadCurrentActiveMutableList()
        val index = currentList.indexOfFirst { it.id == event.id }
        if (index != -1) {
            val oldEvent = currentList[index]
            cancelReminders(oldEvent)
            currentList[index] = event
            updateEvents(currentList)
            Log.d("Undo", "updateEvent后: id=${event.id}, isCheckedIn=${event.isCheckedIn}")
            scheduleRemindersIfNeeded(event)
            if (triggerSync) {
                triggerAutoSync()
            }
        }
    }

    /**
     * 删除事件
     *
     * @param eventId 要删除的事件 ID
     * @param triggerSync 是否触发同步到系统日历（默认 true）
     * 🔥 修复：增加 triggerSync 参数，避免反向同步时触发死循环
     */
    suspend fun deleteEvent(
        eventId: String,
        triggerSync: Boolean = true,
        removeFromRoom: Boolean = true
    ) = eventMutex.withLock {
        val currentList = loadCurrentActiveMutableList()
        val eventToDelete = currentList.find { it.id == eventId }

        if (eventToDelete != null) {
            if (!removeFromRoom && eventToDelete.isRecurring && !eventToDelete.isRecurringParent) {
                markRecurringInstanceCancelled(eventId)
            }
            cancelReminders(eventToDelete)
            currentList.remove(eventToDelete)
            updateEvents(currentList)
            if (removeFromRoom) {
                roomShadowWriter.deleteEvents(listOf(eventId))
            }
            // ✅ 触发胶囊状态刷新，确保胶囊通知被注销
            capsuleStateManager.forceRefresh()
            if (triggerSync) {
                triggerAutoSync()
            }
        }
    }

    private suspend fun updateEvents(newList: List<MyEvent>) {
        val normalizedList = normalizeEventsById(newList)
        if (normalizedList.size != newList.size) {
            Log.w("AppRepository", "检测到重复事件ID，已自动去重: ${newList.size} -> ${normalizedList.size}")
        }

        _events.value = normalizedList
        persistActiveEvents(normalizedList)
    }

    private fun shouldScheduleReminders(event: MyEvent): Boolean {
        return !event.isRecurringParent
    }

    private suspend fun persistActiveEvents(events: List<MyEvent>) {
        if (isRoomMainEnabled()) {
            eventRepository.saveEventsBackup(events)
        } else {
            eventRepository.saveEvents(events)
        }

        try {
            roomShadowWriter.syncEvents(events, RoomEventShadowWriter.SyncMode.ACTIVE)
        } catch (e: Exception) {
            Log.e("AppRepository", "Room 影子写入失败", e)
        }
    }

    private fun scheduleRemindersIfNeeded(event: MyEvent) {
        if (shouldScheduleReminders(event)) {
            NotificationScheduler.scheduleReminders(context, event)
        }
    }

    private fun cancelReminders(event: MyEvent) {
        NotificationScheduler.cancelReminders(context, event)
    }

    private fun normalizeEventsById(events: List<MyEvent>): List<MyEvent> {
        return events.asReversed().distinctBy { it.id }.asReversed()
    }

    private fun cleanupLegacyJsonIfStable() {
        if (!isRoomMainEnabled()) return
        if (!migrationPrefs.isEventsMigrated() || !migrationPrefs.isArchivesMigrated() || !migrationPrefs.isRecurringMigrated()) {
            return
        }

        val deleted = LEGACY_JSON_FILES.count { fileName ->
            val file = File(appContext.filesDir, fileName)
            if (file.exists()) {
                if (!file.delete()) {
                    Log.w("AppRepository", "删除旧 JSON 失败: $fileName")
                    false
                } else {
                    true
                }
            } else {
                false
            }
        }

        if (deleted > 0) {
            Log.i("AppRepository", "已清理 $deleted 个旧 JSON 文件")
        }
    }

    private fun sanitizeRecurringEvents(events: List<MyEvent>): List<MyEvent> {
        return normalizeEventsById(events)
    }

    private fun mergeIncomingCalendarEvent(existingEvent: MyEvent, incomingEvent: MyEvent): MyEvent {
        val resolvedTag = if (
            incomingEvent.tag == EventTags.GENERAL &&
            existingEvent.tag != EventTags.GENERAL
        ) {
            Log.d("AppRepository", "保留本地事件 tag，避免被反向同步降级: ${existingEvent.id}, ${existingEvent.tag} <- ${incomingEvent.tag}")
            existingEvent.tag
        } else {
            incomingEvent.tag
        }

        return existingEvent.copy(
            title = incomingEvent.title,
            description = incomingEvent.description,
            location = incomingEvent.location,
            startDate = incomingEvent.startDate,
            endDate = incomingEvent.endDate,
            startTime = incomingEvent.startTime,
            endTime = incomingEvent.endTime,
            eventType = incomingEvent.eventType,
            tag = resolvedTag,
            isRecurring = incomingEvent.isRecurring,
            isRecurringParent = incomingEvent.isRecurringParent,
            recurringSeriesKey = incomingEvent.recurringSeriesKey,
            recurringInstanceKey = incomingEvent.recurringInstanceKey,
            parentRecurringId = incomingEvent.parentRecurringId,
            excludedRecurringInstances = if (existingEvent.isRecurringParent || incomingEvent.isRecurringParent) {
                existingEvent.excludedRecurringInstances
            } else {
                incomingEvent.excludedRecurringInstances
            },
            // nextOccurrenceStartMillis 只对“重复父节点”有业务意义（用于展示下次发生时间）。
            // 普通单次事件即使系统侧字段变化，也不应因此触发一次无意义的 updateEvent（会连带 cancel 通知）。
            nextOccurrenceStartMillis = if (existingEvent.isRecurringParent || incomingEvent.isRecurringParent) {
                incomingEvent.nextOccurrenceStartMillis
            } else {
                existingEvent.nextOccurrenceStartMillis
            },
            skipCalendarSync = incomingEvent.skipCalendarSync,
            lastModified = System.currentTimeMillis()
        )
    }

    private fun isNoopCalendarMerge(existingEvent: MyEvent, mergedEvent: MyEvent): Boolean {
        // 反向同步阶段一会对已映射事件“轮询式”回调 onEventUpdated；
        // 如果只是 lastModified 变化，不应触发 updateEvent（否则会 cancel 掉胶囊通知，且 uiState 可能不 emit）。
        return existingEvent.copy(lastModified = 0L) == mergedEvent.copy(lastModified = 0L)
    }

    suspend fun detachRecurringInstance(
        parentEventId: String,
        sourceInstanceId: String,
        sourceInstanceKey: String,
        detachedEvent: MyEvent
    ) = eventMutex.withLock {
        val currentList = loadCurrentActiveMutableList()
        val parentIndex = currentList.indexOfFirst { it.id == parentEventId && it.isRecurringParent }
        if (parentIndex == -1) return@withLock

        val parentEvent = currentList[parentIndex]
        val updatedParent = parentEvent.copy(
            excludedRecurringInstances = (parentEvent.excludedRecurringInstances + sourceInstanceKey).distinct(),
            lastModified = System.currentTimeMillis()
        )
        currentList[parentIndex] = updatedParent

        val sourceInstance = currentList.find { it.id == sourceInstanceId }
        if (sourceInstance != null) {
            cancelReminders(sourceInstance)
            currentList.remove(sourceInstance)
        }

        val detachedLocalEvent = detachedEvent.copy(
            id = UUID.randomUUID().toString(),
            isRecurring = false,
            isRecurringParent = false,
            recurringSeriesKey = null,
            recurringInstanceKey = null,
            parentRecurringId = null,
            excludedRecurringInstances = emptyList(),
            nextOccurrenceStartMillis = null,
            skipCalendarSync = true,
            lastModified = System.currentTimeMillis()
        )

        currentList.add(detachedLocalEvent)
        updateEvents(currentList)
        scheduleRemindersIfNeeded(detachedLocalEvent)
        capsuleStateManager.forceRefresh()
    }

    /**
     * 完成日程事件（设置为已完成状态）
     * 点击"已完成"后，保存原始时间，设置已完成状态
     */
    suspend fun completeScheduleEvent(id: String) {
        val event = findActiveEventById(id)
        if (event != null && (event.eventType == EventType.EVENT || event.eventType == EventType.COURSE) && !event.isCompleted) {
            val now = java.time.LocalDateTime.now()
            val formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm")

            val updatedEvent = event.copy(
                endDate = now.toLocalDate(),
                endTime = now.format(formatter),
                isCompleted = true,
                completedAt = System.currentTimeMillis(),
                originalEndDate = event.endDate,
                originalEndTime = event.endTime
            )

            updateEvent(updatedEvent, triggerSync = false)
            capsuleStateManager.forceRefresh()
            syncSingleEventToCalendar(updatedEvent)
        }
    }

    private suspend fun markRecurringInstanceCancelled(eventId: String) {
        try {
            val instanceDao = database.eventInstanceDao()
            val instance = instanceDao.getById(eventId) ?: return
            if (!instance.isCancelled) {
                instanceDao.update(instance.copy(isCancelled = true))
            }
        } catch (e: Exception) {
            Log.e("AppRepository", "标记重复实例取消失败: $eventId", e)
        }
    }

    /**
     * 标记火车票已检票
     */
    suspend fun checkInTransport(id: String) {
        Log.d("Undo", "checkInTransport: id=$id")
        val event = findActiveEventById(id)
        Log.d("Undo", "checkInTransport: event=$event, isCheckedIn=${event?.isCheckedIn}")
        if (event != null && !event.isCheckedIn) {
            val updatedEvent = event.copy(
                isCheckedIn = true,
                completedAt = System.currentTimeMillis()
            )
            updateEvent(updatedEvent, triggerSync = false)
            capsuleStateManager.forceRefresh()
            syncSingleEventToCalendar(updatedEvent)
        }
    }

    /**
     * 撤销已完成事件（检查1分钟内）
     */
    suspend fun undoCompleteEvent(id: String): Boolean {
        val event = findActiveEventById(id)
        if (event != null && event.isCompleted && event.completedAt != null) {
            val elapsed = System.currentTimeMillis() - event.completedAt
            if (elapsed <= 60000) { // 1分钟内
                val restoredEvent = event.copy(
                    endDate = event.originalEndDate ?: event.endDate,
                    endTime = event.originalEndTime ?: event.endTime,
                    isCompleted = false,
                    completedAt = null,
                    originalEndDate = null,
                    originalEndTime = null
                )
                updateEvent(restoredEvent)
                capsuleStateManager.forceRefresh()
                return true
            }
        }
        return false
    }

    /**
     * 撤销已检票（检查1分钟内）
     */
    suspend fun undoCheckInTransport(id: String): Boolean {
        Log.d("Undo", "undoCheckInTransport: id=$id")
        val event = findActiveEventById(id)
        Log.d("Undo", "event=$event, isCheckedIn=${event?.isCheckedIn}, completedAt=${event?.completedAt}")
        if (event != null && event.isCheckedIn && event.completedAt != null) {
            val elapsed = System.currentTimeMillis() - event.completedAt
            Log.d("Undo", "elapsed=$elapsed, 1分钟内=${elapsed <= 60000}")
            if (elapsed <= 60000) { // 1分钟内
                val restoredEvent = event.copy(
                    isCheckedIn = false,
                    completedAt = null
                )
                updateEvent(restoredEvent, triggerSync = false)
                capsuleStateManager.forceRefresh()
                return true
            }
        }
        return false
    }

    suspend fun performPrimaryRuleAction(eventId: String): Boolean {
        val event = findActiveEventById(eventId) ?: return false
        if (event.isRecurringParent) return false
        val decision = EventActionResolver.resolve(event) ?: return false
        when (decision.actionType) {
            RuleActionType.UNDO -> {
                if (decision.ruleId == RuleMatchingEngine.RULE_TRAIN) {
                    undoCheckInTransport(eventId)
                } else {
                    undoCompleteEvent(eventId)
                }
            }
            RuleActionType.CHECKIN -> checkInTransport(eventId)
            RuleActionType.COMPLETE -> completeScheduleEvent(eventId)
        }
        return true
    }

    /**
     * 根据 ID 获取事件
     */
    suspend fun getEventById(id: String): MyEvent? {
        return findActiveEventById(id)
    }

    // --- Courses 操作 ---

    /**
     * 保存课程列表
     *
     * @param newCourses 新的课程列表
     * @param triggerSync 是否触发同步到系统日历（默认 true）
     * 🔥 修复：增加 triggerSync 参数，避免反向同步时触发死循环
     */
    suspend fun saveCourses(newCourses: List<Course>, triggerSync: Boolean = true) = courseMutex.withLock {
        updateCourses(newCourses)
        if (triggerSync) {
            triggerAutoSync()
        }
    }

    /**
     * 添加课程
     *
     * @param course 要添加的课程
     * @param triggerSync 是否触发同步到系统日历（默认 true）
     * 🔥 修复：增加 triggerSync 参数，避免反向同步时触发死循环
     */
    suspend fun addCourse(course: Course, triggerSync: Boolean = true) = courseMutex.withLock {
        val currentList = courseRepository.addCourse(_courses.value, course)
        updateCourses(currentList)
        if (triggerSync) {
            triggerAutoSync()
        }
    }

    // 🔥 核心修复：级联删除逻辑
    /**
     * 删除课程
     *
     * @param course 要删除的课程
     * @param triggerSync 是否触发同步到系统日历（默认 true）
     * 🔥 修复：增加 triggerSync 参数，避免反向同步时触发死循环
     */
    suspend fun deleteCourse(course: Course, triggerSync: Boolean = true) = courseMutex.withLock {
        val currentList = courseRepository.deleteCourse(_courses.value, course)
        updateCourses(currentList)
        if (triggerSync) {
            triggerAutoSync()
        }
    }

    /**
     * 更新课程
     *
     * @param course 要更新的课程
     * @param triggerSync 是否触发同步到系统日历（默认 true）
     * 🔥 修复：增加 triggerSync 参数，避免反向同步时触发死循环
     */
    suspend fun updateCourse(course: Course, triggerSync: Boolean = true) = courseMutex.withLock {
        val currentList = courseRepository.updateCourse(_courses.value, course)
        if (currentList.any { it.id == course.id }) {
            updateCourses(currentList)
            if (triggerSync) {
                triggerAutoSync()
            }
        }
    }

    private suspend fun updateCourses(newList: List<Course>) {
        _courses.value = newList
        courseRepository.saveCourses(newList)
    }

    // --- Settings 操作 ---
    private fun applySettingsNow(newSettings: MySettings) {
        _settings.value = newSettings
        settingsRepository.saveSettings(newSettings)
    }

    fun updateSettings(newSettings: MySettings) {
        scope.launch {
            applySettingsNow(newSettings)
        }
    }

    suspend fun enableCalendarSyncAndSyncNow(): Result<Unit> {
        val enableResult = enableCalendarSync()
        if (enableResult.isFailure) {
            return enableResult
        }

        val forwardResult = manualSync()
        if (forwardResult.isFailure) {
            return forwardResult
        }

        val reverseResult = syncFromCalendar()
        if (reverseResult.isFailure) {
            return Result.failure(reverseResult.exceptionOrNull() ?: Exception("反向同步失败"))
        }

        return Result.success(Unit)
    }

    companion object {
        @Volatile
        private var INSTANCE: AppRepository? = null

        private val LEGACY_JSON_FILES = listOf(
            "events.json",
            "events.json.bak",
            "archives.json",
            "archives.json.bak"
        )

        fun getInstance(context: Context): AppRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // --- 导出/导入功能 ---

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        prettyPrint = true
    }

    /**
     * 导出课程数据（包含课程表和作息时间配置）
     */
    suspend fun exportCoursesData(): String {
        val coursesData = CoursesBackupData(
            courses = _courses.value,
            semesterStartDate = _settings.value.semesterStartDate,
            totalWeeks = _settings.value.totalWeeks,
            timeTableJson = _settings.value.timeTableJson,
            timeTableConfigJson = _settings.value.timeTableConfigJson
        )
        return json.encodeToString(coursesData)
    }

    /**
     * 标准化日期格式
     * 将 yyyy-M-d 格式（如 2025-9-1）转换为 ISO-8601 格式（yyyy-MM-dd，如 2025-09-01）
     * 用于处理外部导入文件中缺失前导零的日期字符串
     *
     * @param dateStr 原始日期字符串
     * @return 标准化后的日期字符串（ISO-8601 格式），如果解析失败则返回 null
     */
    private fun normalizeDateFormat(dateStr: String?): String? {
        if (dateStr.isNullOrBlank()) return null

        return try {
            // 先尝试直接解析（已经是标准格式的情况）
            LocalDate.parse(dateStr)
            dateStr
        } catch (e: DateTimeParseException) {
            // 如果直接解析失败，尝试使用宽松格式解析
            try {
                val formatter = DateTimeFormatter.ofPattern("yyyy-M-d")
                val parsedDate = LocalDate.parse(dateStr, formatter)
                // 转换为 ISO-8601 格式字符串
                parsedDate.toString()
            } catch (e2: Exception) {
                Log.e("AppRepository", "日期格式标准化失败: $dateStr", e2)
                null
            }
        }
    }

    /**
     * 导入课程数据（支持应用备份格式和 WakeUp 课表格式）
     */
    suspend fun importCoursesData(jsonString: String): Result<Unit> {
        // 优先尝试使用 WakeUpCourseImporter 解析
        val wakeUpImporter = WakeUpCourseImporter()
        if (wakeUpImporter.supports(jsonString)) {
            Log.d("AppRepository", "检测到 WakeUp 课表格式，开始导入")
            return try {
                val result = wakeUpImporter.parse(jsonString)
                if (result.isSuccess) {
                    val importResult = result.getOrThrow()

                    // 导入课程
                    saveCourses(importResult.courses)

                    applyImportedSemesterSettings(
                        semesterStartDate = importResult.semesterStartDate,
                        totalWeeks = importResult.totalWeeks
                    )

                    Log.d("AppRepository", "WakeUp 课表导入成功，共 ${importResult.courses.size} 门课程")
                    Result.success(Unit)
                } else {
                    Log.e("AppRepository", "WakeUp 课表解析失败: ${result.exceptionOrNull()?.message}")
                    Result.failure(result.exceptionOrNull() ?: Exception("解析失败"))
                }
            } catch (e: Exception) {
                Log.e("AppRepository", "WakeUp 课表导入异常", e)
                Result.failure(e)
            }
        }

        // 如果不是 WakeUp 格式，尝试应用自己的备份格式
        return try {
            val data = json.decodeFromString<CoursesBackupData>(jsonString)

            // 导入课程
            saveCourses(data.courses)

            applyImportedCourseBackupSettings(data)

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("AppRepository", "导入课程数据失败", e)
            Result.failure(e)
        }
    }

    /**
     * 导入外部课表文件（WakeUp 格式）
     * @param content 文件内容
     * @param mode 导入模式（追加/覆盖）
     * @param importSettings 是否导入设置（开学日期、总周数）
     * @return 成功导入的课程数量
     */
    suspend fun importWakeUpFile(
        content: String,
        mode: ImportMode,
        importSettings: Boolean
    ): Result<Int> {
        val importer = WakeUpCourseImporter()
        return try {
            val result = importer.parse(content)
            if (result.isSuccess) {
                val importResult = result.getOrThrow()
                val courses = importResult.courses

                // 根据模式处理课程
                if (mode == ImportMode.OVERWRITE) {
                    // 覆盖模式：清空现有课程
                    saveCourses(courses)
                    Log.d("AppRepository", "覆盖模式：清空后导入 ${courses.size} 门课程")
                } else {
                    // 追加模式：保留现有课程，添加新课程
                    val existingCourses = _courses.value
                    val mergedCourses = existingCourses + courses
                    saveCourses(mergedCourses)
                    Log.d("AppRepository", "追加模式：从 ${existingCourses.size} 门增加到 ${mergedCourses.size} 门课程")
                }

                if (importSettings) {
                    val normalizedDate = applyImportedSemesterSettings(
                        semesterStartDate = importResult.semesterStartDate,
                        totalWeeks = importResult.totalWeeks
                    )
                    if (normalizedDate != null || importResult.totalWeeks != null) {
                        Log.d("AppRepository", "设置已更新，日期: $normalizedDate")
                    }
                }

                Result.success(courses.size)
            } else {
                Log.e("AppRepository", "解析失败: ${result.exceptionOrNull()?.message}")
                Result.failure(result.exceptionOrNull() ?: Exception("解析失败"))
            }
        } catch (e: Exception) {
            Log.e("AppRepository", "导入异常", e)
            Result.failure(e)
        }
    }

    /**
     * 导出日程数据（包含活跃事件和归档事件）
     */
    suspend fun exportEventsData(): String {
        // 确保归档数据已加载（懒加载）
        ensureArchivesLoaded()

        val eventsData = EventsBackupData(
            events = _events.value,
            archivedEvents = _archivedEvents.value
        )
        return json.encodeToString(eventsData)
    }

    /**
     * 导入日程数据（包含活跃事件和归档事件）
     *
     * 使用内容指纹去重策略：
     * - 指纹组成：title + startDate + endDate + startTime + endTime + location
     * - 重复则跳过，不重复则追加
     * - 保留导入文件的 archivedAt 字段状态
     *
     * @param jsonString 导入的 JSON 字符串
     * @param preserveArchivedStatus 是否保留归档状态（默认 true）
     * @return 导入结果（成功数、跳过数、归档状态更新数）
     */
    suspend fun importEventsData(
        jsonString: String,
        preserveArchivedStatus: Boolean = true
    ): Result<ImportResult> {
        return try {
            // ✅ 修复第一步：强制加载归档数据，确保去重有效
            ensureArchivesLoaded()

            val data = json.decodeFromString<EventsBackupData>(jsonString)

            // 1. 合并导入的活跃事件和归档事件
            val allImportEvents = data.events + data.archivedEvents

            // 2. 确保 archivedAt 字段正确性
            val normalizedImportEvents = normalizeImportedEvents(data, allImportEvents)

            // 3. 执行去重
            val deduplicationResult = EventDeduplicator.deduplicateForImport(
                importEvents = normalizedImportEvents,
                existingActiveEvents = _events.value,
                existingArchivedEvents = _archivedEvents.value,
                preserveArchivedStatus = preserveArchivedStatus
            )

            // 4. 处理需要新增的事件
            val eventsToAdd = deduplicationResult.toAdd
            if (eventsToAdd.isNotEmpty()) {
                // 分离活跃和归档事件
                val newActiveEvents = eventsToAdd.filter { it.archivedAt == null }
                val newArchivedEvents = eventsToAdd.filter { it.archivedAt != null }

                appendImportedActiveEvents(newActiveEvents)
                appendImportedArchivedEvents(newArchivedEvents)

                // 导入的重复事件写入 Room
                legacyRecurringMigrator.upsertRecurringEvents(eventsToAdd)
            }

            // 5. 处理需要更新归档状态的事件
            val archiveStatusUpdates = deduplicationResult.toUpdateArchiveStatus
            if (archiveStatusUpdates.isNotEmpty()) {
                for ((event, shouldBeArchived) in archiveStatusUpdates) {
                    if (shouldBeArchived) {
                        // 需要归档：调用归档逻辑
                        Log.d("AppRepository", "归档状态更新：归档事件 - ${event.title}")
                        archiveEvent(event.id)
                    } else {
                        // 需要还原：调用还原逻辑
                        Log.d("AppRepository", "归档状态更新：还原事件 - ${event.title}")
                        restoreEvent(event.id)
                    }
                }
            }

            // 6. 触发同步（如果有新增）
            if (eventsToAdd.isNotEmpty()) {
                triggerAutoSync()
            }

            // 7. 返回结果
            val importResult = ImportResult(
                successCount = deduplicationResult.toAdd.size,
                skippedCount = deduplicationResult.toSkip.size,
                archiveStatusUpdateCount = deduplicationResult.toUpdateArchiveStatus.size
            )

            Log.d("AppRepository", "导入完成: 新增 ${importResult.successCount}, 跳过 ${importResult.skippedCount}, 归档状态更新 ${importResult.archiveStatusUpdateCount}")

            Result.success(importResult)
        } catch (e: Exception) {
            Log.e("AppRepository", "导入日程数据失败", e)
            Result.failure(e)
        }
    }

    private fun normalizeImportedEvents(
        data: EventsBackupData,
        allImportEvents: List<MyEvent>
    ): List<MyEvent> {
        val archivedIds = data.archivedEvents.map { it.id }.toSet()
        val activeIds = data.events.map { it.id }.toSet()
        return allImportEvents.map { event ->
            when {
                event.id in archivedIds -> {
                    if (event.archivedAt == null) {
                        Log.d("AppRepository", "修正归档事件缺少 archivedAt 字段: ${event.title}")
                        event.copy(archivedAt = System.currentTimeMillis())
                    } else {
                        event
                    }
                }
                event.id in activeIds -> event.copy(archivedAt = null)
                else -> event
            }
        }
    }

    private fun applyImportedSemesterSettings(
        semesterStartDate: String?,
        totalWeeks: Int?
    ): String? {
        if (semesterStartDate == null && totalWeeks == null) return null
        val currentSettings = _settings.value
        val normalizedDate = normalizeDateFormat(semesterStartDate)
        val newSettings = currentSettings.copy(
            semesterStartDate = normalizedDate ?: currentSettings.semesterStartDate,
            totalWeeks = totalWeeks ?: currentSettings.totalWeeks
        )
        updateSettings(newSettings)
        return normalizedDate
    }

    private fun applyImportedCourseBackupSettings(data: CoursesBackupData) {
        val currentSettings = _settings.value
        val newSettings = currentSettings.copy(
            semesterStartDate = data.semesterStartDate,
            totalWeeks = data.totalWeeks,
            timeTableJson = data.timeTableJson,
            timeTableConfigJson = data.timeTableConfigJson
        )
        updateSettings(newSettings)
    }

    private suspend fun appendImportedActiveEvents(newActiveEvents: List<MyEvent>) {
        if (newActiveEvents.isEmpty()) return
        val currentActive = loadCurrentActiveMutableList()
        val existingIds = currentActive.map { it.id }.toSet()
        val uniqueNewActive = newActiveEvents.filter { it.id !in existingIds }
        if (uniqueNewActive.size < newActiveEvents.size) {
            Log.w("Import", "跳过 ${newActiveEvents.size - uniqueNewActive.size} 个重复 ID 的活跃事件")
        }
        currentActive.addAll(uniqueNewActive)
        updateEvents(currentActive)
        uniqueNewActive.forEach(::scheduleRemindersIfNeeded)
    }

    private suspend fun appendImportedArchivedEvents(newArchivedEvents: List<MyEvent>) {
        if (newArchivedEvents.isEmpty()) return
        val currentArchived = loadCurrentArchivedMutableList()
        val existingIds = currentArchived.map { it.id }.toSet()
        val uniqueNewArchived = newArchivedEvents.filter { it.id !in existingIds }
        if (uniqueNewArchived.size < newArchivedEvents.size) {
            Log.w("Import", "跳过 ${newArchivedEvents.size - uniqueNewArchived.size} 个重复 ID 的归档事件")
        }
        currentArchived.addAll(uniqueNewArchived)
        updateArchivedEvents(currentArchived)
    }

    /**
     * 获取当前事件列表（用于导出前检查）
     */
    fun getEventsCount(): Int = _events.value.size

    /**
     * 获取总事件数量（包含活跃事件和归档事件）
     */
    fun getTotalEventsCount(): Int = _events.value.size + _archivedEvents.value.size

    /**
     * 获取当前课程列表（用于导出前检查）
     */
    fun getCoursesCount(): Int = _courses.value.size

    // ==================== 日历同步相关 ====================

    /**
     * 触发自动同步（在数据变更时调用）
     * 如果同步已启用，自动将数据同步到系统日历
     */
    private suspend fun triggerAutoSync() {
        try {
            syncAllToCalendar(seedMapping = true)
        } catch (e: Exception) {
            Log.e("AppRepository", "自动同步失败", e)
        }
    }

    private suspend fun syncAllToCalendar(seedMapping: Boolean) {
        val settings = _settings.value
        val timeNodes = parseTimeTable(settings.timeTableJson)
        val useV2 = isCalendarSyncV2Enabled()

        if (useV2) {
            if (seedMapping) {
                seedSyncMappingIfNeeded()
            }
        }
        syncGateway.syncAllToCalendar(
            useV2 = useV2,
            events = _events.value,
            courses = _courses.value,
            semesterStart = settings.semesterStartDate,
            totalWeeks = settings.totalWeeks,
            timeNodes = timeNodes
        )
    }

    /**
     * 解析作息时间 JSON 为 TimeNode 列表
     */
    private fun parseTimeTable(json: String): List<com.antgskds.calendarassistant.data.model.TimeNode> {
        return try {
            kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                .decodeFromString<List<com.antgskds.calendarassistant.data.model.TimeNode>>(json)
        } catch (e: Exception) {
            Log.e("AppRepository", "解析作息时间失败", e)
            emptyList()
        }
    }

    private suspend fun seedSyncMappingIfNeeded() {
        if (syncSeedAttempted) return

        val status = syncGateway.getV2Status()
        if (!status.isEnabled || !status.hasPermission) return

        if (_events.value.isEmpty() && _archivedEvents.value.isEmpty()) {
            syncSeedAttempted = true
            return
        }

        if (status.mappedEventCount == 0) {
            syncSeedAttempted = true
            syncFromCalendar()
            return
        }

        syncSeedAttempted = true
    }

    /**
     * 手动触发同步（由 UI 调用）
     */
    suspend fun manualSync(): Result<Unit> {
        return try {
            syncAllToCalendar(seedMapping = false)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("AppRepository", "手动同步失败", e)
            Result.failure(e)
        }
    }

    /**
     * 启用日历同步
     */
    suspend fun enableCalendarSync(): Result<Unit> {
        syncSeedAttempted = false
        return syncGateway.enableSync(isCalendarSyncV2Enabled())
    }

    /**
     * 禁用日历同步
     */
    suspend fun disableCalendarSync(): Result<Unit> {
        return syncGateway.disableSync(isCalendarSyncV2Enabled())
    }

    /**
     * 获取同步状态
     */
    suspend fun getSyncStatus(): CalendarSyncManager.SyncStatus {
        return syncGateway.getSyncStatus(isCalendarSyncV2Enabled())
    }

    private suspend fun syncSingleEventToCalendar(event: MyEvent) {
        syncGateway.syncEventToCalendar(isCalendarSyncV2Enabled(), event)
    }

    /**
     * 从系统日历同步变更到应用
     * 由 CalendarContentObserver 在检测到系统日历变化时触发
     *
     * 颜色策略：
     * - 新增事件：随机分配一个 APP 内的颜色，避免统一的青灰色
     * - 更新事件：保留本地原有的颜色、提醒、重要性设置（作为 UI 防火墙）
     * - 删除事件：正常删除
     *
     * 🔥 新增：检查归档事件，防止"僵尸事件"复活
     */
    suspend fun syncFromCalendar(): Result<Int> {
        return try {
            // ✅ 修复第一步：强制加载归档数据，防止"僵尸事件"复活
            ensureArchivesLoaded()

            val snapshot = CalendarSyncSnapshot(
                activeEvents = _events.value,
                archivedEvents = _archivedEvents.value
            )

            val allowRecurringSync = true
            val useV2 = isCalendarSyncV2Enabled()

            val syncTask: suspend (
                suspend (MyEvent) -> Unit,
                suspend (MyEvent) -> Unit,
                suspend (String) -> Unit
            ) -> Result<Int> = { onAdded, onUpdated, onDeleted ->
                syncGateway.syncFromCalendar(
                    useV2 = useV2,
                    onEventAdded = onAdded,
                    onEventUpdated = onUpdated,
                    onEventDeleted = onDeleted,
                    allowRecurringSync = allowRecurringSync,
                    activeEvents = snapshot.activeEvents,
                    archivedEvents = snapshot.archivedEvents
                )
            }

            syncTask(
                { incomingEvent -> handleCalendarAddedOrUpdated(incomingEvent, snapshot) },
                { incomingEvent -> handleCalendarAddedOrUpdated(incomingEvent, snapshot) },
                { eventId -> handleCalendarDeleted(eventId, snapshot) }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun handleCalendarAddedOrUpdated(
        incomingEvent: MyEvent,
        snapshot: CalendarSyncSnapshot
    ) {
        val existingById = snapshot.activeEvents.find { it.id == incomingEvent.id }
        if (existingById != null) {
            val finalEvent = mergeIncomingCalendarEvent(existingById, incomingEvent)
            if (!isNoopCalendarMerge(existingById, finalEvent)) {
                updateEvent(finalEvent, triggerSync = false)
            }
            return
        }

        if (incomingEvent.isRecurring) {
            addEvent(incomingEvent, triggerSync = false)
            return
        }

        val isDup = isDuplicateEvent(incomingEvent, snapshot.activeEvents, snapshot.archivedEvents)
        if (!isDup) {
            addEvent(withRandomColorIfNeeded(incomingEvent), triggerSync = false)
        }
    }

    private suspend fun handleCalendarDeleted(eventId: String, snapshot: CalendarSyncSnapshot) {
        val target = snapshot.activeEvents.find { it.id == eventId }
            ?: snapshot.archivedEvents.find { it.id == eventId }
        val removeFromRoom = !(target?.isRecurring == true && !target.isRecurringParent)
        deleteEvent(eventId, triggerSync = false, removeFromRoom = removeFromRoom)
    }

    private fun withRandomColorIfNeeded(event: MyEvent): MyEvent {
        return if (event.isRecurring) event else event.copy(color = getRandomEventColor())
    }

    private fun isDuplicateEvent(
        event: MyEvent,
        activeEvents: List<MyEvent>,
        archivedEvents: List<MyEvent>
    ): Boolean {
        val fingerprint = "${event.title}|${event.startDate}|${event.startTime}|${event.endTime}|${event.location}|${event.description}"
        
        val allEvents = activeEvents + archivedEvents
        return allEvents.any { existing ->
            val existingFingerprint = "${existing.title}|${existing.startDate}|${existing.startTime}|${existing.endTime}|${existing.location}|${existing.description}"
            existingFingerprint == fingerprint
        }
    }

    // ==================== 归档操作 ====================

    /**
     * 🔥 修复：归档单个事件（原子操作修正版）
     * 1. 先写入归档（持有 archiveMutex）
     * 2. 后删除原日程（复用 deleteEvent，它持有 eventMutex）
     * 3. 类型检查：课程不可归档
     */
    suspend fun archiveEvent(eventId: String) {
        // 1. 类型安全检查与获取对象
        val event = findActiveEventById(eventId) ?: return

        // 🛡️ 拦截规则：课程不可归档
        if (event.eventType == EventType.COURSE) {
            Log.w("AppRepository", "Attempted to archive course event: ${event.eventType}")
            return
        }

        if (event.isRecurring) {
            Log.w("AppRepository", "Attempted to archive imported recurring event: ${event.id}")
            return
        }

        // 2. 先写入归档 (持有 archiveMutex)
        val archivedEvent = event.copy(archivedAt = System.currentTimeMillis())

        try {
            archiveMutex.withLock {
                // 如果内存中还没有加载归档数据，先加载（防止覆盖）
                val currentArchived = loadCurrentArchivedMutableList()

                currentArchived.add(archivedEvent)
                // 这一步可能会抛出 IO 异常
                updateArchivedEvents(currentArchived)
            }
        } catch (e: Exception) {
            Log.e("AppRepository", "归档失败: 写入归档文件错误", e)
            return // 归档写入失败，终止操作，原日程保留
        }

        // 3. 后删除原日程 (复用 deleteEvent，它持有 eventMutex，线程安全)
        // triggerSync = false 因为归档本质上是"移动"，不应视为系统日历的"删除"
        // 如果希望系统日历里的也删掉，可以设为 true
        deleteEvent(eventId, triggerSync = false, removeFromRoom = false)

        Log.d("AppRepository", "Event archived: ${event.title}")
    }

    /**
     * 🔥 修复：还原归档事件（逻辑完善版）
     * 1. 获取归档对象（持有 archiveMutex）
     * 2. 恢复到活跃列表（复用 addEvent，持有 eventMutex）
     * 3. 从归档中移除（持有 archiveMutex）
     */
    suspend fun restoreEvent(archivedEventId: String) {
        // 1. 获取归档对象 (持有 archiveMutex)
        val archivedEvent: MyEvent?

        archiveMutex.withLock {
            archivedEvent = findArchivedEventById(archivedEventId)
        }

        if (archivedEvent == null) return

        // 2. 恢复到活跃列表 (复用 addEvent，持有 eventMutex)
        val activeEvent = archivedEvent.copy(archivedAt = null)

        // 检查是否已过期，如果过期太久，可能不需要 triggerSync
        addEvent(activeEvent, triggerSync = true)

        // 3. 从归档中移除 (持有 archiveMutex)
        archiveMutex.withLock {
            val currentArchived = loadCurrentArchivedMutableList()
            currentArchived.remove(archivedEvent)
            updateArchivedEvents(currentArchived)
        }

        Log.d("AppRepository", "Event restored: ${activeEvent.title}")
    }

    /**
     * 永久删除归档事件
     * 同时删除系统日历中的对应事件
     */
    suspend fun deleteArchivedEvent(archivedEventId: String) = archiveMutex.withLock {
        val currentArchived = loadCurrentArchivedMutableList()
        val event = currentArchived.find { it.id == archivedEventId }
        if (event != null) {
            currentArchived.remove(event)
            updateArchivedEvents(currentArchived)
            roomShadowWriter.deleteEvents(listOf(event.id))

            removeCalendarMappingsForEvents(listOf(event), "deleteArchivedEvent")
        }
    }

    /**
     * 清空所有归档
     * 同时删除系统日历中的对应事件
     */
    suspend fun clearAllArchives() = archiveMutex.withLock {
        val currentArchived = loadCurrentArchivedMutableList()
        if (currentArchived.isEmpty()) return@withLock

        removeCalendarMappingsForEvents(currentArchived, "clearAllArchives")

        updateArchivedEvents(emptyList())
        roomShadowWriter.deleteEvents(currentArchived.map { it.id })
    }

    private suspend fun removeCalendarMappingsForEvents(events: List<MyEvent>, reason: String) {
        if (events.isEmpty()) return
        try {
            val syncData = syncMappingRepository.load()
            val calendarManager = CalendarManager(context)
            val updatedMapping = syncData.mapping.toMutableMap()

            events.forEach { event ->
                val calendarEventIdStr = syncData.mapping[event.id]
                if (calendarEventIdStr == null) {
                    Log.d("AppRepository", "$reason: 未找到映射，event.id=${event.id}")
                    return@forEach
                }

                val calendarEventId = calendarEventIdStr.toLongOrNull()
                if (calendarEventId == null) {
                    Log.w("AppRepository", "$reason: 非法日历映射，event.id=${event.id}, value=$calendarEventIdStr")
                    updatedMapping.remove(event.id)
                    return@forEach
                }

                val success = calendarManager.deleteEvent(calendarEventId)
                Log.d("AppRepository", "$reason: deleteEvent result=$success, ${event.id} -> $calendarEventId")
                updatedMapping.remove(event.id)
            }

            if (updatedMapping != syncData.mapping) {
                syncMappingRepository.save(syncData.copy(mapping = updatedMapping))
            }
        } catch (e: Exception) {
            Log.e("AppRepository", "$reason: 同步删除系统日历事件失败", e)
        }
    }

    private suspend fun updateArchivedEvents(newList: List<MyEvent>) {
        _archivedEvents.value = newList
        persistArchivedEvents(newList)
    }

    private suspend fun persistArchivedEvents(events: List<MyEvent>) {
        if (isRoomMainEnabled()) {
            archiveRepository.saveArchivedEventsBackup(events)
        } else {
            archiveRepository.saveArchivedEvents(events)
        }
        try {
            roomShadowWriter.syncEvents(events, RoomEventShadowWriter.SyncMode.ARCHIVED)
        } catch (e: Exception) {
            Log.e("AppRepository", "Room 归档影子写入失败", e)
        }
    }

    private suspend fun loadCurrentArchivedMutableList(): MutableList<MyEvent> {
        return if (_archivedEvents.value.isEmpty()) {
            archiveRepository.loadArchivedEvents().toMutableList()
        } else {
            _archivedEvents.value.toMutableList()
        }
    }

    private fun loadCurrentActiveMutableList(): MutableList<MyEvent> {
        return _events.value.toMutableList()
    }

    private fun findActiveEventById(eventId: String): MyEvent? {
        return _events.value.find { it.id == eventId }
    }

    private fun findArchivedEventById(eventId: String): MyEvent? {
        return _archivedEvents.value.find { it.id == eventId }
    }

    /**
     * 🔥 修复：自动归档过期事件（使用正确的过期判断逻辑）
     * 条件：使用 DateCalculator.isEventExpired() 判断是否过期（考虑日期+时间）
     * 排除：课程不归档
     * @return 归档的事件数量
     */
    suspend fun autoArchiveExpiredEvents(): Int {
        val settings = _settings.value
        if (!settings.autoArchiveEnabled) return 0

        // 1. 筛选需要归档的事件（使用正确的过期判断）
        val eventsSnapshot = _events.value // 获取快照
        val toArchiveEvents = eventsSnapshot.filter { event ->
            event.eventType != EventType.COURSE &&
            !event.isRecurring &&
            com.antgskds.calendarassistant.core.util.DateCalculator.isEventExpired(event)
        }

        if (toArchiveEvents.isEmpty()) return 0

        Log.d("AppRepository", "Auto-archiving ${toArchiveEvents.size} events...")

        // 2. 批量处理 - 归档部分
        archiveMutex.withLock {
            val currentArchived = loadCurrentArchivedMutableList()

            // ✅ 修复：只添加不在归档列表中的事件，避免重复
            val existingIds = currentArchived.map { it.id }.toSet()
            val newItems = toArchiveEvents.filter { it.id !in existingIds }

            val newArchivedItems = newItems.map {
                it.copy(archivedAt = System.currentTimeMillis())
            }
            currentArchived.addAll(newArchivedItems)
            updateArchivedEvents(currentArchived)
        }

        // 3. 批量处理 - 删除部分 (使用 eventMutex)
        eventMutex.withLock {
            val currentEvents = loadCurrentActiveMutableList()
            // 取消通知
            toArchiveEvents.forEach(::cancelReminders)
            // 移除
            currentEvents.removeAll { event -> toArchiveEvents.any { it.id == event.id } }
            updateEvents(currentEvents)
            // 触发一次同步即可
            triggerAutoSync()
        }

        return toArchiveEvents.size
    }
}

@kotlinx.serialization.Serializable
private data class CoursesBackupData(
    val courses: List<Course>,
    val semesterStartDate: String,
    val totalWeeks: Int,
    val timeTableJson: String,
    val timeTableConfigJson: String = ""
)

@kotlinx.serialization.Serializable
private data class EventsBackupData(
    val events: List<MyEvent>,
    val archivedEvents: List<MyEvent> = emptyList() // 归档事件，默认为空以兼容旧版本
)
