package com.antgskds.calendarassistant.service.notification

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationCompat
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.R
import com.antgskds.calendarassistant.data.model.EventTags
import com.antgskds.calendarassistant.data.model.EventType
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.data.repository.AppRepository
import com.antgskds.calendarassistant.service.receiver.AlarmReceiver
import com.antgskds.calendarassistant.service.receiver.EventActionReceiver
import com.antgskds.calendarassistant.service.capsule.CapsuleService
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object NotificationScheduler {

    val REMINDER_OPTIONS = listOf(
        0 to "日程开始时",
        5 to "5分钟前",
        10 to "10分钟前",
        15 to "15分钟前",
        30 to "30分钟前",
        60 to "1小时前",
        120 to "2小时前",
        360 to "6小时前",
        1440 to "1天前",
        2880 to "2天前"
    )

    // Action 常量
    const val ACTION_REMINDER = "ACTION_REMINDER"
    const val ACTION_CAPSULE_START = "ACTION_CAPSULE_START"  // 保留用于 Alarm 识别
    const val ACTION_CAPSULE_END = "ACTION_CAPSULE_END"    // 保留用于 Alarm 识别
    const val ACTION_REFRESH_CAPSULE = "ACTION_REFRESH_CAPSULE" // 刷新胶囊文案（准点时）

    private const val OFFSET_CAPSULE_START = 100000
    private const val OFFSET_CAPSULE_END = 200000
    private const val OFFSET_REFRESH_CAPSULE = 300000 // 刷新胶囊的偏移量
    const val OFFSET_PICKUP_INITIAL_NOTIF = 1000000 // 取件码初始通知的偏移量，避免与胶囊通知冲突（public供AppRepository使用）

    fun scheduleReminders(context: Context, event: MyEvent) {
        val settings = AppRepository.getInstance(context).settings.value
        if (event.isRecurringParent || (event.isRecurring && !settings.isRecurringCalendarSyncEnabled)) {
            Log.d("NotificationScheduler", "跳过重复日程提醒: ${event.id}")
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

        // 获取全局设置
        val isAdvanceEnabled = settings.isAdvanceReminderEnabled
        val advanceMinutes = settings.advanceReminderMinutes
        val isLiveCapsuleEnabled = settings.isLiveCapsuleEnabled

        // 1. 调度普通提醒
        // 【规则】：用户自定义提醒始终走普通通知（强制）
        val now = System.currentTimeMillis()
        val immediateThreshold = 60 * 1000L

        event.reminders.forEach { minutesBefore ->
            val triggerTime = startMillis - (minutesBefore * 60 * 1000)
            val label = REMINDER_OPTIONS.find { it.first == minutesBefore }?.second ?: ""
            if (triggerTime > now) {
                scheduleSingleAlarm(
                    context, event, minutesBefore, triggerTime, label,
                    ACTION_REMINDER, alarmManager
                )
            } else if (now - triggerTime < immediateThreshold) {
                AlarmReceiver.showStandardNotification(context, event, label)
            }
        }

        // 2. 全局提前提醒：根据胶囊开关决定走胶囊还是普通通知
        if (isAdvanceEnabled && advanceMinutes > 0) {
            val triggerTime = startMillis - (advanceMinutes * 60 * 1000)
            val label = "提前${advanceMinutes}分钟"
            if (triggerTime > now) {
                if (isLiveCapsuleEnabled) {
                    // 胶囊开启时，全局提前提醒走胶囊
                    scheduleCapsuleAlarm(context, event, triggerTime, startMillis, ACTION_CAPSULE_START, alarmManager)
                } else {
                    // 胶囊关闭时，全局提前提醒走普通通知
                    scheduleSingleAlarm(context, event, advanceMinutes, triggerTime, label, ACTION_REMINDER, alarmManager)
                }
            } else if (now - triggerTime < immediateThreshold) {
                // 过去但 < 1分钟，立即通知
                AlarmReceiver.showStandardNotification(context, event, label)
            }
        }

        // 3. 调度胶囊开始（仅当胶囊开启且不是通过全局提前提醒触发的）
        // 如果全局提前提醒已触发胶囊，则不再重复调度
        val capsuleTriggerTime = if (isAdvanceEnabled && isLiveCapsuleEnabled) {
            -1L // 已通过全局提前提醒触发，不再重复
        } else if (isLiveCapsuleEnabled) {
            startMillis // 胶囊开启但未启用提前提醒，从日程开始时触发
        } else {
            -1L
        }
        if (isLiveCapsuleEnabled && !isAdvanceEnabled && capsuleTriggerTime > System.currentTimeMillis()) {
            scheduleCapsuleAlarm(context, event, capsuleTriggerTime, startMillis, ACTION_CAPSULE_START, alarmManager)
        }

        // 4. 如果启用了胶囊通知且有提前提醒，额外设定准点刷新闹钟
        if (isLiveCapsuleEnabled && isAdvanceEnabled && startMillis > System.currentTimeMillis()) {
            scheduleRefreshCapsuleAlarm(context, event, startMillis, alarmManager)
        }

        // 5. 调度胶囊结束（胶囊开启时）
        if (isLiveCapsuleEnabled && endMillis > System.currentTimeMillis()) {
            scheduleCapsuleAlarm(context, event, endMillis, -1L, ACTION_CAPSULE_END, alarmManager)
        }

        // 6. 胶囊关闭时，日程开始时发送普通通知
        if (!isLiveCapsuleEnabled) {
            if (startMillis > now) {
                scheduleSingleAlarm(
                    context, event, 0, startMillis, "日程开始",
                    ACTION_REMINDER, alarmManager
                )
            } else if (now - startMillis < immediateThreshold) {
                // 过去但 < 1分钟，立即通知
                AlarmReceiver.showStandardNotification(context, event, "日程开始")
            }
        }
    }

    private fun scheduleSingleAlarm(
        context: Context, event: MyEvent, minutesBefore: Int, triggerTime: Long, label: String, actionType: String, alarmManager: AlarmManager
    ) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = actionType
            putExtra("EVENT_ID", event.id)
            putExtra("EVENT_TITLE", event.title)
            putExtra("REMINDER_LABEL", label)
            putExtra("EVENT_LOCATION", event.location)
            putExtra("EVENT_START_TIME", event.startTime)
            putExtra("EVENT_END_TIME", event.endTime)
            putExtra("EVENT_TAG", event.tag)
            putExtra("EVENT_COLOR", android.graphics.Color.argb(
                (event.color.alpha * 255).toInt(),
                (event.color.red * 255).toInt(),
                (event.color.green * 255).toInt(),
                (event.color.blue * 255).toInt()
            ))
        }
        val requestCode = (event.id.hashCode() + minutesBefore).toInt()
        scheduleAlarmExact(context, triggerTime, intent, requestCode, alarmManager)
    }

    private fun scheduleCapsuleAlarm(
        context: Context, event: MyEvent, triggerTime: Long, actualStartTime: Long, actionType: String, alarmManager: AlarmManager
    ) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = actionType
            putExtra("EVENT_ID", event.id)
            putExtra("EVENT_TITLE", event.title)
            putExtra("EVENT_LOCATION", event.location)
            putExtra("EVENT_START_TIME", "${event.startTime}")
            putExtra("EVENT_END_TIME", "${event.endTime}")
            putExtra("EVENT_COLOR", android.graphics.Color.argb(
                (event.color.alpha * 255).toInt(),
                (event.color.red * 255).toInt(),
                (event.color.green * 255).toInt(),
                (event.color.blue * 255).toInt()
            ))
            // 传入实际开始时间戳（用于胶囊判断显示"还有x分钟"还是"进行中"）
            if (actualStartTime > 0) {
                putExtra("ACTUAL_START_MILLIS", actualStartTime)
            }
        }
        val offset = when (actionType) {
            ACTION_CAPSULE_START -> OFFSET_CAPSULE_START
            ACTION_CAPSULE_END -> OFFSET_CAPSULE_END
            else -> 0
        }
        val requestCode = (event.id.hashCode() + offset).toInt()
        scheduleAlarmExact(context, triggerTime, intent, requestCode, alarmManager)
    }

    /**
     * 调度刷新胶囊的准点闹钟
     * 当启用了提前提醒时，需要在课程真正开始时刷新胶囊文案（从"还有x分钟"改为"进行中"）
     */
    private fun scheduleRefreshCapsuleAlarm(
        context: Context, event: MyEvent, actualStartTime: Long, alarmManager: AlarmManager
    ) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_REFRESH_CAPSULE
            putExtra("EVENT_ID", event.id)
            putExtra("EVENT_TITLE", event.title)
            putExtra("EVENT_LOCATION", event.location)
            putExtra("EVENT_START_TIME", "${event.startTime}")
            putExtra("EVENT_END_TIME", "${event.endTime}")
            putExtra("ACTUAL_START_MILLIS", actualStartTime)
            putExtra("EVENT_COLOR", android.graphics.Color.argb(
                (event.color.alpha * 255).toInt(),
                (event.color.red * 255).toInt(),
                (event.color.green * 255).toInt(),
                (event.color.blue * 255).toInt()
            ))
        }
        val requestCode = (event.id.hashCode() + OFFSET_REFRESH_CAPSULE).toInt()
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
        } catch (e: SecurityException) {
            Log.e("Scheduler", "Permission missing for exact alarm", e)
        }
    }

    fun cancelReminders(context: Context, event: MyEvent) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val notificationManager = NotificationManagerCompat.from(context)

        // 1. 取消普通提醒（包括全局提前提醒）
        event.reminders.forEach { minutesBefore ->
            cancelPendingIntent(context, event.id.hashCode() + minutesBefore, ACTION_REMINDER, AlarmReceiver::class.java, alarmManager)
        }

        // 【修复】暴力清除所有可能的全局提醒残留（30/45/60分钟）
        // 即使这些分钟数不在 event.reminders 中，也要尝试取消
        listOf(30, 45, 60).forEach { mins ->
            if (mins !in event.reminders) {
                cancelPendingIntent(context, event.id.hashCode() + mins, ACTION_REMINDER, AlarmReceiver::class.java, alarmManager)
            }
        }

        // 2. 取消胶囊开始
        cancelPendingIntent(context, event.id.hashCode() + OFFSET_CAPSULE_START, ACTION_CAPSULE_START, AlarmReceiver::class.java, alarmManager)

        // 3. 取消胶囊结束
        cancelPendingIntent(context, event.id.hashCode() + OFFSET_CAPSULE_END, ACTION_CAPSULE_END, AlarmReceiver::class.java, alarmManager)

        // 4. 取消刷新胶囊闹钟
        cancelPendingIntent(context, event.id.hashCode() + OFFSET_REFRESH_CAPSULE, ACTION_REFRESH_CAPSULE, AlarmReceiver::class.java, alarmManager)

        // ✅ 取消胶囊通知
        notificationManager.cancel(event.id.hashCode())

        // ✅ 取消取件码初始通知（如果存在）
        notificationManager.cancel(event.id.hashCode() + OFFSET_PICKUP_INITIAL_NOTIF)

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
    private fun showPickupInitialNotification(context: Context, event: MyEvent) {
        val notificationManager = NotificationManagerCompat.from(context)

        // 构建"已取"按钮的 PendingIntent
        val completeIntent = Intent(context, EventActionReceiver::class.java).apply {
            action = EventActionReceiver.ACTION_COMPLETE
            putExtra(EventActionReceiver.EXTRA_EVENT_ID, event.id)
        }
        val pendingComplete = PendingIntent.getBroadcast(
            context,
            event.id.hashCode() + 1,
            completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 构建通知
        val notification = NotificationCompat.Builder(context, App.CHANNEL_ID_POPUP)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle(event.title)
            .setContentText("取件码")
            .setStyle(NotificationCompat.BigTextStyle()
                .setBigContentTitle(event.title)
                .bigText("${event.description}")//删除备注：${event.description}
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(false)
            .setOngoing(true)
            .addAction(R.drawable.ic_notification_small, "已取", pendingComplete)
            .build()

        // 【修复问题2】使用带偏移的 ID，避免与胶囊通知冲突
        notificationManager.notify(event.id.hashCode() + OFFSET_PICKUP_INITIAL_NOTIF, notification)

        Log.d("NotificationScheduler", "取件码初始通知已显示: ${event.title}")
    }
}
