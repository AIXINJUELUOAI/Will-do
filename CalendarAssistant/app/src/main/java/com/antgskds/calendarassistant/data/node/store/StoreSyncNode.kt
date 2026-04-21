package com.antgskds.calendarassistant.data.node.store

import android.util.Log
import androidx.compose.ui.graphics.Color
import com.antgskds.calendarassistant.core.calendar.CalendarManager
import com.antgskds.calendarassistant.data.db.entity.CalendarSyncMapEntity
import com.antgskds.calendarassistant.data.model.EventTags
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.data.model.SyncData
import com.antgskds.calendarassistant.data.model.TimeNode
import kotlinx.serialization.json.Json

internal object StoreSyncNode {
    private val parser = Json { ignoreUnknownKeys = true }

    fun parseTimeTable(json: String): List<TimeNode> {
        return try {
            parser.decodeFromString<List<TimeNode>>(json)
        } catch (e: Exception) {
            Log.e("StoreNode", "解析作息时间失败", e)
            emptyList()
        }
    }

    fun resolveSourceCalendarIds(syncData: SyncData): List<Long> {
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

    fun withRandomColorIfNeeded(event: MyEvent, randomColorProvider: () -> Color): MyEvent {
        return if (event.isRecurring) event else event.copy(color = randomColorProvider())
    }

    fun isDuplicateEvent(
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

    fun mergeIncomingCalendarEvent(existingEvent: MyEvent, incomingEvent: MyEvent): MyEvent {
        val resolvedTag = if (
            incomingEvent.tag == EventTags.GENERAL &&
            existingEvent.tag != EventTags.GENERAL
        ) {
            Log.d("StoreNode", "保留本地事件 tag，避免被反向同步降级: ${existingEvent.id}, ${existingEvent.tag} <- ${incomingEvent.tag}")
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
            nextOccurrenceStartMillis = if (existingEvent.isRecurringParent || incomingEvent.isRecurringParent) {
                incomingEvent.nextOccurrenceStartMillis
            } else {
                existingEvent.nextOccurrenceStartMillis
            },
            skipCalendarSync = incomingEvent.skipCalendarSync,
            lastModified = System.currentTimeMillis()
        )
    }

    fun isNoopCalendarMerge(existingEvent: MyEvent, mergedEvent: MyEvent): Boolean {
        return existingEvent.copy(lastModified = 0L) == mergedEvent.copy(lastModified = 0L)
    }

    suspend fun enableCalendarSyncAndSyncNow(
        enableCalendarSync: suspend () -> Result<Unit>,
        manualSync: suspend () -> Result<Unit>,
        syncFromCalendar: suspend () -> Result<Int>
    ): Result<Unit> {
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

    suspend fun manualSync(syncAllToCalendar: suspend () -> Unit): Result<Unit> {
        return try {
            syncAllToCalendar()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("StoreNode", "手动同步失败", e)
            Result.failure(e)
        }
    }

    suspend fun getSelectableSyncCalendars(
        systemCalendarManager: CalendarManager
    ): List<CalendarManager.CalendarInfo> {
        return try {
            systemCalendarManager.getReadableCalendars(visibleOnly = false, syncEnabledOnly = false)
        } catch (e: SecurityException) {
            Log.w("StoreNode", "获取同步来源日历失败：缺少权限", e)
            emptyList()
        } catch (e: Exception) {
            Log.e("StoreNode", "获取同步来源日历失败", e)
            emptyList()
        }
    }

    suspend fun updateSourceCalendars(
        calendarIds: List<Long>,
        loadSyncData: suspend () -> SyncData,
        getSelectableSyncCalendars: suspend () -> List<CalendarManager.CalendarInfo>,
        saveSyncData: suspend (SyncData) -> Unit,
        pruneSourceMappings: suspend (Set<Long>) -> Unit,
        syncFromCalendar: suspend () -> Result<Int>
    ): Result<Unit> {
        return try {
            val syncData = loadSyncData()
            val availableIds = getSelectableSyncCalendars().map { it.id }.toSet()
            val normalizedIds = calendarIds
                .asSequence()
                .filter { availableIds.contains(it) }
                .distinct()
                .toList()

            val previousIds = resolveSourceCalendarIds(syncData).toSet()
            val removedSourceIds = previousIds - normalizedIds.toSet()

            Log.d(
                "StoreNode",
                "updateSourceCalendars: previous=$previousIds, requested=$calendarIds, normalized=$normalizedIds, removed=$removedSourceIds"
            )

            if (normalizedIds == syncData.sourceCalendarIds) {
                Log.d("StoreNode", "updateSourceCalendars: no changes")
                return Result.success(Unit)
            }

            saveSyncData(syncData.copy(sourceCalendarIds = normalizedIds))
            pruneSourceMappings(removedSourceIds)

            if (syncData.isSyncEnabled) {
                Log.d("StoreNode", "updateSourceCalendars: trigger reverse sync")
                val reverseResult = syncFromCalendar()
                if (reverseResult.isFailure) {
                    return Result.failure(reverseResult.exceptionOrNull() ?: Exception("更新同步来源失败"))
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("StoreNode", "更新同步来源日历失败", e)
            Result.failure(e)
        }
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
        val validCalendarIds = calendarIds.filter { it != -1L }
        if (validCalendarIds.isEmpty()) return

        val mappings = getMappingsByCalendarIds(validCalendarIds)
        if (mappings.isEmpty()) return

        val localEventsById = (localEvents() + archivedEvents()).associateBy { it.id }
        val targetCalendarId = loadSyncData().targetCalendarId
        val mappingsToRemove = mutableListOf<CalendarSyncMapEntity>()
        mappings.forEach { mapping ->
            if (mapping.calendarId != targetCalendarId) {
                mappingsToRemove += mapping
                return@forEach
            }

            val localEvent = localEventsById[mapping.localMasterId]
            if (localEvent?.skipCalendarSync == true) {
                mappingsToRemove += mapping
                return@forEach
            }

            if (localEvent == null && !eventMasterExists(mapping.localMasterId)) {
                mappingsToRemove += mapping
            }
        }

        if (mappingsToRemove.isEmpty()) return

        Log.d(
            "StoreNode",
            "pruneSourceMappings: calendars=$validCalendarIds, removeMappings=${mappingsToRemove.size}"
        )

        mappingsToRemove.forEach { mapping ->
            deleteMappingByLocalMasterId(mapping.localMasterId)
        }
        removeLegacyMappings(mappingsToRemove.map { it.localMasterId })
    }
}
