package com.antgskds.calendarassistant.platform.receiver

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.core.sms.SmsPickupSource
import com.antgskds.calendarassistant.data.source.SettingsDataSource

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
        private val DEDUP_LOCK = Any()

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
            if (isSystemHintText(text)) {
                Log.d(TAG, "[探针] 系统提示通知，忽略: ${text.take(40)}")
                return
            }

            // 去重：同一内容 2 秒内不重复处理
            val now = System.currentTimeMillis()
            synchronized(DEDUP_LOCK) {
                if (lastContent == text && now - lastTs < 2000L) {
                    Log.d(TAG, "[探针] 重复通知，跳过: ${text.take(30)}")
                    return
                }
                lastContent = text
                lastTs = now
            }

            Log.d(TAG, "[探针] 收到短信通知, pkg=$pkg, text=${text.take(80)}...")

            processNotification(context, text, pkg, sbn.postTime)
        } catch (e: Exception) {
            Log.e(TAG, "[探针] onNotificationPosted 异常", e)
        }
    }

    /** 将通知文本作为短信候选提交到统一协调器。 */
    private fun processNotification(context: Context, text: String, pkg: String, postTime: Long) {
        val app = context.applicationContext as? App ?: return
        app.smsPickupIngestCoordinator.submit(
            source = SmsPickupSource.NOTIFICATION_LISTENER,
            sender = pkg,
            body = text,
            smsId = postTime
        )
        Log.d(TAG, "[探针] 已提交短信通知候选, pkg=$pkg")
    }

    /**
     * 从通知 extras 中提取文本，兼容各种通知样式
     */
    private fun extractNotificationText(extras: Bundle): String? {
        // MessagingStyle 的 android.messages（优先，最接近短信正文）
        val messages = extras.getParcelableArray("android.messages")
        val lastMsgText = messages?.lastOrNull()
            ?.let { it as? Bundle }
            ?.getCharSequence("text")?.toString()
        if (!lastMsgText.isNullOrBlank()) return lastMsgText

        // textLines（某些应用多行文本放这里）
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?: extras.getCharSequenceArray("android.textLines")
        val fromLines = lines
            ?.mapNotNull { it?.toString()?.trim() }
            ?.lastOrNull { it.isNotBlank() }
        if (!fromLines.isNullOrBlank()) return fromLines

        // 优先取大文本
        val main = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)
            ?: extras.getCharSequence("android.text"))
            ?.toString()
        if (!main.isNullOrBlank()) return main

        return null
    }

    private fun isSystemHintText(text: String): Boolean {
        val normalized = text.trim()
        if (normalized.isEmpty()) return true
        return normalized.contains("点按即可了解详情或停止应用") ||
            normalized.contains("了解详情或停止应用")
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
