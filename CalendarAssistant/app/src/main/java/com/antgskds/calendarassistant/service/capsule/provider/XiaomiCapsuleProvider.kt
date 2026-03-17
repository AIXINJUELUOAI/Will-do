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
import com.antgskds.calendarassistant.data.model.xiaomi.XiaomiActionInfo
import com.antgskds.calendarassistant.data.model.xiaomi.XiaomiBaseInfo
import com.antgskds.calendarassistant.data.model.xiaomi.XiaomiBigIslandArea
import com.antgskds.calendarassistant.data.model.xiaomi.XiaomiFocusExtra
import com.antgskds.calendarassistant.data.model.xiaomi.XiaomiFocusParamV2
import com.antgskds.calendarassistant.data.model.xiaomi.XiaomiHintInfo
import com.antgskds.calendarassistant.data.model.xiaomi.XiaomiImageTextInfoLeft
import com.antgskds.calendarassistant.data.model.xiaomi.XiaomiIslandPicInfo
import com.antgskds.calendarassistant.data.model.xiaomi.XiaomiIslandTextInfo
import com.antgskds.calendarassistant.data.model.xiaomi.XiaomiParamIsland
import com.antgskds.calendarassistant.data.model.xiaomi.XiaomiPicInfo
import com.antgskds.calendarassistant.data.model.xiaomi.XiaomiSmallIslandArea
import com.antgskds.calendarassistant.data.state.CapsuleUiState
import com.antgskds.calendarassistant.service.capsule.CapsuleActionSpec
import com.antgskds.calendarassistant.service.receiver.EventActionReceiver
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class XiaomiCapsuleProvider : ICapsuleProvider {
    companion object {
        private const val KEY_BASE_INFO = "miui.focus.baseInfo"
        private const val KEY_PIC_INFO = "miui.focus.picInfo"
        private const val KEY_HINT_INFO = "miui.focus.hintInfo"
        private const val KEY_PICS = "miui.focus.pics"
        private const val KEY_FOCUS_EXTRA = "miui.focus.extra"
        private const val KEY_FOCUS_EXTRA_V2 = "miui.focus.focusExtra"
        private const val KEY_FOCUS_PARAM = "miui.focus.param"

        private const val PIC_FUNCTION_KEY = "miui.focus.pic_ca_function"
        private const val PIC_ACTION_KEY = "miui.focus.pic_ca_action"
        private const val PIC_APP_KEY = "miui.focus.pic_ca_app"

        private const val BUSINESS_SCHEDULE_REMINDER = "schedule_reminder"
    }

    private val json = Json {
        encodeDefaults = false
        explicitNulls = false
    }

    override fun buildNotification(
        context: Context,
        item: CapsuleUiState.Active.CapsuleItem,
        iconResId: Int
    ): Notification {
        val display = item.display
        val primaryText = display.primaryText.ifBlank { item.title }
        val secondaryText = display.secondaryText?.trim()?.takeIf { it.isNotEmpty() }
        val tertiaryText = display.tertiaryText?.trim()?.takeIf { it.isNotEmpty() }
        val remarkText = secondaryText ?: tertiaryText ?: " "
        val subContent = if (secondaryText != null && tertiaryText != null) tertiaryText else null

        val baseInfo = XiaomiBaseInfo(
            type = 2,
            title = primaryText,
            content = remarkText,
            subContent = subContent,
            picFunction = PIC_FUNCTION_KEY
        )
        val picInfo = XiaomiPicInfo(
            type = 1,
            pic = PIC_APP_KEY,
            picDark = PIC_APP_KEY
        )

        val actionInfo = display.action?.let { buildActionInfo(context, item.id, it) }
        val hintInfo = actionInfo?.let {
            XiaomiHintInfo(
                type = 1,
                title = primaryText,
                content = remarkText,
                picContent = PIC_FUNCTION_KEY,
                actionInfo = it
            )
        }

        val islandRightTitle = remarkText.trim().ifEmpty { primaryText }
        val islandPicInfo = XiaomiIslandPicInfo(pic = PIC_APP_KEY)
        val paramIsland = XiaomiParamIsland(
            highlightColor = toHexColor(item.color),
            bigIslandArea = XiaomiBigIslandArea(
                imageTextInfoLeft = XiaomiImageTextInfoLeft(
                    picInfo = islandPicInfo,
                    textInfo = XiaomiIslandTextInfo(
                        title = primaryText,
                        showHighlightColor = true
                    )
                ),
                textInfo = XiaomiIslandTextInfo(
                    title = islandRightTitle,
                    showHighlightColor = false
                ),
                islandTimeout = 900
            ),
            smallIslandArea = XiaomiSmallIslandArea(picInfo = islandPicInfo)
        )

        val focusParamV2 = XiaomiFocusParamV2(
            protocol = 1,
            aodTitle = primaryText,
            aodPic = PIC_APP_KEY,
            business = BUSINESS_SCHEDULE_REMINDER,
            ticker = buildTickerText(primaryText, remarkText),
            enableFloat = true,
            paramIsland = paramIsland,
            baseInfo = baseInfo,
            picInfo = picInfo,
            hintInfo = hintInfo
        )
        val focusExtraJson = json.encodeToString(
            XiaomiFocusExtra(
                paramV2 = focusParamV2
            )
        )

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
            .setContentTitle(primaryText)
            .setContentText(remarkText)
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

        subContent?.let { builder.setSubText(it) }

        val expandedText = display.expandedText ?: display.secondaryText ?: " "
        builder.setStyle(
            Notification.BigTextStyle()
                .setBigContentTitle(primaryText)
                .bigText(expandedText)
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }

        applyShortCriticalText(builder, collapsedShortText)
        requestPromotedOngoing(builder)
        builder.addExtras(createPromotionExtras(collapsedShortText))

        display.action?.let { addAction(builder, context, item.id, it) }

        val extras = Bundle().apply {
            putString(KEY_BASE_INFO, json.encodeToString(baseInfo))
            putString(KEY_PIC_INFO, json.encodeToString(picInfo))
            if (hintInfo != null) {
                putString(KEY_HINT_INFO, json.encodeToString(hintInfo))
            }
            putBundle(KEY_PICS, buildPicsBundle(context, iconRes))
            putString(KEY_FOCUS_EXTRA, focusExtraJson)
            putString(KEY_FOCUS_EXTRA_V2, focusExtraJson)
            putString(KEY_FOCUS_PARAM, focusExtraJson)
        }

        builder.addExtras(extras)

        return builder.build()
    }

    private fun buildActionInfo(
        context: Context,
        eventId: String,
        action: CapsuleActionSpec
    ): XiaomiActionInfo {
        val intent = Intent(context, EventActionReceiver::class.java).apply {
            this.action = action.receiverAction
            putExtra(EventActionReceiver.EXTRA_EVENT_ID, eventId)
        }
        val intentUri = intent.toUri(Intent.URI_INTENT_SCHEME)
        return XiaomiActionInfo(
            clickWithCollapse = true,
            actionIcon = PIC_ACTION_KEY,
            actionTitle = action.label,
            actionIntentType = 2,
            actionIntent = intentUri
        )
    }

    private fun buildPicsBundle(context: Context, iconRes: Int): Bundle {
        val functionIcon = Icon.createWithResource(context, iconRes)
        val appIcon = Icon.createWithResource(context, R.mipmap.ic_launcher)
        return Bundle().apply {
            putParcelable(PIC_FUNCTION_KEY, functionIcon)
            putParcelable(PIC_ACTION_KEY, functionIcon)
            putParcelable(PIC_APP_KEY, appIcon)
        }
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

    private fun createPromotionExtras(title: String): Bundle {
        return Bundle().apply {
            putBoolean("android.substName", true)
            putString("android.title", title)
        }
    }

    private fun collapseShortText(text: String): String {
        return if (text.length > 10) "${text.take(10)}..." else text
    }

    private fun buildTickerText(primaryText: String, remarkText: String): String {
        val parts = listOf(remarkText.trim(), primaryText.trim()).filter { it.isNotEmpty() }
        return if (parts.isEmpty()) primaryText else parts.joinToString(" ")
    }

    private fun toHexColor(color: Int): String {
        return String.format("#%06X", color and 0x00FFFFFF)
    }
}
