package com.antgskds.calendarassistant.feature.schedule.notification

import com.antgskds.calendarassistant.feature.schedule.domain.calendar.STATE_PENDING
import com.antgskds.calendarassistant.feature.schedule.domain.model.Event
import com.antgskds.calendarassistant.feature.schedule.domain.model.idString
import com.antgskds.calendarassistant.shared.query.EventActionQueryApi
import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import com.antgskds.calendarassistant.feature.schedule.presentation.model.ScheduleDisplayItem
import com.antgskds.calendarassistant.feature.schedule.data.store.reminder.ReminderPolicy
import com.antgskds.calendarassistant.feature.schedule.domain.model.startTime
import com.antgskds.calendarassistant.feature.notification.api.NotificationApi
import com.antgskds.calendarassistant.feature.notification.policy.ReminderWindowPolicy
import com.antgskds.calendarassistant.feature.notification.model.NotificationAction
import com.antgskds.calendarassistant.feature.notification.model.NotificationBehavior
import com.antgskds.calendarassistant.feature.notification.model.NotificationDisplaySnapshot
import com.antgskds.calendarassistant.feature.notification.model.NotificationKey
import com.antgskds.calendarassistant.feature.notification.model.NotificationKind
import com.antgskds.calendarassistant.feature.notification.model.NotificationRequest
import com.antgskds.calendarassistant.feature.notification.model.NotificationRoute
import com.antgskds.calendarassistant.feature.notification.model.NotificationQuery
import com.antgskds.calendarassistant.feature.notification.model.NotificationTapTarget
import com.antgskds.calendarassistant.feature.notification.model.NotificationTapTargetType
import com.antgskds.calendarassistant.feature.schedule.api.model.ScheduleInstanceKey
import com.antgskds.calendarassistant.platform.notification.alarmlegacy.NotificationIds
import com.antgskds.calendarassistant.platform.receiver.EventActionReceiver
import com.antgskds.calendarassistant.shared.management.resource.notification.display.normal.ScheduleNormalDisplay

