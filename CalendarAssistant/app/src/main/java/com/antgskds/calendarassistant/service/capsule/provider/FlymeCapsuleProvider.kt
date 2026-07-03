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
import androidx.core.content.ContextCompat
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.MainActivity
import com.antgskds.calendarassistant.R
import com.antgskds.calendarassistant.data.state.CapsuleUiState
import com.antgskds.calendarassistant.service.capsule.CapsuleActionSpec
import com.antgskds.calendarassistant.service.capsule.CapsuleUiUtils
import com.antgskds.calendarassistant.platform.receiver.EventActionReceiver
import com.antgskds.calendarassistant.shared.management.resource.notification.display.live.vendor.flyme.FlymeLiveNotificationTemplate
import kotlin.math.pow

/**
 * Flyme 实况胶囊提供者
 */
class FlymeCapsuleProvider : ICapsuleProvider {

    override fun buildNotification(
        context: Context,
        item: CapsuleUiState.Active.CapsuleItem,
        iconResId: Int
    ): Notification {
        val content = FlymeLiveNotificationTemplate.create(context, item, iconResId)
        val pendingIntent = createContentPendingIntent(context, item, content.tapOpensPickupList)

        var iconDrawable: Drawable? = ContextCompat.getDrawable(context, content.smallIconResId)
        if (iconDrawable == null) {
            iconDrawable = ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
        }

        val capsuleBgColor = normalizeCapsuleBgColor(item.color)
        val capsuleContentColor = resolveReadableContentColor(capsuleBgColor)
        val rawBitmap = iconDrawable?.let { CapsuleUiUtils.drawableToBitmap(it) }
        val capsuleIconBitmap = rawBitmap?.let { CapsuleUiUtils.tintBitmap(it, capsuleContentColor) }
        val capsuleIcon = capsuleIconBitmap?.let { Icon.createWithBitmap(it) }

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, App.CHANNEL_ID_LIVE)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }

        val icon = Icon.createWithResource(context, content.smallIconResId)

        builder.setSmallIcon(icon)
            .setContentTitle(content.title)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setColor(capsuleBgColor)
            .setCategory(Notification.CATEGORY_EVENT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCustomContentView(content.remoteViews)
            .setCustomBigContentView(content.remoteViews)
            .setGroup("LIVE_CAPSULE_GROUP")
            .setGroupSummary(false)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(false)

        content.subtitleText?.let { builder.setContentText(it) }
        content.expandedText?.let {
            builder.setStyle(
                Notification.BigTextStyle()
                    .setBigContentTitle(content.title)
                    .bigText(it)
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }

        applyShortCriticalText(builder, content.collapsedShortText)
        requestPromotedOngoing(builder)
        builder.addExtras(
            createFlymeExtras(
                context = context,
                title = content.title,
                collapsedShortText = content.collapsedShortText,
                color = capsuleBgColor,
                contentColor = capsuleContentColor,
                capsuleIcon = capsuleIcon
            )
        )

        content.action?.let { addAction(builder, context, item.id, it) }

        return builder.build()
    }

    private fun createContentPendingIntent(
        context: Context,
        item: CapsuleUiState.Active.CapsuleItem,
        tapOpensPickupList: Boolean
    ): PendingIntent {
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (tapOpensPickupList) {
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
            if (action.extraLongKey != null && action.extraLongValue != null) {
                putExtra(action.extraLongKey, action.extraLongValue)
            } else {
                putExtra(EventActionReceiver.EXTRA_EVENT_ID, eventId)
            }
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
        contentColor: Int,
        capsuleIcon: Icon?
    ): Bundle {
        val capsuleBundle = Bundle().apply {
            putInt("notification.live.capsuleStatus", 1)
            putInt("notification.live.capsuleType", 1)
            putString("notification.live.capsuleContent", collapsedShortText)
            putInt("notification.live.capsuleBgColor", color)
            putInt("notification.live.capsuleContentColor", contentColor)
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

    private fun normalizeCapsuleBgColor(color: Int): Int {
        val rgb = color and 0x00FFFFFF
        if (rgb == 0) return DEFAULT_CAPSULE_BG_COLOR
        return Color.rgb(Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun resolveReadableContentColor(backgroundColor: Int): Int {
        val luminance = relativeLuminance(backgroundColor)
        val blackContrast = (luminance + 0.05) / 0.05
        val whiteContrast = 1.05 / (luminance + 0.05)
        return if (blackContrast >= whiteContrast) Color.BLACK else Color.WHITE
    }

    private fun relativeLuminance(color: Int): Double {
        fun channel(value: Int): Double {
            val normalized = value / 255.0
            return if (normalized <= 0.03928) {
                normalized / 12.92
            } else {
                ((normalized + 0.055) / 1.055).pow(2.4)
            }
        }

        val r = channel(Color.red(color))
        val g = channel(Color.green(color))
        val b = channel(Color.blue(color))
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private companion object {
        private val DEFAULT_CAPSULE_BG_COLOR = Color.rgb(180, 195, 161)
    }
}
