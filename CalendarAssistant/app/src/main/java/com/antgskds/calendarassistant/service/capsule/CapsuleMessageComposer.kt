package com.antgskds.calendarassistant.service.capsule

import android.content.Context
import com.antgskds.calendarassistant.core.content.EventCapsulePresenter
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.*
import com.antgskds.calendarassistant.data.model.LiveNotificationTemplateMode
import com.antgskds.calendarassistant.data.model.WeatherAlertData
import com.antgskds.calendarassistant.data.model.WeatherRiskAlert

object CapsuleMessageComposer {
    private const val PRIMARY_TITLE_MAX_CHARS = 11
    private const val SHORT_TITLE_MAX_CHARS = 6

    // --- 非事件类胶囊 ---

    fun composeNetworkSpeed(speed: NetworkSpeedMonitor.NetworkSpeed): CapsuleDisplayModel {
        return CapsuleDisplayModel(
            shortText = speed.formattedSpeed,
            primaryText = speed.formattedSpeed,
            secondaryText = "下载速度",
            expandedText = "下载速度"
        )
    }

    fun composeOcrProgress(title: String, content: String): CapsuleDisplayModel {
        val short = "正在分析"
        val secondary = content.trim().takeIf { it.isNotEmpty() }
        val detail = compactOcrDetail(title, secondary, short)
        return CapsuleDisplayModel(
            shortText = short,
            primaryText = compactPrimaryTitle(short, detail),
            secondaryText = secondary,
            expandedText = secondary
        )
    }

    fun composeOcrResult(title: String, content: String): CapsuleDisplayModel {
        val short = compactShortTitle(title.trim().takeIf { it.isNotEmpty() } ?: "分析完成")
        val secondary = content.trim().takeIf { it.isNotEmpty() }
        val detail = compactOcrDetail(content, null, short)
        return CapsuleDisplayModel(
            shortText = short,
            primaryText = compactPrimaryTitle(short, detail),
            secondaryText = secondary,
            expandedText = secondary
        )
    }

    fun composeModelLoading(title: String, content: String): CapsuleDisplayModel {
        val primary = title.trim().takeIf { it.isNotEmpty() } ?: "本地模型加载中"
        val secondary = content.trim().takeIf { it.isNotEmpty() }
        val short = "模型加载中"
        val detail = compactModelDetail(primary, secondary)
        return CapsuleDisplayModel(
            shortText = short,
            primaryText = compactPrimaryTitle(short, detail),
            secondaryText = secondary,
            expandedText = secondary
        )
    }

    fun composeWeatherAlert(
        locationName: String,
        alert: WeatherAlertData,
        templateMode: String = LiveNotificationTemplateMode.AUTO
    ): CapsuleDisplayModel {
        return NotificationTemplateCenter.composeOfficialWeatherAlert(locationName, alert, templateMode)
    }

    fun composeWeatherRisk(
        locationName: String,
        risk: WeatherRiskAlert,
        templateMode: String = LiveNotificationTemplateMode.AUTO
    ): CapsuleDisplayModel {
        return NotificationTemplateCenter.composeWeatherRisk(locationName, risk, templateMode)
    }

    // --- 事件类胶囊 (委托 EventPresenter) ---

    fun composeSchedule(
        context: Context,
        event: Event,
        isExpired: Boolean,
        templateMode: String = LiveNotificationTemplateMode.AUTO
    ): CapsuleDisplayModel {
        return EventCapsulePresenter.present(context, event, isExpired, templateMode).displayModel
    }

    fun composePickup(context: Context, event: Event, isExpired: Boolean): CapsuleDisplayModel {
        return EventCapsulePresenter.present(context, event, isExpired).displayModel
    }

    fun composeAggregatePickup(context: Context, pickupEvents: List<Event>): CapsuleDisplayModel {
        return EventCapsulePresenter.present(context, pickupEvents).displayModel
    }

    private fun compactPrimaryTitle(shortTitle: String, detailTitle: String?): String {
        val short = compactShortTitle(shortTitle)
        val detail = compactTitleDetail(detailTitle, PRIMARY_TITLE_MAX_CHARS - short.length - 1)
        if (detail == null || detail == short) return short.take(PRIMARY_TITLE_MAX_CHARS)
        val title = "$short|$detail"
        return if (title.length <= PRIMARY_TITLE_MAX_CHARS) title else short.take(PRIMARY_TITLE_MAX_CHARS)
    }

    private fun compactShortTitle(value: String): String {
        val clean = cleanTitle(value) ?: "提醒"
        return if (clean.length <= SHORT_TITLE_MAX_CHARS) clean else clean.take(SHORT_TITLE_MAX_CHARS)
    }

    private fun compactTitleDetail(value: String?, maxChars: Int): String? {
        if (maxChars <= 0) return null
        val clean = cleanTitle(value) ?: return null
        val candidates = listOf(
            clean,
            clean.replace("未识别到有效日程", "无有效日程"),
            clean.replace("没有可入库的新事件", "无新事件"),
            clean.replace("正在分析屏幕内容", "屏幕内容"),
            clean.removePrefix("正在分析"),
            clean.removeSuffix("加载中"),
            clean.removeSuffix("准备中")
        ).mapNotNull(::cleanTitle).distinct()
        return candidates.firstOrNull { it.length <= maxChars } ?: clean.take(maxChars)
    }

    private fun compactOcrDetail(title: String, content: String?, shortTitle: String): String? {
        val titleDetail = cleanTitle(title)
            ?.removePrefix(shortTitle)
            ?.let(::cleanTitle)
        return titleDetail ?: cleanTitle(content)
    }

    private fun compactModelDetail(title: String, content: String?): String? {
        val titleDetail = cleanTitle(title)
            ?.removeSuffix("加载中")
            ?.removeSuffix("准备中")
            ?.let(::cleanTitle)
        return titleDetail ?: cleanTitle(content)
    }

    private fun cleanTitle(value: String?): String? {
        return value
            ?.trim()
            ?.trim('。', '.', '…')
            ?.replace("...", "")
            ?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
    }
}
