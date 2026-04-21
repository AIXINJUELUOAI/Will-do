package com.antgskds.calendarassistant.data.node.store

import android.content.Context
import android.util.Log
import com.antgskds.calendarassistant.core.course.CourseEventMapper
import com.antgskds.calendarassistant.core.util.DataSanitizer
import com.antgskds.calendarassistant.data.model.Course
import com.antgskds.calendarassistant.core.rule.RuleMatchingEngine
import com.antgskds.calendarassistant.core.rule.RuleRegistry
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.service.notification.NotificationScheduler
import com.antgskds.calendarassistant.core.calendar.CalendarSyncGateway
import com.antgskds.calendarassistant.core.util.CrashHandler
import com.antgskds.calendarassistant.core.weather.WeatherSyncWorker
import com.antgskds.calendarassistant.data.model.ImportResult
import com.antgskds.calendarassistant.core.calendar.CalendarManager
import com.antgskds.calendarassistant.core.calendar.CalendarSyncManager
import com.antgskds.calendarassistant.core.calendar.CalendarSyncV2Prefs
import com.antgskds.calendarassistant.ui.theme.getRandomEventColor
import com.antgskds.calendarassistant.core.importer.ImportMode
import com.antgskds.calendarassistant.data.db.AppDatabase
import com.antgskds.calendarassistant.data.db.reader.RoomEventReader
import com.antgskds.calendarassistant.data.db.shadow.RoomEventShadowWriter
import com.antgskds.calendarassistant.data.migration.LegacyArchiveMigrator
import com.antgskds.calendarassistant.data.migration.LegacyEventMigrator
import com.antgskds.calendarassistant.data.migration.MigrationPrefs
import com.antgskds.calendarassistant.data.migration.LegacyRecurringMigrator
import com.antgskds.calendarassistant.data.repository.ArchiveRepository
import com.antgskds.calendarassistant.data.repository.EventRepository
import com.antgskds.calendarassistant.data.repository.SettingsRepository
import com.antgskds.calendarassistant.data.repository.SyncMappingRepository
import com.antgskds.calendarassistant.data.port.StoreRootNodePort
import com.antgskds.calendarassistant.data.source.SyncJsonDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.time.LocalDate

class StoreRootNode private constructor(private val context: Context) : StoreRootNodePort {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    val appContext: Context = context.applicationContext

    private val eventRepository = EventRepository(context)
    private val settingsRepository = SettingsRepository(context)
    private val archiveRepository = ArchiveRepository(context)
    private val syncMappingRepository = SyncMappingRepository(context)

    private val database by lazy { AppDatabase.getInstance(context.applicationContext) }
    private val calendarSyncMapDao by lazy { database.calendarSyncMapDao() }
    private val eventMasterDao by lazy { database.eventMasterDao() }
    private val migrationPrefs by lazy { MigrationPrefs(context.applicationContext) }
    private val legacyEventMigrator by lazy { LegacyEventMigrator(database, migrationPrefs) }
    private val legacyArchiveMigrator by lazy { LegacyArchiveMigrator(database, migrationPrefs) }
    private val legacyRecurringMigrator by lazy { LegacyRecurringMigrator(database, migrationPrefs) }
    private val roomShadowWriter by lazy { RoomEventShadowWriter(database) }
    private val roomEventReader by lazy { RoomEventReader(database) }
    private val backupNode = StoreBackupNode()
    private val archiveStorageSupport by lazy {
        StoreArchiveStorageNode(
            context = context,
            archiveRepository = archiveRepository,
            roomShadowWriter = roomShadowWriter,
            syncMappingRepository = syncMappingRepository
        )
    }
    private val _events = MutableStateFlow<List<MyEvent>>(emptyList())
    override val events: StateFlow<List<MyEvent>> = _events.asStateFlow()

    private val _settings = MutableStateFlow(MySettings())
    override val settings: StateFlow<MySettings> = _settings.asStateFlow()

    private val _archivedEvents = MutableStateFlow<List<MyEvent>>(emptyList())
    override val archivedEvents: StateFlow<List<MyEvent>> = _archivedEvents.asStateFlow()

    @Volatile
    private var capsuleRefreshHandler: (() -> Unit)? = null

