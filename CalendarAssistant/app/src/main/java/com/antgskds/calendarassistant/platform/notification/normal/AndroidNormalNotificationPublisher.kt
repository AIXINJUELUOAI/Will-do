package com.antgskds.calendarassistant.platform.notification.normal

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.antgskds.calendarassistant.MainActivity
import com.antgskds.calendarassistant.R
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationFailureReason
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationKey
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationResult
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationState
import com.antgskds.calendarassistant.feature.api.notification.model.PlatformNotificationPayload
import com.antgskds.calendarassistant.feature.api.notification.ports.PlatformPublisher
import com.antgskds.calendarassistant.platform.receiver.EventActionReceiver

/**
 * Phase 1：新通知链路的「普通 Android 通知」底层发布器。
 *
 * 职责单一：把一份纯数据 [PlatformNotificationPayload] 渲染成系统通知并 notify。
 * 边界：
 * - 不读日程库、不读 Registry、不判断业务规则（这些在上层 NotificationCenter / feature 完成）。
 * - 当前只支持普通（NORMAL）通知；live / 胶囊 / 厂商由后续 publisher 承担。
 * - 渲染对齐旧 [com.antgskds.calendarassistant.calendar.receivers.EventReminderReceiver]：
 *   同一 channel（event_reminders）、ic_launcher、HIGH、autoCancel，便于 Phase 2 无缝切换。
 *
 * Phase 1 仅由开发者「预览 / 强制触发」经 Debug 路径调用；不接管任何真实用户通知。
 */
class AndroidNormalNotificationPublisher(
    context: Context
) : PlatformPublisher {

    private val appContext = context.applicationContext

    override suspend fun publish(payload: PlatformNotificationPayload): NotificationResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                return NotificationResult.Failure(
                    key = payload.key,
                    reason = NotificationFailureReason.PERMISSION_DENIED,
                    message = "POST_NOTIFICATIONS 未授予"
                )
            }
        }

        val channelId = payload.channelKey?.takeIf { it.isNotBlank() } ?: DEFAULT_CHANNEL_ID
        ensureChannel(channelId)

        val display = payload.display
        val title = display.primaryText.ifBlank { display.shortText }
        val contentText = listOfNotNull(
            display.secondaryText?.takeIf { it.isNotBlank() },
            display.tertiaryText?.takeIf { it.isNotBlank() }
        ).joinToString("  ·  ").ifBlank { display.expandedText.orEmpty() }
        val expanded = display.expandedText?.takeIf { it.isNotBlank() }

        val builder = NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(payload.smallIconResId ?: R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(payload.behavior.autoCancel)
            .setOnlyAlertOnce(payload.behavior.onlyAlertOnce)
            .setOngoing(payload.behavior.ongoing)
        payload.behavior.timeoutAfterMillis?.let(builder::setTimeoutAfter)
        if (expanded != null && expanded != contentText) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(expanded))
        }
        builder.setContentIntent(buildContentIntent(payload))
        addActions(builder, payload)

        return try {
            NotificationManagerCompat.from(appContext)
                .notify(payload.notificationId, builder.build())
            Log.d(
                "WillDoNotify",
                "published normal key=${payload.key.value} id=${payload.notificationId} actions=${payload.actions.size}"
            )
            NotificationResult.Success(payload.key, NotificationState.POSTED)
        } catch (t: Throwable) {
            Log.e(TAG, "publish failed for ${payload.key.value}", t)
            NotificationResult.Failure(
                key = payload.key,
                reason = NotificationFailureReason.PUBLISH_FAILED,
                message = t.message,
                cause = t
            )
        }
    }

    override suspend fun cancel(key: NotificationKey): NotificationResult {
        // Phase 1 说明：按 id 取消的真实动作目前仍由 NotificationCenter.cancel() 用快照里的
        // notificationId 执行（见 NotificationCenter.cancelNotification）。本方法保留接口契约，
        // 暂不在发布器侧重复持有 key→id 映射；Phase 2+ 把发布/取消完全收进发布器时再补。
        return NotificationResult.Success(key, NotificationState.CANCELLED)
    }

    private fun buildContentIntent(payload: PlatformNotificationPayload): PendingIntent {
        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            payload.tapTarget?.payload?.forEach { (k, v) -> putExtra(k, v) }
        }
        return PendingIntent.getActivity(
            appContext,
            payload.notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun addActions(
        builder: NotificationCompat.Builder,
        payload: PlatformNotificationPayload
    ) {
        payload.actions.forEach { action ->
            if (action.key.isBlank() || action.label.isBlank()) return@forEach
            val actionIntent = Intent(appContext, EventActionReceiver::class.java).apply {
                this.action = action.key
                action.payload.forEach { (key, value) -> putExtra(key, value) }
            }
            val pendingAction = PendingIntent.getBroadcast(
                appContext,
                payload.notificationId xor action.key.hashCode(),
                actionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(R.drawable.ic_notification_small, action.label, pendingAction)
        }
    }

    private fun ensureChannel(channelId: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(channelId) != null) return
        // 与旧 EventReminderReceiver 对齐：仅在默认提醒 channel 不存在时兜底创建，
        // 不替其它已有 channel 兜底（避免覆盖别处定义的 channel 属性）。
        if (channelId == DEFAULT_CHANNEL_ID) {
            manager.createNotificationChannel(
                NotificationChannel(
                    DEFAULT_CHANNEL_ID,
                    DEFAULT_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
        }
    }

    companion object {
        private const val TAG = "NormalNotifPublisher"

        /** 与旧 EventReminderReceiver 使用同一 channel，便于 Phase 2 平滑接管单次提醒。 */
        const val DEFAULT_CHANNEL_ID = "event_reminders"
        private const val DEFAULT_CHANNEL_NAME = "Event Reminders"
    }
}
