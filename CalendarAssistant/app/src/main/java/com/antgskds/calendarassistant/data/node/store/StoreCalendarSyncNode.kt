package com.antgskds.calendarassistant.data.node.store

import android.util.Log
import com.antgskds.calendarassistant.core.calendar.CalendarManager
import com.antgskds.calendarassistant.core.calendar.CalendarSyncManager
import com.antgskds.calendarassistant.data.db.entity.CalendarSyncMapEntity
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.data.model.SyncData
import com.antgskds.calendarassistant.data.model.TimeNode

internal object StoreCalendarSyncNode {
    suspend fun triggerAutoSync(
        syncAllToCalendar: suspend (Boolean) -> Unit
    ) {
        try {
            syncAllToCalendar(true)
        } catch (e: Exception) {
            Log.e("StoreNode", "自动同步失败", e)
        }
    }

    suspend fun syncAllToCalendar(
        seedMapping: Boolean,
        currentSettings: () -> MySettings,
        currentEvents: () -> List<MyEvent>,
        isCalendarSyncV2Enabled: () -> Boolean,
        parseTimeTable: (String) -> List<TimeNode>,
        seedSyncMappingIfNeeded: suspend () -> Unit,
        syncGatewayCall: suspend (
            useV2: Boolean,
            events: List<MyEvent>,
            semesterStart: String,
            totalWeeks: Int,
            timeNodes: List<TimeNode>
        ) -> Unit
    ) {
        val settings = currentSettings()
        val timeNodes = parseTimeTable(settings.timeTableJson)
        val useV2 = isCalendarSyncV2Enabled()

        if (useV2 && seedMapping) {
            seedSyncMappingIfNeeded()
        }

        syncGatewayCall(
            useV2,
            currentEvents(),
            settings.semesterStartDate,
            settings.totalWeeks,
            timeNodes
        )
    }

    suspend fun seedSyncMappingIfNeeded(
        isAttempted: Boolean,
        loadV2StatusSnapshot: suspend () -> Triple<Boolean, Boolean, Int>,
        hasAnyLocalEvents: () -> Boolean,
        syncFromCalendar: suspend () -> Result<Int>,
        setAttempted: (Boolean) -> Unit
    ) {
        if (isAttempted) return

        val (isEnabled, hasPermission, mappedEventCount) = loadV2StatusSnapshot()
        if (!isEnabled || !hasPermission) return

        if (!hasAnyLocalEvents()) {
            setAttempted(true)
            return
        }

        if (mappedEventCount == 0) {
            setAttempted(true)
            syncFromCalendar()
            return
        }

        setAttempted(true)
    }

    suspend fun manualSync(
        syncAllToCalendar: suspend (Boolean) -> Unit
    ): Result<Unit> {
        return StoreSyncNode.manualSync {
            syncAllToCalendar(false)
        }
    }

    suspend fun enableCalendarSyncAndSyncNow(
        enableCalendarSync: suspend () -> Result<Unit>,
        manualSync: suspend () -> Result<Unit>,
        syncFromCalendar: suspend () -> Result<Int>
    ): Result<Unit> {
        return StoreSyncNode.enableCalendarSyncAndSyncNow(
            enableCalendarSync = enableCalendarSync,
            manualSync = manualSync,
            syncFromCalendar = syncFromCalendar
        )
    }

    suspend fun enableCalendarSync(
        isCalendarSyncV2Enabled: () -> Boolean,
        setAttempted: (Boolean) -> Unit,
        enableSync: suspend (Boolean) -> Result<Unit>
    ): Result<Unit> {
        setAttempted(false)
        return enableSync(isCalendarSyncV2Enabled())
    }

    suspend fun disableCalendarSync(
        isCalendarSyncV2Enabled: () -> Boolean,
        disableSync: suspend (Boolean) -> Result<Unit>
    ): Result<Unit> {
        return disableSync(isCalendarSyncV2Enabled())
    }

    suspend fun getSyncStatus(
        isCalendarSyncV2Enabled: () -> Boolean,
        getSyncStatus: suspend (Boolean) -> CalendarSyncManager.SyncStatus
    ): CalendarSyncManager.SyncStatus {
        return getSyncStatus(isCalendarSyncV2Enabled())
    }

    suspend fun getSelectableSyncCalendars(
        systemCalendarManager: CalendarManager
    ): List<CalendarManager.CalendarInfo> {
        return StoreSyncNode.getSelectableSyncCalendars(systemCalendarManager)
    }

