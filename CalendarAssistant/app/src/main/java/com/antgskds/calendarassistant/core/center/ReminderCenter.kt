package com.antgskds.calendarassistant.core.center

import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.*
import android.content.Context
import com.antgskds.calendarassistant.calendar.helpers.STATE_CHECKED_IN
import com.antgskds.calendarassistant.calendar.helpers.STATE_PENDING
import com.antgskds.calendarassistant.core.query.CapsuleRouteMode
import com.antgskds.calendarassistant.core.query.CapsuleRoutingQueryApi
import com.antgskds.calendarassistant.core.event.DomainEventBus
import com.antgskds.calendarassistant.core.event.DomainEventType
import com.antgskds.calendarassistant.core.event.events.CapsuleRefreshPriority
import com.antgskds.calendarassistant.core.event.events.CapsuleRefreshRequestedEvent
import com.antgskds.calendarassistant.core.event.events.ScheduleChangeOrigin
import com.antgskds.calendarassistant.core.event.events.ScheduleChangeType
import com.antgskds.calendarassistant.core.event.events.ScheduleChangedEvent

import com.antgskds.calendarassistant.core.query.SettingsQueryApi
import com.antgskds.calendarassistant.service.notification.NotificationScheduler
import com.antgskds.calendarassistant.store.reminder.ReminderStoreNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate

