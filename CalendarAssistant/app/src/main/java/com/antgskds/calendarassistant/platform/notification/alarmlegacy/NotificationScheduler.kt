package com.antgskds.calendarassistant.platform.notification.alarmlegacy

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.*
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.platform.receiver.AlarmReceiver
import com.antgskds.calendarassistant.core.rule.RuleMatchingEngine
import com.antgskds.calendarassistant.shared.management.resource.notification.display.normal.ScheduleNormalDisplay
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 【已降级为底层胶囊闹钟工具】历史名「通知调度器」，但普通日程提醒早已迁到新通知链路
 * （ScheduleNotificationBridge → NotificationApi → NotificationCenter → AndroidNormalNotificationPublisher）。
 *
 * 现存职责仅剩：
 * - 胶囊开启时排 CAPSULE_START/END/REFRESH 系统闹钟（scheduleReminders 内）；
 * - 取消旧闹钟/可见通知（cancelScheduledAlarms / cancelVisibleNotifications）；
 * - 取件码初始通知（showPickupInitialNotification）；
 * - 共享常量 ACTION_CAPSULE_* / REMINDER_OPTIONS。
 *
 * 不要再往这里加「普通通知发布/调度」逻辑——那属于新链路。本对象只作为胶囊闹钟的底层 helper 保留。
 */
object NotificationScheduler {

    val REMINDER_OPTIONS = ScheduleNormalDisplay.reminderOptions

    // Action 常量
    const val ACTION_REMINDER = "ACTION_REMINDER"
    const val ACTION_CAPSULE_START = "ACTION_CAPSULE_START"  // 保留用于 Alarm 识别
    const val ACTION_CAPSULE_END = "ACTION_CAPSULE_END"    // 保留用于 Alarm 识别
    const val ACTION_REFRESH_CAPSULE = "ACTION_REFRESH_CAPSULE" // 刷新胶囊文案（准点时）

    private const val OFFSET_CAPSULE_START = 100000
    private const val OFFSET_CAPSULE_END = 200000
    private const val OFFSET_REFRESH_CAPSULE = 300000 // 刷新胶囊的偏移量
    const val OFFSET_PICKUP_INITIAL_NOTIF = 1000000 // 取件码初始通知的偏移量，避免与胶囊通知冲突（public供StoreRootNode使用）

