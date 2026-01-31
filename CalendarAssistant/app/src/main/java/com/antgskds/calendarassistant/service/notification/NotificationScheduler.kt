package com.antgskds.calendarassistant.service.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.data.repository.AppRepository
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

    // Action 常量
    const val ACTION_REMINDER = "ACTION_REMINDER"
    const val ACTION_CAPSULE_START = "ACTION_CAPSULE_START"  // 保留用于 Alarm 识别
    const val ACTION_CAPSULE_END = "ACTION_CAPSULE_END"    // 保留用于 Alarm 识别
    const val ACTION_REFRESH_CAPSULE = "ACTION_REFRESH_CAPSULE" // 刷新胶囊文案（准点时）

    private const val OFFSET_CAPSULE_START = 100000
    private const val OFFSET_CAPSULE_END = 200000
    private const val OFFSET_REFRESH_CAPSULE = 300000 // 刷新胶囊的偏移量

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

        // 获取全局设置
        val settings = AppRepository.getInstance(context).settings.value
        val isAdvanceEnabled = settings.isAdvanceReminderEnabled
        val advanceMinutes = settings.advanceReminderMinutes

        // 临时取件码不应用全局提前提醒
        val shouldApplyAdvance = isAdvanceEnabled && event.eventType != "temp"

        // 计算胶囊启动时间（可能提前）
        val capsuleStartTime = if (shouldApplyAdvance) {
            startMillis - (advanceMinutes * 60 * 1000)
        } else {
            startMillis
        }

        // 1. 调度普通提醒（叠加全局提前提醒）
        val scheduledMinutes = mutableSetOf<Int>() // 用于去重

        // 用户自定义的提醒
        event.reminders.forEach { minutesBefore ->
            val triggerTime = startMillis - (minutesBefore * 60 * 1000)
            if (triggerTime > System.currentTimeMillis()) {
                val label = REMINDER_OPTIONS.find { it.first == minutesBefore }?.second ?: ""
                scheduleSingleAlarm(
                    context, event, minutesBefore, triggerTime, label,
                    ACTION_REMINDER, alarmManager
                )
                scheduledMinutes.add(minutesBefore)
            }
        }

        // 全局提前提醒（叠加模式，需去重）
        if (shouldApplyAdvance && advanceMinutes > 0 && advanceMinutes !in scheduledMinutes) {
            val triggerTime = startMillis - (advanceMinutes * 60 * 1000)
            if (triggerTime > System.currentTimeMillis()) {
                val label = "提前${advanceMinutes}分钟"
                scheduleSingleAlarm(
                    context, event, advanceMinutes, triggerTime, label,
                    ACTION_REMINDER, alarmManager
                )
            }
        }

        // 2. 调度胶囊开始（可能提前）
        if (capsuleStartTime > System.currentTimeMillis()) {
            scheduleCapsuleAlarm(context, event, capsuleStartTime, startMillis, ACTION_CAPSULE_START, alarmManager)
        }

        // 3. 如果启用了提前提醒，额外设定准点刷新闹钟
        if (shouldApplyAdvance && startMillis > System.currentTimeMillis()) {
            scheduleRefreshCapsuleAlarm(context, event, startMillis, alarmManager)
        }

        // 4. 调度胶囊结束（时间不变）
        if (endMillis > System.currentTimeMillis()) {
            scheduleCapsuleAlarm(context, event, endMillis, -1L, ACTION_CAPSULE_END, alarmManager)
        }

        // 5. 为取件码/取餐码设置过期预警
        if (event.eventType == "temp") {
            scheduleExpiryWarning(context, event)
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

        // 1. 取消普通提醒（包括全局提前提醒）
        event.reminders.forEach { minutesBefore ->
            cancelPendingIntent(context, event.id.hashCode() + minutesBefore, ACTION_REMINDER, AlarmReceiver::class.java, alarmManager)
        }

        // 【修复】暴力清除所有可能的全局提醒残留（10/20/30分钟）
        // 即使这些分钟数不在 event.reminders 中，也要尝试取消
        listOf(10, 20, 30).forEach { mins ->
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

        // 5. 取消取件码预警
        cancelPendingIntent(context, event.id.hashCode() + 500000, PickupExpiryReceiver.ACTION_SHOW_WARNING, PickupExpiryReceiver::class.java, alarmManager)

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
}