class ScheduleNotificationBridge(
    private val notificationApi: NotificationApi,
    private val settingsProvider: () -> MySettings,
    private val eventActionQueryApi: EventActionQueryApi? = null,
    private val windowEndProvider: () -> Long = { 0L },
    private val registrationAllowedProvider: () -> Boolean = { false },
    private val nowProvider: () -> Long = System::currentTimeMillis,
) {
    suspend fun onEventCreated(event: Event) = reconcileSingleEvent(event)
    suspend fun onEventUpdated(event: Event) = reconcileSingleEvent(event)
    suspend fun onEventTimeEdited(event: Event) = reconcileSingleEvent(event)

    suspend fun onEventDeleted(eventId: Long) {
        cancelKnownScheduleNotifications(ScheduleInstanceKey.Single(eventId), eventId)
    }

    private suspend fun reconcileSingleEvent(event: Event) {
        val eventId = event.id ?: return
        reconcileRequests(singleRequests(event, settingsProvider()), "schedule:single:$eventId:")
    }

    suspend fun submitSingleEvents(events: List<Event>) {
        val settings = settingsProvider()
        reconcileRequests(events.flatMap { singleRequests(it, settings) }, "schedule:single:")
    }

    private fun singleRequests(event: Event, settings: MySettings): List<NotificationRequest> {
        val eventId = event.id ?: return emptyList()
        if (event.isRecurring || event.state != STATE_PENDING || event.archivedAt != null ||
            event.endTS <= nowEpochSeconds() || !ReminderWindowPolicy.needsNormalReminder(settings) ||
            !com.antgskds.calendarassistant.feature.schedule.domain.course.CourseFeaturePolicy.allows(event, settings)
        ) return emptyList()
        val instanceKey = ScheduleInstanceKey.Single(eventId)
        return ReminderPolicy.effectiveReminders(event, settings).map { it.minutes }.distinct().mapNotNull { offset ->
            val triggerAt = (event.startTS - offset * 60L) * 1000L
            if (!inWindow(triggerAt) || !com.antgskds.calendarassistant.feature.schedule.domain.course.CourseFeaturePolicy
                    .allowsReminder(event.tag, triggerAt, settings)) null
            else buildScheduleReminderRequest(event, eventId, instanceKey, offset, triggerAt)
        }
    }

    private fun inWindow(triggerAt: Long): Boolean =
        ReminderWindowPolicy.contains(triggerAt, nowProvider(), windowEndProvider())

    private suspend fun reconcileRequests(requests: List<NotificationRequest>, prefix: String) {
        val desired = requests.associateBy { it.key.value }
        val existing = notificationApi.list(NotificationQuery(kind = NotificationKind.SCHEDULE_REMINDER))
            .filter { it.key.value.startsWith(prefix) }
        // Free stale slots before arming anything; background calls stop after cleanup.
        val stale = existing.filter { snapshot ->
            val target = desired[snapshot.key.value]
            target == null || target.behavior.triggerAtEpochMillis != snapshot.behavior.triggerAtEpochMillis
        }
        notificationApi.cancelAll(stale.map { it.key })
        if (!registrationAllowedProvider()) return
        val retained = existing.map { it.key.value }.toSet() - stale.map { it.key.value }.toSet()
        requests.forEach { request ->
            if (!registrationAllowedProvider() || !inWindow(request.behavior.triggerAtEpochMillis ?: 0L)) return@forEach
            if (request.key.value in retained) notificationApi.update(request) else notificationApi.create(request)
        }
    }

    private fun buildScheduleReminderRequest(
        event: Event,
        eventId: Long,
        instanceKey: ScheduleInstanceKey,
        offsetMinutes: Int,
        triggerAtMillis: Long
    ): NotificationRequest {
        val label = reminderLabel(offsetMinutes)
        val title = event.title.ifBlank { ScheduleNormalDisplay.unnamedEventTitle() }
        val detail = listOfNotNull(
            event.startTime.takeIf { it.isNotBlank() },
            event.location.takeIf { it.isNotBlank() }
        ).joinToString(" · ").ifBlank { ScheduleNormalDisplay.detailFallback() }
        val display = NotificationDisplaySnapshot(
            shortText = ScheduleNormalDisplay.reminderTitleFallback(),
            primaryText = title,
            secondaryText = label,
            tertiaryText = detail,
            expandedText = event.description.ifBlank { detail }
        )
        return NotificationRequest(
            key = NotificationKey.scheduleReminder(instanceKey, offsetMinutes),
            kind = NotificationKind.SCHEDULE_REMINDER,
            display = display,
            route = NotificationRoute.AUTO,
            notificationId = NotificationIds.standardReminder(eventId),
            scheduleInstanceKey = instanceKey,
            offsetMinutes = offsetMinutes,
            behavior = NotificationBehavior(triggerAtEpochMillis = triggerAtMillis),
            tapTarget = NotificationTapTarget(
                type = NotificationTapTargetType.SCHEDULE_DETAIL,
                payload = mapOf("eventId" to event.idString)
            ),
            actions = buildReminderActions(event, event.idString),
            source = "schedule_center",
            metadata = mapOf(
                "eventId" to event.idString,
                "startTS" to event.startTS.toString(),
                "endTS" to event.endTS.toString(),
                "tag" to event.tag
            )
        )
    }

    private suspend fun cancelKnownScheduleNotifications(instanceKey: ScheduleInstanceKey, eventId: Long) {
        // Phase 2：除 per-event 选项与 0 外，也取消全局提前提醒可能用到的偏移（30/45/60），
        // 否则全局提前=45 时更新事件会残留旧的 45 偏移键。取消不存在的键是安全 no-op。
        val offsets = ScheduleNormalDisplay.reminderOptions.map { it.first }.toSet() +
            eventReminderOffsets(instanceKey) +
            0 + setOf(30, 45, 60)
        val keys = offsets.map { offset -> NotificationKey.scheduleReminder(instanceKey, offset) } +
            NotificationKey.scheduleAction(instanceKey, "pickup-initial") +
            NotificationKey("schedule:${instanceKey.stableKey}:event:$eventId")
        notificationApi.cancelAll(keys)
    }

    private suspend fun eventReminderOffsets(instanceKey: ScheduleInstanceKey): Set<Int> {
        val prefix = "schedule:${instanceKey.stableKey}:offset:"
        return notificationApi.list(NotificationQuery(kind = NotificationKind.SCHEDULE_REMINDER))
            .asSequence()
            .map { it.key.value }
            .filter { it.startsWith(prefix) }
            .mapNotNull { key -> key.removePrefix(prefix).toIntOrNull() }
            .toSet()
    }

    private fun reminderLabel(offsetMinutes: Int): String {
        return ScheduleNormalDisplay.reminderOptions.firstOrNull { it.first == offsetMinutes }?.second
            ?: if (offsetMinutes > 0) ScheduleNormalDisplay.advanceLabel(offsetMinutes) else ScheduleNormalDisplay.startLabel()
    }

    /**
     * Phase 3：重复事件的窗口内实例 → 新通知链路。
     * 复用 ScheduleReminderCoordinator 已算好的 displayItems（展开的实例）+ parentMap，不重做展开。
     * 每实例按实际触发时刻过滤；先清理过期、改期与出窗项，再在前台登记。
     */
    suspend fun submitRecurringWindow(items: List<ScheduleDisplayItem>, parentEvents: Map<Long, Event>) {
        val settings = settingsProvider()
        val requests = mutableListOf<NotificationRequest>()
        if (ReminderWindowPolicy.needsNormalReminder(settings)) {
            for (item in items) {
                if (item.state != STATE_PENDING || item.endTS <= nowEpochSeconds()) continue
                val target = item.action as? ScheduleDisplayItem.ActionTarget.RecurringOccurrence ?: continue
                val parent = parentEvents[target.parentId] ?: continue
                val key = ScheduleInstanceKey.Recurring(target.parentId, target.occurrenceTs)
                for (offset in ReminderPolicy.effectiveReminders(parent, settings).map { it.minutes }.distinct()) {
                    val triggerAt = (item.startTS - offset * 60L) * 1000L
                    if (!inWindow(triggerAt) || !com.antgskds.calendarassistant.feature.schedule.domain.course.CourseFeaturePolicy
                            .allowsReminder(item.tag, triggerAt, settings)) continue
                    requests.add(buildOccurrenceReminderRequest(item, parent, key, offset, triggerAt))
                }
            }
        }
        reconcileRequests(requests, "schedule:rec:")
    }

    private fun buildOccurrenceReminderRequest(
        item: ScheduleDisplayItem,
        parent: Event,
        instanceKey: ScheduleInstanceKey,
        offsetMinutes: Int,
        triggerAtMillis: Long
    ): NotificationRequest {
        val label = reminderLabel(offsetMinutes)
        val title = item.title.ifBlank { ScheduleNormalDisplay.unnamedEventTitle() }
        val detail = item.location.takeIf { it.isNotBlank() } ?: ScheduleNormalDisplay.detailFallback()
        val display = NotificationDisplaySnapshot(
            shortText = ScheduleNormalDisplay.reminderTitleFallback(),
            primaryText = title,
            secondaryText = label,
            tertiaryText = detail,
            expandedText = item.description.ifBlank { detail }
        )
        return NotificationRequest(
            key = NotificationKey.scheduleReminder(instanceKey, offsetMinutes),
            kind = NotificationKind.SCHEDULE_REMINDER,
            display = display,
            route = NotificationRoute.AUTO,
            notificationId = NotificationIds.standardReminder(instanceKey.stableKey),
            scheduleInstanceKey = instanceKey,
            offsetMinutes = offsetMinutes,
            behavior = NotificationBehavior(triggerAtEpochMillis = triggerAtMillis),
            tapTarget = NotificationTapTarget(
                type = NotificationTapTargetType.SCHEDULE_DETAIL,
                payload = mapOf("eventId" to parent.idString)
            ),
            actions = buildReminderActions(parent, instanceKey.stableKey),
            source = "schedule_center",
            metadata = mapOf(
                "eventId" to parent.idString,
                "startTS" to item.startTS.toString(),
                "endTS" to item.endTS.toString(),
                "tag" to item.tag
            )
        )
    }

    private fun buildReminderActions(event: Event, eventIdPayload: String): List<NotificationAction> {
        val actionQuery = eventActionQueryApi ?: return emptyList()
        val ruleId = actionQuery.resolveEffectiveRuleId(
            intentRuleId = null,
            fallbackTag = event.tag,
            event = event
        )
        val button = actionQuery.buildActionButton(ruleId, event) ?: return emptyList()
        return listOf(
            NotificationAction(
                key = button.intentAction,
                label = button.text,
                payload = mapOf(EventActionReceiver.EXTRA_EVENT_ID to eventIdPayload)
            )
        )
    }

    private fun nowEpochSeconds(): Long = nowProvider() / 1000L
}
