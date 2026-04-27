package com.antgskds.calendarassistant.store.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.data.model.ScheduleDisplayItem
import com.antgskds.calendarassistant.calendar.helpers.STATE_PENDING
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.Reminder
import com.antgskds.calendarassistant.calendar.receivers.EventReminderReceiver
import com.antgskds.calendarassistant.data.model.MySettings

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
        // 跳过已完成/非待办事件
        if (event.state != STATE_PENDING) {
            cancelForEvent(eventId)
            clearActiveMarkersForNotificationKey(singleNotificationKey(eventId))
            return
        }

        val now = System.currentTimeMillis()
        val nowSec = now / 1000L
        val notificationKey = singleNotificationKey(eventId)
        if (event.endTS <= nowSec) {
            cancelForEvent(eventId)
            clearActiveMarkersForNotificationKey(notificationKey)
            return
        }

        cancelForEvent(eventId)
        val requestCodes = mutableSetOf<Int>()
        ReminderPolicy.effectiveReminders(event, settings()).forEach { reminder ->
            val triggerMillis = (event.startTS - reminder.minutes * 60L) * 1000L
            val requestCode = buildRequestCode(eventId, reminder.minutes, reminder.type)
            if (triggerMillis > now) {
                val pendingIntent = createPendingIntent(
                    requestCode = requestCode,
                    eventId = eventId,
                    title = event.title,
                    description = event.description
                )
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                requestCodes.add(requestCode)
            } else if (shouldSendImmediateReminder(triggerMillis, event.startTS * 1000L, event.endTS * 1000L, reminder, now) &&
                markImmediateReminderIfNeeded(notificationKey, reminder.minutes)
            ) {
                sendImmediateReminder(eventId, event.title, event.description)
            }
        }

        saveRequestCodes(eventId, requestCodes)
    }

    fun cancelForEvent(eventId: Long) {
        val codes = loadRequestCodes(eventId)
        codes.forEach { requestCode ->
            val pendingIntent = PendingIntent.getBroadcast(
                appContext,
                requestCode,
                Intent(appContext, EventReminderReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
        prefs.edit().remove(keyForEvent(eventId)).apply()
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

            val reminders = when (val target = item.action) {
                is ScheduleDisplayItem.ActionTarget.Single -> {
                    // 单次事件的通知由 rebuildForEvent 管理，这里跳过
                    continue
                }
                is ScheduleDisplayItem.ActionTarget.RecurringOccurrence -> {
                    // 从母事件获取提醒分钟数
                    val parent = parentEvents[target.parentId] ?: continue
                    ReminderPolicy.effectiveReminders(parent, settings())
                }
            }

            val instanceKey = item.stableKey
            val existingCodes = loadRequestCodes(instanceKey).toSet()

            // 注册新通知
            val requestCodes = existingCodes.toMutableSet()
            for (reminder in reminders) {
                val triggerMillis = (item.startTS - reminder.minutes * 60L) * 1000L
                val requestCode = buildInstanceRequestCode(instanceKey, reminder.minutes, reminder.type)
                if (triggerMillis > now) {
                    if (requestCode in existingCodes) continue
                    val pendingIntent = createPendingIntent(
                        requestCode = requestCode,
                        eventId = (item.action as? ScheduleDisplayItem.ActionTarget.RecurringOccurrence)?.parentId ?: 0L,
                        title = item.title,
                        description = item.description
                    )
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                    requestCodes.add(requestCode)
                } else if (shouldSendImmediateReminder(triggerMillis, item.startTS * 1000L, item.endTS * 1000L, reminder, now) &&
                    markImmediateReminderIfNeeded(instanceKey, reminder.minutes)
                ) {
                    sendImmediateReminder(
                        eventId = (item.action as? ScheduleDisplayItem.ActionTarget.RecurringOccurrence)?.parentId ?: 0L,
                        title = item.title,
                        description = item.description
                    )
                }
            }
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

    fun getScheduledReminderCount(eventId: Long): Int = loadRequestCodes(eventId).size

    // ══════════════════════════════════════════════════════════════════════
    // 内部方法
    // ══════════════════════════════════════════════════════════════════════

    private fun cancelForInstanceKey(instanceKey: String) {
        val codes = loadRequestCodes(instanceKey)
        codes.forEach { requestCode ->
            val pendingIntent = PendingIntent.getBroadcast(
                appContext,
                requestCode,
                Intent(appContext, EventReminderReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
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

    private fun buildRequestCode(eventId: Long, minutes: Int, type: Int): Int {
        return (eventId.toString() + ":" + minutes + ":" + type).hashCode()
    }

    private fun buildInstanceRequestCode(instanceKey: String, minutes: Int, type: Int): Int {
        return "$instanceKey:$minutes:$type".hashCode()
    }

    private fun createPendingIntent(requestCode: Int, eventId: Long, title: String, description: String): PendingIntent {
        val intent = Intent(appContext, EventReminderReceiver::class.java).apply {
            putExtra(EventReminderReceiver.EXTRA_EVENT_ID, eventId)
            putExtra(EventReminderReceiver.EXTRA_TITLE, title)
            putExtra(EventReminderReceiver.EXTRA_DESCRIPTION, description)
        }
        return PendingIntent.getBroadcast(
            appContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun sendImmediateReminder(eventId: Long, title: String, description: String) {
        if (eventId == 0L) return
        val intent = Intent(appContext, EventReminderReceiver::class.java).apply {
            putExtra(EventReminderReceiver.EXTRA_EVENT_ID, eventId)
            putExtra(EventReminderReceiver.EXTRA_TITLE, title)
            putExtra(EventReminderReceiver.EXTRA_DESCRIPTION, description)
        }
        appContext.sendBroadcast(intent)
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
        private const val PREF_NAME = "event_reminder_registry"
        private const val KEY_PREFIX = "event_"
        private const val INSTANCE_KEY_PREFIX = "inst_"
        private const val ACTIVE_KEY_PREFIX = "active_"
    }
}
