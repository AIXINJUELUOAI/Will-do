package com.antgskds.calendarassistant.core.center

import com.antgskds.calendarassistant.core.undo.UndoManager
import com.antgskds.calendarassistant.calendar.helpers.STATE_CHECKED_IN
import com.antgskds.calendarassistant.calendar.helpers.STATE_COMPLETED
import com.antgskds.calendarassistant.calendar.helpers.STATE_PENDING
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.EventTags
import com.antgskds.calendarassistant.calendar.models.isCheckedIn
import com.antgskds.calendarassistant.calendar.models.isCompleted
import com.antgskds.calendarassistant.calendar.models.isCourse
import com.antgskds.calendarassistant.calendar.models.isRetiredNoteTag
import com.antgskds.calendarassistant.calendar.models.isTransit
import com.antgskds.calendarassistant.core.model.RecurringMode
import com.antgskds.calendarassistant.core.operation.OperationResult
import com.antgskds.calendarassistant.core.util.stripSourceImageMarkers
import com.antgskds.calendarassistant.data.model.ScheduleDisplayItem
import com.antgskds.calendarassistant.data.model.ScheduleDisplayItem.ActionTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 日程业务中心 — UI 层与 willdo CalendarCenter 之间的桥梁。
 *
 * 直接暴露 willdo Event，不再经过 MyEvent 转换。
 */
