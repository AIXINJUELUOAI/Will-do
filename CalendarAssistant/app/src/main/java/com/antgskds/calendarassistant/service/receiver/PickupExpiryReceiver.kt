package com.antgskds.calendarassistant.service.receiver

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.R
import com.antgskds.calendarassistant.service.accessibility.TextAccessibilityService
import com.antgskds.calendarassistant.service.notification.NotificationScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 取件码过期预警接收器
 * 负责：
 * 1. 接收闹钟广播，弹出"即将过期"通知
 * 2. 处理通知上的"延长30分钟"点击事件
 */
class PickupExpiryReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SHOW_WARNING = "com.antgskds.calendarassistant.action.PICKUP_WARNING"
        const val ACTION_EXTEND_30 = "com.antgskds.calendarassistant.action.EXTEND_30"
        const val ACTION_DISMISS = "com.antgskds.calendarassistant.action.DISMISS_WARNING"

        const val EXTRA_EVENT_ID = "event_id"
        // 使用一个固定的基准 ID，加上 hash 防止冲突
        private const val NOTIFICATION_ID_BASE = 50000
    }

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: return
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // 保证通知 ID 唯一但可复用
        val notifId = NOTIFICATION_ID_BASE + (eventId.hashCode() % 10000)

        when (intent.action) {
            ACTION_SHOW_WARNING -> {
                showWarningNotification(context, eventId, notifId, notificationManager)
            }
            ACTION_EXTEND_30 -> {
                // GoAsync 允许在 Receiver 中执行短时异步操作 (DB IO)
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        extendEventDuration(context, eventId)
                        // 操作完成后取消该预警通知
                        notificationManager.cancel(notifId)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            ACTION_DISMISS -> {
                notificationManager.cancel(notifId)
            }
        }
    }

    private fun showWarningNotification(
        context: Context,
        eventId: String,
        notifId: Int,
        manager: NotificationManager
    ) {
        // 1. 构建 "延长30分钟" 的动作
        val extendIntent = Intent(context, PickupExpiryReceiver::class.java).apply {
            action = ACTION_EXTEND_30
            putExtra(EXTRA_EVENT_ID, eventId)
        }
        val extendPendingIntent = PendingIntent.getBroadcast(
            context,
            notifId + 1, // unique request code
            extendIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 2. 构建 "忽略" 的动作
        val dismissIntent = Intent(context, PickupExpiryReceiver::class.java).apply {
            action = ACTION_DISMISS
            putExtra(EXTRA_EVENT_ID, eventId)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            notifId + 2,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 3. 发送通知
        val notification = NotificationCompat.Builder(context, App.CHANNEL_ID_POPUP)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // 确保有此资源，或者换成你的 ic_notification
            .setContentTitle("取件码即将过期")
            .setContentText("是否延长显示 30 分钟？")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            // 添加操作按钮
            .addAction(android.R.drawable.ic_input_add, "延长 30 分钟", extendPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "忽略", dismissPendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(notifId, notification)
    }

    private suspend fun extendEventDuration(context: Context, eventId: String) {
        val repository = (context.applicationContext as App).repository

        // 1. 获取事件
        val event = repository.events.value.find { it.id == eventId } ?: return

        // 2. 计算新时间 (当前结束时间 + 30分钟)
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        val currentEndTime = try {
            LocalTime.parse(event.endTime, formatter)
        } catch (e: Exception) {
            LocalTime.now()
        }
        val newEndTime = currentEndTime.plusMinutes(30).format(formatter)

        val updatedEvent = event.copy(endTime = newEndTime)

        // 3. 更新数据库
        repository.updateEvent(updatedEvent)

        // 4. 重新设置预警闹钟 (递归：延长后的新结束时间前5分钟再次提醒)
        NotificationScheduler.scheduleExpiryWarning(context, updatedEvent)

        // 5. 立即刷新胶囊服务
        CoroutineScope(Dispatchers.Main).launch {
            TextAccessibilityService.instance?.refreshCapsuleState()
            Toast.makeText(context, "已延长至 $newEndTime", Toast.LENGTH_SHORT).show()
        }
    }
}