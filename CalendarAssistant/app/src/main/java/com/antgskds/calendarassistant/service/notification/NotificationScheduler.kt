package com.antgskds.calendarassistant.service.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.service.receiver.AlarmReceiver
import com.antgskds.calendarassistant.service.receiver.PickupExpiryReceiver
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

    // Action 常量，与 CapsuleService 保持一致
    const val ACTION_REMINDER = "ACTION_REMINDER"
    const val ACTION_CAPSULE_START = CapsuleService.ACTION_START
    const val ACTION_CAPSULE_END = "ACTION_CAPSULE_END"

    private const val OFFSET_CAPSULE_START = 100000
    private const val OFFSET_CAPSULE_END = 200000

    fun scheduleReminders(context: Context, event: MyEvent) {
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

        // 1. 调度普通提醒
        event.reminders.forEach { minutesBefore ->
            val triggerTime = startMillis - (minutesBefore * 60 * 1000)
            if (triggerTime > System.currentTimeMillis()) {
                val label = REMINDER_OPTIONS.find { it.first == minutesBefore }?.second ?: ""
                scheduleSingleAlarm(
                    context, event, minutesBefore, triggerTime, label,
                    ACTION_REMINDER, alarmManager
                )
            }
        }

        // 2. 调度胶囊开始
        if (startMillis > System.currentTimeMillis()) {
            scheduleCapsuleAlarm(context, event, startMillis, ACTION_CAPSULE_START, alarmManager)
        }

        // 3. 调度胶囊结束
        if (endMillis > System.currentTimeMillis()) {
            scheduleCapsuleAlarm(context, event, endMillis, ACTION_CAPSULE_END, alarmManager)
        }
    }

    /**
     * 【新增】为取件码设置过期预警 (结束前5分钟)
     */
    fun scheduleExpiryWarning(context: Context, event: MyEvent) {
        if (event.eventType != "temp") return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        try {
            val endDateTime = LocalDateTime.of(
                event.endDate,
                LocalTime.parse(event.endTime, DateTimeFormatter.ofPattern("HH:mm"))
            )
            // 触发时间 = 结束时间 - 5分钟
            val triggerMillis = endDateTime.minusMinutes(5)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()

            // 如果已经过期或不足5分钟，不设置
            if (triggerMillis < System.currentTimeMillis()) return

            val intent = Intent(context, PickupExpiryReceiver::class.java).apply {
                action = PickupExpiryReceiver.ACTION_SHOW_WARNING
                putExtra(PickupExpiryReceiver.EXTRA_EVENT_ID, event.id)
            }

            // 使用 offset 500000 避免与常规提醒的 ID 冲突
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                event.id.hashCode() + 500000,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 使用 setAndAllowWhileIdle 确保在低功耗模式下也能唤醒
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            }

            Log.d("NotificationScheduler", "预警闹钟已设置: ${event.title} at $triggerMillis")

        } catch (e: Exception) {
            Log.e("NotificationScheduler", "设置预警失败", e)
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
        }
        val requestCode = (event.id.hashCode() + minutesBefore).toInt()
        scheduleAlarmExact(context, triggerTime, intent, requestCode, alarmManager)
    }

    private fun scheduleCapsuleAlarm(
        context: Context, event: MyEvent, triggerTime: Long, actionType: String, alarmManager: AlarmManager
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
        }
        val offset = if (actionType == ACTION_CAPSULE_START) OFFSET_CAPSULE_START else OFFSET_CAPSULE_END
        val requestCode = (event.id.hashCode() + offset).toInt()
        scheduleAlarmExact(context, triggerTime, intent, requestCode, alarmManager)
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

        // 1. 取消普通提醒
        event.reminders.forEach { minutesBefore ->
            cancelPendingIntent(context, event.id.hashCode() + minutesBefore, ACTION_REMINDER, alarmManager)
        }

        // 2. 取消胶囊开始
        cancelPendingIntent(context, event.id.hashCode() + OFFSET_CAPSULE_START, ACTION_CAPSULE_START, alarmManager)

        // 3. 取消胶囊结束
        cancelPendingIntent(context, event.id.hashCode() + OFFSET_CAPSULE_END, ACTION_CAPSULE_END, alarmManager)

        // 4. 取消取件码预警 (假设 offset 是 500000)
        cancelPendingIntent(context, event.id.hashCode() + 500000, PickupExpiryReceiver.ACTION_SHOW_WARNING, alarmManager)

        // 5. 安全停止胶囊服务
        if (CapsuleService.isServiceRunning) {
            try {
                val stopIntent = Intent(context, CapsuleService::class.java).apply {
                    this.action = CapsuleService.ACTION_STOP
                    putExtra("EVENT_ID", event.id)
                }
                context.startService(stopIntent)
            } catch (e: Exception) {
                Log.e("Scheduler", "停止胶囊服务失败", e)
            }
        }
    }

    private fun cancelPendingIntent(context: Context, requestCode: Int, action: String, alarmManager: AlarmManager) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
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
}