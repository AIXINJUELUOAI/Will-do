package com.antgskds.calendarassistant.store.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.data.model.ScheduleDisplayItem
import com.antgskds.calendarassistant.calendar.helpers.STATE_CHECKED_IN
import com.antgskds.calendarassistant.calendar.helpers.STATE_PENDING
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.Reminder
import com.antgskds.calendarassistant.calendar.models.isTransit
import com.antgskds.calendarassistant.core.rule.RuleMatchingEngine
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.platform.notification.alarmlegacy.NotificationIds
import com.antgskds.calendarassistant.platform.notification.alarmlegacy.NotificationScheduler
import com.antgskds.calendarassistant.platform.receiver.AlarmReceiver

class ReminderStoreNode(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    // ══════════════════════════════════════════════════════════════════════
    // 单事件操作（用于真实 DB 事件：单次事件和物化后的子事件）
    // ══════════════════════════════════════════════════════════════════════

    fun rebuildForEvent(event: Event) {
        val eventId = event.id ?: return
        // 跳过重复母事件 —— 母事件的通知由 refreshForWindow 处理
        if (event.isRecurring) return
        if (event.state != STATE_PENDING) {
            cancelForDormantEvent(event)
            return
        }

        val now = System.currentTimeMillis()
        val nowSec = now / 1000L
        val notificationKey = singleNotificationKey(eventId)
        if (event.endTS <= nowSec) {
            cancelForInactiveEvent(eventId)
            return
        }

        val currentSettings = settings()
        cancelForEvent(eventId)
        if (currentSettings.isLiveCapsuleEnabled) {
            cancelDisplayedStandardNotificationsForEvent(eventId)
            clearActiveMarkersForNotificationKey(notificationKey)
            return
        }

        // Phase 2：单次事件的「普通提醒」已切到新通知链路
        // （ScheduleNotificationBridge → NotificationApi → NotificationCenter → AndroidNormalNotificationPublisher）。
        // 此处不再排 EventReminderReceiver 闹钟，避免与新链路双弹；上方的取消/清理与胶囊 early-return 保留不变。
        // 「错过即时补发(missed-immediate)」已由新链路 ScheduleNotificationBridge.shouldFireMissedImmediate 恢复
        // （事件创建/更新/reconcile/开机重排时判定补发，带 state!=POSTED 去重），此处无需再做。
    }

    fun cancelForEvent(eventId: Long) {
        val codes = loadRequestCodes(eventId)
        codes.forEach { requestCode ->
            val pendingIntent = PendingIntent.getBroadcast(
                appContext,
                requestCode,
                Intent(appContext, AlarmReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
        prefs.edit().remove(keyForEvent(eventId)).apply()
    }

    fun cancelForInactiveEvent(eventId: Long) {
        cancelForEvent(eventId)
        cancelDisplayedNotificationsForInactiveEvent(eventId)
        clearActiveMarkersForNotificationKey(singleNotificationKey(eventId))
    }

    fun cancelForDormantEvent(event: Event) {
        val eventId = event.id ?: return
        cancelForEvent(eventId)
        cancelDisplayedStandardNotificationsForEvent(eventId)
        clearActiveMarkersForNotificationKey(singleNotificationKey(eventId))
        if (!(event.state == STATE_CHECKED_IN && event.isTransit)) {
            cancelDisplayedNotificationsForInactiveEvent(eventId)
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 窗口级通知刷新（实例感知，覆盖重复日程实例）
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 根据展示实例列表刷新通知。
     *
     * 通知注册条件（必须全满足）：
     * - state == PENDING
     * - endTS > now
     * - 在调度窗口内
     * - 有提醒分钟数（reminderMinutes > 0）
     *
     * @param items 已展开的实例列表（含单次事件和重复实例）
     * @param parentEvents 母事件列表，用于获取重复实例的提醒分钟数
     */
    fun refreshForWindow(
        items: List<ScheduleDisplayItem>,
        parentEvents: Map<Long, Event>
    ) {
        val now = System.currentTimeMillis()
        val nowSec = now / 1000L

        // 收集所有应该有通知的 instanceKey
        val shouldNotifyKeys = mutableSetOf<String>()

        for (item in items) {
            // 过滤规则
            if (item.state != STATE_PENDING) continue
            if (item.endTS <= nowSec) {
                clearActiveMarkersForNotificationKey(item.stableKey)
                continue
            }
            shouldNotifyKeys.add(item.stableKey)

            val parent = when (val target = item.action) {
                is ScheduleDisplayItem.ActionTarget.Single -> {
                    // 单次事件的通知由 rebuildForEvent 管理，这里跳过
                    continue
                }
                is ScheduleDisplayItem.ActionTarget.RecurringOccurrence -> {
                    parentEvents[target.parentId] ?: continue
                }
            }

            val instanceKey = item.stableKey
            val existingCodes = loadRequestCodes(instanceKey).toSet()

            // 注册新通知
            val requestCodes = mutableSetOf<Int>()
            for (spec in buildRecurringAlarmSpecs(item, parent, instanceKey, now)) {
                val requestCode = spec.requestCode
                if (spec.triggerMillis > now) {
                    val pendingIntent = createRecurringAlarmPendingIntent(spec, item, parent, instanceKey)
                    if (scheduleReminderAlarm(spec.triggerMillis, pendingIntent)) {
                        requestCodes.add(requestCode)
                    }
                } else if (shouldSendImmediateAlarm(spec, item, now) &&
                    markImmediateReminderIfNeeded(instanceKey, spec.marker)
                ) {
                    sendImmediateRecurringAlarm(spec, item, parent, instanceKey)
                }
            }
            (existingCodes - requestCodes).forEach { cancelRequestCode(it) }
            saveRequestCodes(instanceKey, requestCodes)
        }
        clearActiveMarkersExcept(shouldNotifyKeys)

        // 注销不再需要通知的重复实例
        val allInstanceKeys = getAllInstanceKeys()
        for (key in allInstanceKeys) {
            if (key.startsWith("rec:") && key !in shouldNotifyKeys) {
                cancelForInstanceKey(key)
            }
        }
    }

    fun resetAndRebuildAll(events: List<Event>) {
        clearAllScheduled()
        events.forEach { rebuildForEvent(it) }
    }

    fun reconcileForEvents(events: List<Event>) {
        val liveEventIds = events.mapNotNull { it.id }.toSet()
        events.forEach { rebuildForEvent(it) }
        cancelStaleEventKeys(liveEventIds)
    }

    fun cancelStaleEvents(liveEventIds: Set<Long>) {
        cancelStaleEventKeys(liveEventIds)
    }

    fun getScheduledReminderCount(eventId: Long): Int = loadRequestCodes(eventId).size

    // ══════════════════════════════════════════════════════════════════════
    // 内部方法
    // ══════════════════════════════════════════════════════════════════════

    private data class AlarmSpec(
        val action: String,
        val triggerMillis: Long,
        val requestCode: Int,
        val marker: Int,
        val label: String = "",
        val actualStartMillis: Long = -1L
    )

    private fun cancelForInstanceKey(instanceKey: String) {
        val codes = loadRequestCodes(instanceKey)
        codes.forEach { requestCode -> cancelRequestCode(requestCode) }
        cancelDisplayedNotificationsForInstance(instanceKey)
        prefs.edit().remove(keyForInstance(instanceKey)).apply()
    }

    private fun getAllInstanceKeys(): Set<String> {
        return prefs.all.keys
            .filter { it.startsWith(INSTANCE_KEY_PREFIX) }
            .map { it.removePrefix(INSTANCE_KEY_PREFIX) }
            .toSet()
    }

    private fun cancelStaleEventKeys(liveEventIds: Set<Long>) {
        prefs.all.keys
            .filter { it.startsWith(KEY_PREFIX) }
            .mapNotNull { key -> key.removePrefix(KEY_PREFIX).toLongOrNull() }
            .filter { eventId -> eventId !in liveEventIds }
            .forEach { eventId ->
                cancelForEvent(eventId)
                cancelDisplayedNotificationsForInactiveEvent(eventId)
                clearActiveMarkersForNotificationKey(singleNotificationKey(eventId))
            }
    }

    private fun clearAllScheduled() {
        prefs.all.keys.toList().forEach { key ->
            if (key.startsWith(KEY_PREFIX)) {
                val eventId = key.removePrefix(KEY_PREFIX).toLongOrNull() ?: return@forEach
                cancelForEvent(eventId)
            } else if (key.startsWith(INSTANCE_KEY_PREFIX)) {
                val instanceKey = key.removePrefix(INSTANCE_KEY_PREFIX)
                cancelForInstanceKey(instanceKey)
            } else if (key.startsWith(ACTIVE_KEY_PREFIX)) {
                prefs.edit().remove(key).apply()
            }
        }
    }

    private fun buildRecurringAlarmSpecs(
        item: ScheduleDisplayItem,
        parent: Event,
        instanceKey: String,
        now: Long
    ): List<AlarmSpec> {
        val currentSettings = settings()
        val startMillis = item.startTS * 1000L
        val endMillis = item.endTS * 1000L
        if (endMillis <= now) return emptyList()

        return if (currentSettings.isLiveCapsuleEnabled) {
            buildCapsuleAlarmSpecs(instanceKey, startMillis, endMillis, currentSettings)
        } else {
            // Phase 3：普通重复提醒已由新链路（ScheduleNotificationBridge.submitRecurringWindow）接管，
            // 旧路不再排，避免与新链路双弹。胶囊重复提醒仍走上面的旧路。
            emptyList()
        }
    }

    private fun buildCapsuleAlarmSpecs(
        instanceKey: String,
        startMillis: Long,
        endMillis: Long,
        currentSettings: MySettings
    ): List<AlarmSpec> {
        val specs = mutableListOf<AlarmSpec>()
        val advanceEnabled = currentSettings.isAdvanceReminderEnabled && currentSettings.advanceReminderMinutes > 0
        val capsuleStartMillis = if (advanceEnabled) {
            startMillis - currentSettings.advanceReminderMinutes * 60_000L
        } else {
            startMillis
        }

        specs.add(
            AlarmSpec(
                action = NotificationScheduler.ACTION_CAPSULE_START,
                triggerMillis = capsuleStartMillis,
                requestCode = buildInstanceRequestCode(instanceKey, NotificationScheduler.ACTION_CAPSULE_START, CAPSULE_START_MARKER, 0),
                marker = CAPSULE_START_MARKER,
                actualStartMillis = startMillis
            )
        )

        if (advanceEnabled) {
            specs.add(
                AlarmSpec(
                    action = NotificationScheduler.ACTION_REFRESH_CAPSULE,
                    triggerMillis = startMillis,
                    requestCode = buildInstanceRequestCode(instanceKey, NotificationScheduler.ACTION_REFRESH_CAPSULE, CAPSULE_REFRESH_MARKER, 0),
                    marker = CAPSULE_REFRESH_MARKER,
                    actualStartMillis = startMillis
                )
            )
        }

        specs.add(
            AlarmSpec(
                action = NotificationScheduler.ACTION_CAPSULE_END,
                triggerMillis = endMillis,
                requestCode = buildInstanceRequestCode(instanceKey, NotificationScheduler.ACTION_CAPSULE_END, CAPSULE_END_MARKER, 0),
                marker = CAPSULE_END_MARKER
            )
        )

        return specs
    }

    private fun shouldSendImmediateAlarm(spec: AlarmSpec, item: ScheduleDisplayItem, now: Long): Boolean {
        val startMillis = item.startTS * 1000L
        val endMillis = item.endTS * 1000L
        if (now >= endMillis) return false

        return when (spec.action) {
            NotificationScheduler.ACTION_REMINDER -> {
                val minutes = spec.marker.coerceAtLeast(0)
                shouldSendImmediateReminder(
                    triggerMillis = spec.triggerMillis,
                    startMillis = startMillis,
                    endMillis = endMillis,
                    reminder = Reminder(minutes, 0),
                    now = now
                )
            }
            NotificationScheduler.ACTION_CAPSULE_START -> spec.triggerMillis <= now
            NotificationScheduler.ACTION_REFRESH_CAPSULE -> spec.triggerMillis <= now && now >= startMillis
            else -> false
        }
    }

    private fun reminderLabel(minutes: Int): String {
        return NotificationScheduler.REMINDER_OPTIONS.firstOrNull { it.first == minutes }?.second
            ?: if (minutes > 0) "提前${minutes}分钟" else "日程开始"
    }

    private fun createRecurringAlarmPendingIntent(
        spec: AlarmSpec,
        item: ScheduleDisplayItem,
        parent: Event,
        instanceKey: String
    ): PendingIntent {
        return PendingIntent.getBroadcast(
            appContext,
            spec.requestCode,
            createRecurringAlarmIntent(spec, item, parent, instanceKey),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createRecurringAlarmIntent(
        spec: AlarmSpec,
        item: ScheduleDisplayItem,
        parent: Event,
        instanceKey: String
    ): Intent {
        val parentId = (item.action as? ScheduleDisplayItem.ActionTarget.RecurringOccurrence)?.parentId ?: parent.id ?: 0L
        return Intent(appContext, AlarmReceiver::class.java).apply {
            action = spec.action
            putExtra("EVENT_ID", instanceKey)
            putExtra("EVENT_PARENT_ID", parentId)
            putExtra("EVENT_OCCURRENCE_TS", item.startTS)
            putExtra("EVENT_TITLE", item.title)
            putExtra("REMINDER_LABEL", spec.label)
            putExtra("EVENT_LOCATION", item.location)
            putExtra("EVENT_START_TIME", item.startTime)
            putExtra("EVENT_END_TIME", item.endTime)
            putExtra("EVENT_TAG", item.tag)
            putExtra("EVENT_COLOR", item.color)
            putExtra("EVENT_RULE_ID", RuleMatchingEngine.resolvePayload(parent)?.ruleId ?: item.tag)
            if (spec.actualStartMillis > 0L) {
                putExtra("ACTUAL_START_MILLIS", spec.actualStartMillis)
            }
        }
    }

    private fun sendImmediateRecurringAlarm(
        spec: AlarmSpec,
        item: ScheduleDisplayItem,
        parent: Event,
        instanceKey: String
    ) {
        appContext.sendBroadcast(createRecurringAlarmIntent(spec, item, parent, instanceKey))
    }

    private fun cancelRequestCode(requestCode: Int) {
        cancelBroadcast(requestCode, AlarmReceiver::class.java, null)
        listOf(
            NotificationScheduler.ACTION_REMINDER,
            NotificationScheduler.ACTION_CAPSULE_START,
            NotificationScheduler.ACTION_REFRESH_CAPSULE,
            NotificationScheduler.ACTION_CAPSULE_END
        ).forEach { action ->
            cancelBroadcast(requestCode, AlarmReceiver::class.java, action)
        }
    }

    private fun cancelBroadcast(
        requestCode: Int,
        receiverClass: Class<*>,
        actionName: String?
    ) {
        val intent = Intent(appContext, receiverClass).apply {
            if (actionName != null) action = actionName
        }
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun scheduleReminderAlarm(triggerMillis: Long, pendingIntent: PendingIntent): Boolean {
        return try {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact reminder alarm permission missing, falling back to inexact alarm", e)
            scheduleInexactReminderAlarm(triggerMillis, pendingIntent)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Alarm quota reached, skip reminder scheduling", e)
            false
        }
    }

    private fun scheduleInexactReminderAlarm(triggerMillis: Long, pendingIntent: PendingIntent): Boolean {
        return try {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule fallback reminder alarm", e)
            false
        }
    }

    private fun buildRequestCode(eventId: Long, minutes: Int, type: Int): Int {
        return (eventId.toString() + ":" + minutes + ":" + type).hashCode()
    }

    private fun buildInstanceRequestCode(instanceKey: String, action: String, marker: Int, type: Int): Int {
        return "$instanceKey:$action:$marker:$type".hashCode()
    }

    private fun cancelDisplayedStandardNotificationsForEvent(eventId: Long) {
        val notificationManager = NotificationManagerCompat.from(appContext)
        (setOf(NotificationIds.standardReminder(eventId)) + NotificationIds.legacyEventIds(eventId))
            .forEach { notificationManager.cancel(it) }
    }

    private fun cancelDisplayedNotificationsForInactiveEvent(eventId: Long) {
        val notificationManager = NotificationManagerCompat.from(appContext)
        (setOf(
            NotificationIds.standardReminder(eventId),
            NotificationIds.liveCapsule(eventId),
            NotificationIds.pickupInitial(eventId)
        ) + NotificationIds.legacyEventIds(eventId)).forEach { notificationManager.cancel(it) }
    }

    private fun cancelDisplayedNotificationsForInstance(instanceKey: String) {
        val notificationManager = NotificationManagerCompat.from(appContext)
        (setOf(
            NotificationIds.standardReminder(instanceKey),
            NotificationIds.liveCapsule(instanceKey)
        ) + NotificationIds.legacyKeyIds(instanceKey)).forEach { notificationManager.cancel(it) }
    }

    private fun shouldSendImmediateReminder(
        triggerMillis: Long,
        startMillis: Long,
        endMillis: Long,
        reminder: Reminder,
        now: Long
    ): Boolean {
        if (now >= endMillis) return false
        return if (reminder.minutes > 0) {
            now < startMillis && triggerMillis <= now
        } else {
            now >= startMillis
        }
    }

    private fun markImmediateReminderIfNeeded(notificationKey: String, minutes: Int): Boolean {
        val key = activeMarkerKey(notificationKey, minutes)
        if (prefs.getBoolean(key, false)) return false
        prefs.edit().putBoolean(key, true).apply()
        return true
    }

    private fun clearActiveMarkersForNotificationKey(notificationKey: String) {
        val prefix = "$ACTIVE_KEY_PREFIX$notificationKey:"
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach(editor::remove)
        editor.apply()
    }

    private fun clearActiveMarkersExcept(liveKeys: Set<String>) {
        val editor = prefs.edit()
        prefs.all.keys
            .filter { it.startsWith(ACTIVE_KEY_PREFIX) }
            .filter { key -> liveKeys.none { liveKey -> key.startsWith("$ACTIVE_KEY_PREFIX$liveKey:") } }
            .forEach(editor::remove)
        editor.apply()
    }

    private fun settings(): MySettings {
        return (appContext as? App)?.settingsQueryApi?.settings?.value ?: MySettings()
    }

    // 重载：支持 Long eventId 和 String instanceKey
    private fun saveRequestCodes(eventId: Long, codes: Set<Int>) {
        val value = if (codes.isEmpty()) "" else codes.joinToString(",")
        prefs.edit().putString(keyForEvent(eventId), value).apply()
    }

    private fun saveRequestCodes(instanceKey: String, codes: Set<Int>) {
        val value = if (codes.isEmpty()) "" else codes.joinToString(",")
        prefs.edit().putString(keyForInstance(instanceKey), value).apply()
    }

    private fun loadRequestCodes(eventId: Long): List<Int> {
        val raw = prefs.getString(keyForEvent(eventId), "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(',').mapNotNull { it.toIntOrNull() }
    }

    private fun loadRequestCodes(instanceKey: String): List<Int> {
        val raw = prefs.getString(keyForInstance(instanceKey), "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(',').mapNotNull { it.toIntOrNull() }
    }

    private fun keyForEvent(eventId: Long): String = "$KEY_PREFIX$eventId"
    private fun keyForInstance(instanceKey: String): String = "$INSTANCE_KEY_PREFIX$instanceKey"
    private fun activeMarkerKey(notificationKey: String, minutes: Int): String = "$ACTIVE_KEY_PREFIX$notificationKey:$minutes"
    private fun singleNotificationKey(eventId: Long): String = "single:$eventId"

    companion object {
        private const val TAG = "ReminderStoreNode"
        private const val PREF_NAME = "event_reminder_registry"
        private const val KEY_PREFIX = "event_"
        private const val INSTANCE_KEY_PREFIX = "inst_"
        private const val ACTIVE_KEY_PREFIX = "active_"
        private const val CAPSULE_START_MARKER = -100_001
        private const val CAPSULE_REFRESH_MARKER = -100_002
        private const val CAPSULE_END_MARKER = -100_003
    }
}
