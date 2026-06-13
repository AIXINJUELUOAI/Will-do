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
import com.antgskds.calendarassistant.core.rule.RuleMatchingEngine
import com.antgskds.calendarassistant.core.weather.WeatherAlertIconMapper
import com.antgskds.calendarassistant.calendar.models.EventTags
import com.antgskds.calendarassistant.data.state.CapsuleUiState
import com.antgskds.calendarassistant.service.capsule.CapsuleActionSpec
import com.antgskds.calendarassistant.core.capsule.CapsuleStateManager
import com.antgskds.calendarassistant.service.capsule.CapsuleUiUtils
import com.antgskds.calendarassistant.service.receiver.EventActionReceiver
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
        val display = item.display
        val subtitleText = buildFlymeSubtitle(display.secondaryText, display.tertiaryText)
        val pendingIntent = createContentPendingIntent(context, item)

        val defaultIconRes = if (iconResId != 0) iconResId else R.drawable.ic_notification_small
        var iconDrawable: Drawable? = ContextCompat.getDrawable(context, defaultIconRes)
        if (iconDrawable == null) {
            iconDrawable = ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
        }

        val capsuleBgColor = normalizeCapsuleBgColor(item.color)
        val capsuleContentColor = resolveReadableContentColor(capsuleBgColor)
        val rawBitmap = iconDrawable?.let { CapsuleUiUtils.drawableToBitmap(it) }
        val capsuleIconBitmap = rawBitmap?.let { CapsuleUiUtils.tintBitmap(it, capsuleContentColor) }
        val capsuleIcon = capsuleIconBitmap?.let { Icon.createWithBitmap(it) }

        val remoteViews = if (item.type == CapsuleStateManager.TYPE_NETWORK_SPEED) {
            createNetworkSpeedRemoteViews(context, display.primaryText, subtitleText)
        } else {
            createRemoteViews(context, item.type, item.eventType, display.primaryText, subtitleText, iconResId)
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
            .setColor(capsuleBgColor)
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
                color = capsuleBgColor,
                contentColor = capsuleContentColor,
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

    private fun createRemoteViews(
        context: Context,
        capsuleType: Int,
        eventType: String,
        primaryText: String,
        secondaryText: String?,
        iconResId: Int
    ): RemoteViews {
        val resolvedIcon = if (iconResId != 0) iconResId else resolveFlymeIcon(eventType, capsuleType)
        return RemoteViews(context.packageName, R.layout.notification_live_flyme).apply {
            setTextViewText(R.id.tv_main_content, primaryText)
            setTextViewText(R.id.tv_sub_info, secondaryText ?: "")
            setImageViewResource(R.id.iv_icon, resolvedIcon)
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
            CapsuleStateManager.TYPE_OCR_PROGRESS -> R.drawable.ic_stat_scan
            CapsuleStateManager.TYPE_OCR_RESULT -> R.drawable.ic_stat_success
            CapsuleStateManager.TYPE_MODEL_LOADING -> R.drawable.ic_model_loading
            CapsuleStateManager.TYPE_WEATHER_ALERT -> WeatherAlertIconMapper.iconRes(eventType)
            else -> {
                // 使用 RuleMatchingEngine 解析，fallback 到 eventType
                val payload = RuleMatchingEngine.resolvePayload(null, eventType)
                val ruleId = payload?.ruleId ?: eventType
                // 优先从 RuleRegistry 获取
                val customIcon = com.antgskds.calendarassistant.core.rule.RuleRegistry.getCustomCapsuleIconResId(ruleId)
                if (customIcon != null) return customIcon
                val defaultIcon = com.antgskds.calendarassistant.core.rule.RuleRegistry.getIconResId(ruleId)
                if (defaultIcon != null) return defaultIcon
                when (ruleId) {
                    RuleMatchingEngine.RULE_PICKUP -> R.drawable.ic_stat_package
                    RuleMatchingEngine.RULE_FOOD -> R.drawable.ic_stat_food
                    RuleMatchingEngine.RULE_TRAIN -> R.drawable.ic_stat_train
                    RuleMatchingEngine.RULE_TAXI -> R.drawable.ic_stat_car
                    RuleMatchingEngine.RULE_FLIGHT -> R.drawable.ic_stat_flight
                    RuleMatchingEngine.RULE_TICKET -> R.drawable.ic_stat_ticket
                    RuleMatchingEngine.RULE_SENDER -> R.drawable.ic_stat_sender
                    RuleMatchingEngine.RULE_COURSE, EventTags.COURSE, "__removed_course__" -> R.drawable.ic_stat_course
                    RuleMatchingEngine.RULE_GENERAL, EventTags.GENERAL -> R.drawable.ic_stat_event
                    else -> R.drawable.ic_stat_event
                }
            }
        }
    }

    private fun collapseShortText(text: String): String {
        return if (text.length > 10) text.take(10) else text
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