    private val syncGateway = CalendarSyncGateway(context.applicationContext)
    private val syncDataSource by lazy { SyncJsonDataSource.getInstance(context.applicationContext) }
    private val systemCalendarManager by lazy { CalendarManager(context.applicationContext) }
    private val backupWorkflowSupport by lazy {
        StoreBackupWorkflowNode(
            backupNode = backupNode,
            currentCourses = { currentCoursesFromEvents() },
            activeEvents = { _events.value },
            archivedEvents = { _archivedEvents.value },
            currentSettings = { _settings.value },
            ensureArchivesLoaded = { ensureArchivesLoaded() },
            updateSettings = { updateSettings(it) },
            saveCourses = { saveCoursesFromEvents(it) },
            loadCurrentActiveMutableList = { _events.value.toMutableList() },
            updateEvents = { updateEvents(it) },
            scheduleRemindersIfNeeded = { scheduleRemindersIfNeeded(it) },
            loadCurrentArchivedMutableList = {
                archiveStorageSupport.loadCurrentArchivedMutableList(_archivedEvents.value)
            },
            updateArchivedEvents = { updateArchivedEvents(it) },
            upsertRecurringEvents = { legacyRecurringMigrator.upsertRecurringEvents(it) },
            archiveEvent = { archiveEvent(it) },
            restoreEvent = { restoreEvent(it) },
            triggerAutoSync = { triggerAutoSync() }
        )
    }

    private val eventMutex = Mutex()
    private val courseMutex = Mutex()
    private val archiveMutex = Mutex()

    @Volatile
    private var syncSeedAttempted = false

    init {
        refreshData()
        migrateEventTypes()
        migrateEventTags()
        scope.launch(Dispatchers.IO) {
            RuleRegistry.refresh(appContext)
            migrateLegacyCoursesIfNeeded()
        }
    }

    private suspend fun migrateLegacyCoursesIfNeeded() {
        if (_events.value.any { it.tag == com.antgskds.calendarassistant.data.model.EventTags.COURSE }) return

        val roomBackupTemplates = runCatching { eventRepository.loadRoomBackupEvents() }
            .getOrElse { emptyList() }
            .filter { CourseEventMapper.isCourseTemplateEvent(it) }
            .map { CourseEventMapper.normalizeTemplateEvent(it) }
        if (roomBackupTemplates.isNotEmpty()) {
            val mergedEvents = StoreEventStorageNode.normalizeEventsById(_events.value + roomBackupTemplates)
            updateEvents(mergedEvents)
            Log.i("StoreNode", "已从 events.room.bak 恢复 ${roomBackupTemplates.size} 条课表模板")
            return
        }

        val legacyCourses = loadLegacyCoursesFromJson()
        if (legacyCourses.isEmpty()) return

        val effectiveSettings = runCatching { settingsRepository.loadSettings() }.getOrElse { _settings.value }
        val courseTemplateEvents = CourseEventMapper.toTemplateEvents(legacyCourses, effectiveSettings)
        val mergedEvents = StoreEventStorageNode.normalizeEventsById(_events.value + courseTemplateEvents)
        updateEvents(mergedEvents)
        Log.i("StoreNode", "已将 ${legacyCourses.size} 门旧课表课程迁移到事件主存")
    }

    private fun loadLegacyCoursesFromJson(): List<Course> {
        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
        val files = listOf("courses.json", "courses.json.bak")
        files.forEach { name ->
            val file = File(appContext.filesDir, name)
            if (!file.exists()) return@forEach
            val content = runCatching { file.readText() }.getOrElse { return@forEach }
            if (content.isBlank()) return@forEach
            val parsed = runCatching { json.decodeFromString<List<Course>>(content) }.getOrElse { return@forEach }
            val sanitized = DataSanitizer.sanitizeCourses(parsed).data
            if (sanitized.isNotEmpty()) {
                return sanitized
            }
        }
        return emptyList()
    }

    override fun bindCapsuleRefreshHandler(handler: (() -> Unit)?) {
        capsuleRefreshHandler = handler
    }

    private fun requestCapsuleRefresh() {
        capsuleRefreshHandler?.invoke()
    }

    private fun migrateEventTypes() {
        StoreMigrationNode.migrateEventTypes(
            scope = scope,
            shouldSkipMigration = ::shouldSkipLegacyMigration,
            loadEvents = { eventRepository.loadEvents() },
            saveEvents = { eventRepository.saveEvents(it) },
            onEventsUpdated = { _events.value = it }
        )
    }