    suspend fun updateSourceCalendars(
        calendarIds: List<Long>,
        loadSyncData: suspend () -> SyncData,
        getSelectableSyncCalendars: suspend () -> List<CalendarManager.CalendarInfo>,
        saveSyncData: suspend (SyncData) -> Unit,
        pruneSourceMappings: suspend (Set<Long>) -> Unit,
        syncFromCalendar: suspend () -> Result<Int>
    ): Result<Unit> {
        return StoreSyncNode.updateSourceCalendars(
            calendarIds = calendarIds,
            loadSyncData = loadSyncData,
            getSelectableSyncCalendars = getSelectableSyncCalendars,
            saveSyncData = saveSyncData,
            pruneSourceMappings = pruneSourceMappings,
            syncFromCalendar = syncFromCalendar
        )
    }

    suspend fun pruneSourceMappings(
        calendarIds: Set<Long>,
        getMappingsByCalendarIds: suspend (List<Long>) -> List<CalendarSyncMapEntity>,
        localEvents: () -> List<MyEvent>,
        archivedEvents: () -> List<MyEvent>,
        loadSyncData: suspend () -> SyncData,
        eventMasterExists: suspend (String) -> Boolean,
        deleteMappingByLocalMasterId: suspend (String) -> Unit,
        removeLegacyMappings: suspend (List<String>) -> Unit
    ) {
        StoreSyncNode.pruneSourceMappings(
            calendarIds = calendarIds,
            getMappingsByCalendarIds = getMappingsByCalendarIds,
            localEvents = localEvents,
            archivedEvents = archivedEvents,
            loadSyncData = loadSyncData,
            eventMasterExists = eventMasterExists,
            deleteMappingByLocalMasterId = deleteMappingByLocalMasterId,
            removeLegacyMappings = removeLegacyMappings
        )
    }

    suspend fun syncSingleEventToCalendar(
        event: MyEvent,
        isCalendarSyncV2Enabled: () -> Boolean,
        syncEventToCalendar: suspend (Boolean, MyEvent) -> Unit
    ) {
        syncEventToCalendar(isCalendarSyncV2Enabled(), event)
    }

    suspend fun syncFromCalendar(
        ensureArchivesLoaded: suspend () -> Unit,
        activeEvents: () -> List<MyEvent>,
        archivedEvents: () -> List<MyEvent>,
        isCalendarSyncV2Enabled: () -> Boolean,
        syncGatewayCall: suspend (
            useV2: Boolean,
            snapshot: StoreCalendarSyncSnapshot,
            onAdded: suspend (MyEvent) -> Unit,
            onUpdated: suspend (MyEvent) -> Unit,
            onDeleted: suspend (String) -> Unit
        ) -> Result<Int>,
        updateEventWithoutSync: suspend (MyEvent) -> Unit,
        addEventWithoutSync: suspend (MyEvent) -> Unit,
        deleteEventWithoutSync: suspend (String, Boolean) -> Unit,
        mergeIncomingCalendarEvent: (MyEvent, MyEvent) -> MyEvent,
        isNoopCalendarMerge: (MyEvent, MyEvent) -> Boolean,
        isDuplicateEvent: (MyEvent, List<MyEvent>, List<MyEvent>) -> Boolean,
        withRandomColorIfNeeded: (MyEvent) -> MyEvent
    ): Result<Int> {
        return StoreReverseSyncNode.syncFromCalendar(
            ensureArchivesLoaded = ensureArchivesLoaded,
            activeEvents = activeEvents,
            archivedEvents = archivedEvents,
            isCalendarSyncV2Enabled = isCalendarSyncV2Enabled,
            syncGatewayCall = syncGatewayCall,
            onEventUpsert = { incomingEvent, snapshot ->
                StoreReverseSyncNode.handleCalendarAddedOrUpdated(
                    incomingEvent = incomingEvent,
                    snapshot = snapshot,
                    mergeIncomingCalendarEvent = mergeIncomingCalendarEvent,
                    isNoopCalendarMerge = isNoopCalendarMerge,
                    updateEventWithoutSync = updateEventWithoutSync,
                    addEventWithoutSync = addEventWithoutSync,
                    isDuplicateEvent = isDuplicateEvent,
                    withRandomColorIfNeeded = withRandomColorIfNeeded
                )
            },
            onEventDeleted = { eventId, snapshot ->
                StoreReverseSyncNode.handleCalendarDeleted(
                    eventId = eventId,
                    snapshot = snapshot,
                    deleteEventWithoutSync = deleteEventWithoutSync
                )
            }
        )
    }
}
