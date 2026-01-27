package com.antgskds.calendarassistant.service.capsule.provider

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.MainActivity
import com.antgskds.calendarassistant.R
import com.antgskds.calendarassistant.service.capsule.CapsuleUiUtils

class FlymeCapsuleProvider : ICapsuleProvider {
    companion object {
        private const val TAG = "FlymeCapsuleProvider"
    }

    override fun buildNotification(
        context: Context,
        eventId: String,
        title: String,
        content: String,
        color: Int
    ): Notification {

        // 1. 点击跳转
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            eventId.hashCode(),
            tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 2. 准备图标 (Flyme 要求白色 Bitmap)
        // 优先尝试 ic_qs_recognition (如果存在), 否则用 launcher_round
        // 注意：这里为了稳健，直接用 ic_launcher_round，你可以根据资源情况调整
        val rawBitmap = CapsuleUiUtils.drawableToBitmap(context, R.mipmap.ic_launcher_round)
        val whiteIconBitmap = if (rawBitmap != null) CapsuleUiUtils.tintBitmap(rawBitmap, Color.WHITE) else null
        val iconObj = whiteIconBitmap?.let { Icon.createWithBitmap(it) }

        // 3. 构建 Flyme 专属 Bundle
        val collapsedTitle = if (title.length > 10) title.take(10) else title

        val capsuleBundle = Bundle().apply {
            putInt("notification.live.capsuleStatus", 1) // 1=进行中
            putInt("notification.live.capsuleType", 1)   // 1=普通胶囊
            putString("notification.live.capsuleContent", collapsedTitle)

            if (iconObj != null) {
                putParcelable("notification.live.capsuleIcon", iconObj)
            }
            putInt("notification.live.capsuleBgColor", color)
            putInt("notification.live.capsuleContentColor", Color.WHITE)
        }

        val liveBundle = Bundle().apply {
            putBoolean("is_live", true)
            putInt("notification.live.operation", 0)
            putInt("notification.live.type", 10)
            putBundle("notification.live.capsule", capsuleBundle)
            putInt("notification.live.contentColor", Color.BLACK)
        }

        val extras = Bundle().apply {
            putBundle("com.meizu.flyme.live_notification", liveBundle)
            // 兼容普通视图的显示
            putBoolean("android.substName", true)
            putString("android.title", collapsedTitle)
        }

        // 4. 构建 Notification
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, App.CHANNEL_ID_LIVE)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }

        val icon = Icon.createWithResource(context, R.mipmap.ic_launcher_round)

        builder.setSmallIcon(icon)
            .setContentTitle(collapsedTitle)
            .setContentText("进行中")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setColor(color)
            .setCategory(Notification.CATEGORY_EVENT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setStyle(Notification.BigTextStyle()
                .setBigContentTitle(title)
                .bigText(content)
            )

        // Android 12+: 立即显示，不折叠
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }

        builder.setGroup("LIVE_CAPSULE_GROUP")
        builder.setGroupSummary(false)
        builder.setWhen(System.currentTimeMillis())
        builder.setShowWhen(true)

        // Android 16 (Baklava) 适配 (反射调用)
        try {
            val methodSetText = Notification.Builder::class.java.getMethod("setShortCriticalText", String::class.java)
            methodSetText.invoke(builder, collapsedTitle)
        } catch (e: Exception) {
            Log.d(TAG, "setShortCriticalText not available")
        }

        try {
            val methodSetPromoted = Notification.Builder::class.java.getMethod("setRequestPromotedOngoing", Boolean::class.java)
            methodSetPromoted.invoke(builder, true)
        } catch (e: Exception) {
            Log.d(TAG, "setRequestPromotedOngoing not available")
        }

        // 注入 Flyme 参数和其他 extras
        builder.addExtras(extras)

        // 只提醒一次，避免重复响铃
        builder.setOnlyAlertOnce(true)

        return builder.build()
    }
}