    private fun migrateEventTags() {
        StoreMigrationNode.migrateEventTags(
            scope = scope,
            shouldSkipMigration = ::shouldSkipLegacyMigration,
            loadEvents = { eventRepository.loadEvents() },
            saveEvents = { eventRepository.saveEvents(it) },
            resolveRuleId = { description -> RuleMatchingEngine.resolvePayload(description, null)?.ruleId },
            onEventsUpdated = { _events.value = it }
        )
    }

    private fun shouldSkipLegacyMigration(): Boolean {
        return isRoomMainEnabled() && migrationPrefs.isEventsMigrated() && migrationPrefs.isRecurringMigrated()
    }

    override fun loadAndScheduleAll() {
        refreshData()
    }

    private fun refreshData() {
        StoreBootstrapNode.refreshData(
            scope = scope,
            isRoomMainEnabled = ::isRoomMainEnabled,
            isRoomReadEnabled = ::isRoomReadEnabled,
            eventsMigrated = { migrationPrefs.isEventsMigrated() },
            recurringMigrated = { migrationPrefs.isRecurringMigrated() },
            archivesMigrated = { migrationPrefs.isArchivesMigrated() },
            loadJsonEvents = { eventRepository.loadEvents() },
            loadCourses = { emptyList() },
            loadSettings = { settingsRepository.loadSettings() },
            loadRoomActiveEvents = { roomEventReader.loadActiveEvents() },
            loadJsonArchived = { archiveRepository.loadArchivedEvents() },
            consumeEventCleanupInfo = { eventRepository.getAndClearCleanupInfo() },
            consumeCourseCleanupInfo = { "" },
            saveCleanupInfo = { info -> CrashHandler.saveCleanupInfo(appContext, info) },
            saveJsonEvents = { eventRepository.saveEvents(it) },
            saveJsonArchived = { archiveRepository.saveArchivedEvents(it) },
            migrateLegacyEvents = { legacyEventMigrator.migrateIfNeeded(it) },
            migrateLegacyRecurring = { legacyRecurringMigrator.migrateIfNeeded(it) },
            migrateLegacyArchives = { legacyArchiveMigrator.migrateIfNeeded(it) },
            sanitizeRecurringEvents = ::sanitizeRecurringEvents,
            cleanupLegacyJsonIfStable = ::cleanupLegacyJsonIfStable,
            logRoomDiff = StoreRoomDiffNode::logRoomDiff,
            updateState = { events, _, settings ->
                _settings.value = settings
                _events.value = events
            },
            scheduleRemindersForEvents = { events -> events.forEach(::scheduleRemindersIfNeeded) },
            autoArchiveExpiredEvents = ::autoArchiveExpiredEvents
        )
    }

    override fun fetchArchivedEvents() {
        StoreBootstrapNode.fetchArchivedEvents(
            scope = scope,
            withArchiveLock = { action -> archiveMutex.withLock { action() } },
            loadArchivedEventsForState = ::loadArchivedEventsForState,
            updateArchivedEvents = { _archivedEvents.value = it }
        )
    }

    private suspend fun ensureArchivesLoaded() {
        StoreBootstrapNode.ensureArchivesLoaded(
            currentArchivedEvents = { _archivedEvents.value },
            withArchiveLock = { action -> archiveMutex.withLock { action() } },
            loadArchivedEventsForState = ::loadArchivedEventsForState,
            updateArchivedEvents = { _archivedEvents.value = it },
            onLoadingTriggered = { Log.d("StoreNode", "触发归档数据懒加载...") }
        )
    }

