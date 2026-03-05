package com.antgskds.calendarassistant.service.receiver

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.MainActivity
import com.antgskds.calendarassistant.R
import com.antgskds.calendarassistant.data.model.EventTags
import com.antgskds.calendarassistant.data.model.EventType
import com.antgskds.calendarassistant.service.capsule.CapsuleService
import com.antgskds.calendarassistant.service.accessibility.TextAccessibilityService
import com.antgskds.calendarassistant.service.notification.NotificationScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 广播接收器：AlarmReceiver
 *
 * 职责：接收 AlarmManager 的定时广播，并分流处理：
 * 1. 普通提醒 -> 直接发送 Notification
 * 2. 胶囊开始 -> 启动 [CapsuleService] (ACTION_START)
 * 3. 胶囊结束 -> 启动 [CapsuleService] (ACTION_STOP)
 *
 * 安全加固：在处理任何操作前，先验证事件是否仍存在于数据源中
 */
class AlarmReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "AlarmReceiver"
        private const val EVENT_CHECK_TIMEOUT_MS = 1000L // 事件检查超时时间（毫秒）
    }

    override fun onReceive(context: Context, intent: Intent) {
        // ✅ 1. 获取 PendingResult，告诉系统"我还有异步任务要做，别杀我"
        val pendingResult = goAsync()

        // ✅ 2. 在协程中处理业务
        CoroutineScope(Dispatchers.Main).launch {
            try {
                handleReceiveAsync(context, intent)
            } catch (e: Exception) {
                Log.e(TAG, "AlarmReceiver error", e)
            } finally {
                // ✅ 3. 必须在 finally 中调用 finish()，否则会导致 ANR
                pendingResult.finish()
            }
        }
    }

    /**
     * 将原来的逻辑封装到 suspend 函数中
     */
    private suspend fun handleReceiveAsync(context: Context, intent: Intent) {
        val action = intent.action
        val eventId = intent.getStringExtra("EVENT_ID")

        if (eventId == null) {
            Log.w(TAG, "收到广播但 EVENT_ID 为空，忽略处理")
            return
        }

        // 安全加固：第一道防线 - 检查事件是否还存在
        // 如果事件已被删除，直接返回，不进行任何后续操作
        if (!isEventStillValid(context, eventId)) {
            Log.i(TAG, "事件 $eventId 已不存在，跳过通知/服务启动")
            return
        }

        val eventTitle = intent.getStringExtra("EVENT_TITLE") ?: "日程提醒"

        when (action) {
            // 使用 Scheduler 中定义的常量，确保逻辑一致
            NotificationScheduler.ACTION_CAPSULE_START -> {
                handleCapsuleStart(context, intent, eventId, eventTitle)
            }
            NotificationScheduler.ACTION_CAPSULE_END -> {
                handleCapsuleEnd(context, eventId)
            }
            NotificationScheduler.ACTION_REFRESH_CAPSULE -> {
                handleCapsuleRefresh(context, eventId, eventTitle)
            }
            NotificationScheduler.ACTION_REMINDER, null -> {
                // 处理普通提醒（action 可能为 null 的情况作为兜底）
                val reminderLabel = intent.getStringExtra("REMINDER_LABEL") ?: ""
                val eventLocation = intent.getStringExtra("EVENT_LOCATION") ?: ""
                val eventStartTime = intent.getStringExtra("EVENT_START_TIME") ?: ""
                val eventEndTime = intent.getStringExtra("EVENT_END_TIME") ?: ""
                val eventTag = intent.getStringExtra("EVENT_TAG") ?: ""
                val eventColor = intent.getIntExtra("EVENT_COLOR", 0)
                showStandardNotification(
                    context, eventId, eventTitle, reminderLabel,
                    eventLocation, eventStartTime, eventEndTime, eventTag, eventColor
                )
            }
            else -> {
                Log.w(TAG, "未知的 action: $action，按普通提醒处理")
                val reminderLabel = intent.getStringExtra("REMINDER_LABEL") ?: ""
                val eventLocation = intent.getStringExtra("EVENT_LOCATION") ?: ""
                val eventStartTime = intent.getStringExtra("EVENT_START_TIME") ?: ""
                val eventEndTime = intent.getStringExtra("EVENT_END_TIME") ?: ""
                val eventTag = intent.getStringExtra("EVENT_TAG") ?: ""
                val eventColor = intent.getIntExtra("EVENT_COLOR", 0)
                showStandardNotification(
                    context, eventId, eventTitle, reminderLabel,
                    eventLocation, eventStartTime, eventEndTime, eventTag, eventColor
                )
            }
        }
    }

    /**
     * 检查事件是否仍然存在于数据源中
     *
     * 作为安全防线，防止已删除事件的闹钟仍触发通知
     *
     * @param context 上下文
     * @param eventId 事件ID
     * @return true 如果事件仍存在，false 如果事件已被删除
     */
    private fun isEventStillValid(context: Context, eventId: String): Boolean {
        return try {
            val repository = (context.applicationContext as App).repository

            // 使用协程带超时检查，避免阻塞主线程太久
            var isValid = false
            val checkJob = CoroutineScope(Dispatchers.IO).launch {
                val events = repository.events.value
                isValid = events.any { it.id == eventId }
            }

            // 等待检查完成，但设置超时避免卡住
            kotlinx.coroutines.runBlocking {
                withTimeoutOrNull(EVENT_CHECK_TIMEOUT_MS) {
                    checkJob.join()
                }
            }

            if (!isValid) {
                Log.w(TAG, "事件验证失败: eventId=$eventId 不存在")
            }

            isValid
        } catch (e: Exception) {
            Log.e(TAG, "检查事件存在性时出错: ${e.message}", e)
            // 出错时默认返回 true，避免误杀正常通知
            true
        }
    }

    private fun handleCapsuleStart(context: Context, intent: Intent, eventId: String, title: String) {
        // ✅ 适配：通过 Repository 获取设置
        val repository = (context.applicationContext as App).repository
        val settings = repository.settings.value

        // 检查无障碍服务是否运行 (作为系统能力锁)
        // 检查用户开关 (作为意愿锁)
        val isServiceRunning = TextAccessibilityService.instance != null
        val isEnabled = settings.isLiveCapsuleEnabled

        if (isEnabled && isServiceRunning) {
            Log.d(TAG, "启动胶囊服务: $title (新架构：启动后会自动订阅uiState)")

            // ✅ 新架构：Dumb Service 只需要启动，会自动订阅 uiState 并显示胶囊
            val serviceIntent = Intent(context, CapsuleService::class.java)
            // 不再需要 action，Service 启动后会自动处理

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }

            // 2. 听觉层：播放提示音
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val hasPermission = notificationManager.areNotificationsEnabled()

            if (hasPermission) {
                Log.i(TAG, "胶囊已启动，有通知权限 -> 播放提示音")
                playAlert(context)
            } else {
                Log.i(TAG, "胶囊已启动，但无通知权限 -> 保持静默")
            }

        } else {
            // 【降级逻辑】
            Log.d(TAG, "跳过实况胶囊 (开关:$isEnabled, OCR服务:$isServiceRunning) -> 降级为普通通知")
            showStandardNotification(context, eventId, title, "日程开始")
        }
    }

    /**
     * 处理胶囊刷新（准点时刷新文案从"还有x分钟"改为"进行中"）
     * 新架构：Dumb Service 只需要重新启动，会自动重新订阅 uiState 并更新胶囊显示
     */
    private fun handleCapsuleRefresh(context: Context, eventId: String, title: String) {
        Log.d(TAG, "刷新胶囊: $title (准点时刷新文案)")

        // 重新启动 CapsuleService，它会重新计算状态并更新通知
        val serviceIntent = Intent(context, CapsuleService::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    private fun handleCapsuleEnd(context: Context, eventId: String) {
        // ✅ 新架构：Dumb Service 不需要 ACTION_STOP
        // 只需启动 Service，它会重新订阅 uiState 并自动更新/停止
        val serviceIntent = Intent(context, CapsuleService::class.java)
        // 不需要 action，Service 启动后会自动检查状态并更新

        // 使用 startService 发送停止指令通常足够且更安全（避免 Foreground 限制）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    private fun showStandardNotification(
        context: Context, eventId: String, title: String, label: String,
        eventLocation: String = "", eventStartTime: String = "", eventEndTime: String = "",
        eventTag: String = "", eventColor: Int = 0
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, eventId.hashCode(), tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 使用 App.kt 中定义的通知渠道 ID
        val channelId = App.CHANNEL_ID_POPUP

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle(title)
            .setContentText(if(label.isNotEmpty()) "$label: $title" else "日程即将开始")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        // 设置颜色（如果有）
        if (eventColor != 0) {
            builder.setColor(eventColor)
        }

        // 添加操作按钮（根据事件类型）
        // 检查事件是否已完成/已检票
        val repository = try {
            (context.applicationContext as App).repository
        } catch (e: Exception) { null }

        if (repository != null && eventTag.isNotEmpty()) {
            try {
                val event = repository.events.value.find { it.id == eventId }
                if (event != null) {
                    val isCompleted = event.isCompleted
                    val isCheckedIn = event.isCheckedIn

                    // 根据事件类型决定是否显示按钮
                    val shouldShowButton = when (eventTag) {
                        EventTags.TRAIN -> !isCheckedIn
                        EventTags.PICKUP, EventTags.TAXI, EventTags.GENERAL -> !isCompleted
                        else -> false
                    }

                    if (shouldShowButton) {
                        val buttonText = when (eventTag) {
                            EventTags.PICKUP -> "已取"
                            EventTags.TAXI -> "已用车"
                            EventTags.TRAIN -> "已检票"
                            else -> "已完成"
                        }
                        val intentAction = when (eventTag) {
                            EventTags.TRAIN -> EventActionReceiver.ACTION_CHECKIN
                            else -> EventActionReceiver.ACTION_COMPLETE_SCHEDULE
                        }

                        val actionIntent = Intent(context, EventActionReceiver::class.java).apply {
                            action = intentAction
                            putExtra(EventActionReceiver.EXTRA_EVENT_ID, eventId)
                        }
                        val pendingAction = PendingIntent.getBroadcast(
                            context, eventId.hashCode() + 100, actionIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )

                        builder.addAction(
                            R.drawable.ic_notification_small,
                            buttonText,
                            pendingAction
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "检查事件状态失败: ${e.message}")
            }
        }

        val notification = builder.build()
        manager.notify(eventId.hashCode(), notification)
        Log.d(TAG, "已显示普通通知: title=$title, label=$label, tag=$eventTag")
    }

    private fun playAlert(context: Context) {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context, uri)
            ringtone?.play()

            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (vibrator.hasVibrator()) {
                val timing = longArrayOf(0, 200, 100, 200)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(timing, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(timing, -1)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "播放替身提示音失败", e)
        }
    }
}