    fun scheduleReminders(context: Context, event: Event) {
        val settings = (context.applicationContext as? App)
            ?.settingsQueryApi
            ?.settings
            ?.value
            ?: MySettings()
        if (event.isRecurring) {
            Log.d("NotificationScheduler", "跳过重复日程父事件提醒: ${event.id}")
            return
        }

        val isLiveCapsuleEnabled = settings.isLiveCapsuleEnabled
        // Phase 2+：单次日程的「普通提醒」已完全由新通知链路接管
        // （ScheduleNotificationBridge → NotificationApi → NotificationCenter → AndroidNormalNotificationPublisher）。
        // 此处只在胶囊开启时排胶囊闹钟（CAPSULE_START/END/REFRESH，仅作为加速刷新信号，胶囊本体由
        // CapsuleStateManager 实时计算驱动），不再排任何普通提醒，避免与新链路重复。
        if (!isLiveCapsuleEnabled) {
            Log.d("NotificationScheduler", "胶囊关闭，普通提醒由新链路接管，scheduleReminders 不再排程: ${event.id}")
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        val startDateTime = try {
            LocalDateTime.parse("${event.startDate} ${event.startTime}", formatter)
        } catch (e: Exception) { return }

        val endDateTime = try {
            LocalDateTime.parse("${event.endDate} ${event.endTime}", formatter)
        } catch (e: Exception) { startDateTime.plusHours(1) }

        val startMillis = startDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endMillis = endDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val isAdvanceEnabled = settings.isAdvanceReminderEnabled
        val advanceMinutes = settings.advanceReminderMinutes
        val now = System.currentTimeMillis()

        // 全局提前提醒：胶囊开启时走胶囊（从提前点开始显示）
        if (isAdvanceEnabled && advanceMinutes > 0) {
            val triggerTime = startMillis - (advanceMinutes.toLong() * 60 * 1000)
            if (triggerTime > now) {
                scheduleCapsuleAlarm(context, event, triggerTime, startMillis, ACTION_CAPSULE_START, alarmManager)
            }
        }

        // 胶囊开始（仅当未通过全局提前提醒触发时，从日程开始时触发）
        val capsuleTriggerTime = if (isAdvanceEnabled) {
            -1L // 已通过全局提前提醒触发，不再重复
        } else {
            startMillis
        }
        if (!isAdvanceEnabled && capsuleTriggerTime > now) {
            scheduleCapsuleAlarm(context, event, capsuleTriggerTime, startMillis, ACTION_CAPSULE_START, alarmManager)
        }

        // 提前提醒存在时，额外设定准点刷新闹钟（"还有X分钟"→"进行中"）
        if (isAdvanceEnabled && startMillis > now) {
            scheduleRefreshCapsuleAlarm(context, event, startMillis, alarmManager)
        }

        // 胶囊结束
        if (endMillis > now) {
            scheduleCapsuleAlarm(context, event, endMillis, -1L, ACTION_CAPSULE_END, alarmManager)
        }
    }

    private fun scheduleCapsuleAlarm(
        context: Context, event: Event, triggerTime: Long, actualStartTime: Long, actionType: String, alarmManager: AlarmManager
    ) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = actionType
            putExtra("EVENT_ID", event.id?.toString() ?: "")
            putExtra("EVENT_TITLE", event.title)
            putExtra("EVENT_LOCATION", event.location)
            putExtra("EVENT_START_TIME", "${event.startTime}")
            putExtra("EVENT_END_TIME", "${event.endTime}")
            putExtra("EVENT_COLOR", android.graphics.Color.argb(
                android.graphics.Color.alpha(event.color),
                android.graphics.Color.red(event.color),
                android.graphics.Color.green(event.color),
                android.graphics.Color.blue(event.color)
            ))
            // 传入实际开始时间戳（用于胶囊判断显示"还有x分钟"还是"进行中"）
            if (actualStartTime > 0) {
                putExtra("ACTUAL_START_MILLIS", actualStartTime)
            }
            putExtra("EVENT_RULE_ID", RuleMatchingEngine.resolvePayload(event)?.ruleId ?: event.tag)
        }
        val offset = when (actionType) {
            ACTION_CAPSULE_START -> OFFSET_CAPSULE_START
            ACTION_CAPSULE_END -> OFFSET_CAPSULE_END
            else -> 0
        }
        val requestCode = ((event.id ?: 0L).hashCode() + offset).toInt()
        scheduleAlarmExact(context, triggerTime, intent, requestCode, alarmManager)
    }

    /**
     * 调度刷新胶囊的准点闹钟
     * 当启用了提前提醒时，需要在课程真正开始时刷新胶囊文案（从"还有x分钟"改为"进行中"）
     */
    private fun scheduleRefreshCapsuleAlarm(
        context: Context, event: Event, actualStartTime: Long, alarmManager: AlarmManager
    ) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_REFRESH_CAPSULE
            putExtra("EVENT_ID", event.id?.toString() ?: "")
            putExtra("EVENT_TITLE", event.title)
            putExtra("EVENT_LOCATION", event.location)
            putExtra("EVENT_START_TIME", "${event.startTime}")
            putExtra("EVENT_END_TIME", "${event.endTime}")
            putExtra("ACTUAL_START_MILLIS", actualStartTime)
            putExtra("EVENT_COLOR", android.graphics.Color.argb(
                android.graphics.Color.alpha(event.color),
                android.graphics.Color.red(event.color),
                android.graphics.Color.green(event.color),
                android.graphics.Color.blue(event.color)
            ))
            putExtra("EVENT_RULE_ID", RuleMatchingEngine.resolvePayload(event)?.ruleId ?: event.tag)
        }
        val requestCode = ((event.id ?: 0L).hashCode() + OFFSET_REFRESH_CAPSULE).toInt()
        scheduleAlarmExact(context, actualStartTime, intent, requestCode, alarmManager)
    }

    private fun scheduleAlarmExact(
        context: Context, triggerTime: Long, intent: Intent, requestCode: Int, alarmManager: AlarmManager
    ) {
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerTime, pendingIntent), pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        } catch (e: IllegalStateException) {
            Log.e("Scheduler", "Alarm quota reached, skip scheduling", e)
        } catch (e: SecurityException) {
            Log.e("Scheduler", "Permission missing for exact alarm", e)
        }
    }

    fun cancelReminders(context: Context, event: Event) {
        cancelScheduledAlarms(context, event)
        cancelVisibleNotifications(context, event, includeLiveCapsule = true)
    }

    fun cancelScheduledAlarms(context: Context, event: Event) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val reminderMinutes = event.reminderMinutes.toMutableSet()
        reminderMinutes.add(0)
        reminderMinutes.forEach { minutesBefore ->
            cancelPendingIntent(context, (event.id ?: 0L).hashCode() + minutesBefore, ACTION_REMINDER, AlarmReceiver::class.java, alarmManager)
        }

        // 【修复】暴力清除所有可能的全局提醒残留（30/45/60分钟）
        // 即使这些分钟数不在 event.reminderMinutes 中，也要尝试取消
        listOf(30, 45, 60).forEach { mins ->
            if (mins !in event.reminderMinutes) {
                cancelPendingIntent(context, (event.id ?: 0L).hashCode() + mins, ACTION_REMINDER, AlarmReceiver::class.java, alarmManager)
            }
        }

        // 2. 取消胶囊开始
        cancelPendingIntent(context, (event.id ?: 0L).hashCode() + OFFSET_CAPSULE_START, ACTION_CAPSULE_START, AlarmReceiver::class.java, alarmManager)

        // 3. 取消胶囊结束
        cancelPendingIntent(context, (event.id ?: 0L).hashCode() + OFFSET_CAPSULE_END, ACTION_CAPSULE_END, AlarmReceiver::class.java, alarmManager)

        // 4. 取消刷新胶囊闹钟
        cancelPendingIntent(context, (event.id ?: 0L).hashCode() + OFFSET_REFRESH_CAPSULE, ACTION_REFRESH_CAPSULE, AlarmReceiver::class.java, alarmManager)
    }

    fun cancelScheduledAlarms(context: Context, eventId: Long) {
        if (eventId == 0L) return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        REMINDER_OPTIONS.map { it.first }
            .plus(listOf(45))
            .toSet()
            .forEach { minutesBefore ->
                cancelPendingIntent(context, eventId.hashCode() + minutesBefore, ACTION_REMINDER, AlarmReceiver::class.java, alarmManager)
            }
        cancelPendingIntent(context, eventId.hashCode() + OFFSET_CAPSULE_START, ACTION_CAPSULE_START, AlarmReceiver::class.java, alarmManager)
        cancelPendingIntent(context, eventId.hashCode() + OFFSET_CAPSULE_END, ACTION_CAPSULE_END, AlarmReceiver::class.java, alarmManager)
        cancelPendingIntent(context, eventId.hashCode() + OFFSET_REFRESH_CAPSULE, ACTION_REFRESH_CAPSULE, AlarmReceiver::class.java, alarmManager)
    }

    fun cancelVisibleNotifications(context: Context, event: Event, includeLiveCapsule: Boolean) {
        val notificationManager = NotificationManagerCompat.from(context)

        val eventId = event.id ?: 0L
        if (eventId != 0L) {
            val ids = mutableSetOf(
                NotificationIds.standardReminder(eventId),
                NotificationIds.pickupInitial(eventId)
            )
            if (includeLiveCapsule) {
                ids.add(NotificationIds.liveCapsule(eventId))
            }
            (ids + NotificationIds.legacyEventIds(eventId)).forEach(notificationManager::cancel)
        }
    }

    fun cancelVisibleNotifications(context: Context, eventId: Long, includeLiveCapsule: Boolean) {
        if (eventId == 0L) return
        val notificationManager = NotificationManagerCompat.from(context)
        val ids = mutableSetOf(
            NotificationIds.standardReminder(eventId),
            NotificationIds.pickupInitial(eventId)
        )
        if (includeLiveCapsule) {
            ids.add(NotificationIds.liveCapsule(eventId))
        }
        (ids + NotificationIds.legacyEventIds(eventId)).forEach(notificationManager::cancel)

        // ✅ 新架构：Dumb Service 不需要手动停止
        // Service 会通过 uiState 自动管理生命周期
    }

    private fun cancelPendingIntent(context: Context, requestCode: Int, action: String, receiverClass: Class<out BroadcastReceiver>, alarmManager: AlarmManager) {
        val intent = Intent(context, receiverClass).apply {
            this.action = action
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    /**
     * 显示取件码初始通知（创建时立即弹出，带"已取"按钮）
     */
    private fun showPickupInitialNotification(context: Context, event: Event) {
        (context.applicationContext as? App)?.notificationCenter?.showPickupInitialNotification(event)
        Log.d("NotificationScheduler", "取件码初始通知已显示: ${event.title}")
    }
}
