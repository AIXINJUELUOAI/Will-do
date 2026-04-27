package com.antgskds.calendarassistant.store

import android.content.Context
import android.util.Log
import com.antgskds.calendarassistant.calendar.helpers.FLAG_TASK_COMPLETED
import com.antgskds.calendarassistant.calendar.helpers.STATE_CHECKED_IN
import com.antgskds.calendarassistant.calendar.helpers.STATE_COMPLETED
import com.antgskds.calendarassistant.calendar.helpers.STATE_PENDING
import com.antgskds.calendarassistant.calendar.helpers.TAG_FLIGHT
import com.antgskds.calendarassistant.calendar.helpers.TAG_TRAIN
import com.antgskds.calendarassistant.calendar.helpers.TYPE_TASK
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.EventTags
import com.antgskds.calendarassistant.calendar.models.EventType
import com.antgskds.calendarassistant.calendar.models.inferEventTagFromDescription
import com.antgskds.calendarassistant.calendar.models.isNoteTag
import com.antgskds.calendarassistant.core.model.RecognitionDraft
import com.antgskds.calendarassistant.core.model.RecurringMode
import com.antgskds.calendarassistant.core.operation.OperationErrorCode
import com.antgskds.calendarassistant.core.operation.OperationResult
import com.antgskds.calendarassistant.core.center.ScheduleDisplayHelper
import com.antgskds.calendarassistant.store.config.SyncConfigStore
import com.antgskds.calendarassistant.store.local.LocalEventStoreNode
import com.antgskds.calendarassistant.store.reminder.ReminderStoreNode
import com.antgskds.calendarassistant.store.sync.SystemCalendarStoreNode
import com.antgskds.calendarassistant.store.SyncLoopGuard
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class StoreRootNode(context: Context) {
    private val localNode = LocalEventStoreNode(context)
    private val syncNode = SystemCalendarStoreNode(context)
    private val reminderNode = ReminderStoreNode(context)
    private val configStore = SyncConfigStore(context)

    // ══════════════════════════════════════════════════════════════════════
    // 创建
    // ══════════════════════════════════════════════════════════════════════

    fun createFromRecognition(draft: RecognitionDraft, syncToSystem: Boolean = true): Long {
        val event = Event(
            id = null,
            startTS = draft.startTS,
            endTS = draft.endTS,
            title = draft.title,
            location = draft.location,
            description = draft.description,
            timeZone = draft.timeZone,
            tag = draft.tag,
            rrule = "",
            parentId = 0
        )
        return createEvent(event, syncToSystem)
    }

    fun createEvent(event: Event, syncToSystem: Boolean = true): Long {
        val localEvent = event.withInferredTag().copy(lastUpdated = nowSeconds())
        val id = localNode.upsertEvent(localEvent)
        var stored = localEvent.copy(id = id)

        val pushDecision = evaluateSystemPush(syncRequested = syncToSystem, tag = stored.tag)
        Log.i(
            "SyncPush",
            "createEvent localId=$id title=${stored.title} tag=${stored.tag} " +
                "recurring=${stored.isRecurring} syncRequested=$syncToSystem decision=${pushDecision.reason}"
        )

        if (pushDecision.allow) {
            stored = syncNode.insertToSystem(stored)
            localNode.upsertEvent(stored)
            Log.i(
                "SyncPush",
                "createEvent pushed localId=$id importId=${stored.importId} source=${stored.source}"
            )
        } else {
            Log.i("SyncPush", "createEvent skipped localId=$id reason=${pushDecision.reason}")
        }

        reminderNode.rebuildForEvent(stored)
        return id
    }

    // ══════════════════════════════════════════════════════════════════════
    // 状态变更
    // ══════════════════════════════════════════════════════════════════════

    fun completeEvent(eventId: Long, occurrenceTs: Long? = null, syncToSystem: Boolean = true): OperationResult<Long> {
        return changeEventState(eventId, occurrenceTs, STATE_COMPLETED, syncToSystem)
    }

    fun checkInEvent(eventId: Long, occurrenceTs: Long? = null, syncToSystem: Boolean = true): OperationResult<Long> {
        return changeEventState(eventId, occurrenceTs, STATE_CHECKED_IN, syncToSystem)
    }

    fun markPending(eventId: Long, occurrenceTs: Long? = null, syncToSystem: Boolean = true): OperationResult<Long> {
        return changeEventState(eventId, occurrenceTs, STATE_PENDING, syncToSystem)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 更新 & 删除
    // ══════════════════════════════════════════════════════════════════════

    fun updateEvent(event: Event, syncToSystem: Boolean = true) {
        // 防护：负数 id 是虚拟展开实例的合成 id，不应进入数据库
        val eventId = event.id
        if (eventId != null && eventId < 0) {
            android.util.Log.w("StoreRootNode", "Blocked updateEvent with synthetic id=$eventId, title=${event.title}")
            return
        }

        val updated = event.withInferredTag().copy(lastUpdated = nowSeconds())
        localNode.upsertEvent(updated)

        val pushDecision = evaluateSystemPush(syncRequested = syncToSystem, tag = updated.tag)
        Log.i(
            "SyncPush",
            "updateEvent id=${updated.id} title=${updated.title} tag=${updated.tag} " +
                "recurring=${updated.isRecurring} syncRequested=$syncToSystem decision=${pushDecision.reason}"
        )

        val stored = if (pushDecision.allow) {
            val synced = syncNode.updateToSystem(updated)
            localNode.upsertEvent(synced)
            Log.i(
                "SyncPush",
                "updateEvent pushed id=${synced.id} importId=${synced.importId} source=${synced.source}"
            )
            synced
        } else {
            Log.i("SyncPush", "updateEvent skipped id=${updated.id} reason=${pushDecision.reason}")
            updated
        }

        reminderNode.rebuildForEvent(stored)
    }

    fun deleteEvent(id: Long, deleteFromSystem: Boolean = true) {
        if (id < 0) {
            android.util.Log.w("StoreRootNode", "Blocked deleteEvent with synthetic id=$id")
            return
        }
        val event = localNode.getEvent(id)
        if (event != null && allowSystemPush(deleteFromSystem)) {
            syncNode.deleteFromSystem(event)
        }

        localNode.getChildEvents(id).forEach { child ->
            if (allowSystemPush(deleteFromSystem)) {
                syncNode.deleteFromSystem(child)
            }
            child.id?.let { reminderNode.cancelForEvent(it) }
        }
        localNode.deleteChildEvents(id)

        localNode.deleteEvent(id)
        reminderNode.cancelForEvent(id)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 重复事件编辑
    // ══════════════════════════════════════════════════════════════════════

    fun editRecurringEvent(
        parentEventId: Long,
        editedOccurrence: Event,
        mode: RecurringMode,
        occurrenceTs: Long,
        syncToSystem: Boolean = true
    ): Long? {
        val parent = localNode.getEvent(parentEventId) ?: return null
        return when (mode) {
            RecurringMode.THIS -> editThisOccurrence(parent, editedOccurrence, occurrenceTs, syncToSystem)
            RecurringMode.THIS_AND_FUTURE -> editThisAndFuture(parent, editedOccurrence, occurrenceTs, syncToSystem)
            RecurringMode.ALL -> {
                val target = editedOccurrence.copy(id = parentEventId, parentId = 0)
                updateEvent(target, syncToSystem)
                parentEventId
            }
        }
    }

    fun deleteRecurringEvent(parentEventId: Long, mode: RecurringMode, occurrenceTs: Long, deleteFromSystem: Boolean = true) {
        val parent = localNode.getEvent(parentEventId) ?: return
        when (mode) {
            RecurringMode.THIS -> {
                deleteRecurringOccurrence(parentEventId, occurrenceTs, deleteFromSystem)
            }
            RecurringMode.THIS_AND_FUTURE -> {
                if (occurrenceTs <= parent.startTS) {
                    deleteEvent(parentEventId, deleteFromSystem)
                    return
                }

                val updatedParent = parent.copy(
                    rrule = applyUntilToRRule(parent.rrule, occurrenceTs - 1),
                    lastUpdated = nowSeconds()
                )
                localNode.upsertEvent(updatedParent)
                if (allowSystemPush(deleteFromSystem)) {
                    val synced = syncNode.updateToSystem(updatedParent)
                    localNode.upsertEvent(synced)
                }

                val childToDelete = localNode.getChildEventsFrom(parentEventId, occurrenceTs)
                childToDelete.forEach { child ->
                    if (allowSystemPush(deleteFromSystem)) {
                        syncNode.deleteFromSystem(child)
                    }
                    child.id?.let { reminderNode.cancelForEvent(it) }
                }
                localNode.deleteChildEventsFrom(parentEventId, occurrenceTs)
                reminderNode.rebuildForEvent(updatedParent)
            }
            RecurringMode.ALL -> {
                deleteEvent(parentEventId, deleteFromSystem)
            }
        }
    }

    fun deleteRecurringOccurrence(parentEventId: Long, occurrenceTs: Long, syncToSystem: Boolean = true): Event? {
        val exdateUtc = formatExdateUtc(occurrenceTs)
        val parent = localNode.getEvent(parentEventId) ?: return null
        val updated = parent.copy(
            exdates = (parent.exdates + exdateUtc).distinct(),
            lastUpdated = nowSeconds()
        )
        localNode.upsertEvent(updated)

        val stored = if (allowSystemPush(syncToSystem) && !isNoteTag(updated.tag)) {
            val synced = syncNode.updateToSystem(updated)
            syncNode.insertRepeatException(synced, occurrenceTs)
            localNode.upsertEvent(synced)
            synced
        } else {
            updated
        }

        reminderNode.rebuildForEvent(stored)
        return stored
    }

    fun deleteRecurringOccurrenceByExdate(parentEventId: Long, exdateUtc: String, syncToSystem: Boolean = true): Event? {
        val ts = runCatching {
            Instant.from(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssX").parse(exdateUtc)).epochSecond
        }.getOrDefault(0L)
        return deleteRecurringOccurrence(parentEventId, ts, syncToSystem)
    }

    // ══════════════════════════════════════════════════════════════════════
    // EventType
    // ══════════════════════════════════════════════════════════════════════

    fun createOrUpdateEventType(eventType: EventType): Long = localNode.upsertEventType(eventType)

    // ══════════════════════════════════════════════════════════════════════
    // 查询
    // ══════════════════════════════════════════════════════════════════════

    fun getEvents(): List<Event> = localNode.getEvents().map { it.withInferredTag() }

    fun getEventsInRange(fromTS: Long, toTS: Long): List<Event> = localNode.getEventsInRange(fromTS, toTS).map { it.withInferredTag() }

    fun getEvent(id: Long): Event? = localNode.getEvent(id)?.withInferredTag()

    fun getEventTypes(): List<EventType> = localNode.getEventTypes()

    fun getScheduledReminderCount(eventId: Long): Int = reminderNode.getScheduledReminderCount(eventId)

    // ══════════════════════════════════════════════════════════════════════
    // 归档
    // ══════════════════════════════════════════════════════════════════════

    fun archiveEvent(eventId: Long) {
        val event = localNode.getEvent(eventId) ?: return
        localNode.archiveEvent(eventId, nowSeconds())
        event.id?.let { reminderNode.cancelForEvent(it) }
    }

    /**
     * 归档重复日程的某个实例。
     * 先物化为子事件，再归档。
     */
    fun archiveOccurrence(parentId: Long, occurrenceTs: Long, syncToSystem: Boolean = true) {
        val childId = materializeOccurrence(parentId, occurrenceTs, syncToSystem) ?: return
        localNode.archiveEvent(childId, nowSeconds())
        reminderNode.cancelForEvent(childId)
    }

    fun restoreEvent(eventId: Long) {
        val archivedEvent = localNode.getEvent(eventId) ?: return
        if (restoreArchivedOccurrenceToSeries(archivedEvent)) return

        localNode.restoreEvent(eventId)
        val restored = localNode.getEvent(eventId) ?: return
        reminderNode.rebuildForEvent(restored)
    }

    fun deleteArchivedEvent(eventId: Long, deleteFromSystem: Boolean = true) {
        deleteEvent(eventId, deleteFromSystem)
    }

    fun clearAllArchives(deleteFromSystem: Boolean = true) {
        val archived = localNode.getArchivedEvents()
        archived.forEach { event ->
            event.id?.let { deleteEvent(it, deleteFromSystem) }
        }
    }

    /**
     * 自动归档 endTS < beforeTs 的未归档已过期事件。
     * @return 归档的事件数量
     */
    fun autoArchiveExpiredEvents(beforeTs: Long): Int {
        val now = nowSeconds()
        val events = localNode.getEvents()
        var count = 0
        events.forEach { event ->
            val id = event.id ?: return@forEach
            // 只归档非重复母体、已过期且未归档的事件
            if (event.archivedAt == null && event.endTS < beforeTs && !event.isRecurring) {
                localNode.archiveEvent(id, now)
                reminderNode.cancelForEvent(id)
                count++
            }
        }
        return count
    }

    fun getArchivedEvents(): List<Event> = localNode.getArchivedEvents()

    fun getActiveEventCount(): Int = localNode.getActiveEventCount()

    fun getTotalEventCount(): Int = localNode.getTotalEventCount()

    // ══════════════════════════════════════════════════════════════════════
    // 日历同步
    // ══════════════════════════════════════════════════════════════════════

    fun manualSyncNow() {
        val enabled = configStore.isSyncEnabled()
        val ids = configStore.getSyncedCalendarIdsRaw()
        Log.d("SyncChain", "manualSyncNow enabled=$enabled ids=$ids")
        if (!enabled) {
            Log.d("SyncChain", "ABORTED manualSyncNow - sync disabled")
            return
        }
        val syncToken = SyncLoopGuard.beginPullSync()
        try {
            syncNode.refreshCalendars(ids, manual = true)
            syncNode.recheckCalendars(scheduleNextCalDAVSync = true)
            val events = localNode.getEvents()
            Log.d("SyncChain", "manualSyncNow completed localEvents=${events.size}")
            reconcileNotificationsFromStore()
        } finally {
            SyncLoopGuard.endPullSync()
            @Suppress("UNUSED_VARIABLE")
            val ignored = syncToken
        }
    }

    fun setSyncEnabled(enabled: Boolean) {
        configStore.setSyncEnabled(enabled)
        syncNode.scheduleSync(enabled)
    }

    fun setSyncedCalendarIds(ids: String) {
        configStore.setSyncedCalendarIdsRaw(ids)
    }

    fun onScheduledSyncTick() {
        val enabled = configStore.isSyncEnabled()
        val ids = configStore.getSyncedCalendarIdsRaw()
        Log.d("SyncChain", "onScheduledSyncTick enabled=$enabled ids=$ids")
        if (!enabled) {
            Log.d("SyncChain", "ABORTED onScheduledSyncTick - sync disabled")
            return
        }
        val syncToken = SyncLoopGuard.beginPullSync()
        try {
            syncNode.refreshCalendars(ids, manual = false)
            syncNode.recheckCalendars(scheduleNextCalDAVSync = true)
            val events = localNode.getEvents()
            Log.d("SyncChain", "onScheduledSyncTick completed localEvents=${events.size}")
            reconcileNotificationsFromStore()
        } finally {
            SyncLoopGuard.endPullSync()
            @Suppress("UNUSED_VARIABLE")
            val ignored = syncToken
        }
    }

    fun onSystemCalendarChanged() {
        val enabled = configStore.isSyncEnabled()
        Log.d("SyncChain", "onSystemCalendarChanged enabled=$enabled")
        if (!enabled) {
            Log.d("SyncChain", "ABORTED onSystemCalendarChanged - sync disabled")
            return
        }
        val syncToken = SyncLoopGuard.beginPullSync()
        try {
            syncNode.recheckCalendars(scheduleNextCalDAVSync = false)
            val events = localNode.getEvents()
            Log.d("SyncChain", "onSystemCalendarChanged completed localEvents=${events.size}")
            reconcileNotificationsFromStore()
        } finally {
            SyncLoopGuard.endPullSync()
            @Suppress("UNUSED_VARIABLE")
            val ignored = syncToken
        }
    }

    /**
     * 窗口级通知刷新：展开重复实例，按规则注册/注销通知。
     * 应在事件变更后异步调用。
     */
    fun refreshNotificationsForWindow(
        displayItems: List<com.antgskds.calendarassistant.data.model.ScheduleDisplayItem>
    ) {
        val events = localNode.getEvents()
        val parentMap = events.filter { it.isRecurring }.associateBy { it.id ?: 0L }
        reminderNode.refreshForWindow(displayItems, parentMap)
    }

    fun reconcileNotificationsFromStore(windowDays: Long = 7L) {
        val today = LocalDate.now()
        val events = localNode.getEvents()
            .map { it.withInferredTag() }
            .filter { it.archivedAt == null }
        reminderNode.reconcileForEvents(events)

        val displayItems = ScheduleDisplayHelper.buildDisplayItems(events, today, today.plusDays(windowDays))
        val parentMap = events.filter { it.isRecurring }.associateBy { it.id ?: 0L }
        reminderNode.refreshForWindow(displayItems, parentMap)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 内部方法
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 统一物化入口：将虚拟重复实例落地为真实子事件。
     *
     * 如果该 occurrence 已有子事件，直接返回其 id。
     * 否则在事务内：给父事件加 EXDATE + 创建子事件。
     *
     * @return 子事件的 id（新建或已有）
     */
    private fun materializeOccurrence(parentId: Long, occurrenceTs: Long, syncToSystem: Boolean = true): Long? {
        val parent = localNode.getEvent(parentId) ?: return null
        val duration = parent.endTS - parent.startTS
        val parentTag = inferEventTagFromDescription(parent.description, parent.tag)

        // 检查是否已有子事件
        val existing = localNode.getChildEventWithParentAndStart(parentId, occurrenceTs)
        if (existing != null) return existing.id

        // 原子操作：EXDATE + 创建子事件
        return localNode.runInTransaction {
            val exdateUtc = formatExdateUtc(occurrenceTs)
            val updatedParent = parent.copy(
                tag = parentTag,
                exdates = (parent.exdates + exdateUtc).distinct(),
                lastUpdated = nowSeconds()
            )
            localNode.upsertEvent(updatedParent)

            val child = parent.copy(
                id = null,
                startTS = occurrenceTs,
                endTS = occurrenceTs + duration,
                parentId = parentId,
                tag = parentTag,
                rrule = "",
                exdates = emptyList(),
                importId = "",
                lastUpdated = nowSeconds()
            )
            val childId = localNode.upsertEvent(child)

            // 事务外的同步操作
            if (allowSystemPush(syncToSystem) && !isNoteTag(updatedParent.tag)) {
                val syncedParent = syncNode.updateToSystem(updatedParent)
                localNode.upsertEvent(syncedParent)
                val childWithId = child.copy(id = childId)
                val syncedChild = syncNode.insertToSystem(childWithId)
                localNode.upsertEvent(syncedChild)
            }
            reminderNode.rebuildForEvent(updatedParent)

            childId
        }
    }

    private fun restoreArchivedOccurrenceToSeries(event: Event): Boolean {
        val eventId = event.id ?: return false
        if (event.archivedAt == null || event.parentId == 0L || event.rrule.isNotBlank()) return false

        val parent = localNode.getEvent(event.parentId) ?: return false
        if (!parent.isRecurring || parent.archivedAt != null) return false

        val occurrenceExdate = formatExdateUtc(event.startTS)
        if (occurrenceExdate !in parent.exdates) return false
        if (!isUnchangedMaterializedOccurrence(event, parent)) return false

        val updatedParent = parent.copy(
            exdates = parent.exdates.filterNot { it == occurrenceExdate },
            lastUpdated = nowSeconds()
        )
        localNode.upsertEvent(updatedParent)

        val storedParent = if (allowSystemPush(true) && !isNoteTag(updatedParent.tag)) {
            if (!isNoteTag(event.tag)) {
                syncNode.deleteFromSystem(event)
            }
            val synced = syncNode.updateToSystem(updatedParent)
            localNode.upsertEvent(synced)
            synced
        } else {
            updatedParent
        }

        localNode.deleteEvent(eventId)
        reminderNode.cancelForEvent(eventId)
        reminderNode.rebuildForEvent(storedParent)
        return true
    }

    private fun isUnchangedMaterializedOccurrence(child: Event, parent: Event): Boolean {
        val duration = parent.endTS - parent.startTS
        return child.endTS == child.startTS + duration &&
            child.title == parent.title &&
            child.location == parent.location &&
            child.description == parent.description &&
            child.reminder1Minutes == parent.reminder1Minutes &&
            child.reminder2Minutes == parent.reminder2Minutes &&
            child.reminder3Minutes == parent.reminder3Minutes &&
            child.reminder1Type == parent.reminder1Type &&
            child.reminder2Type == parent.reminder2Type &&
            child.reminder3Type == parent.reminder3Type &&
            child.attendees == parent.attendees &&
            child.timeZone == parent.timeZone &&
            child.flags == parent.flags &&
            child.eventType == parent.eventType &&
            child.availability == parent.availability &&
            child.color == parent.color &&
            child.type == parent.type &&
            child.state == parent.state &&
            child.tag == parent.tag &&
            child.exdates.isEmpty()
    }

    private fun editThisOccurrence(parent: Event, editedOccurrence: Event, occurrenceTs: Long, syncToSystem: Boolean): Long? {
        val parentId = parent.id ?: return null

        // 1. 物化实例
        val childId = materializeOccurrence(parentId, occurrenceTs, syncToSystem) ?: return null

        // 2. 将用户编辑应用到物化后的子事件
        //    时间自动对齐到 occurrenceTs（如果调用方没改时间，就用 occurrence 的时间）
        val duration = parent.endTS - parent.startTS
        val effectiveStartTS = if (editedOccurrence.startTS == parent.startTS) occurrenceTs else editedOccurrence.startTS
        val effectiveEndTS = if (editedOccurrence.endTS == parent.endTS) occurrenceTs + duration else editedOccurrence.endTS

        val childEvent = localNode.getEvent(childId) ?: return childId
        val updated = childEvent.copy(
            title = editedOccurrence.title,
            startTS = effectiveStartTS,
            endTS = effectiveEndTS,
            location = editedOccurrence.location,
            description = editedOccurrence.description,
            tag = editedOccurrence.tag,
            color = editedOccurrence.color,
            reminder1Minutes = editedOccurrence.reminder1Minutes,
            reminder2Minutes = editedOccurrence.reminder2Minutes,
            reminder3Minutes = editedOccurrence.reminder3Minutes,
            lastUpdated = nowSeconds()
        )
        localNode.upsertEvent(updated)
        if (allowSystemPush(syncToSystem) && !isNoteTag(updated.tag)) {
            val synced = syncNode.updateToSystem(updated)
            localNode.upsertEvent(synced)
        }
        reminderNode.rebuildForEvent(updated)
        return childId
    }

    private fun editThisAndFuture(parent: Event, editedOccurrence: Event, occurrenceTs: Long, syncToSystem: Boolean): Long? {
        if (occurrenceTs <= parent.startTS) {
            val replacement = editedOccurrence.copy(id = parent.id, parentId = 0)
            updateEvent(replacement, syncToSystem)
            return parent.id
        }

        val updatedParent = parent.copy(
            rrule = applyUntilToRRule(parent.rrule, occurrenceTs - 1),
            lastUpdated = nowSeconds()
        )
        localNode.upsertEvent(updatedParent)
        if (allowSystemPush(syncToSystem) && !isNoteTag(updatedParent.tag)) {
            val synced = syncNode.updateToSystem(updatedParent)
            localNode.upsertEvent(synced)
        }
        reminderNode.rebuildForEvent(updatedParent)

        val childToDelete = localNode.getChildEventsFrom(parent.id ?: 0L, occurrenceTs)
        childToDelete.forEach { child ->
            if (allowSystemPush(syncToSystem)) {
                syncNode.deleteFromSystem(child)
            }
            child.id?.let { reminderNode.cancelForEvent(it) }
        }
        localNode.deleteChildEventsFrom(parent.id ?: 0L, occurrenceTs)

        val newSeries = editedOccurrence.copy(
            id = null,
            parentId = 0,
            source = if (editedOccurrence.source.isBlank()) parent.source else editedOccurrence.source
        )
        return createEvent(newSeries, syncToSystem)
    }

    private fun applyUntilToRRule(rrule: String, untilTs: Long): String {
        if (rrule.isBlank()) return rrule
        val parts = rrule.split(';')
            .filterNot { it.startsWith("UNTIL=") || it.startsWith("COUNT=") }
            .toMutableList()
        parts.add("UNTIL=${formatExdateUtc(untilTs)}")
        return parts.joinToString(";")
    }

    private fun formatExdateUtc(occurrenceTs: Long): String {
        return DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC)
            .format(Instant.ofEpochSecond(occurrenceTs))
    }

    private fun allowSystemPush(syncRequested: Boolean): Boolean {
        return syncRequested && configStore.isSyncEnabled() && !SyncLoopGuard.isPullSyncInProgress()
    }

    private data class SystemPushDecision(
        val allow: Boolean,
        val reason: String
    )

    private fun evaluateSystemPush(syncRequested: Boolean, tag: String): SystemPushDecision {
        if (isNoteTag(tag)) return SystemPushDecision(false, "tag-note")
        if (!syncRequested) return SystemPushDecision(false, "sync-requested-false")
        if (!configStore.isSyncEnabled()) return SystemPushDecision(false, "sync-disabled")
        if (SyncLoopGuard.isPullSyncInProgress()) return SystemPushDecision(false, "pull-sync-in-progress")
        return SystemPushDecision(true, "ok")
    }

    private fun changeEventState(
        eventId: Long,
        occurrenceTs: Long?,
        targetState: Int,
        syncToSystem: Boolean
    ): OperationResult<Long> {
        if (eventId <= 0L) {
            return OperationResult.Failure(OperationErrorCode.INVALID_ARGUMENT, "eventId must be > 0")
        }

        val event = localNode.getEvent(eventId)
            ?: return OperationResult.Failure(OperationErrorCode.NOT_FOUND, "Event not found")

        val effectiveTag = inferEventTagFromDescription(event.description, event.tag)
        val validation = validateTargetStateForTag(effectiveTag, targetState)
        if (validation != null) {
            return OperationResult.Failure(validation)
        }

        val normalizedEvent = if (event.tag == effectiveTag) event else event.copy(tag = effectiveTag)

        val now = nowSeconds()

        // 非重复事件 或 已有子事件 或 没有 occurrenceTs → 直接改状态
        if (normalizedEvent.rrule.isBlank() || normalizedEvent.parentId != 0L || occurrenceTs == null) {
            val updated = applyState(normalizedEvent, targetState, now)
            return runCatching {
                updateEvent(updated, syncToSystem)
                OperationResult.Success(updated.id ?: eventId)
            }.getOrElse {
                OperationResult.Failure(OperationErrorCode.SYNC_FAILED, it.message)
            }
        }

        // 重复母事件 + 有 occurrenceTs → 先物化，再改状态
        return runCatching {
            val childId = materializeOccurrence(eventId, occurrenceTs, syncToSystem)
                ?: return OperationResult.Failure(OperationErrorCode.SYNC_FAILED, "Failed to materialize occurrence")
            val child = localNode.getEvent(childId)
                ?: return OperationResult.Failure(OperationErrorCode.NOT_FOUND, "Materialized child not found")

            val normalizedChild = child.copy(tag = inferEventTagFromDescription(child.description, child.tag))
            val childValidation = validateTargetStateForTag(normalizedChild.tag, targetState)
            if (childValidation != null) {
                return OperationResult.Failure(childValidation)
            }

            val updated = applyState(normalizedChild, targetState, now)
            updateEvent(updated, syncToSystem)
            OperationResult.Success(childId)
        }.getOrElse {
            OperationResult.Failure(OperationErrorCode.SYNC_FAILED, it.message)
        }
    }

    private fun validateTargetStateForTag(tagRaw: String, targetState: Int): OperationErrorCode? {
        val tag = tagRaw.lowercase()
        if (tag == EventTags.COURSE) return OperationErrorCode.STATE_NOT_ALLOWED_FOR_TAG
        val isTransit = tag == TAG_FLIGHT || tag == TAG_TRAIN
        val allowed = if (isTransit) {
            targetState == STATE_PENDING || targetState == STATE_CHECKED_IN
        } else {
            targetState == STATE_PENDING || targetState == STATE_COMPLETED
        }
        return if (allowed) null else OperationErrorCode.STATE_NOT_ALLOWED_FOR_TAG
    }

    private fun applyState(event: Event, targetState: Int, now: Long): Event {
        val isTaskCompleted = targetState == STATE_COMPLETED && event.type == TYPE_TASK
        val taskFlags = if (isTaskCompleted) event.flags or FLAG_TASK_COMPLETED else event.flags and FLAG_TASK_COMPLETED.inv()
        // 完成 = 立即结束，无论实例是过去还是未来，统一 endTS = now
        val endTs = if (targetState == STATE_COMPLETED) now else event.endTS
        return event.copy(
            state = targetState,
            endTS = endTs,
            flags = taskFlags
        )
    }

    private fun Event.withInferredTag(): Event {
        val inferredTag = inferEventTagFromDescription(description, tag)
        return if (tag == inferredTag) this else copy(tag = inferredTag)
    }

    private fun nowSeconds(): Long = System.currentTimeMillis() / 1000L
}
