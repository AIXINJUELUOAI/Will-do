package com.antgskds.calendarassistant.shared.management.resource.notification.display.live.vendor.flyme

import android.content.Context
import android.widget.RemoteViews
import com.antgskds.calendarassistant.R
import com.antgskds.calendarassistant.calendar.models.EventTags
import com.antgskds.calendarassistant.data.state.CapsuleType
import com.antgskds.calendarassistant.core.rule.RuleMatchingEngine
import com.antgskds.calendarassistant.feature.weather.domain.WeatherAlertIconMapper
import com.antgskds.calendarassistant.data.state.CapsuleUiState
import com.antgskds.calendarassistant.service.capsule.CapsuleActionSpec

data class FlymeLiveNotificationContent(
    val title: String,
    val subtitleText: String?,
    val expandedText: String?,
    val collapsedShortText: String,
    val smallIconResId: Int,
    val remoteViews: RemoteViews,
    val tapOpensPickupList: Boolean,
    val actions: List<CapsuleActionSpec>
)

object FlymeLiveNotificationTemplate {
    fun create(
        context: Context,
        item: CapsuleUiState.Active.CapsuleItem,
        iconResId: Int
    ): FlymeLiveNotificationContent {
        val display = item.display
        val subtitleText = buildFlymeSubtitle(display.secondaryText, display.tertiaryText)
        val remoteViews = if (item.type == CapsuleType.NETWORK_SPEED) {
            createNetworkSpeedRemoteViews(context, display.primaryText, subtitleText)
        } else {
            createRemoteViews(
                context = context,
                capsuleType = item.type,
                eventType = item.eventType,
                primaryText = display.primaryText,
                secondaryText = subtitleText,
                iconResId = iconResId
            )
        }

        return FlymeLiveNotificationContent(
            title = display.primaryText,
            subtitleText = subtitleText,
            expandedText = display.expandedText,
            collapsedShortText = collapseShortText(display.shortText),
            smallIconResId = if (iconResId != 0) iconResId else R.drawable.ic_notification_small,
            remoteViews = remoteViews,
            tapOpensPickupList = display.tapOpensPickupList,
            actions = display.effectiveActions
        )
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
            CapsuleType.OCR_PROGRESS -> R.drawable.ic_stat_scan
            CapsuleType.OCR_RESULT -> R.drawable.ic_stat_success
            CapsuleType.MODEL_LOADING -> R.drawable.ic_model_loading
            CapsuleType.VOICE_TRANSCRIPTION -> R.drawable.ic_stat_quickmemo
            CapsuleType.TEXT_QUICK_MEMO -> R.drawable.ic_stat_quickmemo
            CapsuleType.QUICK_MEMO_RECORDING -> R.drawable.ic_stat_recording
            CapsuleType.WEATHER_ALERT -> WeatherAlertIconMapper.iconRes(eventType)
            else -> {
                val payload = RuleMatchingEngine.resolvePayload(null, eventType)
                val ruleId = payload?.ruleId ?: eventType
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
                    RuleMatchingEngine.RULE_COURSE,
                    EventTags.COURSE,
                    "__removed_course__" -> R.drawable.ic_stat_course
                    RuleMatchingEngine.RULE_GENERAL,
                    EventTags.GENERAL -> R.drawable.ic_stat_event
                    else -> R.drawable.ic_stat_event
                }
            }
        }
    }

    private fun collapseShortText(text: String): String {
        return if (text.length > 10) text.take(10) else text
    }
}
