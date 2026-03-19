package com.antgskds.calendarassistant.service.receiver

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
import com.antgskds.calendarassistant.core.capsule.CapsuleStateManager
import com.antgskds.calendarassistant.core.util.OsUtils
import com.antgskds.calendarassistant.data.model.EventTags
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.data.model.EventType
import com.antgskds.calendarassistant.service.notification.NotificationScheduler
import com.antgskds.calendarassistant.ui.components.UniversalToastUtil
import com.antgskds.calendarassistant.xposed.XposedModuleStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 事件动作接收器
 * 处理取件码的"已取"和"延长"操作
 */
class EventActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_COMPLETE = "com.antgskds.calendarassistant.action.COMPLETE"
        const val ACTION_COMPLETE_SCHEDULE = "com.antgskds.calendarassistant.action.COMPLETE_SCHEDULE"
        const val ACTION_CHECKIN = "com.antgskds.calendarassistant.action.CHECKIN"
        const val EXTRA_EVENT_ID = "event_id"
        private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
    }

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID)
        val repository = (context.applicationContext as App).repository
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        when (intent.action) {
            ACTION_COMPLETE -> {
                val targetEventId = eventId ?: return
                // 点击"已完成" - 将日程设置为立即过期
                val pendingResult = goAsync()
                scope.launch {
                    try {
                        repository.completeScheduleEvent(targetEventId)
                        withContext(Dispatchers.Main) {
                            UniversalToastUtil.showSuccess(context, "已完成")
                        }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            ACTION_COMPLETE_SCHEDULE -> {
                // 点击"已完成" - 将日程设置为立即过期
                val pendingResult = goAsync()
                scope.launch {
                    try {
                        if (eventId == CapsuleStateManager.AGGREGATE_PICKUP_ID) {
                            completeAllActivePickups(repository, context)
                        } else {
                            val targetEventId = eventId ?: return@launch
                            repository.completeScheduleEvent(targetEventId)
                            withContext(Dispatchers.Main) {
                                UniversalToastUtil.showSuccess(context, "日程已完成")
                            }
                        }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            ACTION_CHECKIN -> {
                val targetEventId = eventId ?: return
                // 点击"已检票" - 标记火车票已检票
                val pendingResult = goAsync()
                scope.launch {
                    try {
                        repository.checkInTransport(targetEventId)
                        withContext(Dispatchers.Main) {
                            UniversalToastUtil.showSuccess(context, "已检票")
                        }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    /**
     * 延长事件结束时间30分钟
     * 复用 PickupExpiryReceiver 的逻辑
     */
    private suspend fun extendEventDuration(context: Context, eventId: String, repository: com.antgskds.calendarassistant.data.repository.AppRepository) {
        val event = repository.getEventById(eventId) ?: return

        // 计算新时间 (当前结束时间 + 30分钟)
        val currentEndTime = try {
            LocalTime.parse(event.endTime, TIME_FORMATTER)
        } catch (e: Exception) {
            LocalTime.now()
        }
        val newEndTime = currentEndTime.plusMinutes(30)
        val newEndTimeStr = newEndTime.format(TIME_FORMATTER)

        // 检查是否跨越午夜，需要更新 endDate
        val newEndDate = if (newEndTime.isBefore(currentEndTime)) {
            event.endDate.plusDays(1)
        } else {
            event.endDate
        }

        val updatedEvent = event.copy(endTime = newEndTimeStr, endDate = newEndDate)

        // 更新数据库
        repository.updateEvent(updatedEvent)

        // 【修复问题3】主动触发胶囊状态刷新，无需等待 ticker
        repository.capsuleStateManager.forceRefresh()

        // 刷新胶囊状态（兼容旧逻辑）
        withContext(Dispatchers.Main) {
            if (!isMiuiIslandMode(context)) {
                val serviceIntent = Intent(context, com.antgskds.calendarassistant.service.capsule.CapsuleService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
            UniversalToastUtil.showSuccess(context, "已延长至 $newEndTimeStr")
        }
    }

    /**
     * 批量完成所有活跃的取件码（聚合胶囊使用）
     * 获取所有未过期的取件码并批量删除
     */
    private suspend fun completeAllActivePickups(
        repository: com.antgskds.calendarassistant.data.repository.AppRepository,
        context: Context
    ) {
        val now = java.time.LocalDateTime.now()
        val settings = repository.settings.value

        // 获取所有取件码类型的活跃事件
        val activePickups = repository.events.value.filter { event ->
            isAggregateActivePickup(event, settings, now)
        }

        // 批量完成所有活跃取件码
        activePickups.forEach { event ->
            repository.completeScheduleEvent(event.id)
        }

        // 取消聚合胶囊的通知
        val nm = NotificationManagerCompat.from(context)
        nm.cancel(CapsuleStateManager.AGGREGATE_NOTIF_ID)

        // 显示删除数量
        if (activePickups.isNotEmpty()) {
            withContext(Dispatchers.Main) {
                UniversalToastUtil.showSuccess(context, "已完成 ${activePickups.size} 个取件码")
            }
        }

        // 主动触发胶囊状态刷新
        repository.capsuleStateManager.forceRefresh()
    }

    /**
     * 批量延长所有活跃取件码的结束时间（聚合胶囊使用）
     * 将所有活跃取件码的结束时间延长30分钟
     */
    private suspend fun extendAllActivePickups(
        repository: com.antgskds.calendarassistant.data.repository.AppRepository,
        context: Context
    ) {
        val now = java.time.LocalDateTime.now()
        val settings = repository.settings.value
        val nowTimeStr = LocalTime.now().format(TIME_FORMATTER)
        Log.d("EventActionReceiver", "extendAllActivePickups: now=$now ($nowTimeStr)")

        // 获取所有取件码类型的活跃事件（考虑30分钟宽限期）
        val activePickups = repository.events.value.filter { event ->
            isAggregateActivePickup(event, settings, now)
        }

        Log.d("EventActionReceiver", "extendAllActivePickups: 找到 ${activePickups.size} 个活跃取件码")

        // 批量延长所有活跃取件码
        activePickups.forEach { event ->
            Log.d("EventActionReceiver", "延长取件码: ${event.title}, 当前endTime=${event.endTime}, endDate=${event.endDate}")

            // 计算新时间 (当前结束时间 + 30分钟)
            val currentEndTime = try {
                LocalTime.parse(event.endTime, TIME_FORMATTER)
            } catch (e: Exception) {
                LocalTime.now()
            }
            val newEndTime = currentEndTime.plusMinutes(30)
            val newEndTimeStr = newEndTime.format(TIME_FORMATTER)

            // 检查是否跨越午夜，需要更新 endDate
            val newEndDate = if (newEndTime.isBefore(currentEndTime)) {
                event.endDate.plusDays(1)
            } else {
                event.endDate
            }

            val updatedEvent = event.copy(endTime = newEndTimeStr, endDate = newEndDate)
            Log.d("EventActionReceiver", "更新取件码: 新endTime=$newEndTimeStr, 新endDate=$newEndDate")

            // 更新数据库
            repository.updateEvent(updatedEvent)
        }

        // 主动触发胶囊状态刷新
        repository.capsuleStateManager.forceRefresh()

        // 取消聚合胶囊的通知
        val nm = NotificationManagerCompat.from(context)
        nm.cancel(CapsuleStateManager.AGGREGATE_NOTIF_ID)

        // 显示延长数量
        if (activePickups.isNotEmpty()) {
            withContext(Dispatchers.Main) {
                UniversalToastUtil.showSuccess(context, "已延长 ${activePickups.size} 个取件码30分钟")
            }
        }
    }

    /**
     * 标记火车票已检票
     * 在 description 末尾追加 (已检票) 标记
     */
    private suspend fun checkInTransport(
        eventId: String,
        repository: com.antgskds.calendarassistant.data.repository.AppRepository
    ) {
        val event = repository.getEventById(eventId) ?: return

        val currentDesc = event.description
        val checkedInSuffix = "(已检票)"

        if (currentDesc.endsWith(checkedInSuffix)) {
            return
        }

        val updatedEvent = event.copy(
            description = "$currentDesc $checkedInSuffix",
            isCheckedIn = true
        )
        repository.updateEvent(updatedEvent)

        repository.capsuleStateManager.forceRefresh()

        if (!isMiuiIslandMode(App.instance)) {
            val serviceIntent = Intent(App.instance, com.antgskds.calendarassistant.service.capsule.CapsuleService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                App.instance.startForegroundService(serviceIntent)
            } else {
                App.instance.startService(serviceIntent)
            }
        }
    }

    private fun isAggregateActivePickup(
        event: MyEvent,
        settings: MySettings,
        now: java.time.LocalDateTime
    ): Boolean {
        if (event.tag != EventTags.PICKUP || event.isCompleted || event.isRecurringParent) {
            return false
        }

        return try {
            val startDateTime = java.time.LocalDateTime.of(
                event.startDate,
                LocalTime.parse(event.startTime, TIME_FORMATTER)
            )
            val endDateTime = java.time.LocalDateTime.of(
                event.endDate,
                LocalTime.parse(event.endTime, TIME_FORMATTER)
            )
            val effectiveStartTime = if (settings.isAdvanceReminderEnabled && settings.advanceReminderMinutes > 0) {
                startDateTime.minusMinutes(settings.advanceReminderMinutes.toLong())
            } else {
                startDateTime.minusMinutes(1)
            }

            now.isBefore(endDateTime) && !now.isBefore(effectiveStartTime)
        } catch (e: Exception) {
            Log.e("EventActionReceiver", "解析聚合取件时间失败: ${event.id}", e)
            false
        }
    }

    private fun isMiuiIslandMode(context: Context): Boolean {
        return try {
            val settings = (context.applicationContext as App).repository.settings.value
            settings.isLiveCapsuleEnabled && OsUtils.isHyperOS() && XposedModuleStatus.isActive()
        } catch (_: Exception) {
            false
        }
    }
}
