package com.antgskds.calendarassistant.core.calendar

import androidx.room.withTransaction
import com.antgskds.calendarassistant.core.rule.RuleActionDefaults
import com.antgskds.calendarassistant.data.db.AppDatabase
import com.antgskds.calendarassistant.data.db.dao.CalendarSyncMapDao
import com.antgskds.calendarassistant.data.db.dao.EventExcludedDateDao
import com.antgskds.calendarassistant.data.db.dao.EventInstanceDao
import com.antgskds.calendarassistant.data.db.dao.EventMasterDao
import com.antgskds.calendarassistant.data.db.entity.CalendarSyncMapEntity
import com.antgskds.calendarassistant.data.db.entity.EventExcludedDateEntity
import com.antgskds.calendarassistant.data.db.entity.EventInstanceEntity
import com.antgskds.calendarassistant.data.db.entity.EventMasterEntity
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.data.model.SyncData

internal class CalendarSyncManagerV2RecurringSupport(
    private val calendarManager: CalendarManager,
    private val database: AppDatabase,
    private val syncMapDao: CalendarSyncMapDao,
    private val masterDao: EventMasterDao,
    private val instanceDao: EventInstanceDao,
    private val excludedDateDao: EventExcludedDateDao,
    private val defaultSyncColor: Int,
    private val sourceSystemRecurring: String
) {
    suspend fun cleanupStaleRecurringMappings(
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

    suspend fun recordMissingRecurringInstances(
        activeRecurringEvents: List<MyEvent>,
        recurringInstances: List<CalendarManager.SystemEventInfo>,
        parentEventsById: Map<String, MyEvent>,
        masterIdBySeriesKey: Map<String, String>,
        syncWindowStart: Long,
        syncWindowEnd: Long,
        isWithinSyncWindow: (MyEvent, Long, Long) -> Boolean,
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
                val startMillis = CalendarSyncManagerV2Hashing.parseInstanceStartMillis(instanceKey, seriesKey)
                    ?: return@forEach

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

    fun buildMasterIdBySeriesKey(
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

    suspend fun syncExternalRecurringSeriesToRoom(
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
            val ruleId = CalendarSyncManagerV2Hashing.resolveRuleIdFromSystem(seriesEvent)
            val stateId = RuleActionDefaults.stateId(ruleId, RuleActionDefaults.STATE_PENDING)
            seriesKeys.add(seriesKey)

            masters.add(
                EventMasterEntity(
                    masterId = seriesKey,
                    ruleId = ruleId,
                    title = seriesEvent.title,
                    description = seriesEvent.description,
                    location = seriesEvent.location,
                    colorArgb = seriesEvent.color ?: defaultSyncColor,
                    rrule = seriesEvent.recurringRule,
                    syncId = null,
                    remindersJson = "[]",
                    isImportant = false,
                    sourceImagePath = null,
                    skipCalendarSync = true,
                    createdAt = now,
                    updatedAt = now,
                    source = sourceSystemRecurring
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
                        syncFingerprint = CalendarSyncManagerV2Hashing.buildSyncFingerprint(
                            seriesKey,
                            instanceEvent.startMillis,
                            instanceEvent.endMillis
                        ),
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

            val existingExternalMasters = masterDao.getBySource(sourceSystemRecurring)
            val staleMasters = existingExternalMasters.filter { it.masterId !in seriesKeys }
            if (staleMasters.isNotEmpty()) {
                val staleIds = staleMasters.map { it.masterId }
                staleIds.forEach { masterId -> instanceDao.deleteByMasterId(masterId) }
                masterDao.deleteAll(staleIds)
                excludedDateDao.deleteByMasterIds(staleIds)
            }
        }
    }

    private suspend fun markInstanceCancelled(instanceId: String) {
        val instance = instanceDao.getById(instanceId) ?: return
        if (!instance.isCancelled) {
            instanceDao.update(instance.copy(isCancelled = true))
        }
    }
}
