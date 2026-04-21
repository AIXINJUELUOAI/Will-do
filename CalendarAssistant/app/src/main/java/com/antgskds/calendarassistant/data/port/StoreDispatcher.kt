package com.antgskds.calendarassistant.data.port

import android.content.Context
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.core.calendar.CalendarManager
import com.antgskds.calendarassistant.core.calendar.CalendarSyncManager
import com.antgskds.calendarassistant.core.event.DomainEventBus
import com.antgskds.calendarassistant.core.event.DomainEventType
import com.antgskds.calendarassistant.core.event.EventIdentity
import com.antgskds.calendarassistant.core.event.events.ScheduleChangeOrigin
import com.antgskds.calendarassistant.core.event.events.ScheduleChangeType
import com.antgskds.calendarassistant.core.event.events.ScheduleChangedEvent
import com.antgskds.calendarassistant.core.importer.ImportMode
import com.antgskds.calendarassistant.core.operation.BackupOperationApi
import com.antgskds.calendarassistant.core.operation.ScheduleOperationApi
import com.antgskds.calendarassistant.core.operation.SettingsOperationApi
import com.antgskds.calendarassistant.core.course.CourseEventMapper
import com.antgskds.calendarassistant.core.query.ScheduleQueryApi
import com.antgskds.calendarassistant.core.query.SettingsQueryApi
import com.antgskds.calendarassistant.data.model.Course
import com.antgskds.calendarassistant.data.model.ImportResult
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.data.node.store.StoreRootNode
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicLong

