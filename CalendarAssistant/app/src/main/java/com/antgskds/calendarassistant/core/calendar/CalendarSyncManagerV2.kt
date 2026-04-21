package com.antgskds.calendarassistant.core.calendar

import android.content.Context
import android.provider.CalendarContract
import android.util.Log
import androidx.room.withTransaction
import com.antgskds.calendarassistant.core.rule.RuleActionDefaults
import com.antgskds.calendarassistant.core.rule.RuleMatchingEngine
import com.antgskds.calendarassistant.core.util.EventDeduplicator
import com.antgskds.calendarassistant.data.db.AppDatabase
import com.antgskds.calendarassistant.data.db.entity.CalendarSyncMapEntity
import com.antgskds.calendarassistant.data.db.entity.EventExcludedDateEntity
import com.antgskds.calendarassistant.data.db.entity.EventInstanceEntity
import com.antgskds.calendarassistant.data.db.entity.EventMasterEntity
import com.antgskds.calendarassistant.data.model.EventFingerprint
import com.antgskds.calendarassistant.data.model.EventTags
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.data.model.SyncData
import com.antgskds.calendarassistant.data.model.TimeNode
import com.antgskds.calendarassistant.data.source.SyncJsonDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean

class CalendarSyncManagerV2(private val context: Context) {

    companion object {
        private const val TAG = "CalendarSyncManagerV2"
        private const val SYNC_LOOK_BACK_DAYS = 30L
        private const val SYNC_LOOK_AHEAD_DAYS = 30L
        private const val RECURRING_INSTANCES_SYNC_LIMIT = 2000
        private const val SOURCE_SYSTEM_RECURRING = "system_recurring"
        private const val DEFAULT_SYNC_COLOR = 0xFFA2B5BB.toInt()
    }

    /** 并发守卫：防止 ContentObserver 触发和定时同步同时执行导致重复 */
    private val _isSyncing = AtomicBoolean(false)

    private val calendarManager = CalendarManager(context)
    private val syncDataSource = SyncJsonDataSource.getInstance(context)
    private val database = AppDatabase.getInstance(context.applicationContext)
    private val syncMapDao = database.calendarSyncMapDao()
    private val masterDao = database.eventMasterDao()
    private val instanceDao = database.eventInstanceDao()
    private val excludedDateDao = database.eventExcludedDateDao()
    private val zoneId = ZoneId.systemDefault()
    private val seeding = CalendarSyncManagerV2Seeding(
        context = context,
        calendarManager = calendarManager,
        syncDataSource = syncDataSource,
        syncMapDao = syncMapDao,
        masterDao = masterDao,
        instanceDao = instanceDao,
        syncLookBackDays = SYNC_LOOK_BACK_DAYS,
        syncLookAheadDays = SYNC_LOOK_AHEAD_DAYS,
        logTag = TAG
    )
    private val recurringSupport = CalendarSyncManagerV2RecurringSupport(
        calendarManager = calendarManager,
        database = database,
        syncMapDao = syncMapDao,
        masterDao = masterDao,
        instanceDao = instanceDao,
        excludedDateDao = excludedDateDao,
        defaultSyncColor = DEFAULT_SYNC_COLOR,
        sourceSystemRecurring = SOURCE_SYSTEM_RECURRING
    )

    suspend fun syncAllToCalendar(
        events: List<MyEvent>,
        semesterStart: String?,
        totalWeeks: Int,
        timeNodes: List<TimeNode>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!CalendarPermissionHelper.hasAllPermissions(context)) {
                Log.w(TAG, "Missing calendar permissions, skip sync")
                return@withContext Result.failure(SecurityException("Missing calendar permissions"))
            }

            var syncData = syncDataSource.loadSyncData()
            if (!syncData.isSyncEnabled) {
                Log.d(TAG, "Sync disabled, skip")
                return@withContext Result.success(Unit)
            }

            val calendarResult = seeding.resolveTargetCalendar(syncData) ?: return@withContext Result.failure(
                Exception("Unable to resolve calendar ID")
            )
            var calendarId = calendarResult.first
            syncData = calendarResult.second

            val calendarMeta = seeding.loadCalendarMeta(calendarId)
            val eventsById = events.associateBy { it.id }
            syncData = seeding.ensureSyncMapSeeded(syncData, calendarId, eventsById)
            syncData = seeding.seedExistingCalendarMappings(syncData, calendarId, calendarMeta, eventsById)

            syncData = syncEvents(events, calendarId, calendarMeta, syncData)
            syncData = syncRecurringMasters(calendarId, calendarMeta, syncData)

