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
import com.antgskds.calendarassistant.service.capsule.CapsuleService
import com.antgskds.calendarassistant.service.receiver.EventActionReceiver
import java.time.Duration
import java.time.Instant

class NativeCapsuleProvider : ICapsuleProvider {
    companion object {
        private const val TAG = "NativeCapsuleProvider"
    }

    override fun buildNotification(
        context: Context,
        eventId: String,
        title: String,
        content: String,
        color: Int,
        capsuleType: Int,  // 新增参数，但原生胶囊暂时忽略此参数
        eventType: String,  // 新增参数：事件类型（暂时忽略）
        actualStartTime: Long,  // 实际开始时间（毫秒），用于计算"还有x分钟开始"
        actualEndTime: Long  // 实际结束时间（毫秒），用于判断取件码是否过期
    ): Notification {

        // 根据胶囊类型添加跳转参数：取件码胶囊跳转到临时事件列表
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (capsuleType == 2 || eventType == "temp") {
                putExtra("openPickupList", true)
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            eventId.hashCode(),
            tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val collapsedTitle = when {
            transportInfo != null && transportInfo.type != TransportType.NONE -> transportInfo.mainDisplay
            title.length > 10 -> "${title.take(10)}..."
            else -> title
        }

        // 【网速胶囊】直接使用 title 作为状态文本
        val statusText = if (capsuleType == CapsuleService.TYPE_NETWORK_SPEED) {
            title
        } else {
            // 计算胶囊文案（根据是否提前开始）
            when {
                actualStartTime > 0 && System.currentTimeMillis() < actualStartTime -> {
                    // 提前提醒阶段：显示"还有 x 分钟开始"
                    val now = System.currentTimeMillis()
                    val minutesRemaining = Duration.between(
                        Instant.ofEpochMilli(now),
                        Instant.ofEpochMilli(actualStartTime)
                    ).toMinutes()
                    when {
                        minutesRemaining <= 0 -> "即将开始"
                        minutesRemaining == 1L -> "还有 1 分钟开始"
                        else -> "还有 ${minutesRemaining} 分钟开始"
                    }
                }
                else -> "进行中"
            }
        }

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, App.CHANNEL_ID_LIVE)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }

        val icon = Icon.createWithResource(context, R.drawable.ic_notification_small)

        builder.setSmallIcon(icon)
            .setContentTitle(collapsedTitle)
            .setContentText(statusText)
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

        // ========================================================================
        // 【按钮逻辑】根据胶囊类型和过期状态动态添加按钮
        // ========================================================================

        // 判定是否过期
        val isExpired = capsuleType == 3 || (actualEndTime > 0 && System.currentTimeMillis() >= actualEndTime)

        when (capsuleType) {
            CapsuleService.TYPE_NETWORK_SPEED -> {
                // 网速胶囊：不需要额外按钮
                builder.setOnlyAlertOnce(true)
            }
            CapsuleService.TYPE_PICKUP -> {
                // 取件码未过期：显示"已取"按钮
                val completeIntent = Intent(context, EventActionReceiver::class.java).apply {
                    action = EventActionReceiver.ACTION_COMPLETE
                    putExtra(EventActionReceiver.EXTRA_EVENT_ID, eventId)
                }
                val pendingComplete = PendingIntent.getBroadcast(
                    context,
                    eventId.hashCode() + 1,
                    completeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val completeAction = Notification.Action.Builder(
                    R.drawable.ic_notification_small,
                    "已取",
                    pendingComplete
                ).build()
                builder.addAction(completeAction)
                builder.setOnlyAlertOnce(true)
            }
            CapsuleService.TYPE_PICKUP_EXPIRED -> {
                // 取件码已过期：显示"延长"按钮
                val extendIntent = Intent(context, EventActionReceiver::class.java).apply {
                    action = EventActionReceiver.ACTION_EXTEND
                    putExtra(EventActionReceiver.EXTRA_EVENT_ID, eventId)
                }
                val pendingExtend = PendingIntent.getBroadcast(
                    context,
                    eventId.hashCode() + 2,
                    extendIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val extendAction = Notification.Action.Builder(
                    R.drawable.ic_notification_small,
                    "延长30分",
                    pendingExtend
                ).build()
                builder.addAction(extendAction)
                builder.setOnlyAlertOnce(false)
                builder.setPriority(Notification.PRIORITY_MAX)
                builder.setCategory(Notification.CATEGORY_ALARM)
                builder.setDefaults(Notification.DEFAULT_ALL)
            }
            CapsuleService.TYPE_SCHEDULE -> {
                // 日程胶囊：未过期显示"已完成"按钮，已过期不显示按钮
                if (!isExpired) {
                    val completeIntent = Intent(context, EventActionReceiver::class.java).apply {
                        action = EventActionReceiver.ACTION_COMPLETE_SCHEDULE
                        putExtra(EventActionReceiver.EXTRA_EVENT_ID, eventId)
                    }
                    val pendingComplete = PendingIntent.getBroadcast(
                        context,
                        eventId.hashCode() + 3,
                        completeIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    val completeAction = Notification.Action.Builder(
                        R.drawable.ic_notification_small,
                        "已完成",
                        pendingComplete
                    ).build()
                    builder.addAction(completeAction)
                }
                builder.setOnlyAlertOnce(true)
            }
            else -> {
                // 其他类型默认只提醒一次
                builder.setOnlyAlertOnce(true)
            }
        }

        return builder.build()
    }
}