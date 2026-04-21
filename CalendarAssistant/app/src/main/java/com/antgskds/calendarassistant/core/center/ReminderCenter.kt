package com.antgskds.calendarassistant.core.center

import android.content.Context
import com.antgskds.calendarassistant.core.query.CapsuleRouteMode
import com.antgskds.calendarassistant.core.query.CapsuleRoutingQueryApi
import com.antgskds.calendarassistant.core.event.DomainEventBus
import com.antgskds.calendarassistant.core.event.DomainEventType
import com.antgskds.calendarassistant.core.event.events.CapsuleRefreshPriority
import com.antgskds.calendarassistant.core.event.events.CapsuleRefreshRequestedEvent
import com.antgskds.calendarassistant.core.event.events.ScheduleChangeOrigin
import com.antgskds.calendarassistant.core.event.events.ScheduleChangeType
import com.antgskds.calendarassistant.core.event.events.ScheduleChangedEvent
import com.antgskds.calendarassistant.core.query.ScheduleQueryApi
import com.antgskds.calendarassistant.core.query.SettingsQueryApi
import com.antgskds.calendarassistant.service.notification.NotificationScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ReminderCenter(
    private val appContext: Context,
    private val capsuleCenter: CapsuleCenter,
    private val settingsQueryApi: SettingsQueryApi,
    private val scheduleQueryApi: ScheduleQueryApi,
    private val domainEventBus: DomainEventBus,
    private val appScope: CoroutineScope
) {
    private enum class ReminderSyncAction {
        SCHEDULE_ONLY,
        RESCHEDULE,
        CANCEL_ONLY
    }

    private val pendingReminderOps = linkedMapOf<String, ReminderSyncAction>()
    private var pendingFullReminderReconcile = false
    private var reminderReconcileJob: Job? = null
    private var eventSubscriptionsStarted = false

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

        if (event.eventIds.isEmpty()) {
            if (!shouldRunFullReminderReconcile(event)) {
                return
            }
            pendingFullReminderReconcile = true
        } else {
            val action = resolveReminderSyncAction(event.changeType)
            event.eventIds.forEach { eventId ->
                pendingReminderOps[eventId] = action
            }
        }

        if (reminderReconcileJob?.isActive == true) {
            return
        }

        reminderReconcileJob = appScope.launch {
            delay(if (pendingFullReminderReconcile) 400 else 250)
            if (pendingFullReminderReconcile) {
                pendingFullReminderReconcile = false
                pendingReminderOps.clear()
                reconcileAllReminders()
            } else {
                val ops = pendingReminderOps.toMap()
                pendingReminderOps.clear()
                reconcileRemindersForChangedEvents(ops)
            }
        }
    }

    private fun shouldReconcileReminders(event: ScheduleChangedEvent): Boolean {
        if (event.eventIds.isEmpty() && !shouldRunFullReminderReconcile(event)) return false

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

    private fun shouldRunFullReminderReconcile(event: ScheduleChangedEvent): Boolean {
        return event.changeType == ScheduleChangeType.BULK &&
            (event.origin == ScheduleChangeOrigin.IMPORT ||
                event.origin == ScheduleChangeOrigin.SYNC ||
                event.origin == ScheduleChangeOrigin.SYSTEM)
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

    private fun reconcileRemindersForChangedEvents(ops: Map<String, ReminderSyncAction>) {
        if (ops.isEmpty()) return

        val activeById = scheduleQueryApi.events.value.associateBy { it.id }
        val archivedById = scheduleQueryApi.archivedEvents.value.associateBy { it.id }

        ops.forEach { (eventId, action) ->
            val active = activeById[eventId]
            val archived = archivedById[eventId]

            when (action) {
                ReminderSyncAction.SCHEDULE_ONLY -> {
                    if (active != null) {
                        NotificationScheduler.scheduleReminders(appContext, active)
                    } else if (archived != null) {
                        NotificationScheduler.cancelReminders(appContext, archived)
                    }
                }

                ReminderSyncAction.RESCHEDULE -> {
                    if (active != null) {
                        NotificationScheduler.cancelReminders(appContext, active)
                        NotificationScheduler.scheduleReminders(appContext, active)
                    } else if (archived != null) {
                        NotificationScheduler.cancelReminders(appContext, archived)
                    }
                }

                ReminderSyncAction.CANCEL_ONLY -> {
                    if (active != null) {
                        NotificationScheduler.cancelReminders(appContext, active)
                    } else if (archived != null) {
                        NotificationScheduler.cancelReminders(appContext, archived)
                    }
                }
            }
        }
    }

    private fun reconcileAllReminders() {
        val activeEvents = scheduleQueryApi.events.value
        val archivedEvents = scheduleQueryApi.archivedEvents.value

        activeEvents.forEach { event ->
            NotificationScheduler.cancelReminders(appContext, event)
            NotificationScheduler.scheduleReminders(appContext, event)
        }

        archivedEvents.forEach { event ->
            NotificationScheduler.cancelReminders(appContext, event)
        }
    }
}