            syncDataSource.saveSyncData(syncData.copy(lastSyncTime = System.currentTimeMillis()))
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            Result.failure(e)
        }
    }

    suspend fun syncFromCalendar(
        onEventAdded: suspend (MyEvent) -> Unit,
        onEventUpdated: suspend (MyEvent) -> Unit,
        onEventDeleted: suspend (String) -> Unit,
        allowRecurringSync: Boolean = false,
        activeEvents: List<MyEvent> = emptyList(),
        archivedEvents: List<MyEvent> = emptyList()
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (!_isSyncing.compareAndSet(false, true)) {
            Log.d(TAG, "反向同步已在执行，跳过")
            return@withContext Result.success(0)
        }
        try {
            if (!CalendarPermissionHelper.hasAllPermissions(context)) {
                return@withContext Result.failure(SecurityException("Missing calendar permissions"))
            }

            var syncData = syncDataSource.loadSyncData()
            if (!syncData.isSyncEnabled) {
                Log.d(TAG, "Sync disabled, skip")
                return@withContext Result.success(0)
            }

            val sourceCalendarIds = resolveSourceCalendarIds(syncData)
            Log.d(TAG, "Reverse sync requested: sourceCalendars=$sourceCalendarIds")
            if (sourceCalendarIds.isEmpty()) {
                Log.d(TAG, "No source calendars configured, skip reverse sync")
                return@withContext Result.success(0)
            }

            val mutableActiveEvents = activeEvents.toMutableList()
            val mutableArchivedEvents = archivedEvents.toMutableList()
            var totalAdded = 0
            var totalUpdated = 0
            var totalDeleted = 0
            var syncDataChanged = false

            suspend fun trackAdded(event: MyEvent) {
                onEventAdded(event)
                mutableArchivedEvents.removeAll { it.id == event.id }
                val existingIndex = mutableActiveEvents.indexOfFirst { it.id == event.id }
                if (existingIndex >= 0) {
                    mutableActiveEvents[existingIndex] = event
                } else {
                    mutableActiveEvents.add(event)
                }
            }

            suspend fun trackUpdated(event: MyEvent) {
                onEventUpdated(event)
                val activeIndex = mutableActiveEvents.indexOfFirst { it.id == event.id }
                if (activeIndex >= 0) {
                    mutableActiveEvents[activeIndex] = event
                    return
                }

                val archivedIndex = mutableArchivedEvents.indexOfFirst { it.id == event.id }
                if (archivedIndex >= 0) {
                    mutableArchivedEvents[archivedIndex] = event
                } else {
                    mutableActiveEvents.add(event)
                }
            }

            suspend fun trackDeleted(eventId: String) {
                onEventDeleted(eventId)
                mutableActiveEvents.removeAll { it.id == eventId }
                mutableArchivedEvents.removeAll { it.id == eventId }
            }

            sourceCalendarIds.forEach { calendarId ->
                val calendarMeta = seeding.loadCalendarMeta(calendarId)
                Log.d(
                    TAG,
                    "Reverse sync calendar start: calendarId=$calendarId, displayName=${calendarMeta.displayName}, accountName=${calendarMeta.accountName}, accountType=${calendarMeta.accountType}"
                )
                val eventsById = (mutableActiveEvents + mutableArchivedEvents).associateBy { it.id }
                val seededSyncData = seeding.ensureSyncMapSeeded(syncData, calendarId, eventsById)
                syncDataChanged = syncDataChanged || seededSyncData.mapping != syncData.mapping
                syncData = seededSyncData

                val result = syncFromSingleCalendar(
                    calendarId = calendarId,
                    calendarMeta = calendarMeta,
                    syncData = syncData,
                    allowRecurringSync = allowRecurringSync,
                    activeEvents = mutableActiveEvents,
                    archivedEvents = mutableArchivedEvents,
                    onEventAdded = ::trackAdded,
                    onEventUpdated = ::trackUpdated,
                    onEventDeleted = ::trackDeleted
                )

                syncData = result.syncData
                syncDataChanged = syncDataChanged || result.hasChanges
                totalAdded += result.addedCount
                totalUpdated += result.updatedCount
                totalDeleted += result.deletedCount

                Log.d(
                    TAG,
                    "Reverse sync calendar done: calendarId=$calendarId, +${result.addedCount}, ~${result.updatedCount}, -${result.deletedCount}, changed=${result.hasChanges}"
                )
            }

            if (syncDataChanged) {
                syncDataSource.saveSyncData(syncData.copy(lastSyncTime = System.currentTimeMillis()))
            }

            Log.d(TAG, "Reverse sync done: +$totalAdded, ~$totalUpdated, -$totalDeleted")
            Result.success(totalAdded + totalUpdated + totalDeleted)
        } catch (e: Exception) {
            Log.e(TAG, "Reverse sync failed", e)
            Result.failure(e)
        } finally {
            _isSyncing.set(false)
        }
    }

    suspend fun syncEventToCalendar(event: MyEvent): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (event.isRecurring) {
                Log.d(TAG, "Skip single sync for recurring event id=${event.id}")
                return@withContext Result.success(Unit)
            }

            if (!CalendarPermissionHelper.hasAllPermissions(context)) {
                Log.w(TAG, "Single sync missing calendar permissions")
                return@withContext Result.failure(SecurityException("Missing calendar permissions"))
            }

            var syncData = syncDataSource.loadSyncData()
            if (!syncData.isSyncEnabled) {
                Log.d(TAG, "Single sync disabled, skip")
                return@withContext Result.success(Unit)
            }

            val calendarId = syncData.targetCalendarId
            if (calendarId == -1L) {
                Log.w(TAG, "Single sync target calendar missing")
                return@withContext Result.success(Unit)
            }

            val existingMapping = syncMapDao.getByLocalMasterId(event.id)
            val resolvedCalendarId = existingMapping?.calendarId ?: calendarId

            if (event.skipCalendarSync && existingMapping == null) {
                Log.d(TAG, "Skip single sync for imported event without mapping id=${event.id}")
                return@withContext Result.success(Unit)
            }

            val calendarMeta = seeding.loadCalendarMeta(resolvedCalendarId)
            syncData = seeding.ensureSyncMapSeeded(syncData, resolvedCalendarId, mapOf(event.id to event))

            val mapping = existingMapping ?: syncMapDao.getByLocalMasterId(event.id)
            if (mapping == null) {
                Log.w(TAG, "Single sync mapping missing: ${event.id}")
                return@withContext Result.success(Unit)
            }

            val updated = calendarManager.updateEvent(
                eventId = mapping.systemEventId,
                event = event,
                calendarId = mapping.calendarId
            )
            if (updated) {
                val lastSyncHash = CalendarSyncManagerV2Hashing.computeEventHash(event, zoneId)
                syncMapDao.update(mapping.copy(lastSyncHash = lastSyncHash))
                if (syncData.mapping[event.id] != mapping.systemEventId.toString()) {
                    syncData = syncData.copy(mapping = syncData.mapping + (event.id to mapping.systemEventId.toString()))
                    syncDataSource.saveSyncData(syncData.copy(lastSyncTime = System.currentTimeMillis()))
                }
                Result.success(Unit)
            } else {
                Result.failure(Exception("Sync failed"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Single sync failed: ${event.id}", e)
            Result.failure(e)
        }
    }

    suspend fun enableSync(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!CalendarPermissionHelper.hasAllPermissions(context)) {
                return@withContext Result.failure(SecurityException("Missing calendar permissions"))
            }

            val calendarId = calendarManager.getOrCreateAppCalendar()
            if (calendarId == -1L) {
                return@withContext Result.failure(Exception("Unable to resolve calendar ID"))
            }

            val existingSyncData = syncDataSource.loadSyncData()
            val syncData = SyncData(
                isSyncEnabled = true,
                targetCalendarId = calendarId,
                sourceCalendarIds = existingSyncData.sourceCalendarIds.ifEmpty {
                    listOf(calendarId)
                },
                mapping = existingSyncData.mapping,
                lastSyncTime = System.currentTimeMillis()
            )
            syncDataSource.saveSyncData(syncData)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Enable sync failed", e)
            Result.failure(e)
        }
    }

    suspend fun disableSync(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val syncData = syncDataSource.loadSyncData()
            syncDataSource.saveSyncData(syncData.copy(isSyncEnabled = false))
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Disable sync failed", e)
            Result.failure(e)
        }
    }

    suspend fun getSyncStatus(): SyncStatus = withContext(Dispatchers.IO) {
        val syncData = syncDataSource.loadSyncData()
        val hasPermission = CalendarPermissionHelper.hasAllPermissions(context)

        SyncStatus(
            isEnabled = syncData.isSyncEnabled,
            hasPermission = hasPermission,
            targetCalendarId = syncData.targetCalendarId,
            sourceCalendarIds = resolveSourceCalendarIds(syncData),
            lastSyncTime = syncData.lastSyncTime,
            mappedEventCount = syncData.mapping.size
        )
    }

    data class SyncStatus(
        val isEnabled: Boolean,
        val hasPermission: Boolean,
        val targetCalendarId: Long,
        val sourceCalendarIds: List<Long>,
        val lastSyncTime: Long,
        val mappedEventCount: Int
    )

    private suspend fun syncFromSingleCalendar(
        calendarId: Long,
        calendarMeta: CalendarSyncCalendarMeta,
        syncData: SyncData,
        allowRecurringSync: Boolean,
        activeEvents: List<MyEvent>,
        archivedEvents: List<MyEvent>,
        onEventAdded: suspend (MyEvent) -> Unit,
        onEventUpdated: suspend (MyEvent) -> Unit,
        onEventDeleted: suspend (String) -> Unit
    ): ReverseSyncResult {
        val eventsById = (activeEvents + archivedEvents).associateBy { it.id }
        val existingMappings = syncMapDao.getByCalendarId(calendarId)
        val mappingByLocal = existingMappings.associateBy { it.localMasterId }
        val mappingBySystem = existingMappings.associateBy { it.systemEventId }

        var hasChanges = false
        val syncMapping = syncData.mapping.toMutableMap()
        var addedCount = 0
        var updatedCount = 0
        var deletedCount = 0

        val localIds = eventsById.keys
        val staleLocalIds = mappingByLocal.keys - localIds
        staleLocalIds.forEach { localId ->
            syncMapDao.deleteByLocalMasterId(localId)
            if (syncMapping.remove(localId) != null) {
                hasChanges = true
            }
        }

        val now = System.currentTimeMillis()
        val syncWindowStart = now - SYNC_LOOK_BACK_DAYS * 24 * 60 * 60 * 1000
        val syncWindowEnd = now + SYNC_LOOK_AHEAD_DAYS * 24 * 60 * 60 * 1000
        val rangeEvents = calendarManager.queryEventsInRange(calendarId, syncWindowStart, syncWindowEnd)
        Log.d(
            TAG,
            "syncFromSingleCalendar: calendarId=$calendarId, displayName=${calendarMeta.displayName}, mappings=${existingMappings.size}, active=${activeEvents.size}, archived=${archivedEvents.size}, rangeEvents=${rangeEvents.size}"
        )
        val fingerprintToSystemEvent = rangeEvents
            .filter { !it.isRecurring }
            .associateBy { EventDeduplicator.generateFingerprintFromSystemEvent(it) }

        val mappedSystemIds = existingMappings.map { it.systemEventId }.toMutableSet()
        val existingSystemEvents = calendarManager.queryEventsByIds(mappedSystemIds, calendarId)
        val foundSystemIds = existingSystemEvents.map { it.eventId }.toSet()
        Log.d(
            TAG,
            "syncFromSingleCalendar: calendarId=$calendarId, mappedSystemIds=${mappedSystemIds.size}, foundSystemIds=${foundSystemIds.size}"
        )

        if (foundSystemIds.isEmpty() && mappedSystemIds.isNotEmpty()) {
            val recurringEventsForCheck = if (allowRecurringSync) {
                calendarManager.queryRecurringInstancesInRangeLimited(
                    calendarId = calendarId,
                    startMillis = syncWindowStart,
                    endMillis = syncWindowEnd,
                    limit = 1
                ).events
            } else {
                emptyList()
            }
            if (rangeEvents.isEmpty() && recurringEventsForCheck.isEmpty()) {
                Log.d(TAG, "System calendar empty, skip reverse sync for calendarId=$calendarId")
                return ReverseSyncResult(syncData, hasChanges, addedCount, updatedCount, deletedCount)
            }
        }

        val missingSystemIds = mappedSystemIds - foundSystemIds
        missingSystemIds.forEach { systemId ->
            val mapping = mappingBySystem[systemId] ?: return@forEach
            val localEvent = eventsById[mapping.localMasterId]
            val localFingerprint = localEvent?.let { EventDeduplicator.generateFingerprint(it) }
            val remapCandidate = localFingerprint?.let { fingerprintToSystemEvent[it] }
            if (remapCandidate != null && !mappedSystemIds.contains(remapCandidate.eventId)) {
                syncMapDao.update(
                    mapping.copy(
                        systemEventId = remapCandidate.eventId,
                        lastSyncHash = CalendarSyncManagerV2Hashing.computeSystemHash(remapCandidate)
                    )
                )
                syncMapping[mapping.localMasterId] = remapCandidate.eventId.toString()
                mappedSystemIds.remove(systemId)
                mappedSystemIds.add(remapCandidate.eventId)
                hasChanges = true
                return@forEach
            }
            onEventDeleted(mapping.localMasterId)
            syncMapDao.deleteByLocalMasterId(mapping.localMasterId)
            if (syncMapping.remove(mapping.localMasterId) != null) {
                hasChanges = true
            }
            deletedCount++
        }

        existingSystemEvents.forEach { systemEvent ->
            if (systemEvent.isRecurring) return@forEach
            val mapping = mappingBySystem[systemEvent.eventId] ?: return@forEach

            val systemHash = CalendarSyncManagerV2Hashing.computeSystemHash(systemEvent)
            val systemIdStr = systemEvent.eventId.toString()
            if (syncMapping[mapping.localMasterId] != systemIdStr) {
                syncMapping[mapping.localMasterId] = systemIdStr
                hasChanges = true
            }
            if (mapping.lastSyncHash == systemHash) return@forEach

            val myEvent = CalendarEventMapper.mapSystemEventToMyEvent(systemEvent, fixedId = mapping.localMasterId)
            if (myEvent != null) {
                onEventUpdated(myEvent)
                syncMapDao.update(mapping.copy(lastSyncHash = systemHash))
                hasChanges = true
                updatedCount++
            }
        }

        val fingerprintToActive = activeEvents
            .filter { it.tag != EventTags.NOTE }
            .associateBy { EventDeduplicator.generateFingerprint(it) }
        val fingerprintToArchived = archivedEvents
            .filter { it.tag != EventTags.NOTE }
            .associateBy { EventDeduplicator.generateFingerprint(it) }

        rangeEvents.forEach { systemEvent ->
            if (systemEvent.isRecurring) return@forEach
            if (mappedSystemIds.contains(systemEvent.eventId)) return@forEach
            if (systemEvent.isManaged) return@forEach

            val systemFingerprint = EventDeduplicator.generateFingerprintFromSystemEvent(systemEvent)
            val duplicateActive = fingerprintToActive[systemFingerprint]
            val duplicateArchived = fingerprintToArchived[systemFingerprint]

            if (duplicateActive != null) {
                val existingLocalMapping = syncMapDao.getByLocalMasterId(duplicateActive.id)
                if (existingLocalMapping != null) {
                    Log.d(
                        TAG,
                        "skip import duplicate active event: calendarId=$calendarId, systemEventId=${systemEvent.eventId}, localId=${duplicateActive.id}, existingMappingCalendar=${existingLocalMapping.calendarId}, title=${systemEvent.title}"
                    )
                    return@forEach
                }

                syncMapDao.insert(
                    CalendarSyncMapEntity(
                        localMasterId = duplicateActive.id,
                        systemEventId = systemEvent.eventId,
                        calendarId = calendarId,
                        accountName = calendarMeta.accountName,
                        accountType = calendarMeta.accountType,
                        displayName = calendarMeta.displayName,
                        lastSyncHash = CalendarSyncManagerV2Hashing.computeSystemHash(systemEvent)
                    )
                )
                if (syncMapping[duplicateActive.id] != systemEvent.eventId.toString()) {
                    syncMapping[duplicateActive.id] = systemEvent.eventId.toString()
                    hasChanges = true
                }
                return@forEach
            }

            if (duplicateArchived != null) {
                Log.d(
                    TAG,
                    "skip import archived duplicate: calendarId=$calendarId, systemEventId=${systemEvent.eventId}, archivedId=${duplicateArchived.id}, title=${systemEvent.title}"
                )
                return@forEach
            }

            val myEvent = CalendarEventMapper.mapSystemEventToMyEvent(systemEvent)
            if (myEvent != null) {
                Log.d(
                    TAG,
                    "import new system event: calendarId=$calendarId, systemEventId=${systemEvent.eventId}, localId=${myEvent.id}, title=${systemEvent.title}"
                )
                onEventAdded(myEvent)
                syncMapDao.insert(
                    CalendarSyncMapEntity(
                        localMasterId = myEvent.id,
                        systemEventId = systemEvent.eventId,
                        calendarId = calendarId,
                        accountName = calendarMeta.accountName,
                        accountType = calendarMeta.accountType,
                        displayName = calendarMeta.displayName,
                        lastSyncHash = CalendarSyncManagerV2Hashing.computeSystemHash(systemEvent)
                    )
                )
                if (syncMapping[myEvent.id] != systemEvent.eventId.toString()) {
                    syncMapping[myEvent.id] = systemEvent.eventId.toString()
                    hasChanges = true
                }
                addedCount++
            }
        }

        val recurringSeriesPrefix = "${calendarId}_"
        val activeRecurringEvents = activeEvents.filter {
            it.isRecurring && it.recurringSeriesKey?.startsWith(recurringSeriesPrefix) == true
        }

        if (allowRecurringSync) {
            val recurringSeries = calendarManager.queryRecurringSeries(calendarId)
            val recurringInstancesResult = calendarManager.queryRecurringInstancesInRangeLimited(
                calendarId = calendarId,
                startMillis = syncWindowStart,
                endMillis = syncWindowEnd,
                limit = RECURRING_INSTANCES_SYNC_LIMIT
            )
            if (recurringInstancesResult.isTruncated) {
                val message = "Recurring instances exceeded limit ($RECURRING_INSTANCES_SYNC_LIMIT)"
                Log.w(TAG, message)
                throw RecurringSyncLimitException(message)
            }
            val recurringInstances = recurringInstancesResult.events

            val recurringParents = (activeEvents + archivedEvents)
                .filter {
                    it.isRecurring &&
                        it.isRecurringParent &&
                        !it.recurringSeriesKey.isNullOrBlank() &&
                        it.recurringSeriesKey?.startsWith(recurringSeriesPrefix) == true
                }
                .associateBy { it.id }

            val desiredRecurringEvents = buildRecurringEvents(
                calendarId = calendarId,
                recurringSeries = recurringSeries,
                recurringInstances = recurringInstances,
                existingRecurringParents = recurringParents,
                now = now
            )

            val activeRecurringById = activeRecurringEvents.associateBy { it.id }
            val desiredRecurringById = desiredRecurringEvents.associateBy { it.id }

            val parentEventsById = (activeEvents + archivedEvents)
                .filter { it.isRecurringParent && it.recurringSeriesKey?.startsWith(recurringSeriesPrefix) == true }
                .associateBy { it.id }
            val masterIdBySeriesKey = recurringSupport.buildMasterIdBySeriesKey(
                calendarId,
                mappingBySystem,
                recurringSeries
            )

            desiredRecurringEvents.forEach { incomingEvent ->
                val existingEvent = activeRecurringById[incomingEvent.id]
                if (existingEvent == null) {
                    onEventAdded(incomingEvent)
                    addedCount++
                } else {
                    val mergedEvent = CalendarSyncManagerV2RecurringUtils.mergeRecurringEvent(existingEvent, incomingEvent)
                    if (mergedEvent != existingEvent) {
                        onEventUpdated(mergedEvent.copy(lastModified = System.currentTimeMillis()))
                        updatedCount++
                    }
                }
            }

            activeRecurringEvents.forEach { existingEvent ->
                val shouldDelete = if (existingEvent.isRecurringParent) {
                    existingEvent.id !in desiredRecurringById
                } else {
                    existingEvent.id !in desiredRecurringById &&
                        CalendarSyncManagerV2RecurringUtils.isWithinSyncWindow(
                            existingEvent,
                            syncWindowStart,
                            syncWindowEnd
                        )
                }

                if (shouldDelete) {
                    onEventDeleted(existingEvent.id)
                    deletedCount++
                }
            }

            recurringSupport.recordMissingRecurringInstances(
                activeRecurringEvents = activeRecurringEvents,
                recurringInstances = recurringInstances,
                parentEventsById = parentEventsById,
                masterIdBySeriesKey = masterIdBySeriesKey,
                syncWindowStart = syncWindowStart,
                syncWindowEnd = syncWindowEnd,
                isWithinSyncWindow = CalendarSyncManagerV2RecurringUtils::isWithinSyncWindow,
                onEventUpdated = onEventUpdated
            )

            recurringSupport.syncExternalRecurringSeriesToRoom(
                recurringSeries = recurringSeries,
                recurringInstances = recurringInstances
            )
        } else {
            activeRecurringEvents.forEach { existingEvent ->
                onEventDeleted(existingEvent.id)
                deletedCount++
            }
        }

        val updatedSyncData = if (hasChanges) {
            syncData.copy(mapping = syncMapping)
        } else {
            syncData
        }

        return ReverseSyncResult(updatedSyncData, hasChanges, addedCount, updatedCount, deletedCount)
    }

    private fun resolveSourceCalendarIds(syncData: SyncData): List<Long> {
        val configured = syncData.sourceCalendarIds
            .asSequence()
            .filter { it != -1L }
            .distinct()
            .toList()

        if (configured.isNotEmpty()) {
            return configured
        }

        return syncData.targetCalendarId
            .takeIf { it != -1L }
            ?.let(::listOf)
            ?: emptyList()
    }

    private data class ReverseSyncResult(
        val syncData: SyncData,
        val hasChanges: Boolean,
        val addedCount: Int,
        val updatedCount: Int,
        val deletedCount: Int
    )

    private suspend fun buildRecurringEvents(
        calendarId: Long,
        recurringSeries: List<CalendarManager.SystemEventInfo>,
        recurringInstances: List<CalendarManager.SystemEventInfo>,
        existingRecurringParents: Map<String, MyEvent>,
        now: Long
    ): List<MyEvent> {
        val desiredEvents = mutableListOf<MyEvent>()
        val recurringInstancesBySeries = recurringInstances
            .filter { it.isRecurring && !it.seriesKey.isNullOrBlank() && !it.isManaged }
            .groupBy { it.seriesKey!! }

        recurringSeries
            .filter { it.isRecurring && !it.seriesKey.isNullOrBlank() && !it.isManaged }
            .forEach { seriesEvent ->
                val seriesKey = seriesEvent.seriesKey ?: return@forEach
                val instances = recurringInstancesBySeries[seriesKey].orEmpty()
                val parentId = RecurringEventUtils.buildParentId(seriesKey)
                val existingParent = existingRecurringParents[parentId]
                val excludedKeys = existingParent?.excludedRecurringInstances.orEmpty().toSet()

                val childEvents = instances
                    .sortedBy { it.startMillis }
                    .distinctBy { it.instanceKey }
                    .filter { it.instanceKey !in excludedKeys }
                    .mapNotNull { CalendarEventMapper.mapSystemEventToMyEvent(it) }

                val nextSystemInstance = calendarManager.queryNextRecurringInstance(
                    calendarId = calendarId,
                    eventId = seriesEvent.eventId,
                    seriesKey = seriesKey,
                    fromMillis = now,
                    recurringRule = seriesEvent.recurringRule,
                    excludedInstanceKeys = excludedKeys
                )

                val currentFallbackEvent = childEvents
                    .mapNotNull { child ->
                        val startMillis = RecurringEventUtils.eventStartMillis(child) ?: return@mapNotNull null
                        val endMillis = RecurringEventUtils.eventEndMillis(child) ?: return@mapNotNull null
                        if (endMillis > now) child to startMillis else null
                    }
                    .minByOrNull { (_, startMillis) -> startMillis }
                    ?.first

                val parentSourceEvent = nextSystemInstance?.let { CalendarEventMapper.mapSystemEventToMyEvent(it) }
                    ?: currentFallbackEvent
                    ?: return@forEach

                val parentEvent = parentSourceEvent.copy(
                    id = parentId,
                    reminders = emptyList(),
                    isRecurring = true,
                    isRecurringParent = true,
                    recurringSeriesKey = seriesKey,
                    recurringInstanceKey = nextSystemInstance?.instanceKey ?: parentSourceEvent.recurringInstanceKey,
                    parentRecurringId = null,
                    excludedRecurringInstances = existingParent?.excludedRecurringInstances ?: emptyList(),
                    nextOccurrenceStartMillis = nextSystemInstance?.startMillis ?: RecurringEventUtils.eventStartMillis(parentSourceEvent),
                    skipCalendarSync = true
                )

                desiredEvents.add(parentEvent)
                desiredEvents.addAll(childEvents)
            }

        return desiredEvents
    }

    private suspend fun syncEvents(
        events: List<MyEvent>,
        calendarId: Long,
        calendarMeta: CalendarSyncCalendarMeta,
        syncData: SyncData
    ): SyncData {
        val eventsToSync = events.filter {
            !it.skipCalendarSync && !it.isRecurring && it.tag != EventTags.NOTE
        }
        val eventsById = events.associateBy { it.id }
        val existingMaps = syncMapDao.getByCalendarId(calendarId).associateBy { it.localMasterId }
        val seenIds = mutableSetOf<String>()

        val syncMapping = syncData.mapping.toMutableMap()
        var mappingChanged = false

        eventsToSync.forEach { event ->
            val lastSyncHash = CalendarSyncManagerV2Hashing.computeEventHash(event, zoneId)
            val mapping = existingMaps[event.id]

            if (mapping != null) {
                if (mapping.lastSyncHash != lastSyncHash) {
                    val updated = calendarManager.updateEvent(
                        eventId = mapping.systemEventId,
                        event = event,
                        calendarId = calendarId
                    )
                    if (updated) {
                        syncMapDao.update(mapping.copy(lastSyncHash = lastSyncHash))
                    }
                }
                if (syncMapping[event.id] != mapping.systemEventId.toString()) {
                    syncMapping[event.id] = mapping.systemEventId.toString()
                    mappingChanged = true
                }
            } else {
                val systemEventId = calendarManager.createEvent(event, calendarId)
                if (systemEventId != -1L) {
                    syncMapDao.insert(
                        CalendarSyncMapEntity(
                            localMasterId = event.id,
                            systemEventId = systemEventId,
                            calendarId = calendarId,
                            accountName = calendarMeta.accountName,
                            accountType = calendarMeta.accountType,
                            displayName = calendarMeta.displayName,
                            lastSyncHash = lastSyncHash
                        )
                    )
                    if (syncMapping[event.id] != systemEventId.toString()) {
                        syncMapping[event.id] = systemEventId.toString()
                        mappingChanged = true
                    }
                }
            }

            seenIds.add(event.id)
        }

        val staleIds = mutableListOf<String>()
        existingMaps.keys.forEach { localId ->
            if (seenIds.contains(localId)) return@forEach
            val localEvent = eventsById[localId]
            when {
                localEvent != null && !localEvent.skipCalendarSync -> staleIds += localId
                localEvent == null && masterDao.getById(localId) == null -> staleIds += localId
            }
        }
        staleIds.forEach { localId ->
            val mapping = existingMaps[localId] ?: return@forEach
            calendarManager.deleteEvent(mapping.systemEventId)
            syncMapDao.deleteByLocalMasterId(localId)
            if (syncMapping.remove(localId) != null) {
                mappingChanged = true
            }
        }

        return if (mappingChanged) {
            syncData.copy(mapping = syncMapping)
        } else {
            syncData
        }
    }

    private suspend fun syncRecurringMasters(
        calendarId: Long,
        calendarMeta: CalendarSyncCalendarMeta,
        syncData: SyncData
    ): SyncData {
        val recurringMasters = masterDao.getRecurringMasters()
        if (recurringMasters.isEmpty()) {
            return recurringSupport.cleanupStaleRecurringMappings(calendarId, emptySet(), syncData)
        }

        val recurringMasterIds = recurringMasters.map { it.masterId }.toSet()
        val existingMaps = syncMapDao.getByCalendarId(calendarId).associateBy { it.localMasterId }
        val syncMapping = syncData.mapping.toMutableMap()
        var mappingChanged = false

        recurringMasters.forEach { master ->
            val instance = instanceDao.getFirstInstanceByMasterId(master.masterId) ?: return@forEach
            val excludedStartTimes = excludedDateDao.getStartTimesByMasterId(master.masterId)
            val tag = CalendarSyncManagerV2Hashing.normalizeTag(master.ruleId)
            val lastSyncHash = CalendarSyncManagerV2Hashing.computeRecurringSyncHash(master, instance, excludedStartTimes)
            val mapping = existingMaps[master.masterId]

            if (mapping != null) {
                if (mapping.lastSyncHash != lastSyncHash) {
                    val updated = calendarManager.updateRecurringEvent(
                        eventId = mapping.systemEventId,
                        master = master,
                        instance = instance,
                        tag = tag,
                        excludedStartTimes = excludedStartTimes,
                        calendarId = calendarId
                    )
                    if (updated) {
                        syncMapDao.update(mapping.copy(lastSyncHash = lastSyncHash))
                    } else {
                        val newEventId = calendarManager.createRecurringEvent(
                            master = master,
                            instance = instance,
                            tag = tag,
                            excludedStartTimes = excludedStartTimes,
                            calendarId = calendarId
                        )
                        if (newEventId != -1L) {
                            syncMapDao.update(
                                mapping.copy(
                                    systemEventId = newEventId,
                                    lastSyncHash = lastSyncHash
                                )
                            )
                            if (syncMapping[master.masterId] != newEventId.toString()) {
                                syncMapping[master.masterId] = newEventId.toString()
                                mappingChanged = true
                            }
                        }
                    }
                }
                if (syncMapping[master.masterId] != mapping.systemEventId.toString()) {
                    syncMapping[master.masterId] = mapping.systemEventId.toString()
                    mappingChanged = true
                }
            } else {
                val systemEventId = calendarManager.createRecurringEvent(
                    master = master,
                    instance = instance,
                    tag = tag,
                    excludedStartTimes = excludedStartTimes,
                    calendarId = calendarId
                )
                if (systemEventId != -1L) {
                    syncMapDao.insert(
                        CalendarSyncMapEntity(
                            localMasterId = master.masterId,
                            systemEventId = systemEventId,
                            calendarId = calendarId,
                            accountName = calendarMeta.accountName,
                            accountType = calendarMeta.accountType,
                            displayName = calendarMeta.displayName,
                            lastSyncHash = lastSyncHash
                        )
                    )
                    if (syncMapping[master.masterId] != systemEventId.toString()) {
                        syncMapping[master.masterId] = systemEventId.toString()
                        mappingChanged = true
                    }
                }
            }
        }

        val syncDataWithMapping = if (mappingChanged) {
            syncData.copy(mapping = syncMapping)
        } else {
            syncData
        }

        return recurringSupport.cleanupStaleRecurringMappings(
            calendarId,
            recurringMasterIds,
            syncDataWithMapping
        )
    }


}