class StoreDispatcher private constructor(
    private val rootNode: StoreRootNodePort,
    private val domainEventBus: DomainEventBus?
) : ScheduleOperationApi, ScheduleQueryApi, SettingsOperationApi, SettingsQueryApi, BackupOperationApi {
    private data class EventState(
        val inArchive: Boolean,
        val lastModified: Long
    )

    private val scheduleEntityVersion = AtomicLong(System.currentTimeMillis())

    companion object {
        private const val EVENT_SOURCE = "store_dispatcher"
        private const val EVENT_ENTITY_KEY = "schedule_store"

        @Volatile
        private var INSTANCE: StoreDispatcher? = null

        fun getInstance(context: Context): StoreDispatcher {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: StoreDispatcher(
                    rootNode = StoreRootNode.getInstance(context.applicationContext),
                    domainEventBus = (context.applicationContext as? App)?.domainEventBus
                ).also { INSTANCE = it }
            }
        }
    }

    override val events: StateFlow<List<MyEvent>>
        get() = rootNode.events

    override val archivedEvents: StateFlow<List<MyEvent>>
        get() = rootNode.archivedEvents

    override val settings: StateFlow<MySettings>
        get() = rootNode.settings

    fun bindCapsuleRefreshHandler(handler: (() -> Unit)?) {
        rootNode.bindCapsuleRefreshHandler(handler)
    }

    override fun refreshAndScheduleAll() {
        rootNode.loadAndScheduleAll()
    }

    override fun fetchArchivedEvents() {
        rootNode.fetchArchivedEvents()
    }

    override suspend fun addEvent(event: MyEvent) {
        val before = snapshotEventStates()
        rootNode.addEvent(event)
        emitScheduleChangedIfNeeded(
            changeType = ScheduleChangeType.CREATE,
            origin = ScheduleChangeOrigin.MANUAL,
            before = before,
            fallbackEventIds = listOf(event.id)
        )
    }

    override suspend fun updateEvent(event: MyEvent) {
        val before = snapshotEventStates()
        rootNode.updateEvent(event)
        emitScheduleChangedIfNeeded(
            changeType = ScheduleChangeType.UPDATE,
            origin = ScheduleChangeOrigin.MANUAL,
            before = before,
            fallbackEventIds = listOf(event.id)
        )
    }

    override suspend fun deleteEvent(eventId: String) {
        val before = snapshotEventStates()
        rootNode.deleteEvent(eventId)
        emitScheduleChangedIfNeeded(
            changeType = ScheduleChangeType.DELETE,
            origin = ScheduleChangeOrigin.MANUAL,
            before = before,
            fallbackEventIds = listOf(eventId)
        )
    }

    override suspend fun detachRecurringInstance(
        parentEventId: String,
        sourceInstanceId: String,
        sourceInstanceKey: String,
        detachedEvent: MyEvent
    ) {
        val before = snapshotEventStates()
        rootNode.detachRecurringInstance(parentEventId, sourceInstanceId, sourceInstanceKey, detachedEvent)
        emitScheduleChangedIfNeeded(
            changeType = ScheduleChangeType.UPDATE,
            origin = ScheduleChangeOrigin.MANUAL,
            before = before,
            fallbackEventIds = listOf(detachedEvent.id, parentEventId, sourceInstanceId)
        )
    }

    override suspend fun performPrimaryRuleAction(eventId: String): Boolean {
        val before = snapshotEventStates()
        val applied = rootNode.performPrimaryRuleAction(eventId)
        if (applied) {
            emitScheduleChangedIfNeeded(
                changeType = ScheduleChangeType.UPDATE,
                origin = ScheduleChangeOrigin.MANUAL,
                before = before,
                fallbackEventIds = listOf(eventId)
            )
        }
        return applied
    }

    override suspend fun completeScheduleEvent(eventId: String) {
        val before = snapshotEventStates()
        rootNode.completeScheduleEvent(eventId)
        emitScheduleChangedIfNeeded(
            changeType = ScheduleChangeType.UPDATE,
            origin = ScheduleChangeOrigin.MANUAL,
            before = before,
            fallbackEventIds = listOf(eventId)
        )
    }

    override suspend fun archiveEvent(eventId: String) {
        val before = snapshotEventStates()
        rootNode.archiveEvent(eventId)
        emitScheduleChangedIfNeeded(
            changeType = ScheduleChangeType.ARCHIVE,
            origin = ScheduleChangeOrigin.MANUAL,
            before = before,
            fallbackEventIds = listOf(eventId)
        )
    }

    override suspend fun restoreEvent(archivedEventId: String) {
        val before = snapshotEventStates()
        rootNode.restoreEvent(archivedEventId)
        emitScheduleChangedIfNeeded(
            changeType = ScheduleChangeType.RESTORE,
            origin = ScheduleChangeOrigin.MANUAL,
            before = before,
            fallbackEventIds = listOf(archivedEventId)
        )
    }

    override suspend fun deleteArchivedEvent(archivedEventId: String) {
        val before = snapshotEventStates()
        rootNode.deleteArchivedEvent(archivedEventId)
        emitScheduleChangedIfNeeded(
            changeType = ScheduleChangeType.DELETE,
            origin = ScheduleChangeOrigin.MANUAL,
            before = before,
            fallbackEventIds = listOf(archivedEventId)
        )
    }

    override suspend fun clearAllArchives() {
        val before = snapshotEventStates()
        rootNode.clearAllArchives()
        emitScheduleChangedIfNeeded(
            changeType = ScheduleChangeType.BULK,
            origin = ScheduleChangeOrigin.MANUAL,
            before = before
        )
    }

    override suspend fun autoArchiveExpiredEvents(): Int {
        val before = snapshotEventStates()
        val archivedCount = rootNode.autoArchiveExpiredEvents()
        if (archivedCount > 0) {
            emitScheduleChangedIfNeeded(
                changeType = ScheduleChangeType.ARCHIVE,
                origin = ScheduleChangeOrigin.SYSTEM,
                before = before
            )
        }
        return archivedCount
    }

    override fun getEventsCount(): Int {
        return rootNode.getEventsCount()
    }

    override fun getTotalEventsCount(): Int {
        return rootNode.getTotalEventsCount()
    }

    override suspend fun updateSettings(settings: MySettings) {
        rootNode.updateSettings(settings)
    }

    override suspend fun exportCoursesData(): String {
        return rootNode.exportCoursesData()
    }

    override suspend fun importCoursesData(jsonString: String): Result<Unit> {
        val before = snapshotCourses()
        val result = rootNode.importCoursesData(jsonString)
        if (result.isSuccess && before != snapshotCourses()) {
            emitScheduleChanged(
                changeType = ScheduleChangeType.BULK,
                origin = ScheduleChangeOrigin.IMPORT,
                eventIds = emptyList()
            )
        }
        return result
    }

    override suspend fun exportEventsData(): String {
        return rootNode.exportEventsData()
    }

    override suspend fun importEventsData(jsonString: String): Result<ImportResult> {
        val before = snapshotEventStates()
        val result = rootNode.importEventsData(jsonString)
        if (result.isSuccess) {
            val payload = result.getOrNull()
            val hasAppliedChange = (payload?.successCount ?: 0) > 0 || (payload?.archiveStatusUpdateCount ?: 0) > 0
            if (hasAppliedChange) {
                emitScheduleChangedIfNeeded(
                    changeType = ScheduleChangeType.BULK,
                    origin = ScheduleChangeOrigin.IMPORT,
                    before = before,
                    forceEmit = true
                )
            }
        }
        return result
    }

    override suspend fun importWakeUpFile(
        content: String,
        mode: ImportMode,
        importSettings: Boolean
    ): Result<Int> {
        val beforeEvents = snapshotEventStates()
        val beforeCourses = snapshotCourses()
        val result = rootNode.importWakeUpFile(content, mode, importSettings)
        if (result.isSuccess) {
            val importedCount = result.getOrNull() ?: 0
            val coursesChanged = beforeCourses != snapshotCourses()
            if (importedCount > 0 || coursesChanged) {
                emitScheduleChangedIfNeeded(
                    changeType = ScheduleChangeType.BULK,
                    origin = ScheduleChangeOrigin.IMPORT,
                    before = beforeEvents,
                    forceEmit = true
                )
            }
        }
        return result
    }

    override suspend fun enableCalendarSync(): Result<Unit> {
        return rootNode.enableCalendarSync()
    }

    override suspend fun disableCalendarSync(): Result<Unit> {
        return rootNode.disableCalendarSync()
    }

    override suspend fun enableCalendarSyncAndSyncNow(): Result<Unit> {
        val before = snapshotEventStates()
        val result = rootNode.enableCalendarSyncAndSyncNow()
        if (result.isSuccess) {
            emitScheduleChangedIfNeeded(
                changeType = ScheduleChangeType.BULK,
                origin = ScheduleChangeOrigin.SYNC,
                before = before
            )
        }
        return result
    }

    override suspend fun updateSourceCalendars(calendarIds: List<Long>): Result<Unit> {
        val before = snapshotEventStates()
        val result = rootNode.updateSourceCalendars(calendarIds)
        if (result.isSuccess) {
            emitScheduleChangedIfNeeded(
                changeType = ScheduleChangeType.BULK,
                origin = ScheduleChangeOrigin.SYNC,
                before = before
            )
        }
        return result
    }

    override suspend fun manualSync(): Result<Unit> {
        return rootNode.manualSync()
    }

    override suspend fun syncFromCalendar(): Result<Int> {
        val before = snapshotEventStates()
        val result = rootNode.syncFromCalendar()
        val changedCount = result.getOrNull() ?: 0
        if (result.isSuccess && changedCount > 0) {
            emitScheduleChangedIfNeeded(
                changeType = ScheduleChangeType.BULK,
                origin = ScheduleChangeOrigin.SYNC,
                before = before,
                forceEmit = true
            )
        }
        return result
    }

    override suspend fun getSyncStatus(): CalendarSyncManager.SyncStatus {
        return rootNode.getSyncStatus()
    }

    override suspend fun getSelectableSyncCalendars(): List<CalendarManager.CalendarInfo> {
        return rootNode.getSelectableSyncCalendars()
    }

    private fun snapshotEventStates(): Map<String, EventState> {
        val active = events.value.associate { event ->
            event.id to EventState(inArchive = false, lastModified = event.lastModified)
        }
        val archived = archivedEvents.value.associate { event ->
            event.id to EventState(inArchive = true, lastModified = event.lastModified)
        }
        return active + archived
    }

    private fun snapshotCourses(): List<Course> = CourseEventMapper.extractCourses(events.value, settings.value)

    private suspend fun emitScheduleChangedIfNeeded(
        changeType: ScheduleChangeType,
        origin: ScheduleChangeOrigin,
        before: Map<String, EventState>,
        fallbackEventIds: List<String> = emptyList(),
        forceEmit: Boolean = false
    ) {
        val changedIds = collectChangedEventIds(before, snapshotEventStates())
        if (!forceEmit && changedIds.isEmpty()) {
            return
        }
        emitScheduleChanged(changeType, origin, (changedIds + fallbackEventIds).distinct())
    }

    private suspend fun emitScheduleChanged(
        changeType: ScheduleChangeType,
        origin: ScheduleChangeOrigin,
        eventIds: List<String>
    ) {
        val bus = domainEventBus ?: return
        val now = System.currentTimeMillis()
        bus.emit(
            eventType = DomainEventType.SCHEDULE_CHANGED,
            traceId = EventIdentity.newTraceId("schedule"),
            source = EVENT_SOURCE,
            entityKey = EVENT_ENTITY_KEY,
            payload = ScheduleChangedEvent(
                changeType = changeType,
                eventIds = eventIds,
                origin = origin,
                entityVersion = scheduleEntityVersion.incrementAndGet(),
                updatedAt = now
            ),
            occurredAt = now
        )
    }

    private fun collectChangedEventIds(
        before: Map<String, EventState>,
        after: Map<String, EventState>
    ): List<String> {
        return (before.keys + after.keys)
            .asSequence()
            .filter { eventId -> before[eventId] != after[eventId] }
            .toSet()
            .toList()
    }
}
