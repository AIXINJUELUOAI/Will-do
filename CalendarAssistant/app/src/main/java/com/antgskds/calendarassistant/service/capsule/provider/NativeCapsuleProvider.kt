package com.antgskds.calendarassistant.service.capsule.provider

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.MainActivity
import com.antgskds.calendarassistant.R
import com.antgskds.calendarassistant.data.state.CapsuleUiState
import com.antgskds.calendarassistant.service.capsule.CapsuleActionSpec
import com.antgskds.calendarassistant.service.receiver.EventActionReceiver

class NativeCapsuleProvider : ICapsuleProvider {
    companion object {
        private const val TAG = "NativeCapsuleProvider"
    }

    override fun buildNotification(
        context: Context,
        item: CapsuleUiState.Active.CapsuleItem,
        iconResId: Int
    ): Notification {
        val display = item.display
        val collapsedShortText = collapseShortText(display.shortText)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, App.CHANNEL_ID_LIVE)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }

        val iconRes = if (iconResId != 0) iconResId else R.drawable.ic_notification_small
        val icon = Icon.createWithResource(context, iconRes)

        builder.setSmallIcon(icon)
            .setContentTitle(display.primaryText)
            .setContentIntent(createContentPendingIntent(context, item))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setColor(item.color)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)

        builder.setContentText(display.secondaryText ?: " ")
        display.tertiaryText?.let { builder.setSubText(it) }

        val expandedText = display.expandedText ?: display.secondaryText ?: " "
        builder.setStyle(
            Notification.BigTextStyle()
                .setBigContentTitle(display.primaryText)
                .bigText(expandedText)
        )

        // Android 15+: 请求提升为实况通知（Live Activity）
        if (Build.VERSION.SDK_INT >= 35) {
            val extras = Bundle()
            extras.putBoolean("android.requestPromotedOngoing", true)
            builder.addExtras(extras)
        }
        // Android 16+: 在状态栏胶囊中显示简短文本
        if (Build.VERSION.SDK_INT >= 36) {
            builder.setShortCriticalText(collapsedShortText)
        }

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

    private fun collapseShortText(text: String): String {
        return if (text.length > 10) "${text.take(10)}..." else text
    }
}