    private suspend fun loadArchivedEventsForState(): StoreBootstrapNode.ArchivedLoadResult {
        return StoreBootstrapNode.loadArchivedEventsForState(
            isRoomReadEnabled = ::isRoomReadEnabled,
            isRoomMainEnabled = ::isRoomMainEnabled,
            archivesMigrated = { migrationPrefs.isArchivesMigrated() },
            loadRoomArchivedEvents = { roomEventReader.loadArchivedEvents() },
            loadJsonArchivedEvents = { archiveRepository.loadArchivedEvents() },
            sanitizeRecurringEvents = ::sanitizeRecurringEvents,
            saveJsonArchivedEvents = { archiveRepository.saveArchivedEvents(it) },
            migrateLegacyArchives = { legacyArchiveMigrator.migrateIfNeeded(it) },
            logRoomDiff = StoreRoomDiffNode::logRoomDiff
        )
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

    override suspend fun addEvent(event: MyEvent, triggerSync: Boolean) {
        StoreMutationNode.addEvent(
            event = event,
            triggerSync = triggerSync,
            withEventLock = { action -> eventMutex.withLock { action() } },
            loadCurrentActiveMutableList = { _events.value.toMutableList() },
            normalizeEventForPersistence = ::normalizeEventForPersistence,
            updateEvents = ::updateEvents,
            scheduleRemindersIfNeeded = ::scheduleRemindersIfNeeded,
            triggerAutoSync = ::triggerAutoSync
        )
    }

    override suspend fun updateEvent(event: MyEvent, triggerSync: Boolean) {
        StoreMutationNode.updateEvent(
            event = event,
            triggerSync = triggerSync,
            withEventLock = { action -> eventMutex.withLock { action() } },
            loadCurrentActiveMutableList = { _events.value.toMutableList() },
            normalizeEventForPersistence = ::normalizeEventForPersistence,
            cancelReminders = ::cancelReminders,
            updateEvents = ::updateEvents,
            onEventUpdated = { normalizedEvent ->
                Log.d("Undo", "updateEvent后: id=${normalizedEvent.id}, isCheckedIn=${normalizedEvent.isCheckedIn}")
            },
            scheduleRemindersIfNeeded = ::scheduleRemindersIfNeeded,
            triggerAutoSync = ::triggerAutoSync
        )
    }

    override suspend fun deleteEvent(
        eventId: String,
        triggerSync: Boolean,
        removeFromRoom: Boolean
    ) {
        StoreMutationNode.deleteEvent(
            eventId = eventId,
            triggerSync = triggerSync,
            removeFromRoom = removeFromRoom,
            withEventLock = { action -> eventMutex.withLock { action() } },
            loadCurrentActiveMutableList = { _events.value.toMutableList() },
            onBeforeDelete = { event, shouldRemoveFromRoom ->
                if (!shouldRemoveFromRoom && event.isRecurring && !event.isRecurringParent) {
                    markRecurringInstanceCancelled(event.id)
                }
            },
            cancelReminders = ::cancelReminders,
            updateEvents = ::updateEvents,
            deleteRoomShadowEvents = { ids -> roomShadowWriter.deleteEvents(ids) },
            requestCapsuleRefresh = ::requestCapsuleRefresh,
            triggerAutoSync = ::triggerAutoSync
        )
    }

    private suspend fun updateEvents(newList: List<MyEvent>) {
        val normalizedTemplates = newList.map { CourseEventMapper.normalizeTemplateEvent(it) }
        StoreEventStorageNode.updateEvents(
            newList = normalizedTemplates,
            onEventsUpdated = {
                _events.value = it
            },
            persistActiveEvents = ::persistActiveEvents
        )
        legacyRecurringMigrator.upsertRecurringEvents(normalizedTemplates)
    }

    private fun normalizeEventForPersistence(event: MyEvent): MyEvent {
        return StoreEventStorageNode.normalizeEventForPersistence(event)
    }

    private suspend fun persistActiveEvents(events: List<MyEvent>) {
        StoreEventStorageNode.persistActiveEvents(
            events = events,
            isRoomMainEnabled = ::isRoomMainEnabled,
            saveEventsBackup = { eventRepository.saveEventsBackup(it) },
            saveEvents = { eventRepository.saveEvents(it) },
            syncRoomShadowActive = { roomShadowWriter.syncEvents(it, RoomEventShadowWriter.SyncMode.ACTIVE) }
        )
    }

    private fun scheduleRemindersIfNeeded(event: MyEvent) {
        if (StoreEventStorageNode.shouldScheduleReminders(event)) {
            NotificationScheduler.scheduleReminders(context, event)
        }
    }

    private fun cancelReminders(event: MyEvent) {
        NotificationScheduler.cancelReminders(context, event)
    }

    private fun cleanupLegacyJsonIfStable() {
        StoreLegacyCleanupNode.cleanupLegacyJsonIfStable(
            appFilesDir = appContext.filesDir,
            isRoomMainEnabled = ::isRoomMainEnabled,
            eventsMigrated = { migrationPrefs.isEventsMigrated() },
            archivesMigrated = { migrationPrefs.isArchivesMigrated() },
            recurringMigrated = { migrationPrefs.isRecurringMigrated() },
            legacyJsonFiles = LEGACY_JSON_FILES
        )
    }

    private fun sanitizeRecurringEvents(events: List<MyEvent>): List<MyEvent> {
        return StoreEventStorageNode.sanitizeRecurringEvents(events)
    }

    override suspend fun detachRecurringInstance(
        parentEventId: String,
        sourceInstanceId: String,
        sourceInstanceKey: String,
        detachedEvent: MyEvent
    ) {
        StoreEventCoordinatorNode.detachRecurringInstance(
            parentEventId = parentEventId,
            sourceInstanceId = sourceInstanceId,
            sourceInstanceKey = sourceInstanceKey,
            detachedEvent = detachedEvent,
            withEventLock = { action -> eventMutex.withLock { action() } },
            loadCurrentActiveMutableList = { _events.value.toMutableList() },
            cancelReminders = ::cancelReminders,
            updateEvents = ::updateEvents,
            scheduleRemindersIfNeeded = ::scheduleRemindersIfNeeded,
            requestCapsuleRefresh = ::requestCapsuleRefresh
        )
    }

    override suspend fun completeScheduleEvent(id: String) {
        StoreEventCoordinatorNode.completeScheduleEvent(
            id = id,
            findActiveEventById = { eventId -> _events.value.find { it.id == eventId } },
            updateEventWithoutSync = { event -> updateEvent(event, triggerSync = false) },
            requestCapsuleRefresh = ::requestCapsuleRefresh,
            syncSingleEventToCalendar = ::syncSingleEventToCalendar
        )
    }

    private suspend fun markRecurringInstanceCancelled(eventId: String) {
        val instanceDao = database.eventInstanceDao()
        StoreEventCoordinatorNode.markRecurringInstanceCancelled(
            eventId = eventId,
            loadInstanceById = { id -> instanceDao.getById(id) },
            updateInstance = { instance -> instanceDao.update(instance) }
        )
    }

    suspend fun checkInTransport(id: String) {
        StoreEventCoordinatorNode.checkInTransport(
            id = id,
            findActiveEventById = { eventId -> _events.value.find { it.id == eventId } },
            updateEventWithoutSync = { event -> updateEvent(event, triggerSync = false) },
            requestCapsuleRefresh = ::requestCapsuleRefresh,
            syncSingleEventToCalendar = ::syncSingleEventToCalendar
        )
    }

    suspend fun undoCompleteEvent(id: String): Boolean {
        return StoreEventCoordinatorNode.undoCompleteEvent(
            id = id,
            findActiveEventById = { eventId -> _events.value.find { it.id == eventId } },
            updateEvent = { event -> updateEvent(event) },
            requestCapsuleRefresh = ::requestCapsuleRefresh
        )
    }

    suspend fun undoCheckInTransport(id: String): Boolean {
        return StoreEventCoordinatorNode.undoCheckInTransport(
            id = id,
            findActiveEventById = { eventId -> _events.value.find { it.id == eventId } },
            updateEventWithoutSync = { event -> updateEvent(event, triggerSync = false) },
            requestCapsuleRefresh = ::requestCapsuleRefresh
        )
    }

    override suspend fun performPrimaryRuleAction(eventId: String): Boolean {
        return StoreEventCoordinatorNode.performPrimaryRuleAction(
            eventId = eventId,
            findActiveEventById = { id -> _events.value.find { it.id == id } },
            undoCheckInTransport = ::undoCheckInTransport,
            undoCompleteEvent = ::undoCompleteEvent,
            checkInTransport = ::checkInTransport,
            completeScheduleEvent = ::completeScheduleEvent
        )
    }

    suspend fun getEventById(id: String): MyEvent? {
        return _events.value.find { it.id == id }
    }

    private suspend fun saveCoursesFromEvents(newCourses: List<Course>, triggerSync: Boolean = true) {
        StoreMutationNode.saveCourses(
            newCourses = newCourses,
            triggerSync = triggerSync,
            withCourseLock = { action -> courseMutex.withLock { action() } },
            updateCourses = ::replaceCourseTemplates,
            triggerAutoSync = ::triggerAutoSync
        )
    }

    private suspend fun replaceCourseTemplates(newList: List<Course>) {
        val courseTemplateEvents = CourseEventMapper.toTemplateEvents(newList, _settings.value)
        val nonCourseEvents = _events.value.filterNot { it.tag == com.antgskds.calendarassistant.data.model.EventTags.COURSE }
        updateEvents(nonCourseEvents + courseTemplateEvents)
    }

    private fun currentCoursesFromEvents(): List<Course> {
        return CourseEventMapper.extractCourses(_events.value, _settings.value)
    }

    private fun applySettingsNow(newSettings: MySettings) {
        StoreSettingsNode.applySettingsNow(
            newSettings = newSettings,
            onSettingsUpdated = { _settings.value = it },
            saveSettings = { settingsRepository.saveSettings(it) },
            syncWeatherForSettings = { WeatherSyncWorker.syncForSettings(appContext, it) }
        )
    }

    override fun updateSettings(newSettings: MySettings) {
        StoreSettingsNode.updateSettings(
            scope = scope,
            newSettings = newSettings,
            applySettingsNow = ::applySettingsNow
        )
    }

    override suspend fun enableCalendarSyncAndSyncNow(): Result<Unit> {
        return StoreCalendarSyncNode.enableCalendarSyncAndSyncNow(
            enableCalendarSync = { enableCalendarSync() },
            manualSync = { manualSync() },
            syncFromCalendar = { syncFromCalendar() }
        )
    }

    companion object {
        @Volatile
        private var INSTANCE: StoreRootNode? = null

        private val LEGACY_JSON_FILES = listOf(
            "events.json",
            "events.json.bak",
            "archives.json",
            "archives.json.bak"
        )

        fun getInstance(context: Context): StoreRootNode {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: StoreRootNode(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    override suspend fun exportCoursesData(): String {
        return backupWorkflowSupport.exportCoursesData()
    }

    override suspend fun importCoursesData(jsonString: String): Result<Unit> {
        return backupWorkflowSupport.importCoursesData(jsonString)
    }

    override suspend fun importWakeUpFile(
        content: String,
        mode: ImportMode,
        importSettings: Boolean
    ): Result<Int> {
        return backupWorkflowSupport.importWakeUpFile(content, mode, importSettings)
    }

    override suspend fun exportEventsData(): String {
        return backupWorkflowSupport.exportEventsData()
    }

    override suspend fun importEventsData(jsonString: String): Result<ImportResult> {
        return importEventsData(jsonString, preserveArchivedStatus = true)
    }

    suspend fun importEventsData(
        jsonString: String,
        preserveArchivedStatus: Boolean = true
    ): Result<ImportResult> {
        return backupWorkflowSupport.importEventsData(jsonString, preserveArchivedStatus)
    }

    override fun getEventsCount(): Int = _events.value.size

    override fun getTotalEventsCount(): Int = _events.value.size + _archivedEvents.value.size

    private suspend fun triggerAutoSync() {
        StoreCalendarSyncNode.triggerAutoSync(::syncAllToCalendar)
    }

    private suspend fun syncAllToCalendar(seedMapping: Boolean) {
        StoreCalendarSyncNode.syncAllToCalendar(
            seedMapping = seedMapping,
            currentSettings = { _settings.value },
            currentEvents = { _events.value },
            isCalendarSyncV2Enabled = ::isCalendarSyncV2Enabled,
            parseTimeTable = StoreSyncNode::parseTimeTable,
            seedSyncMappingIfNeeded = ::seedSyncMappingIfNeeded,
            syncGatewayCall = { useV2, events, semesterStart, totalWeeks, timeNodes ->
                syncGateway.syncAllToCalendar(
                    useV2 = useV2,
                    events = events,
                    semesterStart = semesterStart,
                    totalWeeks = totalWeeks,
                    timeNodes = timeNodes
                )
            }
        )
    }

    private suspend fun seedSyncMappingIfNeeded() {
        StoreCalendarSyncNode.seedSyncMappingIfNeeded(
            isAttempted = syncSeedAttempted,
            loadV2StatusSnapshot = {
                syncGateway.getV2Status().let { Triple(it.isEnabled, it.hasPermission, it.mappedEventCount) }
            },
            hasAnyLocalEvents = { _events.value.isNotEmpty() || _archivedEvents.value.isNotEmpty() },
            syncFromCalendar = ::syncFromCalendar,
            setAttempted = { syncSeedAttempted = it }
        )
    }

    override suspend fun manualSync(): Result<Unit> {
        return StoreCalendarSyncNode.manualSync(::syncAllToCalendar)
    }

    override suspend fun enableCalendarSync(): Result<Unit> {
        return StoreCalendarSyncNode.enableCalendarSync(
            isCalendarSyncV2Enabled = ::isCalendarSyncV2Enabled,
            setAttempted = { syncSeedAttempted = it },
            enableSync = { useV2 -> syncGateway.enableSync(useV2) }
        )
    }

    override suspend fun disableCalendarSync(): Result<Unit> {
        return StoreCalendarSyncNode.disableCalendarSync(
            isCalendarSyncV2Enabled = ::isCalendarSyncV2Enabled,
            disableSync = { useV2 -> syncGateway.disableSync(useV2) }
        )
    }

    override suspend fun getSyncStatus(): CalendarSyncManager.SyncStatus {
        return StoreCalendarSyncNode.getSyncStatus(
            isCalendarSyncV2Enabled = ::isCalendarSyncV2Enabled,
            getSyncStatus = { useV2 -> syncGateway.getSyncStatus(useV2) }
        )
    }

    override suspend fun getSelectableSyncCalendars(): List<CalendarManager.CalendarInfo> {
        return StoreCalendarSyncNode.getSelectableSyncCalendars(systemCalendarManager)
    }

    override suspend fun updateSourceCalendars(calendarIds: List<Long>): Result<Unit> {
        return StoreCalendarSyncNode.updateSourceCalendars(
            calendarIds = calendarIds,
            loadSyncData = { syncDataSource.loadSyncData() },
            getSelectableSyncCalendars = { getSelectableSyncCalendars() },
            saveSyncData = { syncData -> syncDataSource.saveSyncData(syncData) },
            pruneSourceMappings = { removedIds -> pruneSourceMappings(removedIds) },
            syncFromCalendar = { syncFromCalendar() }
        )
    }

    private suspend fun syncSingleEventToCalendar(event: MyEvent) {
        StoreCalendarSyncNode.syncSingleEventToCalendar(
            event = event,
            isCalendarSyncV2Enabled = ::isCalendarSyncV2Enabled,
            syncEventToCalendar = { useV2, target -> syncGateway.syncEventToCalendar(useV2, target) }
        )
    }

    private suspend fun pruneSourceMappings(calendarIds: Set<Long>) {
        StoreCalendarSyncNode.pruneSourceMappings(
            calendarIds = calendarIds,
            getMappingsByCalendarIds = { ids -> calendarSyncMapDao.getByCalendarIds(ids) },
            localEvents = { _events.value },
            archivedEvents = { _archivedEvents.value },
            loadSyncData = { syncDataSource.loadSyncData() },
            eventMasterExists = { localMasterId -> eventMasterDao.getById(localMasterId) != null },
            deleteMappingByLocalMasterId = { localMasterId ->
                calendarSyncMapDao.deleteByLocalMasterId(localMasterId)
            },
            removeLegacyMappings = { localIds -> syncMappingRepository.removeMappings(localIds) }
        )
    }

    override suspend fun syncFromCalendar(): Result<Int> {
        return StoreCalendarSyncNode.syncFromCalendar(
            ensureArchivesLoaded = { ensureArchivesLoaded() },
            activeEvents = { _events.value },
            archivedEvents = { _archivedEvents.value },
            isCalendarSyncV2Enabled = ::isCalendarSyncV2Enabled,
            syncGatewayCall = { useV2, snapshot, onAdded, onUpdated, onDeleted ->
                syncGateway.syncFromCalendar(
                    useV2 = useV2,
                    onEventAdded = onAdded,
                    onEventUpdated = onUpdated,
                    onEventDeleted = onDeleted,
                    allowRecurringSync = true,
                    activeEvents = snapshot.activeEvents,
                    archivedEvents = snapshot.archivedEvents
                )
            },
            updateEventWithoutSync = { event -> updateEvent(event, triggerSync = false) },
            addEventWithoutSync = { event -> addEvent(event, triggerSync = false) },
            deleteEventWithoutSync = { id, removeFromRoom ->
                deleteEvent(id, triggerSync = false, removeFromRoom = removeFromRoom)
            },
            mergeIncomingCalendarEvent = StoreSyncNode::mergeIncomingCalendarEvent,
            isNoopCalendarMerge = StoreSyncNode::isNoopCalendarMerge,
            isDuplicateEvent = StoreSyncNode::isDuplicateEvent,
            withRandomColorIfNeeded = { event ->
                StoreSyncNode.withRandomColorIfNeeded(event, ::getRandomEventColor)
            }
        )
    }

    override suspend fun archiveEvent(eventId: String) {
        StoreArchiveCoordinatorNode.archiveEvent(
            eventId = eventId,
            findActiveEventById = { id -> _events.value.find { it.id == id } },
            withArchiveLock = { action -> archiveMutex.withLock { action() } },
            loadCurrentArchivedMutableList = {
                archiveStorageSupport.loadCurrentArchivedMutableList(_archivedEvents.value)
            },
            updateArchivedEvents = { updateArchivedEvents(it) },
            deleteEventWithoutSyncAndKeepRoom = { id ->
                deleteEvent(id, triggerSync = false, removeFromRoom = false)
            }
        )
    }

    override suspend fun restoreEvent(archivedEventId: String) {
        StoreArchiveCoordinatorNode.restoreEvent(
            archivedEventId = archivedEventId,
            withArchiveLock = { action -> archiveMutex.withLock { action() } },
            findArchivedEventById = { id -> _archivedEvents.value.find { it.id == id } },
            addEventWithSync = { event -> addEvent(event, triggerSync = true) },
            loadCurrentArchivedMutableList = {
                archiveStorageSupport.loadCurrentArchivedMutableList(_archivedEvents.value)
            },
            updateArchivedEvents = { updateArchivedEvents(it) }
        )
    }

    override suspend fun deleteArchivedEvent(archivedEventId: String) {
        StoreArchiveCoordinatorNode.deleteArchivedEvent(
            archivedEventId = archivedEventId,
            withArchiveLock = { action -> archiveMutex.withLock { action() } },
            loadCurrentArchivedMutableList = {
                archiveStorageSupport.loadCurrentArchivedMutableList(_archivedEvents.value)
            },
            updateArchivedEvents = { updateArchivedEvents(it) },
            deleteRoomShadowEvents = { ids -> roomShadowWriter.deleteEvents(ids) },
            removeCalendarMappingsForEvents = { events, reason ->
                archiveStorageSupport.removeCalendarMappingsForEvents(events, reason)
            }
        )
    }

    override suspend fun clearAllArchives() {
        StoreArchiveCoordinatorNode.clearAllArchives(
            withArchiveLock = { action -> archiveMutex.withLock { action() } },
            loadCurrentArchivedMutableList = {
                archiveStorageSupport.loadCurrentArchivedMutableList(_archivedEvents.value)
            },
            removeCalendarMappingsForEvents = { events, reason ->
                archiveStorageSupport.removeCalendarMappingsForEvents(events, reason)
            },
            updateArchivedEvents = { updateArchivedEvents(it) },
            deleteRoomShadowEvents = { ids -> roomShadowWriter.deleteEvents(ids) }
        )
    }

    private suspend fun updateArchivedEvents(newList: List<MyEvent>) {
        StoreArchiveCoordinatorNode.updateArchivedEvents(
            newList = newList,
            onArchivedEventsUpdated = { _archivedEvents.value = it },
            persistArchivedEvents = {
                archiveStorageSupport.persistArchivedEvents(it, roomMainEnabled = isRoomMainEnabled())
            }
        )
    }

    override suspend fun autoArchiveExpiredEvents(): Int {
        return StoreArchiveCoordinatorNode.autoArchiveExpiredEvents(
            autoArchiveEnabled = _settings.value.autoArchiveEnabled,
            activeEvents = { _events.value },
            withArchiveLock = { action -> archiveMutex.withLock { action() } },
            withEventLock = { action -> eventMutex.withLock { action() } },
            loadCurrentArchivedMutableList = {
                archiveStorageSupport.loadCurrentArchivedMutableList(_archivedEvents.value)
            },
            updateArchivedEvents = { updateArchivedEvents(it) },
            loadCurrentActiveMutableList = { _events.value.toMutableList() },
            cancelReminders = { cancelReminders(it) },
            updateEvents = { updateEvents(it) },
            triggerAutoSync = { triggerAutoSync() }
        )
    }
}
