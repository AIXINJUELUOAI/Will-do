package com.antgskds.calendarassistant.core.center

import android.util.Log
import com.antgskds.calendarassistant.calendar.helpers.STATE_PENDING
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.idString
import com.antgskds.calendarassistant.core.query.EventActionQueryApi
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.data.model.ScheduleDisplayItem
import com.antgskds.calendarassistant.store.reminder.ReminderPolicy
import com.antgskds.calendarassistant.calendar.models.startTime
import com.antgskds.calendarassistant.feature.api.notification.NotificationApi
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationAction
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationBehavior
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationDisplaySnapshot
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationKey
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationKind
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationRequest
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationRoute
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationQuery
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationState
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationTapTarget
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationTapTargetType
import com.antgskds.calendarassistant.feature.api.schedule.model.ScheduleInstanceKey
import com.antgskds.calendarassistant.platform.notification.alarmlegacy.NotificationIds
import com.antgskds.calendarassistant.platform.receiver.EventActionReceiver
import com.antgskds.calendarassistant.shared.management.resource.notification.display.normal.ScheduleNormalDisplay

class ScheduleNotificationBridge(
    private val notificationApi: NotificationApi,
    private val settingsProvider: () -> MySettings,
    private val eventActionQueryApi: EventActionQueryApi? = null
) {
    suspend fun onEventCreated(event: Event) {
        submitEventNotifications(event, isUpdate = false)
    }

    suspend fun onEventUpdated(event: Event) {
        submitEventNotifications(event, isUpdate = true)
    }

    suspend fun onEventTimeEdited(event: Event) {
        val eventId = event.id ?: return
        cancelKnownScheduleNotifications(ScheduleInstanceKey.Single(eventId), eventId)
        submitEventNotifications(event, isUpdate = false, cancelBeforeSubmit = false)
    }

    suspend fun onEventDeleted(eventId: Long) {
        cancelKnownScheduleNotifications(ScheduleInstanceKey.Single(eventId), eventId)
    }

    suspend fun submitSingleEvents(events: List<Event>) {
        val settings = settingsProvider()
        val desiredKeys = mutableSetOf<String>()

        events.filterNot { it.isRecurring }.forEach { event ->
            val eventId = event.id ?: return@forEach
            val instanceKey = ScheduleInstanceKey.Single(eventId)
            if (event.state == STATE_PENDING && event.archivedAt == null && event.endTS > nowEpochSeconds()) {
                ReminderPolicy.effectiveReminders(event, settings)
                    .map { it.minutes }
                    .distinct()
                    .forEach { offsetMinutes ->
                        desiredKeys.add(NotificationKey.scheduleReminder(instanceKey, offsetMinutes).value)
                    }
            }
            submitEventNotifications(
                event = event,
                isUpdate = true,
                settings = settings,
                cancelBeforeSubmit = false,
                cancelInactive = false
            )
        }

        val existingSingles = notificationApi
            .list(NotificationQuery(kind = NotificationKind.SCHEDULE_REMINDER))
            .filter { it.key.value.startsWith("schedule:single:") }
        val staleKeys = existingSingles
            .filter { it.key.value !in desiredKeys }
            .map { it.key }
        notificationApi.cancelAll(staleKeys)
        staleKeys.forEach { key ->
            Log.d("WillDoNotify", "single-cleanup cancel key=${key.value}")
        }
    }

    private suspend fun submitEventNotifications(
        event: Event,
        isUpdate: Boolean,
        settings: MySettings = settingsProvider(),
        cancelBeforeSubmit: Boolean = isUpdate,
        cancelInactive: Boolean = true
    ) {
        val eventId = event.id ?: return
        val instanceKey = ScheduleInstanceKey.Single(eventId)
        if (event.isRecurring) {
            // Phase 3 修复：重复事件由 submitRecurringWindow 按实例排程，单次路径必须跳过，
            // 否则会给母事件多排一条 single 提醒、与首个实例重复（旧 rebuildForEvent 当年也是 isRecurring 早退）。
            if (cancelInactive) cancelKnownScheduleNotifications(instanceKey, eventId)
            return
        }
        if (event.state != STATE_PENDING || event.archivedAt != null || event.endTS <= nowEpochSeconds()) {
            if (cancelInactive) cancelKnownScheduleNotifications(instanceKey, eventId)
            return
        }

        if (cancelBeforeSubmit) {
            cancelKnownScheduleNotifications(instanceKey, eventId)
        }

        // Phase 2：用 ReminderPolicy.effectiveReminders 取偏移，纳入全局提前提醒、与旧链路一致总含 0 偏移。
        val reminders = ReminderPolicy.effectiveReminders(event, settings).map { it.minutes }.distinct()
        reminders.forEach { offsetMinutes ->
            val triggerAtMillis = (event.startTS - offsetMinutes * 60L) * 1000L
            val request = buildScheduleReminderRequest(
                event = event,
                eventId = eventId,
                instanceKey = instanceKey,
                offsetMinutes = offsetMinutes,
                triggerAtMillis = triggerAtMillis
            )
            val now = System.currentTimeMillis()
            val existing = notificationApi.get(request.key)
            val action = when {
                triggerAtMillis >= now -> {
                    if (isUpdate) {
                        notificationApi.update(request); "UPDATE"
                    } else {
                        notificationApi.create(request); "CREATE"
                    }
                }
                existing?.state == NotificationState.POSTED -> "KEEP_POSTED"
                // Phase 2.1：已过点的提醒——满足补发条件且未补发过，即时补发一条
                // （重排到 ~now，复用整条发布管线：create→闹钟→trigger→publishOrSuppress）。
                shouldFireMissedImmediate(event.startTS, event.endTS, offsetMinutes) &&
                    existing?.state != NotificationState.POSTED -> {
                    val immediate = buildScheduleReminderRequest(
                        event = event,
                        eventId = eventId,
                        instanceKey = instanceKey,
                        offsetMinutes = offsetMinutes,
                        triggerAtMillis = now + 1000L
                    )
                    notificationApi.create(immediate)
                    "MISSED_IMMEDIATE"
                }
                else -> {
                    notificationApi.cancel(request.key); "CANCEL_PAST"
                }
            }
            // 观测后门(WillDoNotify)：每条提醒的排程结果。adb logcat -s WillDoNotify 可见。
            Log.d("WillDoNotify", "schedule eventId=$eventId offset=${offsetMinutes}m triggerAt=$triggerAtMillis action=$action key=${request.key.value}")
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
     * 复用 ReminderCenter 已算好的 displayItems（展开的实例）+ parentMap，不重做展开。
     * 每实例用 Recurring(parentId, 实例时刻) 排提醒；含即时补发；最后做出窗清理。
     */
    suspend fun submitRecurringWindow(
        items: List<ScheduleDisplayItem>,
        parentEvents: Map<Long, Event>
    ) {
        val now = System.currentTimeMillis()
        val nowSec = now / 1000L
        val settings = settingsProvider()
        val desiredKeys = mutableSetOf<String>()

        for (item in items) {
            if (item.state != STATE_PENDING) continue
            if (item.endTS <= nowSec) continue
            val target = item.action
            if (target !is ScheduleDisplayItem.ActionTarget.RecurringOccurrence) continue
            val parent = parentEvents[target.parentId] ?: continue
            val instanceKey = ScheduleInstanceKey.Recurring(target.parentId, target.occurrenceTs)

            val offsets = ReminderPolicy.effectiveReminders(parent, settings).map { it.minutes }.distinct()
            for (offsetMinutes in offsets) {
                val key = NotificationKey.scheduleReminder(instanceKey, offsetMinutes)
                desiredKeys.add(key.value)
                val triggerAtMillis = (item.startTS - offsetMinutes * 60L) * 1000L
                val request = buildOccurrenceReminderRequest(item, parent, instanceKey, offsetMinutes, triggerAtMillis)
                val existing = notificationApi.get(key)
                val action = when {
                    triggerAtMillis >= now -> {
                        if (existing != null) {
                            notificationApi.update(request); "UPDATE"
                        } else {
                            notificationApi.create(request); "CREATE"
                        }
                    }
                    existing?.state == NotificationState.POSTED -> "KEEP_POSTED"
                    shouldFireMissedImmediate(item.startTS, item.endTS, offsetMinutes) &&
                        existing?.state != NotificationState.POSTED -> {
                        val immediate = buildOccurrenceReminderRequest(item, parent, instanceKey, offsetMinutes, now + 1000L)
                        notificationApi.create(immediate)
                        "MISSED_IMMEDIATE"
                    }
                    else -> {
                        notificationApi.cancel(key); "CANCEL_PAST"
                    }
                }
                Log.d("WillDoNotify", "rec-schedule parent=${target.parentId} occ=${target.occurrenceTs} offset=${offsetMinutes}m triggerAt=$triggerAtMillis action=$action key=${key.value}")
            }
        }

        // 出窗清理：取消不再在窗口内的重复实例提醒（仅 rec: 前缀，不动单次事件）
        val existingRecurring = notificationApi
            .list(NotificationQuery(kind = NotificationKind.SCHEDULE_REMINDER))
            .filter { it.key.value.startsWith("schedule:rec:") }
        val staleKeys = existingRecurring
            .filter { it.key.value !in desiredKeys }
            .map { it.key }
        notificationApi.cancelAll(staleKeys)
        staleKeys.forEach { key ->
            Log.d("WillDoNotify", "rec-cleanup cancel key=${key.value}")
        }
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

    private fun shouldFireMissedImmediate(startTS: Long, endTS: Long, offsetMinutes: Int): Boolean {
        // 对齐旧 ReminderStoreNode.shouldSendImmediateReminder：
        // 已结束→不补；提前提醒(offset>0)→尚未开始才补；开始时提醒(0)→已开始才补。单次与重复实例共用。
        val now = System.currentTimeMillis()
        val startMillis = startTS * 1000L
        val endMillis = endTS * 1000L
        if (now >= endMillis) return false
        return if (offsetMinutes > 0) now < startMillis else now >= startMillis
    }

    private fun nowEpochSeconds(): Long = System.currentTimeMillis() / 1000L
}
