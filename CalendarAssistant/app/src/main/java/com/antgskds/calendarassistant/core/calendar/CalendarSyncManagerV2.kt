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
import com.antgskds.calendarassistant.data.model.Course
import com.antgskds.calendarassistant.data.model.EventType
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.data.model.SyncData
import com.antgskds.calendarassistant.data.model.TimeNode
import com.antgskds.calendarassistant.data.source.SyncJsonDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class CalendarSyncManagerV2(private val context: Context) {

    companion object {
        private const val TAG = "CalendarSyncManagerV2"
        private const val SYNC_LOOK_BACK_DAYS = 30L
        private const val SYNC_LOOK_AHEAD_DAYS = 30L
        private const val COURSE_SYNC_WEEKS_AHEAD = 16
        private const val RECURRING_INSTANCES_SYNC_LIMIT = 2000
        private const val SOURCE_SYSTEM_RECURRING = "system_recurring"
        private const val DEFAULT_SYNC_COLOR = 0xFFA2B5BB.toInt()
    }

    private val calendarManager = CalendarManager(context)
    private val syncDataSource = SyncJsonDataSource.getInstance(context)
    private val database = AppDatabase.getInstance(context.applicationContext)
    private val syncMapDao = database.calendarSyncMapDao()
    private val masterDao = database.eventMasterDao()
    private val instanceDao = database.eventInstanceDao()
    private val excludedDateDao = database.eventExcludedDateDao()
    private val zoneId = ZoneId.systemDefault()

    suspend fun syncAllToCalendar(
        events: List<MyEvent>,
        courses: List<Course>,
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

            val calendarResult = resolveTargetCalendar(syncData) ?: return@withContext Result.failure(
                Exception("Unable to resolve calendar ID")
            )
            var calendarId = calendarResult.first
            syncData = calendarResult.second

            val calendarMeta = loadCalendarMeta(calendarId)
            val eventsById = events.associateBy { it.id }
            syncData = ensureSyncMapSeeded(syncData, calendarId, calendarMeta, eventsById)
            syncData = seedExistingCalendarMappings(syncData, calendarId, calendarMeta, eventsById)

            syncData = syncCourses(courses, semesterStart, totalWeeks, timeNodes, calendarId, syncData)
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
        try {
            if (!CalendarPermissionHelper.hasAllPermissions(context)) {
                return@withContext Result.failure(SecurityException("Missing calendar permissions"))
            }

            var syncData = syncDataSource.loadSyncData()
            if (!syncData.isSyncEnabled) {
                Log.d(TAG, "Sync disabled, skip")
                return@withContext Result.success(0)
            }

            val calendarId = syncData.targetCalendarId
            if (calendarId == -1L) {
                return@withContext Result.failure(Exception("Target calendar not configured"))
            }

            val calendarMeta = loadCalendarMeta(calendarId)
            val eventsById = (activeEvents + archivedEvents).associateBy { it.id }
            val seededSyncData = ensureSyncMapSeeded(syncData, calendarId, calendarMeta, eventsById)

            val existingMappings = syncMapDao.getByCalendarId(calendarId)
            val mappingByLocal = existingMappings.associateBy { it.localMasterId }
            val mappingBySystem = existingMappings.associateBy { it.systemEventId }

            var hasChanges = seededSyncData.mapping != syncData.mapping
            syncData = seededSyncData
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
            val fingerprintToSystemEvent = rangeEvents
                .filter { !it.isRecurring }
                .associateBy { EventDeduplicator.generateFingerprintFromSystemEvent(it) }

            val mappedSystemIds = existingMappings.map { it.systemEventId }.toMutableSet()
            val existingSystemEvents = calendarManager.queryEventsByIds(mappedSystemIds, calendarId)
            val foundSystemIds = existingSystemEvents.map { it.eventId }.toSet()

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
                    Log.d(TAG, "System calendar empty, skip reverse sync")
                    return@withContext Result.success(0)
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
                            lastSyncHash = computeLastSyncHash(remapCandidate)
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

                val systemHash = computeLastSyncHash(systemEvent)
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
                .filter { it.eventType == EventType.EVENT }
                .associateBy { EventDeduplicator.generateFingerprint(it) }
            val fingerprintToArchived = archivedEvents
                .filter { it.eventType == EventType.EVENT }
                .associateBy { EventDeduplicator.generateFingerprint(it) }

            rangeEvents.forEach { systemEvent ->
                if (systemEvent.isRecurring) return@forEach
                if (mappedSystemIds.contains(systemEvent.eventId)) return@forEach
                if (systemEvent.isManaged) return@forEach

                val systemFingerprint = EventDeduplicator.generateFingerprintFromSystemEvent(systemEvent)
                val duplicateActive = fingerprintToActive[systemFingerprint]
                val duplicateArchived = fingerprintToArchived[systemFingerprint]

                if (duplicateActive != null) {
                    syncMapDao.insert(
                        CalendarSyncMapEntity(
                            localMasterId = duplicateActive.id,
                            systemEventId = systemEvent.eventId,
                            calendarId = calendarId,
                            accountName = calendarMeta.accountName,
                            accountType = calendarMeta.accountType,
                            displayName = calendarMeta.displayName,
                            lastSyncHash = computeLastSyncHash(systemEvent)
                        )
                    )
                    if (syncMapping[duplicateActive.id] != systemEvent.eventId.toString()) {
                        syncMapping[duplicateActive.id] = systemEvent.eventId.toString()
                        hasChanges = true
                    }
                    return@forEach
                }

                if (duplicateArchived != null) {
                    return@forEach
                }

                val myEvent = CalendarEventMapper.mapSystemEventToMyEvent(systemEvent)
                if (myEvent != null) {
                    onEventAdded(myEvent)
                    syncMapDao.insert(
                        CalendarSyncMapEntity(
                            localMasterId = myEvent.id,
                            systemEventId = systemEvent.eventId,
                            calendarId = calendarId,
                            accountName = calendarMeta.accountName,
                            accountType = calendarMeta.accountType,
                            displayName = calendarMeta.displayName,
                            lastSyncHash = computeLastSyncHash(systemEvent)
                        )
                    )
                    if (syncMapping[myEvent.id] != systemEvent.eventId.toString()) {
                        syncMapping[myEvent.id] = systemEvent.eventId.toString()
                        hasChanges = true
                    }
                    addedCount++
                }
            }

            val activeRecurringEvents = activeEvents.filter { it.isRecurring }

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
                    return@withContext Result.failure(RecurringSyncLimitException(message))
                }
                val recurringInstances = recurringInstancesResult.events

                val recurringParents = (activeEvents + archivedEvents)
                    .filter { it.isRecurring && it.isRecurringParent && !it.recurringSeriesKey.isNullOrBlank() }
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
                    .filter { it.isRecurringParent }
                    .associateBy { it.id }
                val masterIdBySeriesKey = buildMasterIdBySeriesKey(calendarId, mappingBySystem, recurringSeries)

                desiredRecurringEvents.forEach { incomingEvent ->
                    val existingEvent = activeRecurringById[incomingEvent.id]
                    if (existingEvent == null) {
                        onEventAdded(incomingEvent)
                        addedCount++
                    } else {
                        val mergedEvent = mergeRecurringEvent(existingEvent, incomingEvent)
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
                            isWithinSyncWindow(existingEvent, syncWindowStart, syncWindowEnd)
                    }

                    if (shouldDelete) {
                        onEventDeleted(existingEvent.id)
                        deletedCount++
                    }
                }

                recordMissingRecurringInstances(
                    activeRecurringEvents = activeRecurringEvents,
                    recurringInstances = recurringInstances,
                    parentEventsById = parentEventsById,
                    masterIdBySeriesKey = masterIdBySeriesKey,
                    syncWindowStart = syncWindowStart,
                    syncWindowEnd = syncWindowEnd,
                    onEventUpdated = onEventUpdated
                )

                syncExternalRecurringSeriesToRoom(
                    recurringSeries = recurringSeries,
                    recurringInstances = recurringInstances
                )
            } else {
                activeRecurringEvents.forEach { existingEvent ->
                    onEventDeleted(existingEvent.id)
                    deletedCount++
                }
            }

            if (hasChanges) {
                syncDataSource.saveSyncData(
                    syncData.copy(
                        mapping = syncMapping,
                        lastSyncTime = System.currentTimeMillis()
                    )
                )
            }

            Log.d(TAG, "Reverse sync done: +$addedCount, ~$updatedCount, -$deletedCount")
            Result.success(addedCount + updatedCount + deletedCount)
        } catch (e: Exception) {
            Log.e(TAG, "Reverse sync failed", e)
            Result.failure(e)
        }
    }

    suspend fun syncEventToCalendar(event: MyEvent): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (event.skipCalendarSync || event.isRecurring) {
                Log.d(TAG, "Skip single sync for readonly/recurring event id=${event.id}")
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

            val calendarMeta = loadCalendarMeta(calendarId)
            syncData = ensureSyncMapSeeded(syncData, calendarId, calendarMeta, mapOf(event.id to event))

            val mapping = syncMapDao.getByLocalMasterId(event.id)
            if (mapping == null) {
                Log.w(TAG, "Single sync mapping missing: ${event.id}")
                return@withContext Result.success(Unit)
            }

            val updated = calendarManager.updateEvent(
                eventId = mapping.systemEventId,
                event = event,
                calendarId = calendarId
            )
            if (updated) {
                val lastSyncHash = computeLastSyncHash(event)
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
                mapping = existingSyncData.mapping,
                lastSyncTime = System.currentTimeMillis(),
                lastSemesterHash = existingSyncData.lastSemesterHash
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
        val mappedCount = if (syncData.targetCalendarId == -1L) {
            0
        } else {
            syncMapDao.getByCalendarId(syncData.targetCalendarId).size
        }

        SyncStatus(
            isEnabled = syncData.isSyncEnabled,
            hasPermission = hasPermission,
            targetCalendarId = syncData.targetCalendarId,
            lastSyncTime = syncData.lastSyncTime,
            mappedEventCount = mappedCount
        )
    }

    data class SyncStatus(
        val isEnabled: Boolean,
        val hasPermission: Boolean,
        val targetCalendarId: Long,
        val lastSyncTime: Long,
        val mappedEventCount: Int
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

    private fun mergeRecurringEvent(existingEvent: MyEvent, incomingEvent: MyEvent): MyEvent {
        return existingEvent.copy(
            title = incomingEvent.title,
            startDate = incomingEvent.startDate,
            endDate = incomingEvent.endDate,
            startTime = incomingEvent.startTime,
            endTime = incomingEvent.endTime,
            location = incomingEvent.location,
            description = incomingEvent.description,
            eventType = incomingEvent.eventType,
            tag = incomingEvent.tag,
            isRecurring = incomingEvent.isRecurring,
            isRecurringParent = incomingEvent.isRecurringParent,
            recurringSeriesKey = incomingEvent.recurringSeriesKey,
            recurringInstanceKey = incomingEvent.recurringInstanceKey,
            parentRecurringId = incomingEvent.parentRecurringId,
            nextOccurrenceStartMillis = incomingEvent.nextOccurrenceStartMillis,
            excludedRecurringInstances = if (existingEvent.isRecurringParent) {
                existingEvent.excludedRecurringInstances
            } else {
                incomingEvent.excludedRecurringInstances
            },
            skipCalendarSync = true
        )
    }

    private fun isWithinSyncWindow(event: MyEvent, syncWindowStart: Long, syncWindowEnd: Long): Boolean {
        val effectiveStart = if (event.isRecurringParent) {
            event.nextOccurrenceStartMillis ?: RecurringEventUtils.eventStartMillis(event)
        } else {
            RecurringEventUtils.eventStartMillis(event)
        }
        val effectiveEnd = if (event.isRecurringParent) {
            effectiveStart ?: RecurringEventUtils.eventEndMillis(event)
        } else {
            RecurringEventUtils.eventEndMillis(event)
        }

        if (effectiveStart == null || effectiveEnd == null) return false
        return effectiveEnd > syncWindowStart && effectiveStart < syncWindowEnd
    }

    private suspend fun syncCourses(
        courses: List<Course>,
        semesterStart: String?,
        totalWeeks: Int,
        timeNodes: List<TimeNode>,
        calendarId: Long,
        syncData: SyncData
    ): SyncData {
        val parsedSemesterStart = try {
            LocalDate.parse(semesterStart)
        } catch (_: Exception) {
            LocalDate.now()
        }

        val currentSemesterHash = CalendarEventMapper.generateSemesterHash(
            parsedSemesterStart,
            totalWeeks
        )
        val needRebuild = syncData.lastSemesterHash != currentSemesterHash

        if (needRebuild) {
            val deletedCount = calendarManager.batchDeleteManagedCourseEvents(calendarId)
            Log.d(TAG, "Deleted $deletedCount managed course events")

            val instances = CalendarEventMapper.expandAllCourses(
                courses = courses,
                semesterStart = parsedSemesterStart,
                totalWeeks = totalWeeks
            )

            val today = LocalDate.now()
            val weeksAheadDate = today.plusWeeks(COURSE_SYNC_WEEKS_AHEAD.toLong())
            val futureInstances = instances.filter { it.date <= weeksAheadDate }

            if (futureInstances.isNotEmpty()) {
                calendarManager.batchCreateCourseEvents(
                    courseEvents = futureInstances,
                    calendarId = calendarId,
                    timeNodes = timeNodes
                )
            }
        }

        return syncData.copy(lastSemesterHash = currentSemesterHash)
    }

    private suspend fun syncEvents(
        events: List<MyEvent>,
        calendarId: Long,
        calendarMeta: CalendarMeta,
        syncData: SyncData
    ): SyncData {
        val eventsToSync = events.filter {
            it.eventType == EventType.EVENT && !it.skipCalendarSync && !it.isRecurring
        }
        val existingMaps = syncMapDao.getByCalendarId(calendarId).associateBy { it.localMasterId }
        val seenIds = mutableSetOf<String>()

        val syncMapping = syncData.mapping.toMutableMap()
        var mappingChanged = false

        eventsToSync.forEach { event ->
            val lastSyncHash = computeLastSyncHash(event)
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

        val staleIds = existingMaps.keys - seenIds
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
        calendarMeta: CalendarMeta,
        syncData: SyncData
    ): SyncData {
        val recurringMasters = masterDao.getRecurringMasters(EventType.EVENT)
        if (recurringMasters.isEmpty()) {
            return cleanupStaleRecurringMappings(calendarId, emptySet(), syncData)
        }

        val recurringMasterIds = recurringMasters.map { it.masterId }.toSet()
        val existingMaps = syncMapDao.getByCalendarId(calendarId).associateBy { it.localMasterId }
        val syncMapping = syncData.mapping.toMutableMap()
        var mappingChanged = false

        recurringMasters.forEach { master ->
            val instance = instanceDao.getFirstInstanceByMasterId(master.masterId) ?: return@forEach
            val excludedStartTimes = excludedDateDao.getStartTimesByMasterId(master.masterId)
            val tag = normalizeTag(master.ruleId, master.eventType)
            val lastSyncHash = computeRecurringSyncHash(master, instance, excludedStartTimes)
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

        return cleanupStaleRecurringMappings(calendarId, recurringMasterIds, syncDataWithMapping)
    }

    private fun computeLastSyncHash(event: MyEvent): Int {
        val startMillis = toEpochMillis(event.startDate, event.startTime)
        val endMillis = toEpochMillis(event.endDate, event.endTime)
        return computeLastSyncHash(
            title = event.title,
            startMillis = startMillis,
            endMillis = endMillis,
            location = event.location,
            description = event.description,
            rrule = ""
        )
    }

    private fun computeRecurringSyncHash(
        master: EventMasterEntity,
        instance: EventInstanceEntity,
        excludedStartTimes: List<Long>
    ): Int {
        val exDatePayload = excludedStartTimes.sorted().joinToString(",")
        val rrulePayload = listOf(master.rrule.orEmpty(), exDatePayload).joinToString("|")
        return computeLastSyncHash(
            title = master.title,
            startMillis = instance.startTime,
            endMillis = instance.endTime,
            location = master.location,
            description = master.description,
            rrule = rrulePayload
        )
    }

    private fun computeRecurringBaseHash(
        master: EventMasterEntity,
        instance: EventInstanceEntity
    ): Int {
        return computeLastSyncHash(
            title = master.title,
            startMillis = instance.startTime,
            endMillis = instance.endTime,
            location = master.location,
            description = master.description,
            rrule = master.rrule.orEmpty()
        )
    }

    private suspend fun cleanupStaleRecurringMappings(
        calendarId: Long,
        recurringMasterIds: Set<String>,
        syncData: SyncData
    ): SyncData {
        val existingMaps = syncMapDao.getByCalendarId(calendarId)
        if (existingMaps.isEmpty()) return syncData

        val candidates = existingMaps.filter { it.localMasterId !in recurringMasterIds }
        if (candidates.isEmpty()) return syncData

        val systemEvents = calendarManager.queryEventsByIds(
            candidates.map { it.systemEventId },
            calendarId
        )
        val recurringSystemIds = systemEvents.filter { it.isRecurring }.map { it.eventId }.toSet()
        if (recurringSystemIds.isEmpty()) return syncData

        val updatedMapping = syncData.mapping.toMutableMap()
        var mappingChanged = false

        candidates.forEach { mapping ->
            if (!recurringSystemIds.contains(mapping.systemEventId)) return@forEach
            val masterExists = masterDao.getById(mapping.localMasterId) != null
            if (masterExists) return@forEach
            calendarManager.deleteEvent(mapping.systemEventId)
            syncMapDao.deleteByLocalMasterId(mapping.localMasterId)
            if (updatedMapping.remove(mapping.localMasterId) != null) {
                mappingChanged = true
            }
        }

        return if (mappingChanged) {
            syncData.copy(mapping = updatedMapping)
        } else {
            syncData
        }
    }

    private suspend fun recordMissingRecurringInstances(
        activeRecurringEvents: List<MyEvent>,
        recurringInstances: List<CalendarManager.SystemEventInfo>,
        parentEventsById: Map<String, MyEvent>,
        masterIdBySeriesKey: Map<String, String>,
        syncWindowStart: Long,
        syncWindowEnd: Long,
        onEventUpdated: suspend (MyEvent) -> Unit
    ) {
        val systemInstanceKeys = recurringInstances.mapNotNull { it.instanceKey }.toSet()
        if (systemInstanceKeys.isEmpty()) return

        val pendingParentUpdates = mutableMapOf<String, MutableSet<String>>()
        val excludedCache = mutableMapOf<String, MutableSet<Long>>()
        val cancelledInstances = mutableSetOf<String>()

        activeRecurringEvents
            .filter { it.isRecurring && !it.isRecurringParent }
            .forEach { localInstance ->
                val instanceKey = localInstance.recurringInstanceKey ?: return@forEach
                if (systemInstanceKeys.contains(instanceKey)) return@forEach
                if (!isWithinSyncWindow(localInstance, syncWindowStart, syncWindowEnd)) return@forEach

                if (cancelledInstances.add(localInstance.id)) {
                    markInstanceCancelled(localInstance.id)
                }

                val seriesKey = localInstance.recurringSeriesKey ?: return@forEach
                val masterId = masterIdBySeriesKey[seriesKey] ?: return@forEach
                val startMillis = parseInstanceStartMillis(instanceKey, seriesKey) ?: return@forEach

                val existing = excludedCache.getOrPut(masterId) {
                    excludedDateDao.getStartTimesByMasterId(masterId).toMutableSet()
                }
                if (existing.add(startMillis)) {
                    excludedDateDao.insert(
                        EventExcludedDateEntity(
                            excludedId = "${masterId}_$startMillis",
                            masterId = masterId,
                            excludedStartTime = startMillis
                        )
                    )
                }

                val parentId = RecurringEventUtils.buildParentId(seriesKey)
                pendingParentUpdates.getOrPut(parentId) { mutableSetOf() }.add(instanceKey)
            }

        pendingParentUpdates.forEach { (parentId, keys) ->
            val parent = parentEventsById[parentId] ?: return@forEach
            val newKeys = keys.filter { it !in parent.excludedRecurringInstances }
            if (newKeys.isEmpty()) return@forEach
            val updated = parent.copy(
                excludedRecurringInstances = (parent.excludedRecurringInstances + newKeys).distinct(),
                lastModified = System.currentTimeMillis()
            )
            onEventUpdated(updated)
        }
    }

    private suspend fun markInstanceCancelled(instanceId: String) {
        val instance = instanceDao.getById(instanceId) ?: return
        if (!instance.isCancelled) {
            instanceDao.update(instance.copy(isCancelled = true))
        }
    }

    private fun parseInstanceStartMillis(instanceKey: String, seriesKey: String): Long? {
        val prefix = "${seriesKey}_"
        if (!instanceKey.startsWith(prefix)) return null
        return instanceKey.removePrefix(prefix).toLongOrNull()
    }

    private fun buildMasterIdBySeriesKey(
        calendarId: Long,
        mappingBySystem: Map<Long, CalendarSyncMapEntity>,
        recurringSeries: List<CalendarManager.SystemEventInfo>
    ): Map<String, String> {
        val mapped = mappingBySystem.mapNotNull { (systemId, mapping) ->
            val masterId = mapping.localMasterId
            if (masterId.isBlank()) {
                null
            } else {
                RecurringEventUtils.buildSeriesKey(calendarId, systemId) to masterId
            }
        }.toMap()

        val external = recurringSeries
            .filter { it.isRecurring && !it.seriesKey.isNullOrBlank() && !it.isManaged }
            .associate { it.seriesKey!! to it.seriesKey!! }

        return mapped + external
    }

    private suspend fun syncExternalRecurringSeriesToRoom(
        recurringSeries: List<CalendarManager.SystemEventInfo>,
        recurringInstances: List<CalendarManager.SystemEventInfo>
    ) {
        val seriesList = recurringSeries
            .filter { it.isRecurring && !it.seriesKey.isNullOrBlank() && !it.isManaged }
        if (seriesList.isEmpty()) return

        val instancesBySeries = recurringInstances
            .filter { it.isRecurring && !it.seriesKey.isNullOrBlank() && !it.isManaged }
            .groupBy { it.seriesKey!! }

        val now = System.currentTimeMillis()
        val masters = mutableListOf<EventMasterEntity>()
        val instances = mutableListOf<EventInstanceEntity>()
        val seriesKeys = mutableSetOf<String>()

        seriesList.forEach { seriesEvent ->
            val seriesKey = seriesEvent.seriesKey ?: return@forEach
            val ruleId = resolveRuleIdFromSystem(seriesEvent)
            val stateId = RuleActionDefaults.stateId(ruleId, RuleActionDefaults.STATE_PENDING)
            seriesKeys.add(seriesKey)

            masters.add(
                EventMasterEntity(
                    masterId = seriesKey,
                    ruleId = ruleId,
                    title = seriesEvent.title,
                    description = seriesEvent.description,
                    location = seriesEvent.location,
                    colorArgb = seriesEvent.color ?: DEFAULT_SYNC_COLOR,
                    rrule = seriesEvent.recurringRule,
                    syncId = null,
                    eventType = EventType.EVENT,
                    remindersJson = "[]",
                    isImportant = false,
                    sourceImagePath = null,
                    skipCalendarSync = true,
                    createdAt = now,
                    updatedAt = now,
                    source = SOURCE_SYSTEM_RECURRING
                )
            )

            val seriesInstances = instancesBySeries[seriesKey].orEmpty()
            seriesInstances.forEach { instanceEvent ->
                val instanceKey = instanceEvent.instanceKey ?: return@forEach
                val instanceId = RecurringEventUtils.buildInstanceId(instanceKey)
                instances.add(
                    EventInstanceEntity(
                        instanceId = instanceId,
                        masterId = seriesKey,
                        startTime = instanceEvent.startMillis,
                        endTime = instanceEvent.endMillis,
                        currentStateId = stateId,
                        completedAt = null,
                        archivedAt = null,
                        syncFingerprint = buildSyncFingerprint(seriesKey, instanceEvent.startMillis, instanceEvent.endMillis),
                        isSynced = false,
                        isCancelled = false
                    )
                )
            }
        }

        database.withTransaction {
            masterDao.insertAll(masters)
            instanceDao.insertAll(instances)

            seriesKeys.forEach { masterId ->
                val expectedIds = instances.filter { it.masterId == masterId }.map { it.instanceId }.toSet()
                val existingIds = instanceDao.getInstanceIdsByMasterId(masterId).toSet()
                val staleIds = existingIds - expectedIds
                if (staleIds.isNotEmpty()) {
                    instanceDao.deleteAll(staleIds.toList())
                }
            }

            val existingExternalMasters = masterDao.getBySource(SOURCE_SYSTEM_RECURRING)
            val staleMasters = existingExternalMasters.filter { it.masterId !in seriesKeys }
            if (staleMasters.isNotEmpty()) {
                val staleIds = staleMasters.map { it.masterId }
                staleIds.forEach { masterId -> instanceDao.deleteByMasterId(masterId) }
                masterDao.deleteAll(staleIds)
                excludedDateDao.deleteByMasterIds(staleIds)
            }
        }
    }

    private fun resolveRuleIdFromSystem(event: CalendarManager.SystemEventInfo): String {
        val resolved = RuleMatchingEngine.resolvePayload(event.description, null)?.ruleId
        if (!resolved.isNullOrBlank()) return resolved
        return when (event.tag) {
            EventTags.PICKUP -> RuleMatchingEngine.RULE_PICKUP
            EventTags.TRAIN -> RuleMatchingEngine.RULE_TRAIN
            EventTags.TAXI -> RuleMatchingEngine.RULE_TAXI
            else -> RuleMatchingEngine.RULE_GENERAL
        }
    }

    private fun buildSyncFingerprint(masterId: String, startMillis: Long, endMillis: Long): String {
        val source = "$masterId|$startMillis|$endMillis"
        val digest = java.security.MessageDigest.getInstance("SHA-1").digest(source.toByteArray())
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun computeLastSyncHash(systemEvent: CalendarManager.SystemEventInfo): Int {
        return computeLastSyncHash(
            title = systemEvent.title,
            startMillis = systemEvent.startMillis,
            endMillis = systemEvent.endMillis,
            location = systemEvent.location,
            description = systemEvent.description,
            rrule = systemEvent.recurringRule
        )
    }

    private fun computeLastSyncHash(
        title: String,
        startMillis: Long,
        endMillis: Long,
        location: String?,
        description: String?,
        rrule: String?
    ): Int {
        val payload = listOf(
            title.trim(),
            startMillis.toString(),
            endMillis.toString(),
            location.orEmpty().trim(),
            description.orEmpty().trim(),
            rrule.orEmpty().trim()
        ).joinToString("|")
        return payload.hashCode()
    }

    private fun toEpochMillis(date: LocalDate, timeStr: String): Long {
        return try {
            val localDateTime = LocalDateTime.of(date, LocalTime.parse(timeStr))
            localDateTime.atZone(zoneId).toInstant().toEpochMilli()
        } catch (_: Exception) {
            LocalDateTime.of(date, LocalTime.MIDNIGHT).atZone(zoneId).toInstant().toEpochMilli()
        }
    }

    private fun normalizeTag(ruleId: String?, eventType: String): String {
        if (eventType == EventType.COURSE) return EventTags.GENERAL
        return when (ruleId) {
            null, "" -> EventTags.GENERAL
            RuleMatchingEngine.RULE_GENERAL -> EventTags.GENERAL
            RuleMatchingEngine.RULE_PICKUP -> EventTags.PICKUP
            RuleMatchingEngine.RULE_TRAIN -> EventTags.TRAIN
            RuleMatchingEngine.RULE_TAXI -> EventTags.TAXI
            else -> ruleId
        }
    }

    private suspend fun ensureSyncMapSeeded(
        syncData: SyncData,
        calendarId: Long,
        calendarMeta: CalendarMeta,
        eventsById: Map<String, MyEvent>
    ): SyncData {
        val existing = syncMapDao.getByCalendarId(calendarId).associateBy { it.localMasterId }
        val updatedMapping = syncData.mapping.toMutableMap()
        var mappingChanged = false

        existing.values.forEach { mapping ->
            val systemId = mapping.systemEventId.toString()
            if (updatedMapping[mapping.localMasterId] != systemId) {
                updatedMapping[mapping.localMasterId] = systemId
                mappingChanged = true
            }
        }

        val missing = syncData.mapping.filterKeys { it !in existing }
        if (missing.isEmpty()) {
            return if (mappingChanged) {
                syncData.copy(mapping = updatedMapping)
            } else {
                syncData
            }
        }

        val toInsert = mutableListOf<CalendarSyncMapEntity>()

        missing.forEach { (localId, systemIdStr) ->
            val systemId = systemIdStr.toLongOrNull()
            if (systemId == null) {
                updatedMapping.remove(localId)
                mappingChanged = true
                return@forEach
            }
            val lastSyncHash = eventsById[localId]?.let { computeLastSyncHash(it) } ?: 0
            toInsert.add(
                CalendarSyncMapEntity(
                    localMasterId = localId,
                    systemEventId = systemId,
                    calendarId = calendarId,
                    accountName = calendarMeta.accountName,
                    accountType = calendarMeta.accountType,
                    displayName = calendarMeta.displayName,
                    lastSyncHash = lastSyncHash
                )
            )
            if (updatedMapping[localId] != systemIdStr) {
                updatedMapping[localId] = systemIdStr
                mappingChanged = true
            }
        }

        if (toInsert.isNotEmpty()) {
            syncMapDao.insertAll(toInsert)
        }

        return if (mappingChanged) {
            syncData.copy(mapping = updatedMapping)
        } else {
            syncData
        }
    }

    private suspend fun seedExistingCalendarMappings(
        syncData: SyncData,
        calendarId: Long,
        calendarMeta: CalendarMeta,
        eventsById: Map<String, MyEvent>
    ): SyncData {
        val existingMappings = syncMapDao.getByCalendarId(calendarId)
        val mappedLocalIds = existingMappings.map { it.localMasterId }.toMutableSet()
        val mappedSystemIds = existingMappings.map { it.systemEventId }.toMutableSet()
        val updatedMapping = syncData.mapping.toMutableMap()
        val seededMappings = mutableListOf<CalendarSyncMapEntity>()

        val activeSingleEventsById = eventsById.values
            .filter { it.eventType == EventType.EVENT && !it.isRecurring && !it.isRecurringParent }
            .associateBy { it.id }
        val activeSingleEventsByFingerprint = activeSingleEventsById.values.associateBy {
            EventDeduplicator.generateFingerprint(it)
        }

        val windowStart = System.currentTimeMillis() - SYNC_LOOK_BACK_DAYS * 24 * 60 * 60 * 1000
        val windowEnd = System.currentTimeMillis() + SYNC_LOOK_AHEAD_DAYS * 24 * 60 * 60 * 1000
        val systemSingles = calendarManager.queryEventsInRange(calendarId, windowStart, windowEnd)

        systemSingles.forEach { systemEvent ->
            if (mappedSystemIds.contains(systemEvent.eventId)) return@forEach

            val localEvent = resolveSeedSingleEvent(
                systemEvent = systemEvent,
                activeEventsById = activeSingleEventsById,
                activeEventsByFingerprint = activeSingleEventsByFingerprint
            ) ?: return@forEach

            if (!mappedLocalIds.add(localEvent.id)) return@forEach

            seededMappings.add(
                CalendarSyncMapEntity(
                    localMasterId = localEvent.id,
                    systemEventId = systemEvent.eventId,
                    calendarId = calendarId,
                    accountName = calendarMeta.accountName,
                    accountType = calendarMeta.accountType,
                    displayName = calendarMeta.displayName,
                    lastSyncHash = computeLastSyncHash(systemEvent)
                )
            )
            mappedSystemIds.add(systemEvent.eventId)
            updatedMapping[localEvent.id] = systemEvent.eventId.toString()
        }

        val recurringMasters = masterDao.getRecurringMasters(EventType.EVENT)
        if (recurringMasters.isNotEmpty()) {
            val recurringInstancesByMasterId = recurringMasters.mapNotNull { master ->
                instanceDao.getFirstInstanceByMasterId(master.masterId)?.let { instance ->
                    master.masterId to instance
                }
            }.toMap()
            val recurringMastersById = recurringMasters.associateBy { it.masterId }
            val recurringMastersByBaseHash = recurringMasters.mapNotNull { master ->
                val instance = recurringInstancesByMasterId[master.masterId] ?: return@mapNotNull null
                computeRecurringBaseHash(master, instance) to master
            }.toMap()

            val systemRecurringSeries = calendarManager.queryRecurringSeries(calendarId)
            systemRecurringSeries.forEach { systemSeries ->
                if (mappedSystemIds.contains(systemSeries.eventId)) return@forEach

                val localMaster = resolveSeedRecurringMaster(
                    systemSeries = systemSeries,
                    mastersById = recurringMastersById,
                    mastersByBaseHash = recurringMastersByBaseHash
                ) ?: return@forEach

                if (!mappedLocalIds.add(localMaster.masterId)) return@forEach

                seededMappings.add(
                    CalendarSyncMapEntity(
                        localMasterId = localMaster.masterId,
                        systemEventId = systemSeries.eventId,
                        calendarId = calendarId,
                        accountName = calendarMeta.accountName,
                        accountType = calendarMeta.accountType,
                        displayName = calendarMeta.displayName,
                        lastSyncHash = computeLastSyncHash(systemSeries)
                    )
                )
                mappedSystemIds.add(systemSeries.eventId)
                updatedMapping[localMaster.masterId] = systemSeries.eventId.toString()
            }
        }

        if (seededMappings.isNotEmpty()) {
            syncMapDao.insertAll(seededMappings)
        }

        return if (seededMappings.isNotEmpty() || updatedMapping != syncData.mapping) {
            syncData.copy(mapping = updatedMapping)
        } else {
            syncData
        }
    }

    private fun resolveSeedSingleEvent(
        systemEvent: CalendarManager.SystemEventInfo,
        activeEventsById: Map<String, MyEvent>,
        activeEventsByFingerprint: Map<EventFingerprint, MyEvent>
    ): MyEvent? {
        val appId = systemEvent.appId
        if (!appId.isNullOrBlank()) {
            val directMatch = activeEventsById[appId]
            if (directMatch != null) return directMatch
        }

        if (!systemEvent.isManaged) return null
        return activeEventsByFingerprint[EventDeduplicator.generateFingerprintFromSystemEvent(systemEvent)]
    }

    private fun resolveSeedRecurringMaster(
        systemSeries: CalendarManager.SystemEventInfo,
        mastersById: Map<String, EventMasterEntity>,
        mastersByBaseHash: Map<Int, EventMasterEntity>
    ): EventMasterEntity? {
        val appId = systemSeries.appId
        if (!appId.isNullOrBlank()) {
            val directMatch = mastersById[appId]
            if (directMatch != null) return directMatch
        }

        if (!systemSeries.isManaged) return null
        return mastersByBaseHash[computeLastSyncHash(systemSeries)]
    }

    private suspend fun resolveTargetCalendar(syncData: SyncData): Pair<Long, SyncData>? {
        if (syncData.targetCalendarId != -1L) {
            return syncData.targetCalendarId to syncData
        }

        val calendarId = calendarManager.getOrCreateAppCalendar()
        if (calendarId == -1L) return null

        val updated = syncData.copy(targetCalendarId = calendarId)
        syncDataSource.saveSyncData(updated)
        return calendarId to updated
    }

    private suspend fun loadCalendarMeta(calendarId: Long): CalendarMeta = withContext(Dispatchers.IO) {
        var accountName = ""
        var accountType = ""
        var displayName = "Calendar $calendarId"

        val projection = arrayOf(
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME
        )
        val selection = "${CalendarContract.Calendars._ID} = ?"
        val selectionArgs = arrayOf(calendarId.toString())

        try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val accountNameIndex = cursor.getColumnIndex(CalendarContract.Calendars.ACCOUNT_NAME)
                    val accountTypeIndex = cursor.getColumnIndex(CalendarContract.Calendars.ACCOUNT_TYPE)
                    val displayNameIndex = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)

                    if (accountNameIndex >= 0) {
                        accountName = cursor.getString(accountNameIndex) ?: ""
                    }
                    if (accountTypeIndex >= 0) {
                        accountType = cursor.getString(accountTypeIndex) ?: ""
                    }
                    if (displayNameIndex >= 0) {
                        displayName = cursor.getString(displayNameIndex) ?: displayName
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load calendar meta", e)
        }

        CalendarMeta(accountName = accountName, accountType = accountType, displayName = displayName)
    }

    private data class CalendarMeta(
        val accountName: String,
        val accountType: String,
        val displayName: String
    )
}
