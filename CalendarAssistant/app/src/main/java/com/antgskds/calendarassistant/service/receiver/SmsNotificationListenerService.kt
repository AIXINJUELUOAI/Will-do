package com.antgskds.calendarassistant.service.receiver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.R
import com.antgskds.calendarassistant.core.sms.SmsAnalysis
import com.antgskds.calendarassistant.data.source.SettingsDataSource
import com.antgskds.calendarassistant.service.notification.NotificationScheduler
import com.antgskds.calendarassistant.ui.theme.EventColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 短信通知监听服务
 *
 * 对齐 parcel 项目方案：MIUI 会拦截 SMS_RECEIVED 广播，
 * 但系统短信 App 收到短信后一定会弹出通知，通过监听通知来兜底。
 *
 * 系统短信包名：com.android.mms、com.miui.mms 等
 */
class SmsNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "SmsNotifyListener"

        /** 去重：同一内容 2 秒内不重复处理 */
        @Volatile private var lastContent: String? = null
        @Volatile private var lastTs: Long = 0L

        /** 系统短信应用包名 */
        private val SYSTEM_SMS_PACKAGES = setOf(
            "com.android.mms",
            "com.google.android.apps.messaging",
            "com.miui.mms",
            "com.huawei.message",
            "com.samsung.android.messaging",
            "com.coloros.mms",
            "com.oneplus.mms"
        )

        /**
         * 检查通知监听服务是否已启用
         */
        fun isEnabled(context: Context): Boolean {
            val flat = android.provider.Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            val full = ComponentName(context, SmsNotificationListenerService::class.java)
                .flattenToString()
            val short = "${context.packageName}/.service.receiver.SmsNotificationListenerService"
            return flat.split(":").any { it == full || it == short }
        }

        /**
         * 引导用户开启通知监听权限
         */
        fun requestEnable(context: Context) {
            val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

        /**
         * 请求重新绑定（开机/恢复后调用）
         */
        fun rebind(context: Context) {
            try {
                if (isEnabled(context)) {
                    NotificationListenerService.requestRebind(
                        ComponentName(context, SmsNotificationListenerService::class.java)
                    )
                    Log.d(TAG, "请求重新绑定通知监听服务")
                }
            } catch (e: Exception) {
                Log.e(TAG, "重新绑定失败: ${e.message}")
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "[探针] 通知监听服务已连接")

        // Android 8.0+ 需要前台通知
        if (Build.VERSION.SDK_INT >= 26) {
            val channelId = "sms_notify_listener"
            val mgr = getSystemService(NotificationManager::class.java)
            if (mgr?.getNotificationChannel(channelId) == null) {
                val ch = NotificationChannel(channelId, "短信通知监听", NotificationManager.IMPORTANCE_MIN).apply {
                    setShowBadge(false)
                    enableLights(false)
                    enableVibration(false)
                }
                mgr?.createNotificationChannel(ch)
            }
            val notif = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("短信通知监听")
                .setContentText("监听已开启")
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setOngoing(true)
                .build()
            startForeground(1003, notif)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "[探针] 通知监听服务断开连接，尝试重连")
        try {
            val enabled = NotificationManagerCompat
                .getEnabledListenerPackages(applicationContext)
                .contains(applicationContext.packageName)
            if (enabled) {
                requestRebind(
                    ComponentName(this, SmsNotificationListenerService::class.java)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "重连失败: ${e.message}")
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return

        // 只处理系统短信应用的通知
        if (pkg !in SYSTEM_SMS_PACKAGES) return

        try {
            val context = applicationContext
            val settings = SettingsDataSource(context).loadSettings()
            if (!settings.isSmsMonitoringEnabled) return

            val text = extractNotificationText(sbn.notification.extras)
            if (text.isNullOrBlank()) {
                Log.d(TAG, "[探针] 短信通知文本为空, pkg=$pkg")
                return
            }

            // 去重：同一内容 2 秒内不重复处理
            val now = System.currentTimeMillis()
            synchronized(this) {
                if (lastContent == text && now - lastTs < 2000L) {
                    Log.d(TAG, "[探针] 重复通知，跳过: ${text.take(30)}")
                    return
                }
                lastContent = text
                lastTs = now
            }

            Log.d(TAG, "[探针] 收到短信通知, pkg=$pkg, text=${text.take(80)}...")

            // 延迟处理，确保短信已写入数据库（避免与广播通道重复入库）
            Thread {
                try {
                    Thread.sleep(1500L)
                    processNotification(context, text, pkg)
                } catch (e: Exception) {
                    Log.e(TAG, "[探针] 通知处理异常", e)
                }
            }.start()
        } catch (e: Exception) {
            Log.e(TAG, "[探针] onNotificationPosted 异常", e)
        }
    }

    /**
     * 解析通知文本，提取取件码入库
     */
    private fun processNotification(context: Context, text: String, pkg: String) {
        // 尝试从短信数据库检查是否已存在（避免与广播通道重复）
        if (isAlreadyInInbox(context, text)) {
            Log.d(TAG, "[探针] 文本已在短信箱中（可能已被广播通道处理），跳过: ${text.take(30)}")
            return
        }

        val eventData = SmsAnalysis.parse(pkg, text)
        if (eventData == null) {
            Log.d(TAG, "[探针] SmsAnalysis.parse 返回 null，未识别到取件码")
            return
        }

        Log.d(TAG, "[探针] 解析成功: title=${eventData.title}, tag=${eventData.tag}")

        val app = context.applicationContext as? App ?: return
        val repository = app.repository
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        scope.launch {
            try {
                val event = eventDataToMyEvent(eventData, repository.events.value.size)

                // 去重
                val isDuplicate = repository.events.value.any { existing ->
                    existing.tag == eventData.tag &&
                            existing.description == eventData.description &&
                            !existing.endDate.isBefore(java.time.LocalDate.now())
                }
                if (isDuplicate) {
                    Log.d(TAG, "[探针] 重复取件码已跳过: ${eventData.title}")
                    return@launch
                }

                repository.addEvent(event)
                NotificationScheduler.scheduleReminders(context, event)
                Log.d(TAG, "[探针] ✅ 通知通道取件码已入库: ${eventData.title}")
            } catch (e: Exception) {
                Log.e(TAG, "[探针] 入库异常", e)
            }
        }
    }

    /**
     * 从通知 extras 中提取文本，兼容各种通知样式
     */
    private fun extractNotificationText(extras: Bundle): String? {
        // 优先取大文本
        val main = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)
            ?: extras.getCharSequence("android.text"))
            ?.toString()
        if (!main.isNullOrBlank()) return main

        // textLines（某些应用多行文本放这里）
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?: extras.getCharSequenceArray("android.textLines")
        val fromLines = lines?.mapNotNull { it?.toString() }?.lastOrNull { it.isNotBlank() }
        if (!fromLines.isNullOrBlank()) return fromLines

        // MessagingStyle 的 android.messages
        val messages = extras.getParcelableArray("android.messages")
        val lastMsgText = messages?.lastOrNull()
            ?.let { it as? Bundle }
            ?.getCharSequence("text")?.toString()
        if (!lastMsgText.isNullOrBlank()) return lastMsgText

        return null
    }

    /**
     * 检查短信是否已存在于收件箱（避免与广播通道重复）
     */
    private fun isAlreadyInInbox(context: Context, text: String): Boolean {
        return try {
            val cursor = context.contentResolver.query(
                android.provider.Telephony.Sms.CONTENT_URI,
                arrayOf(android.provider.Telephony.Sms.BODY),
                "${android.provider.Telephony.Sms.BODY} = ?",
                arrayOf(text),
                null
            )
            val exists = cursor?.count ?: 0 > 0
            cursor?.close()
            exists
        } catch (e: Exception) {
            false
        }
    }

    override fun onDestroy() {
        try {
            if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {}
        super.onDestroy()
    }
}

/**
 * CalendarEventData → MyEvent（与 SmsReceiver 中保持一致）
 */
private fun eventDataToMyEvent(
    eventData: com.antgskds.calendarassistant.data.model.CalendarEventData,
    currentEventsCount: Int
): com.antgskds.calendarassistant.data.model.MyEvent {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val now = LocalDateTime.now()

    val startDateTime = try {
        if (eventData.startTime.isNotBlank()) LocalDateTime.parse(eventData.startTime, formatter) else now
    } catch (_: Exception) { now }

    val endDateTime = try {
        if (eventData.endTime.isNotBlank()) LocalDateTime.parse(eventData.endTime, formatter) else startDateTime.plusHours(1)
    } catch (_: Exception) { startDateTime.plusHours(1) }

    return com.antgskds.calendarassistant.data.model.MyEvent(
        title = eventData.title.trim(),
        startDate = startDateTime.toLocalDate(),
        endDate = endDateTime.toLocalDate(),
        startTime = startDateTime.format(timeFormatter),
        endTime = endDateTime.format(timeFormatter),
        location = eventData.location,
        description = eventData.description,
        color = EventColors[currentEventsCount % EventColors.size],
        eventType = com.antgskds.calendarassistant.data.model.EventType.EVENT,
        tag = eventData.tag.ifBlank { com.antgskds.calendarassistant.data.model.EventTags.GENERAL }
    )
}
