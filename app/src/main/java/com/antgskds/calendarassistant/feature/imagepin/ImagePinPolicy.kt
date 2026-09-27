package com.antgskds.calendarassistant.feature.imagepin

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import com.antgskds.calendarassistant.platform.permission.AndroidPermissionChecker

object ImagePinPolicy {
    fun enabled(settings: MySettings) = settings.imagePinEnabled
    fun canPublish(settings: MySettings) = enabled(settings) && settings.isLiveCapsuleEnabled

    fun captureBlockReason(context: Context, settings: MySettings): String? = captureBlockReason(
        settings = settings,
        overlayAllowed = AndroidPermissionChecker().canDrawOverlays(context),
        notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled(),
        channelAllowed = context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(App.CHANNEL_ID_LIVE)?.importance != NotificationManager.IMPORTANCE_NONE,
    )

    internal fun captureBlockReason(settings: MySettings, overlayAllowed: Boolean,
        notificationsAllowed: Boolean, channelAllowed: Boolean): String? = when {
        !enabled(settings) -> "请先在实验室开启图片挂起"
        !settings.isLiveCapsuleEnabled -> "请先开启实况通知"
        !overlayAllowed -> "请先允许悬浮窗权限，以便查看挂起图片"
        !notificationsAllowed -> "请先允许通知权限"
        !channelAllowed -> "请先开启实况通知渠道"
        else -> null
    }
}
