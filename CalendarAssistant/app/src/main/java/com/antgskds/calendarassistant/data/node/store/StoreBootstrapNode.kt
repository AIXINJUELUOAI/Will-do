package com.antgskds.calendarassistant.data.node.store

import android.util.Log
import com.antgskds.calendarassistant.data.model.Course
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.data.model.MySettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal object StoreBootstrapNode {
    data class ArchivedLoadResult(
        val mergedArchived: List<MyEvent>
    )

    fun refreshData(
        scope: CoroutineScope,
        isRoomMainEnabled: () -> Boolean,
        isRoomReadEnabled: () -> Boolean,
        eventsMigrated: () -> Boolean,
        recurringMigrated: () -> Boolean,
        archivesMigrated: () -> Boolean,
        loadJsonEvents: suspend () -> List<MyEvent>,
        loadCourses: suspend () -> List<Course>,
        loadSettings: suspend () -> MySettings,
        loadRoomActiveEvents: suspend () -> List<MyEvent>,
        loadJsonArchived: suspend () -> List<MyEvent>,
        consumeEventCleanupInfo: () -> String,
        consumeCourseCleanupInfo: () -> String,
        saveCleanupInfo: (String) -> Unit,
        saveJsonEvents: suspend (List<MyEvent>) -> Unit,
        saveJsonArchived: suspend (List<MyEvent>) -> Unit,
        migrateLegacyEvents: suspend (List<MyEvent>) -> Unit,
        migrateLegacyRecurring: suspend (List<MyEvent>) -> Unit,
        migrateLegacyArchives: suspend (List<MyEvent>) -> Unit,
        sanitizeRecurringEvents: (List<MyEvent>) -> List<MyEvent>,
        cleanupLegacyJsonIfStable: () -> Unit,
        logRoomDiff: (String, List<MyEvent>, List<MyEvent>) -> Unit,
        updateState: (List<MyEvent>, List<Course>, MySettings) -> Unit,
        scheduleRemindersForEvents: (List<MyEvent>) -> Unit,
        autoArchiveExpiredEvents: suspend () -> Int
    ) {
        scope.launch {
            val useRoomMain = isRoomMainEnabled()
            val roomHasData = if (useRoomMain) {
                runCatching { loadRoomActiveEvents().isNotEmpty() }.getOrElse { false }
            } else {
                false
            }
            val shouldLoadJson = !useRoomMain || !eventsMigrated() || !recurringMigrated() || !roomHasData
            val rawEvents = if (shouldLoadJson) loadJsonEvents() else emptyList()
            val loadedCourses = loadCourses()
            val loadedSettings = loadSettings()

            val eventCleanupInfo = if (shouldLoadJson) consumeEventCleanupInfo() else ""
            val courseCleanupInfo = consumeCourseCleanupInfo()
            val cleanupInfo = listOf(eventCleanupInfo, courseCleanupInfo)
                .filter { it.isNotEmpty() }
                .joinToString("，")

            if (cleanupInfo.isNotEmpty()) {
                saveCleanupInfo(cleanupInfo)
                Log.i("StoreNode", "数据自愈: $cleanupInfo")
            }

            val loadedEvents = rawEvents
            if (rawEvents.isNotEmpty() && loadedEvents.size != rawEvents.size) {
                saveJsonEvents(loadedEvents)
            }

            if (shouldLoadJson) {
                migrateLegacyEvents(loadedEvents)
                migrateLegacyRecurring(rawEvents)
            }

            if (!archivesMigrated()) {
                val rawArchived = loadJsonArchived()
                val archivedForMigration = sanitizeRecurringEvents(rawArchived)
                if (archivedForMigration.size != rawArchived.size) {
                    saveJsonArchived(archivedForMigration)
                }
                migrateLegacyArchives(archivedForMigration)
            }

            cleanupLegacyJsonIfStable()

            val useRoomRead = isRoomReadEnabled()
            val roomEvents = if (useRoomRead || useRoomMain) loadRoomActiveEvents() else emptyList()
            val sanitizedRoomEvents = sanitizeRecurringEvents(roomEvents)
            val roomRecurringPresent = sanitizedRoomEvents.any { it.isRecurring || it.isRecurringParent }
            val jsonRecurringEvents = loadedEvents.filter { it.isRecurring || it.isRecurringParent }
            val jsonRecurringById = jsonRecurringEvents.associateBy { it.id }
            val jsonRecurringIds = jsonRecurringById.keys
            val mergedRoomEvents = when {
                useRoomMain && roomRecurringPresent -> sanitizedRoomEvents
                useRoomMain && jsonRecurringEvents.isNotEmpty() -> {
                    Log.w("StoreNode", "Room 主存缺少重复事件，回退 JSON 递补")
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
                    Log.w("StoreNode", "Room 主存开启但未读取到数据，回退 JSON")
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

            updateState(activeEvents, loadedCourses, loadedSettings)
            scheduleRemindersForEvents(activeEvents)

            launch {
                autoArchiveExpiredEvents()
            }
        }
    }

    fun fetchArchivedEvents(
        scope: CoroutineScope,
        withArchiveLock: suspend (suspend () -> Unit) -> Unit,
        loadArchivedEventsForState: suspend () -> ArchivedLoadResult,
        updateArchivedEvents: (List<MyEvent>) -> Unit
    ) {
        scope.launch {
            withArchiveLock {
                updateArchivedEvents(loadArchivedEventsForState().mergedArchived)
            }
        }
    }

    suspend fun ensureArchivesLoaded(
        currentArchivedEvents: () -> List<MyEvent>,
        withArchiveLock: suspend (suspend () -> Unit) -> Unit,
        loadArchivedEventsForState: suspend () -> ArchivedLoadResult,
        updateArchivedEvents: (List<MyEvent>) -> Unit,
        onLoadingTriggered: () -> Unit
    ) {
        if (currentArchivedEvents().isNotEmpty()) {
            return
        }

        withArchiveLock {
            if (currentArchivedEvents().isEmpty()) {
                onLoadingTriggered()
                updateArchivedEvents(loadArchivedEventsForState().mergedArchived)
            }
        }
    }

    suspend fun loadArchivedEventsForState(
        isRoomReadEnabled: () -> Boolean,
        isRoomMainEnabled: () -> Boolean,
        archivesMigrated: () -> Boolean,
        loadRoomArchivedEvents: suspend () -> List<MyEvent>,
        loadJsonArchivedEvents: suspend () -> List<MyEvent>,
        sanitizeRecurringEvents: (List<MyEvent>) -> List<MyEvent>,
        saveJsonArchivedEvents: suspend (List<MyEvent>) -> Unit,
        migrateLegacyArchives: suspend (List<MyEvent>) -> Unit,
        logRoomDiff: (String, List<MyEvent>, List<MyEvent>) -> Unit
    ): ArchivedLoadResult {
        val useRoomRead = isRoomReadEnabled()
        val useRoomMain = isRoomMainEnabled()
        val roomArchived = if (useRoomRead || useRoomMain) {
            loadRoomArchivedEvents()
        } else {
            emptyList()
        }
        val shouldLoadJson = !useRoomMain || !archivesMigrated()
        val rawLoaded = if (shouldLoadJson) {
            loadJsonArchivedEvents()
        } else {
            emptyList()
        }
        val loaded = sanitizeRecurringEvents(rawLoaded)
        if (shouldLoadJson && loaded.size != rawLoaded.size) {
            saveJsonArchivedEvents(loaded)
        }
        if (shouldLoadJson) {
            migrateLegacyArchives(loaded)
        }
        val mergedArchived = when {
            useRoomMain && (roomArchived.isNotEmpty() || loaded.isEmpty()) -> roomArchived
            useRoomMain -> {
                Log.w("StoreNode", "Room 主存开启但未读取到归档数据，回退 JSON")
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
}
