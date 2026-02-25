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
import com.antgskds.calendarassistant.service.notification.NotificationScheduler
import com.antgskds.calendarassistant.ui.components.UniversalToastUtil
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
        const val ACTION_EXTEND = "com.antgskds.calendarassistant.action.EXTEND"
        const val ACTION_COMPLETE_SCHEDULE = "com.antgskds.calendarassistant.action.COMPLETE_SCHEDULE"
        const val ACTION_TRANSPORT_CHECK_IN = "com.antgskds.calendarassistant.action.TRANSPORT_CHECK_IN"
        const val ACTION_TRANSPORT_COMPLETE_RIDE = "com.antgskds.calendarassistant.action.TRANSPORT_COMPLETE_RIDE"
        const val EXTRA_EVENT_ID = "event_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: return
        val repository = (context.applicationContext as App).repository
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        when (intent.action) {
            ACTION_COMPLETE -> {
                // 点击"已取" - 直接删除取件码事件
                val pendingResult = goAsync()
                scope.launch {
                    try {
                        // 检查是否是聚合胶囊
                        if (eventId == CapsuleStateManager.AGGREGATE_PICKUP_ID) {
                            // 聚合胶囊：批量删除所有活跃的取件码
                            completeAllActivePickups(repository, context)
                        } else {
                            // 单体取件码：直接删除
                            repository.completePickupEvent(eventId)
                        }
                        withContext(Dispatchers.Main) {
                            UniversalToastUtil.showSuccess(context, "取件码已完成")
                        }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            ACTION_EXTEND -> {
                // 点击"延长" - 复用 PickupExpiryReceiver 的延长逻辑
                val pendingResult = goAsync()
                scope.launch {
                    try {
                        // 检查是否是聚合胶囊
                        if (eventId == CapsuleStateManager.AGGREGATE_PICKUP_ID) {
                            // 聚合胶囊：批量延长所有活跃取件码
                            extendAllActivePickups(repository, context)
                        } else {
                            // 单体取件码：延长单个
                            extendEventDuration(context, eventId, repository)
                        }
                        withContext(Dispatchers.Main) {
                            // 取消通知栏对应的通知（同时取消胶囊通知和初始通知）
                            val nm = NotificationManagerCompat.from(context)
                            nm.cancel(eventId.hashCode())  // 取消胶囊通知
                            nm.cancel(eventId.hashCode() + NotificationScheduler.OFFSET_PICKUP_INITIAL_NOTIF)  // 取消初始通知
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
                        repository.completeScheduleEvent(eventId)
                        withContext(Dispatchers.Main) {
                            UniversalToastUtil.showSuccess(context, "日程已完成")
                        }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            ACTION_TRANSPORT_CHECK_IN -> {
                // 点击"已检票" - 追加已检票状态
                val pendingResult = goAsync()
                scope.launch {
                    try {
                        checkInTransport(eventId, repository)
                        withContext(Dispatchers.Main) {
                            UniversalToastUtil.showSuccess(context, "已标记已检票")
                        }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            ACTION_TRANSPORT_COMPLETE_RIDE -> {
                // 点击"已用车" - 将行程设置为立即过期
                val pendingResult = goAsync()
                scope.launch {
                    try {
                        repository.completeScheduleEvent(eventId)
                        withContext(Dispatchers.Main) {
                            UniversalToastUtil.showSuccess(context, "行程已完成")
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
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        val currentEndTime = try {
            LocalTime.parse(event.endTime, formatter)
        } catch (e: Exception) {
            LocalTime.now()
        }
        val newEndTime = currentEndTime.plusMinutes(30)
        val newEndTimeStr = newEndTime.format(formatter)

        // 检查是否跨越午夜，需要更新 endDate
        val newEndDate = if (newEndTime.isBefore(currentEndTime)) {
            event.endDate.plusDays(1)
        } else {
            event.endDate
        }

        val updatedEvent = event.copy(endTime = newEndTimeStr, endDate = newEndDate)

        // 更新数据库
        repository.updateEvent(updatedEvent)

        // 重新设置预警闹钟
        NotificationScheduler.scheduleExpiryWarning(context, updatedEvent)

        // 【修复问题3】主动触发胶囊状态刷新，无需等待 ticker
        repository.capsuleStateManager.forceRefresh()

        // 刷新胶囊状态（兼容旧逻辑）
        withContext(Dispatchers.Main) {
            val serviceIntent = Intent(context, com.antgskds.calendarassistant.service.capsule.CapsuleService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
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
        val now = System.currentTimeMillis()
        val formatter = DateTimeFormatter.ofPattern("HH:mm")

        // 获取所有取件码类型的活跃事件
        val activePickups = repository.events.value.filter { event ->
            event.eventType == "temp" && try {
                // 检查是否未过期
                val endDateTime = java.time.LocalDateTime.of(
                    event.endDate,
                    java.time.LocalTime.parse(event.endTime, formatter)
                )
                val endMillis = endDateTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                now < endMillis
            } catch (e: Exception) {
                false
            }
        }

        // 批量删除所有活跃取件码
        activePickups.forEach { event ->
            repository.completePickupEvent(event.id)
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
        val now = System.currentTimeMillis()
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        val nowTimeStr = LocalTime.now().format(formatter)
        Log.d("EventActionReceiver", "extendAllActivePickups: now=$now ($nowTimeStr)")

        // 获取所有取件码类型的活跃事件（考虑30分钟宽限期）
        val activePickups = repository.events.value.filter { event ->
            event.eventType == "temp" && try {
                val endDateTime = java.time.LocalDateTime.of(
                    event.endDate,
                    java.time.LocalTime.parse(event.endTime, formatter)
                )
                val endMillis = endDateTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

                // ✅ 修复：胶囊有5分钟宽限期，所以过了 endTime 5分钟内也算活跃
                val graceEndMillis = endMillis + (5 * 60 * 1000)
                now < graceEndMillis
            } catch (e: Exception) {
                false
            }
        }

        Log.d("EventActionReceiver", "extendAllActivePickups: 找到 ${activePickups.size} 个活跃取件码")

        // 批量延长所有活跃取件码
        activePickups.forEach { event ->
            Log.d("EventActionReceiver", "延长取件码: ${event.title}, 当前endTime=${event.endTime}, endDate=${event.endDate}")

            // 计算新时间 (当前结束时间 + 30分钟)
            val currentEndTime = try {
                LocalTime.parse(event.endTime, formatter)
            } catch (e: Exception) {
                LocalTime.now()
            }
            val newEndTime = currentEndTime.plusMinutes(30)
            val newEndTimeStr = newEndTime.format(formatter)

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

            // 重新设置预警闹钟
            NotificationScheduler.scheduleExpiryWarning(context, updatedEvent)
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

        val updatedEvent = event.copy(description = "$currentDesc $checkedInSuffix")
        repository.updateEvent(updatedEvent)

        repository.capsuleStateManager.forceRefresh()

        val serviceIntent = Intent(App.instance, com.antgskds.calendarassistant.service.capsule.CapsuleService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            App.instance.startForegroundService(serviceIntent)
        } else {
            App.instance.startService(serviceIntent)
        }
    }
}