class ReminderCenter(
    private val appContext: Context,
    private val capsuleCenter: CapsuleCenter,
    private val settingsQueryApi: SettingsQueryApi,
    private val scheduleCenter: ScheduleCenter,
    private val domainEventBus: DomainEventBus,
    private val appScope: CoroutineScope
) {
    private enum class ReminderSyncAction {
        SCHEDULE_ONLY,
        RESCHEDULE,
        CANCEL_ONLY
    }

    private val pendingReminderOps = linkedMapOf<String, ReminderSyncAction>()
    private val pendingCancellationIds = linkedSetOf<Long>()
    private var pendingFullReminderReconcile = false
    private var reminderReconcileJob: Job? = null
    private var eventSubscriptionsStarted = false
    private val reminderNode = ReminderStoreNode(appContext)

    fun reconcileAll() {
        appScope.launch {
            reconcileAllNow()
        }
    }

    suspend fun reconcileAllNow() {
        runFullReminderReconcile()
        refreshCapsuleState()
    }

    fun startEventSubscriptions() {
        if (eventSubscriptionsStarted) return
        eventSubscriptionsStarted = true

        appScope.launch {
            domainEventBus
                .eventsOfType<ScheduleChangedEvent>(DomainEventType.SCHEDULE_CHANGED)
                .collect {
                    enqueueReminderReconcile(it.payload)
                    domainEventBus.emit(
                        eventType = DomainEventType.CAPSULE_REFRESH_REQUESTED,
                        traceId = it.traceId,
                        source = "reminder_center_schedule_bridge",
                        entityKey = "capsule_refresh_schedule_changed",
                        payload = CapsuleRefreshRequestedEvent(
                            reason = "schedule_changed",
                            priority = CapsuleRefreshPriority.NORMAL
                        )
                    )
                }
        }

        appScope.launch {
            domainEventBus
                .eventsOfType<CapsuleRefreshRequestedEvent>(DomainEventType.CAPSULE_REFRESH_REQUESTED)
                .collectLatest {
                    capsuleCenter.forceRefresh()
                }
        }
    }

    fun isLiveCapsuleEnabled(): Boolean {
        return settingsQueryApi.settings.value.isLiveCapsuleEnabled
    }

    fun resolveCapsuleMode(capsuleRoutingQueryApi: CapsuleRoutingQueryApi): CapsuleRouteMode {
        return capsuleRoutingQueryApi.resolveMode(isLiveCapsuleEnabled())
    }

    fun isStandardNotificationMode(capsuleRoutingQueryApi: CapsuleRoutingQueryApi): Boolean {
        return resolveCapsuleMode(capsuleRoutingQueryApi) == CapsuleRouteMode.STANDARD_NOTIFICATION
    }

    fun isMiuiIslandMode(capsuleRoutingQueryApi: CapsuleRoutingQueryApi): Boolean {
        return resolveCapsuleMode(capsuleRoutingQueryApi) == CapsuleRouteMode.MIUI_ISLAND
    }

    inline fun routeByCapsuleMode(
        capsuleRoutingQueryApi: CapsuleRoutingQueryApi,
        onMiuiIsland: () -> Unit,
        onLiveCapsule: () -> Unit,
        onStandardNotification: () -> Unit
    ) {
        when (resolveCapsuleMode(capsuleRoutingQueryApi)) {
            CapsuleRouteMode.MIUI_ISLAND -> onMiuiIsland()
            CapsuleRouteMode.LIVE_CAPSULE -> onLiveCapsule()
            CapsuleRouteMode.STANDARD_NOTIFICATION -> onStandardNotification()
        }
    }

    fun refreshCapsuleState() {
        capsuleCenter.forceRefresh()
    }

    private fun enqueueReminderReconcile(event: ScheduleChangedEvent) {
        if (!shouldReconcileReminders(event)) return

        pendingFullReminderReconcile = true
        if (event.changeType == ScheduleChangeType.DELETE || event.changeType == ScheduleChangeType.ARCHIVE) {
            event.eventIds.mapNotNull { it.toLongOrNull() }.forEach(pendingCancellationIds::add)
        }

        if (reminderReconcileJob?.isActive == true) {
            return
        }

        reminderReconcileJob = appScope.launch {
            delay(if (pendingFullReminderReconcile) 400 else 250)
            if (pendingFullReminderReconcile) {
                val cancellationIds = pendingCancellationIds.toList()
                pendingFullReminderReconcile = false
                pendingReminderOps.clear()
                pendingCancellationIds.clear()
                cancellationIds.forEach { eventId -> cancelEvent(null, eventId) }
                runFullReminderReconcile()
            } else {
                val ops = pendingReminderOps.toMap()
                pendingReminderOps.clear()
                reconcileRemindersForChangedEvents(ops)
            }
        }
    }

    private fun shouldReconcileReminders(event: ScheduleChangedEvent): Boolean {
        return when (event.changeType) {
            ScheduleChangeType.CREATE,
            ScheduleChangeType.UPDATE,
            ScheduleChangeType.DELETE,
            ScheduleChangeType.ARCHIVE,
            ScheduleChangeType.RESTORE,
            ScheduleChangeType.BULK -> true
        } && when (event.origin) {
            ScheduleChangeOrigin.MANUAL,
            ScheduleChangeOrigin.INGEST,
            ScheduleChangeOrigin.SYNC,
            ScheduleChangeOrigin.IMPORT,
            ScheduleChangeOrigin.SYSTEM -> true
        }
    }

    private fun resolveReminderSyncAction(changeType: ScheduleChangeType): ReminderSyncAction {
        return when (changeType) {
            ScheduleChangeType.CREATE,
            ScheduleChangeType.RESTORE -> ReminderSyncAction.SCHEDULE_ONLY

            ScheduleChangeType.UPDATE,
            ScheduleChangeType.BULK -> ReminderSyncAction.RESCHEDULE

            ScheduleChangeType.DELETE,
            ScheduleChangeType.ARCHIVE -> ReminderSyncAction.CANCEL_ONLY
        }
    }

    private suspend fun reconcileRemindersForChangedEvents(ops: Map<String, ReminderSyncAction>) {
        if (ops.isEmpty()) return

        val activeById = scheduleCenter.getLatestActiveEvents().associateBy { it.id?.toString() ?: "" }
        val archivedById = scheduleCenter.archivedEvents.value.associateBy { it.id?.toString() ?: "" }

        ops.forEach { (eventId, action) ->
            val active = activeById[eventId]
            val archived = archivedById[eventId]

            when (action) {
                ReminderSyncAction.SCHEDULE_ONLY -> {
                    if (active != null) reconcileEvent(active) else cancelEvent(archived, eventId.toLongOrNull())
                }

                ReminderSyncAction.RESCHEDULE -> {
                    if (active != null) reconcileEvent(active) else cancelEvent(archived, eventId.toLongOrNull())
                }

                ReminderSyncAction.CANCEL_ONLY -> {
                    cancelEvent(active ?: archived, eventId.toLongOrNull())
                }
            }
        }
    }

    private suspend fun runFullReminderReconcile() {
        val activeEvents = scheduleCenter.getLatestActiveEvents()
            .filter { it.archivedAt == null }
        val now = LocalDate.now()
        val displayItems = ScheduleDisplayHelper.buildDisplayItems(
            activeEvents,
            now,
            now.plusDays(NOTIFICATION_WINDOW_DAYS)
        )
        val parentMap = activeEvents.filter { it.isRecurring }.associateBy { it.id ?: 0L }
        val liveIds = activeEvents.mapNotNull { it.id }.toSet()

        activeEvents
            .filterNot { it.isRecurring }
            .forEach(::reconcileEvent)

        reminderNode.refreshForWindow(displayItems, parentMap)
        reminderNode.cancelStaleEvents(liveIds)
    }

    private fun reconcileEvent(event: Event) {
        val eventId = event.id ?: return
        NotificationScheduler.cancelScheduledAlarms(appContext, event)
        reminderNode.cancelForEvent(eventId)

        if (!shouldKeepEventScheduled(event)) {
            val includeLiveCapsule = !(shouldPreserveLiveCapsule(event) && isLiveCapsuleEnabled())
            NotificationScheduler.cancelVisibleNotifications(appContext, event, includeLiveCapsule)
            return
        }

        NotificationScheduler.cancelVisibleNotifications(appContext, event, includeLiveCapsule = false)
        NotificationScheduler.scheduleReminders(appContext, event)
    }

    private fun cancelEvent(event: Event?, eventId: Long?) {
        if (event != null) {
            NotificationScheduler.cancelScheduledAlarms(appContext, event)
            NotificationScheduler.cancelVisibleNotifications(appContext, event, includeLiveCapsule = true)
            event.id?.let { reminderNode.cancelForInactiveEvent(it) }
            return
        }

        if (eventId != null && eventId > 0L) {
            NotificationScheduler.cancelScheduledAlarms(appContext, eventId)
            NotificationScheduler.cancelVisibleNotifications(appContext, eventId, includeLiveCapsule = true)
            reminderNode.cancelForInactiveEvent(eventId)
        }
    }

    private fun shouldKeepEventScheduled(event: Event): Boolean {
        if (event.archivedAt != null || event.endTS <= System.currentTimeMillis() / 1000L) return false
        if (event.state == STATE_PENDING) return true
        return shouldPreserveLiveCapsule(event) && isLiveCapsuleEnabled()
    }

    private fun shouldPreserveLiveCapsule(event: Event): Boolean {
        return event.state == STATE_CHECKED_IN && event.isTransit
    }

    companion object {
        private const val NOTIFICATION_WINDOW_DAYS = 7L
    }
}
