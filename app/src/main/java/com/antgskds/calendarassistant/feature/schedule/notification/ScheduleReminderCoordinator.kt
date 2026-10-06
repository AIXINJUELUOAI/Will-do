package com.antgskds.calendarassistant.feature.schedule.notification

import com.antgskds.calendarassistant.feature.schedule.domain.model.Event
import com.antgskds.calendarassistant.feature.schedule.domain.model.*
import android.content.Context
import com.antgskds.calendarassistant.shared.util.AppLogger as Log
import com.antgskds.calendarassistant.feature.schedule.domain.calendar.STATE_CHECKED_IN
import com.antgskds.calendarassistant.feature.schedule.domain.calendar.STATE_PENDING
import com.antgskds.calendarassistant.feature.capsule.application.CapsuleController
import com.antgskds.calendarassistant.feature.schedule.application.ScheduleFacade
import com.antgskds.calendarassistant.shared.query.CapsuleRouteMode
import com.antgskds.calendarassistant.shared.query.CapsuleRoutingQueryApi
import com.antgskds.calendarassistant.shared.event.DomainEventBus
import com.antgskds.calendarassistant.shared.event.DomainEventType
import com.antgskds.calendarassistant.shared.event.events.CapsuleRefreshPriority
import com.antgskds.calendarassistant.shared.event.events.CapsuleRefreshRequestedEvent
import com.antgskds.calendarassistant.shared.event.events.ScheduleChangeOrigin
import com.antgskds.calendarassistant.shared.event.events.ScheduleChangeType
import com.antgskds.calendarassistant.shared.event.events.ScheduleChangedEvent

import com.antgskds.calendarassistant.shared.query.SettingsQueryApi
import com.antgskds.calendarassistant.feature.schedule.domain.ScheduleDisplayHelper
import com.antgskds.calendarassistant.platform.notification.alarmlegacy.NotificationScheduler
import com.antgskds.calendarassistant.feature.schedule.data.store.reminder.ReminderStoreNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

class ScheduleReminderCoordinator(
    private val appContext: Context,
    private val capsuleCenter: CapsuleController,
    private val settingsQueryApi: SettingsQueryApi,
    private val scheduleCenter: ScheduleFacade,
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
    private val fullReconcileMutex = Mutex()
    private val reminderNode = ReminderStoreNode(appContext)

    fun onForeground() {
        val app = appContext.applicationContext as com.antgskds.calendarassistant.App
        val reset = app.reminderWindowStore.enterForeground()
        appScope.launch {
            if (reset) {
                app.runtimeCenter.restoreAfterBoot()
                com.antgskds.calendarassistant.feature.schedule.data.sync.SystemCalendarSyncManager(appContext)
                    .scheduleCalDAVSync(app.syncCenter.getSyncStatus().isEnabled)
            }
            reconcileAllNow()
        }
    }

    fun reconcileAll() {
        appScope.launch {
            reconcileAllNow()
        }
    }

    suspend fun reconcileAllNow() = fullReconcileMutex.withLock {
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

        appScope.launch {
            settingsQueryApi.settings
                .map { settings ->
                    ReminderSettingsKey(
                        advanceReminderEnabled = settings.isAdvanceReminderEnabled,
                        advanceReminderMinutes = settings.advanceReminderMinutes,
                        liveCapsuleEnabled = settings.isLiveCapsuleEnabled,
                        braceletModeEnabled = settings.braceletModeEnabled,
                        courseModuleEnabled = settings.courseModuleEnabled,
                        courseResumeAt = settings.courseRemindersResumeAtMillis
                    )
                }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    reconcileAllNow()
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
                reconcileAllNow()
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
        val settings = settingsQueryApi.settings.value
        val storedEvents = scheduleCenter.getLatestActiveEvents().filter { it.archivedAt == null }
        storedEvents.filterNot { com.antgskds.calendarassistant.feature.schedule.domain.course.CourseFeaturePolicy.allows(it, settings) }
            .forEach { cancelEvent(it, it.id) }
        val activeEvents = storedEvents.filter { com.antgskds.calendarassistant.feature.schedule.domain.course.CourseFeaturePolicy.allows(it, settings) }
        val app = appContext.applicationContext as com.antgskds.calendarassistant.App
        val window = app.reminderWindowStore
        val nowMillis = System.currentTimeMillis()
        val zone = java.time.ZoneId.systemDefault()
        val maxDuration = activeEvents.maxOfOrNull { (it.endTS - it.startTS).coerceAtLeast(0L) } ?: 0L
        val maxAdvance = activeEvents.maxOfOrNull {
            com.antgskds.calendarassistant.feature.schedule.data.store.reminder.ReminderPolicy
                .effectiveReminders(it, settings).maxOfOrNull { reminder -> reminder.minutes.toLong() } ?: 0L
        } ?: 0L
        // Expand boundary-crossing occurrences too; actual alarm times are filtered by the saved window.
        val from = java.time.Instant.ofEpochMilli(nowMillis - maxDuration * 1000L).atZone(zone).toLocalDate().minusDays(1)
        val to = java.time.Instant.ofEpochMilli(window.endExclusive + maxAdvance * 60_000L).atZone(zone).toLocalDate().plusDays(1)
        val displayItems = if (window.endExclusive > nowMillis) ScheduleDisplayHelper.buildDisplayItems(activeEvents, from, to) else emptyList()
        val parentMap = activeEvents.associateBy { it.id ?: 0L }
        val liveIds = activeEvents.mapNotNull { it.id }.toSet()
        val singleEvents = activeEvents.filterNot { it.isRecurring }
        // Retire old per-event capsule identities. New single and recurring capsules share the tracked window path.
        singleEvents.forEach {
            NotificationScheduler.cancelScheduledAlarms(appContext, it)
            it.id?.let(reminderNode::cancelForEvent)
        }
        suspend fun syncWindow() {
            scheduleCenter.submitSingleEvents(singleEvents)
            scheduleCenter.submitRecurringWindow(displayItems, parentMap)
            reminderNode.refreshForWindow(displayItems, parentMap)
        }
        // Include memo alarms in both passes so stale slots in any reminder chain are freed first.
        window.cleaning {
            syncWindow()
            reminderNode.cancelStaleEvents(liveIds)
            app.quickMemoCenter.rescheduleReminders()
        }
        if (window.canRegister) {
            syncWindow()
            app.quickMemoCenter.rescheduleReminders()
        }
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
        if (!com.antgskds.calendarassistant.feature.schedule.domain.course.CourseFeaturePolicy.allows(event, settingsQueryApi.settings.value)) return false
        if (event.archivedAt != null || event.endTS <= System.currentTimeMillis() / 1000L) return false
        if (event.state == STATE_PENDING) return true
        return shouldPreserveLiveCapsule(event) && isLiveCapsuleEnabled()
    }

    private fun shouldPreserveLiveCapsule(event: Event): Boolean {
        return event.state == STATE_CHECKED_IN && event.isTransit
    }

    companion object {
        private const val TAG = "ScheduleReminder"
    }

    private data class ReminderSettingsKey(
        val advanceReminderEnabled: Boolean,
        val advanceReminderMinutes: Int,
        val liveCapsuleEnabled: Boolean,
        val braceletModeEnabled: Boolean,
        val courseModuleEnabled: Boolean,
        val courseResumeAt: Long,
    )
}