class ScheduleCenter(
    private val calendarCenter: CalendarCenter,
    private val appScope: CoroutineScope
) {
    var onScheduleChanged: (() -> Unit)? = null

    val undoManager = UndoManager(appScope)

    private val _events = MutableStateFlow<List<Event>>(emptyList())
    val events: StateFlow<List<Event>> = _events.asStateFlow()

    private val _archivedEvents = MutableStateFlow<List<Event>>(emptyList())
    val archivedEvents: StateFlow<List<Event>> = _archivedEvents.asStateFlow()

    data class PendingItemState(
        val keys: Set<String>
    )

    private val _pendingItemStates = MutableStateFlow<Map<String, PendingItemState>>(emptyMap())
    val pendingItemStates: StateFlow<Map<String, PendingItemState>> = _pendingItemStates.asStateFlow()
    private val statusOperationInFlight = AtomicBoolean(false)

    private data class StateUndoSnapshot(
        val action: ActionTarget,
        val itemStableKey: String,
        val parentBefore: Event? = null,
        val targetBefore: Event? = null,
        val createdChildId: Long? = null,
        val pendingKeys: Set<String> = setOf(itemStableKey)
    )

    // ── 数据刷新 ─────────────────────────────────────────────────

    fun refreshEvents() {
        appScope.launch(Dispatchers.IO) {
            _events.value = calendarCenter.getEvents().filter { it.archivedAt == null && !isRetiredNoteTag(it.tag) }
            scheduleNotificationRefresh()
            onScheduleChanged?.invoke()
        }
    }

    fun refreshArchivedEvents() {
        appScope.launch(Dispatchers.IO) {
            _archivedEvents.value = calendarCenter.getArchivedEvents().filter { it.archivedAt != null && !isRetiredNoteTag(it.tag) }
        }
    }

    fun refreshAll() {
        refreshEvents()
        refreshArchivedEvents()
        scheduleNotificationRefresh()
    }

    suspend fun getLatestActiveEvents(): List<Event> = withContext(Dispatchers.IO) {
        calendarCenter.getEvents().filter { it.archivedAt == null && !isRetiredNoteTag(it.tag) }
    }

    // ── 通知调度 ─────────────────────────────────────────────────

    private var notificationRefreshJob: Job? = null
    private val NOTIFICATION_DEBOUNCE_MS = 300L
    private val NOTIFICATION_WINDOW_DAYS = 7L

    /**
     * 触发通知窗口刷新（带 300ms debounce）。
     * 任何事件变更后调用，内部自动去重。
     */
    fun scheduleNotificationRefresh() {
        notificationRefreshJob?.cancel()
        notificationRefreshJob = appScope.launch(Dispatchers.IO) {
            delay(NOTIFICATION_DEBOUNCE_MS)
            performNotificationRefresh()
        }
    }

    /**
     * 实际执行通知窗口刷新。
     * 展开 [now, now+7天] 的实例，按规则注册/注销通知。
     */
    private fun performNotificationRefresh() {
        val today = java.time.LocalDate.now()
        val windowEnd = today.plusDays(NOTIFICATION_WINDOW_DAYS)
        val items = ScheduleDisplayHelper.buildDisplayItems(_events.value, today, windowEnd)
        calendarCenter.refreshNotificationsForWindow(items)
    }

    // ── CRUD ─────────────────────────────────────────────────────

    suspend fun addEvent(event: Event): Long = withContext(Dispatchers.IO) {
        val id = calendarCenter.createEvent(event)
        refreshEvents()
        id
    }

    suspend fun updateEvent(event: Event) {
        val eventId = event.id
        if (eventId != null && eventId >= 0) {
            _events.value = _events.value.map { current ->
                if (current.id == eventId) event else current
            }
            scheduleNotificationRefresh()
        }

        withContext(Dispatchers.IO) {
            calendarCenter.updateEvent(event)
            refreshEvents()
        }
    }

    suspend fun deleteEvent(eventId: Long) = withContext(Dispatchers.IO) {
        calendarCenter.deleteEvent(eventId)
        refreshEvents()
    }

    /** 带延迟提交的删除 */
    fun deleteEventWithUndo(event: Event) {
        val eventId = event.id ?: return
        _events.value = _events.value.filter { it.id != eventId }

        undoManager.submit(UndoManager.PendingAction(
            id = "delete-$eventId",
            label = "已删除「${event.title}」",
            commitAction = { withContext(Dispatchers.IO) { calendarCenter.deleteEvent(eventId) } },
            rollbackAction = { refreshEvents() }
        ))
    }

    // ── 状态操作 (带 Undo) ────────────────────────────────────────

    fun completeEventWithUndo(event: Event) {
        val eventId = event.id ?: return
        applyStateToUiList(eventId, com.antgskds.calendarassistant.calendar.helpers.STATE_COMPLETED)
        undoManager.submit(UndoManager.PendingAction(
            id = "complete-$eventId",
            label = "已完成「${event.title}」",
            commitAction = { withContext(Dispatchers.IO) { calendarCenter.completeEvent(eventId); refreshEvents() } },
            rollbackAction = { refreshEvents() }
        ))
    }

    fun checkInEventWithUndo(event: Event) {
        val eventId = event.id ?: return
        applyStateToUiList(eventId, com.antgskds.calendarassistant.calendar.helpers.STATE_CHECKED_IN)
        undoManager.submit(UndoManager.PendingAction(
            id = "checkin-$eventId",
            label = "已签到「${event.title}」",
            commitAction = { withContext(Dispatchers.IO) { calendarCenter.checkInEvent(eventId); refreshEvents() } },
            rollbackAction = { refreshEvents() }
        ))
    }

    fun markPendingWithUndo(event: Event) {
        val eventId = event.id ?: return
        applyStateToUiList(eventId, com.antgskds.calendarassistant.calendar.helpers.STATE_PENDING)
        undoManager.submit(UndoManager.PendingAction(
            id = "pending-$eventId",
            label = "已恢复「${event.title}」",
            commitAction = { withContext(Dispatchers.IO) { calendarCenter.markPending(eventId); refreshEvents() } },
            rollbackAction = { refreshEvents() }
        ))
    }

    /** 根据 tag 执行首选操作 */
    @Deprecated("Use performPrimaryActionOnItem(ScheduleDisplayItem) or completeItem/checkInItem(ActionTarget) instead")
    fun performPrimaryAction(event: Event) {
        if (event.isTransit) {
            if (event.isCheckedIn || event.isCompleted) markPendingWithUndo(event)
            else checkInEventWithUndo(event)
        } else {
            if (event.isCompleted) markPendingWithUndo(event)
            else completeEventWithUndo(event)
        }
    }

    /** 批量完成所有活跃取件码 */
    suspend fun completeAllActivePickups(): Int {
        val pickups = _events.value.filter {
            it.tag == com.antgskds.calendarassistant.calendar.models.EventTags.PICKUP && !it.isCompleted
        }
        pickups.forEach { event ->
            val eid = event.id ?: return@forEach
            withContext(Dispatchers.IO) { calendarCenter.completeEvent(eid) }
        }
        refreshEvents()
        return pickups.size
    }

    // ── 重复事件 ──────────────────────────────────────────────────

    suspend fun editRecurringEvent(
        parentEventId: Long, editedEvent: Event,
        mode: RecurringMode, occurrenceTs: Long
    ): Long? = withContext(Dispatchers.IO) {
        calendarCenter.editRecurringEvent(parentEventId, editedEvent, mode, occurrenceTs)
            .also { refreshEvents() }
    }

    suspend fun deleteRecurringEvent(
        parentEventId: Long, mode: RecurringMode, occurrenceTs: Long
    ) = withContext(Dispatchers.IO) {
        calendarCenter.deleteRecurringEvent(parentEventId, mode, occurrenceTs)
        refreshEvents()
    }

    // ── 归档 ──────────────────────────────────────────────────────

    suspend fun archiveEvent(eventId: Long) = withContext(Dispatchers.IO) {
        calendarCenter.archiveEvent(eventId); refreshAll()
    }

    suspend fun restoreEvent(eventId: Long) = withContext(Dispatchers.IO) {
        calendarCenter.restoreEvent(eventId); refreshAll()
    }

    suspend fun deleteArchivedEvent(eventId: Long) = withContext(Dispatchers.IO) {
        calendarCenter.deleteArchivedEvent(eventId); refreshAll()
    }

    suspend fun clearAllArchives() = withContext(Dispatchers.IO) {
        calendarCenter.clearAllArchives(); refreshAll()
    }

    suspend fun autoArchiveExpiredEvents(): Int = withContext(Dispatchers.IO) {
        val threshold = System.currentTimeMillis() / 1000L
        calendarCenter.autoArchiveExpiredEvents(threshold).also { if (it > 0) refreshAll() }
    }

    // ── 统计 ──────────────────────────────────────────────────────

    fun getEventsCount(): Int = calendarCenter.getActiveEventCount()
    fun getTotalEventsCount(): Int = calendarCenter.getTotalEventCount()

    // ══════════════════════════════════════════════════════════════════════
    // 新 API：面向 ScheduleDisplayItem 的操作
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 将数据库事件列表展开为展示项（含重复实例展开）。
     * UI 用这个替代直接读 Event 列表。
     */
    fun expandForDisplay(from: java.time.LocalDate, to: java.time.LocalDate): List<ScheduleDisplayItem> {
        return ScheduleDisplayHelper.buildDisplayItems(_events.value, from, to)
    }

    fun applyPendingStateOverrides(items: List<ScheduleDisplayItem>): List<ScheduleDisplayItem> {
        return items
    }

    // ── 编辑草稿 API ──────────────────────────────────────────────

    /**
     * 为新建事件准备编辑草稿。
     */
    fun prepareNewEvent(): com.antgskds.calendarassistant.data.model.EditDraft {
        val now = java.time.LocalDateTime.now().withSecond(0).withNano(0)
        return com.antgskds.calendarassistant.data.model.EditDraft(
            startDate = now.toLocalDate(),
            startTime = now.toLocalTime(),
            endDate = now.toLocalDate(),
            endTime = now.plusHours(1).toLocalTime()
        )
    }

    /**
     * 为编辑单次事件准备草稿：从内存事件列表读取（避免主线程 DB 访问）。
     */
    fun prepareEditSingle(eventId: Long): com.antgskds.calendarassistant.data.model.EditDraft? {
        val event = _events.value.find { it.id == eventId } ?: return null
        return eventToEditDraft(event, null)
    }

    /**
     * 为编辑重复日程的某次实例准备草稿：从内存读取母事件，将时间调整到 occurrenceTs。
     */
    fun prepareEditRecurringOccurrence(
        parentId: Long,
        occurrenceTs: Long
    ): com.antgskds.calendarassistant.data.model.EditDraft? {
        val parent = _events.value.find { it.id == parentId } ?: return null
        val duration = parent.endTS - parent.startTS
        return eventToEditDraft(
            event = parent,
            hint = "本次修改将脱离重复系列",
            overrideStartTS = occurrenceTs,
            overrideEndTS = occurrenceTs + duration
        )
    }

    private fun eventToEditDraft(
        event: Event,
        hint: String?,
        overrideStartTS: Long = event.startTS,
        overrideEndTS: Long = event.endTS
    ): com.antgskds.calendarassistant.data.model.EditDraft {
        val zone = try { java.time.ZoneId.of(event.getTimeZoneString()) } catch (_: Exception) { java.time.ZoneId.systemDefault() }
        val start = java.time.Instant.ofEpochSecond(overrideStartTS).atZone(zone).toLocalDateTime()
        val end = java.time.Instant.ofEpochSecond(overrideEndTS).atZone(zone).toLocalDateTime()
        val reminders = listOfNotNull(
            event.reminder1Minutes.takeIf { it != com.antgskds.calendarassistant.calendar.helpers.REMINDER_OFF },
            event.reminder2Minutes.takeIf { it != com.antgskds.calendarassistant.calendar.helpers.REMINDER_OFF },
            event.reminder3Minutes.takeIf { it != com.antgskds.calendarassistant.calendar.helpers.REMINDER_OFF }
        )
        return com.antgskds.calendarassistant.data.model.EditDraft(
            title = event.title,
            startDate = start.toLocalDate(),
            startTime = start.toLocalTime(),
            endDate = end.toLocalDate(),
            endTime = end.toLocalTime(),
            location = event.location,
            description = stripSourceImageMarkers(event.description),
            tag = event.tag,
            color = event.color,
            rrule = event.rrule,
            reminders = reminders,
            isRecurring = event.isRecurring,
            eventId = event.id,
            editHint = hint
        )
    }

    // ── Patch 操作 API ────────────────────────────────────────────

    /**
     * 从 patch 新建事件。
     */
    suspend fun addEventFromPatch(patch: com.antgskds.calendarassistant.data.model.EventPatch): Long = withContext(Dispatchers.IO) {
        val event = patchToNewEvent(patch)
        val id = calendarCenter.createEvent(event)
        refreshEvents()
        id
    }

    suspend fun addEventFromPatchWithResult(patch: com.antgskds.calendarassistant.data.model.EventPatch): Long {
        return addEventFromPatch(patch)
    }

    /**
     * 用 patch 更新已存在的单次事件。
     * 内部从 DB 读取完整事件，只覆盖用户可编辑字段，保留同步身份。
     */
    suspend fun updateSingleFromPatch(eventId: Long, patch: com.antgskds.calendarassistant.data.model.EventPatch) = withContext(Dispatchers.IO) {
        val existing = calendarCenter.getEvent(eventId) ?: return@withContext
        val merged = applyPatchToEvent(existing, patch)
        calendarCenter.updateEvent(merged)
        refreshEvents()
    }

    /**
     * 用 patch 编辑重复日程（按 mode 作用到本次/本次及以后/全部）。
     */
    suspend fun editRecurringFromPatch(
        parentId: Long,
        occurrenceTs: Long,
        mode: RecurringMode,
        patch: com.antgskds.calendarassistant.data.model.EventPatch
    ): Long? = withContext(Dispatchers.IO) {
        val parent = calendarCenter.getEvent(parentId) ?: return@withContext null
        val effectivePatch = if (mode == RecurringMode.ALL) {
            val startOffset = patch.startTS - occurrenceTs
            val duration = (patch.endTS - patch.startTS).coerceAtLeast(0L)
            val remappedStart = parent.startTS + startOffset
            patch.copy(
                startTS = remappedStart,
                endTS = remappedStart + duration
            )
        } else {
            patch
        }

        val merged = applyPatchToEvent(parent, effectivePatch)
        val editedEvent = when (mode) {
            RecurringMode.THIS -> merged.copy(
                id = null,
                importId = "",
                parentId = parentId,
                rrule = "",
                exdates = emptyList()
            )
            RecurringMode.THIS_AND_FUTURE -> merged.copy(
                id = null,
                importId = "",
                parentId = parentId,
                rrule = parent.rrule,
                exdates = parent.exdates
            )
            RecurringMode.ALL -> merged.copy(
                id = parentId,
                parentId = 0,
                importId = parent.importId,
                exdates = if (merged.rrule.isBlank()) emptyList() else parent.exdates
            )
        }
        calendarCenter.editRecurringEvent(parentId, editedEvent, mode, occurrenceTs)
            .also { refreshEvents() }
    }

    /**
     * 从 UI 删除重复日程（按 mode 路由）。
     */
    suspend fun deleteRecurringFromUi(
        parentId: Long,
        occurrenceTs: Long,
        mode: RecurringMode
    ) = withContext(Dispatchers.IO) {
        calendarCenter.deleteRecurringEvent(parentId, mode, occurrenceTs)
        refreshEvents()
    }

    /**
     * 对展示项执行首选状态操作。
     * train/flight → checkIn, 其他 → complete。
     */
    fun performPrimaryActionOnItem(item: ScheduleDisplayItem) {
        if (item.tag == EventTags.COURSE) return
        if (item.isTransit) {
            checkInItem(item.action)
        } else {
            completeItem(item.action)
        }
    }

    /**
     * 完成事件（单次或重复实例）。
     * 重复实例会先物化为子事件再完成。
     * 完成 = state=COMPLETED + endTS=now
     */
    fun completeItem(target: ActionTarget) {
        appScope.launch(Dispatchers.IO) {
            when (target) {
                is ActionTarget.Single -> {
                    if (calendarCenter.getEvent(target.eventId)?.isCourse == true) return@launch
                    calendarCenter.completeEvent(target.eventId)
                }
                is ActionTarget.RecurringOccurrence -> {
                    if (calendarCenter.getEvent(target.parentId)?.isCourse == true) return@launch
                    calendarCenter.completeEvent(target.parentId, target.occurrenceTs)
                }
            }
            refreshEvents()
        }
    }

    /**
     * 签到事件（仅 train/flight）。
     */
    fun checkInItem(target: ActionTarget) {
        appScope.launch(Dispatchers.IO) {
            when (target) {
                is ActionTarget.Single -> {
                    calendarCenter.checkInEvent(target.eventId)
                }
                is ActionTarget.RecurringOccurrence -> {
                    calendarCenter.checkInEvent(target.parentId, target.occurrenceTs)
                }
            }
            refreshEvents()
        }
    }

    fun markPendingItem(target: ActionTarget) {
        appScope.launch(Dispatchers.IO) {
            when (target) {
                is ActionTarget.Single -> calendarCenter.markPending(target.eventId)
                is ActionTarget.RecurringOccurrence -> calendarCenter.markPending(target.parentId, target.occurrenceTs)
            }
            refreshEvents()
        }
    }

    fun performPrimaryActionOnItemWithUndo(item: ScheduleDisplayItem) {
        if (item.tag == EventTags.COURSE) return
        if (_pendingItemStates.value.containsKey(item.stableKey)) {
            undoStatusAction()
            return
        }
        if (item.isCompleted || item.isCheckedIn) return
        when {
            item.isTransit -> checkInItemWithUndo(item)
            else -> completeItemWithUndo(item)
        }
    }

    fun completeItemWithUndo(item: ScheduleDisplayItem) {
        launchStatusOperation { submitStateItemWithUndo(item, STATE_COMPLETED, "已完成") }
    }

    fun checkInItemWithUndo(item: ScheduleDisplayItem) {
        val label = if (item.tag == EventTags.FLIGHT) "已登机" else "已检票"
        launchStatusOperation { submitStateItemWithUndo(item, STATE_CHECKED_IN, label) }
    }

    fun markPendingItemWithUndo(item: ScheduleDisplayItem) {
        launchStatusOperation { submitStateItemWithUndo(item, STATE_PENDING, "已恢复") }
    }

    fun undoStatusAction() {
        launchStatusOperation { undoManager.undoNow() }
    }

    private fun launchStatusOperation(block: suspend () -> Unit) {
        if (!statusOperationInFlight.compareAndSet(false, true)) return
        appScope.launch(Dispatchers.IO) {
            try {
                block()
            } finally {
                statusOperationInFlight.set(false)
            }
        }
    }

    private suspend fun submitStateItemWithUndo(item: ScheduleDisplayItem, targetState: Int, labelPrefix: String) {
        if (item.tag == EventTags.COURSE) return
        val snapshotBefore = captureStateUndoSnapshot(item) ?: return
        val changedId = applyItemState(item.action, targetState) ?: return
        refreshEvents()

        val pendingKeys = buildSet {
            add(item.stableKey)
            add("single:$changedId")
        }
        val snapshot = snapshotBefore.copy(
            createdChildId = if (item.action is ActionTarget.RecurringOccurrence && snapshotBefore.targetBefore == null) changedId else null,
            pendingKeys = pendingKeys
        )
        setPendingItemState(pendingKeys)
        undoManager.submit(UndoManager.PendingAction(
            id = "state-${item.stableKey}-$targetState",
            label = "$labelPrefix「${item.title}」",
            commitAction = {
                clearPendingItemStates(pendingKeys)
            },
            rollbackAction = {
                try {
                    restoreStateUndoSnapshot(snapshot)
                    refreshEvents()
                } finally {
                    clearPendingItemStates(pendingKeys)
                }
            }
        ))
    }

    private fun captureStateUndoSnapshot(item: ScheduleDisplayItem): StateUndoSnapshot? {
        return when (val target = item.action) {
            is ActionTarget.Single -> {
                val event = calendarCenter.getEvent(target.eventId) ?: return null
                if (event.isCourse) return null
                StateUndoSnapshot(
                    action = target,
                    itemStableKey = item.stableKey,
                    targetBefore = event
                )
            }
            is ActionTarget.RecurringOccurrence -> {
                val parent = calendarCenter.getEvent(target.parentId) ?: return null
                if (parent.isCourse) return null
                val existingChild = calendarCenter.getEvents().firstOrNull {
                    it.parentId == target.parentId && it.startTS == target.occurrenceTs && !it.isRecurring
                }
                StateUndoSnapshot(
                    action = target,
                    itemStableKey = item.stableKey,
                    parentBefore = parent,
                    targetBefore = existingChild
                )
            }
        }
    }

    private fun applyItemState(target: ActionTarget, targetState: Int): Long? {
        val result = when (target) {
            is ActionTarget.Single -> {
                if (calendarCenter.getEvent(target.eventId)?.isCourse == true) return null
                when (targetState) {
                    STATE_COMPLETED -> calendarCenter.completeEvent(target.eventId)
                    STATE_CHECKED_IN -> calendarCenter.checkInEvent(target.eventId)
                    STATE_PENDING -> calendarCenter.markPending(target.eventId)
                    else -> return null
                }
            }
            is ActionTarget.RecurringOccurrence -> {
                if (calendarCenter.getEvent(target.parentId)?.isCourse == true) return null
                when (targetState) {
                    STATE_COMPLETED -> calendarCenter.completeEvent(target.parentId, target.occurrenceTs)
                    STATE_CHECKED_IN -> calendarCenter.checkInEvent(target.parentId, target.occurrenceTs)
                    STATE_PENDING -> calendarCenter.markPending(target.parentId, target.occurrenceTs)
                    else -> return null
                }
            }
        }
        return (result as? OperationResult.Success<Long>)?.data
    }

    private fun restoreStateUndoSnapshot(snapshot: StateUndoSnapshot) {
        when (snapshot.action) {
            is ActionTarget.Single -> {
                snapshot.targetBefore?.let { calendarCenter.updateEvent(it) }
            }
            is ActionTarget.RecurringOccurrence -> {
                val createdChildId = snapshot.createdChildId
                if (createdChildId != null) {
                    calendarCenter.deleteEvent(createdChildId)
                    snapshot.parentBefore?.let { calendarCenter.updateEvent(it) }
                } else {
                    snapshot.targetBefore?.let { calendarCenter.updateEvent(it) }
                    snapshot.parentBefore?.let { calendarCenter.updateEvent(it) }
                }
            }
        }
    }

    private fun setPendingItemState(keys: Set<String>) {
        val pendingState = PendingItemState(keys)
        _pendingItemStates.value = keys.associateWith { pendingState }
    }

    private fun clearPendingItemStates(keys: Set<String>) {
        _pendingItemStates.value = _pendingItemStates.value - keys
    }

    /**
     * 归档事件（单次或重复实例）。
     * 重复实例会先物化为子事件再归档。
     * 归档 = 仅设 archivedAt，不改 endTS。
     */
    suspend fun archiveItem(target: ActionTarget) = withContext(Dispatchers.IO) {
        when (target) {
            is ActionTarget.Single -> {
                calendarCenter.archiveEvent(target.eventId)
            }
            is ActionTarget.RecurringOccurrence -> {
                calendarCenter.archiveOccurrence(target.parentId, target.occurrenceTs)
            }
        }
        refreshAll()
    }

    // ── Patch 转换工具 ────────────────────────────────────────────

    private fun patchToNewEvent(patch: com.antgskds.calendarassistant.data.model.EventPatch): Event {
        return Event(
            id = null,
            title = patch.title,
            startTS = patch.startTS,
            endTS = patch.endTS,
            location = patch.location,
            description = stripSourceImageMarkers(patch.description),
            tag = patch.tag,
            color = patch.color,
            rrule = patch.rrule,
            reminder1Minutes = patch.reminder1Minutes,
            reminder2Minutes = patch.reminder2Minutes,
            reminder3Minutes = patch.reminder3Minutes
        )
    }

    private fun applyPatchToEvent(existing: Event, patch: com.antgskds.calendarassistant.data.model.EventPatch): Event {
        val shouldReactivate = (existing.state == STATE_COMPLETED || existing.state == STATE_CHECKED_IN) &&
            patch.endTS >= System.currentTimeMillis() / 1000L
        return existing.copy(
            title = patch.title,
            startTS = patch.startTS,
            endTS = patch.endTS,
            location = patch.location,
            description = patch.description,
            tag = patch.tag,
            color = patch.color,
            rrule = patch.rrule,
            reminder1Minutes = patch.reminder1Minutes,
            reminder2Minutes = patch.reminder2Minutes,
            reminder3Minutes = patch.reminder3Minutes,
            state = if (shouldReactivate) STATE_PENDING else existing.state
        )
    }

    // ── 内部 ──────────────────────────────────────────────────────

    private fun applyStateToUiList(eventId: Long, newState: Int) {
        _events.value = _events.value.map { e ->
            if (e.id == eventId) e.copy(state = newState) else e
        }
    }
}
