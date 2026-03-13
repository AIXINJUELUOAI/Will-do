package com.antgskds.calendarassistant.service.capsule.provider

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.MainActivity
import com.antgskds.calendarassistant.R
import com.antgskds.calendarassistant.data.model.EventTags
import com.antgskds.calendarassistant.data.model.EventType
import com.antgskds.calendarassistant.data.state.CapsuleUiState
import com.antgskds.calendarassistant.service.capsule.CapsuleActionSpec
import com.antgskds.calendarassistant.service.capsule.CapsuleService
import com.antgskds.calendarassistant.service.capsule.CapsuleUiUtils
import com.antgskds.calendarassistant.service.receiver.EventActionReceiver

/**
 * Flyme 实况胶囊提供者
 */
class FlymeCapsuleProvider : ICapsuleProvider {

    override fun buildNotification(
        context: Context,
        item: CapsuleUiState.Active.CapsuleItem,
        iconResId: Int
    ): Notification {
        val display = item.display
        val subtitleText = buildFlymeSubtitle(display.secondaryText, display.tertiaryText)
        val pendingIntent = createContentPendingIntent(context, item)

        val defaultIconRes = if (iconResId != 0) iconResId else R.drawable.ic_notification_small
        var iconDrawable: Drawable? = ContextCompat.getDrawable(context, defaultIconRes)
        if (iconDrawable == null) {
            iconDrawable = ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
        }

        val rawBitmap = iconDrawable?.let { CapsuleUiUtils.drawableToBitmap(it) }
        val whiteIconBitmap = rawBitmap?.let { CapsuleUiUtils.tintBitmap(it, Color.WHITE) }
        val capsuleIcon = whiteIconBitmap?.let { Icon.createWithBitmap(it) }

        val remoteViews = if (item.type == CapsuleService.TYPE_NETWORK_SPEED) {
            createNetworkSpeedRemoteViews(context, display.primaryText, subtitleText)
        } else {
            createRemoteViews(context, item.type, item.eventType, display.primaryText, subtitleText)
        }

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, App.CHANNEL_ID_LIVE)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }

        val iconRes = if (iconResId != 0) iconResId else R.drawable.ic_notification_small
        val icon = Icon.createWithResource(context, iconRes)
        val collapsedShortText = collapseShortText(display.shortText)

        builder.setSmallIcon(icon)
            .setContentTitle(display.primaryText)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setColor(item.color)
            .setCategory(Notification.CATEGORY_EVENT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCustomContentView(remoteViews)
            .setCustomBigContentView(remoteViews)
            .setGroup("LIVE_CAPSULE_GROUP")
            .setGroupSummary(false)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(false)

        subtitleText?.let { builder.setContentText(it) }
        display.expandedText?.let {
            builder.setStyle(
                Notification.BigTextStyle()
                    .setBigContentTitle(display.primaryText)
                    .bigText(it)
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }

        applyShortCriticalText(builder, collapsedShortText)
        requestPromotedOngoing(builder)
        builder.addExtras(
            createFlymeExtras(
                context = context,
                title = display.primaryText,
                collapsedShortText = collapsedShortText,
                color = item.color,
                capsuleIcon = capsuleIcon
            )
        )

        display.action?.let { addAction(builder, context, item.id, it) }

        return builder.build()
    }

    private fun createContentPendingIntent(
        context: Context,
        item: CapsuleUiState.Active.CapsuleItem
    ): PendingIntent {
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (item.display.tapOpensPickupList) {
                putExtra("openPickupList", true)
            }
        }
        return PendingIntent.getActivity(
            context,
            item.id.hashCode(),
            tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun addAction(
        builder: Notification.Builder,
        context: Context,
        eventId: String,
        action: CapsuleActionSpec
    ) {
        val broadcastIntent = Intent(context, EventActionReceiver::class.java).apply {
            this.action = action.receiverAction
            putExtra(EventActionReceiver.EXTRA_EVENT_ID, eventId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            eventId.hashCode() + 3,
            broadcastIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notificationAction = Notification.Action.Builder(
            null,
            action.label,
            pendingIntent
        ).build()
        builder.addAction(notificationAction)
    }

    private fun createFlymeExtras(
        context: Context,
        title: String,
        collapsedShortText: String,
        color: Int,
        capsuleIcon: Icon?
    ): Bundle {
        val capsuleBundle = Bundle().apply {
            putInt("notification.live.capsuleStatus", 1)
            putInt("notification.live.capsuleType", 1)
            putString("notification.live.capsuleContent", collapsedShortText)
            putInt("notification.live.capsuleBgColor", color)
            putInt("notification.live.capsuleContentColor", Color.WHITE)
            if (capsuleIcon != null) {
                putParcelable("notification.live.capsuleIcon", capsuleIcon)
            }
        }

        return Bundle().apply {
            putBoolean("is_live", true)
            putInt("notification.live.operation", 0)
            putInt("notification.live.type", 10)
            putBundle("notification.live.capsule", capsuleBundle)
            putString("android.substName", context.getString(R.string.app_name))
            putString("android.title", title)
        }
    }

    private fun applyShortCriticalText(builder: Notification.Builder, text: String) {
        try {
            val methodSetText = Notification.Builder::class.java.getMethod(
                "setShortCriticalText",
                String::class.java
            )
            methodSetText.invoke(builder, text)
        } catch (_: Exception) {
        }
    }

    private fun requestPromotedOngoing(builder: Notification.Builder) {
        try {
            val methodSetPromoted = Notification.Builder::class.java.getMethod(
                "setRequestPromotedOngoing",
                Boolean::class.java
            )
            methodSetPromoted.invoke(builder, true)
        } catch (_: Exception) {
        }
    }

    private fun createRemoteViews(
        context: Context,
        capsuleType: Int,
        eventType: String,
        primaryText: String,
        secondaryText: String?
    ): RemoteViews {
        return RemoteViews(context.packageName, R.layout.notification_live_flyme).apply {
            setTextViewText(R.id.tv_main_content, primaryText)
            setTextViewText(R.id.tv_sub_info, secondaryText ?: "")
            setImageViewResource(R.id.iv_icon, resolveFlymeIcon(eventType, capsuleType))
        }
    }

    private fun createNetworkSpeedRemoteViews(
        context: Context,
        primaryText: String,
        subtitleText: String?
    ): RemoteViews {
        return RemoteViews(context.packageName, R.layout.notification_live_network_speed).apply {
            setTextViewText(R.id.tv_main_content, primaryText)
            setTextViewText(R.id.tv_sub_info, subtitleText ?: "下载速度")
            setImageViewResource(R.id.iv_icon, android.R.drawable.stat_sys_download)
        }
    }

    private fun buildFlymeSubtitle(secondaryText: String?, tertiaryText: String?): String? {
        return listOfNotNull(secondaryText, tertiaryText)
            .filter { it.isNotBlank() }
            .distinct()
            .takeIf { it.isNotEmpty() }
            ?.joinToString(" · ")
    }

    private fun resolveFlymeIcon(eventType: String, capsuleType: Int): Int {
        return when (capsuleType) {
            CapsuleService.TYPE_OCR_PROGRESS -> R.drawable.ic_stat_scan
            CapsuleService.TYPE_OCR_RESULT -> R.drawable.ic_stat_success
            else -> when (eventType) {
                EventTags.PICKUP -> R.drawable.ic_capsule_pickup
                EventTags.TRAIN -> R.drawable.ic_capsule_train
                EventTags.TAXI -> R.drawable.ic_capsule_taxi
                EventType.COURSE -> R.drawable.ic_capsule_course
                EventTags.GENERAL -> R.drawable.ic_stat_event
                EventType.EVENT -> R.drawable.ic_capsule_event
                else -> R.drawable.ic_capsule_event
            }
        }
    }

    private fun collapseShortText(text: String): String {
        return if (text.length > 10) text.take(10) else text
    }
}
