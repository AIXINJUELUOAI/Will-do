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
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setColor(item.color)
            .setCategory(Notification.CATEGORY_EVENT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setGroup("LIVE_CAPSULE_GROUP")
            .setGroupSummary(false)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)

        builder.setContentText(display.secondaryText ?: " ")
        display.tertiaryText?.let { builder.setSubText(it) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }

        val expandedText = display.expandedText ?: display.secondaryText ?: " "
        builder.setStyle(
            Notification.BigTextStyle()
                .setBigContentTitle(display.primaryText)
                .bigText(expandedText)
        )

        applyShortCriticalText(builder, collapsedShortText)
        requestPromotedOngoing(builder)
        builder.addExtras(createPromotionExtras(collapsedShortText))

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

    private fun applyShortCriticalText(builder: Notification.Builder, text: String) {
        if (Build.VERSION.SDK_INT >= 36) {
            builder.setShortCriticalText(text)
            return
        }

        try {
            val methodSetText = Notification.Builder::class.java.getMethod(
                "setShortCriticalText",
                String::class.java
            )
            methodSetText.invoke(builder, text)
        } catch (e: Exception) {
            Log.d(TAG, "setShortCriticalText not available")
        }
    }

    private fun requestPromotedOngoing(builder: Notification.Builder) {
        try {
            val methodSetPromoted = Notification.Builder::class.java.getMethod(
                "setRequestPromotedOngoing",
                Boolean::class.java
            )
            methodSetPromoted.invoke(builder, true)
        } catch (e: Exception) {
            Log.d(TAG, "setRequestPromotedOngoing not available")
        }
    }

    private fun createPromotionExtras(title: String): Bundle {
        return Bundle().apply {
            putBoolean("android.substName", true)
            putString("android.title", title)
        }
    }

    private fun collapseShortText(text: String): String {
        return if (text.length > 10) "${text.take(10)}..." else text
    }
}
