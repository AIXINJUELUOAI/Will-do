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
import com.antgskds.calendarassistant.core.util.TransportInfo
import com.antgskds.calendarassistant.core.util.TransportType
import com.antgskds.calendarassistant.core.util.TransportUtils
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
        capsuleType: Int,
        eventType: String,
        description: String,
        actualStartTime: Long,
        actualEndTime: Long
    ): Notification {

        val transportInfo: TransportInfo? = if (capsuleType == CapsuleService.TYPE_SCHEDULE && description.isNotBlank()) {
            TransportUtils.parse(description)
        } else {
            null
        }

        val isTransportTrain = transportInfo?.type == TransportType.TRAIN
        val isTransportRide = transportInfo?.type == TransportType.RIDE

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
                // 日程胶囊：根据交通场景或普通日程显示不同按钮
                when {
                    // 火车场景：未检票显示"已检票"按钮，已检票不显示
                    isTransportTrain && !isExpired -> {
                        if (!transportInfo.isCheckedIn) {
                            val checkInIntent = Intent(context, EventActionReceiver::class.java).apply {
                                action = EventActionReceiver.ACTION_TRANSPORT_CHECK_IN
                                putExtra(EventActionReceiver.EXTRA_EVENT_ID, eventId)
                            }
                            val pendingCheckIn = PendingIntent.getBroadcast(
                                context,
                                eventId.hashCode() + 4,
                                checkInIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                            )
                            val checkInAction = Notification.Action.Builder(
                                R.drawable.ic_notification_small,
                                "已检票",
                                pendingCheckIn
                            ).build()
                            builder.addAction(checkInAction)
                        }
                    }
                    // 用车场景：显示"已用车"按钮
                    isTransportRide && !isExpired -> {
                        val completeRideIntent = Intent(context, EventActionReceiver::class.java).apply {
                            action = EventActionReceiver.ACTION_TRANSPORT_COMPLETE_RIDE
                            putExtra(EventActionReceiver.EXTRA_EVENT_ID, eventId)
                        }
                        val pendingCompleteRide = PendingIntent.getBroadcast(
                            context,
                            eventId.hashCode() + 5,
                            completeRideIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        val completeRideAction = Notification.Action.Builder(
                            R.drawable.ic_notification_small,
                            "已用车",
                            pendingCompleteRide
                        ).build()
                        builder.addAction(completeRideAction)
                    }
                    // 普通日程：未过期显示"已完成"按钮
                    !isExpired -> {
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