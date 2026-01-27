package com.antgskds.calendarassistant.service.capsule.provider

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.MainActivity
import com.antgskds.calendarassistant.R

class NativeCapsuleProvider : ICapsuleProvider {
    companion object {
        private const val TAG = "NativeCapsuleProvider"
    }

    override fun buildNotification(
        context: Context,
        eventId: String,
        title: String,
        content: String,
        color: Int
    ): Notification {

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            eventId.hashCode(),
            tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val collapsedTitle = if (title.length > 10) "${title.take(10)}..." else title

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

        // ========================================================================
        // 【关键修复】添加原生胶囊所需的 extras 配置
        // 这些配置对所有 Android 系统都有效，是胶囊正常显示的关键
        // ========================================================================
        val extras = Bundle().apply {
            putBoolean("android.substName", true)
            putString("android.title", collapsedTitle)
        }
        builder.addExtras(extras)

        // 只提醒一次，避免重复响铃
        builder.setOnlyAlertOnce(true)

        return builder.build()
    }
}