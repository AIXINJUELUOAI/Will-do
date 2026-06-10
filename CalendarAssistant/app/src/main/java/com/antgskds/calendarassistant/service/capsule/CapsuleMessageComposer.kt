package com.antgskds.calendarassistant.service.capsule

import android.content.Context
import com.antgskds.calendarassistant.core.content.EventCapsulePresenter
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.*
import com.antgskds.calendarassistant.data.model.LiveNotificationTemplateMode
import com.antgskds.calendarassistant.data.model.WeatherAlertData
import com.antgskds.calendarassistant.data.model.WeatherRiskAlert

object CapsuleMessageComposer {

    // --- 非事件类胶囊 (保持不变) ---

    fun composeNetworkSpeed(speed: NetworkSpeedMonitor.NetworkSpeed): CapsuleDisplayModel {
        return CapsuleDisplayModel(
            shortText = speed.formattedSpeed,
            primaryText = speed.formattedSpeed,
            secondaryText = "下载速度",
            expandedText = "下载速度"
        )
    }

    fun composeOcrProgress(title: String, content: String): CapsuleDisplayModel {
        val primary = title.trim().takeIf { it.isNotEmpty() } ?: "正在分析"
        val secondary = content.trim().takeIf { it.isNotEmpty() }
        return CapsuleDisplayModel(
            shortText = primary,
            primaryText = primary,
            secondaryText = secondary,
            expandedText = secondary
        )
    }

    fun composeOcrResult(title: String, content: String): CapsuleDisplayModel {
        val primary = title.trim().takeIf { it.isNotEmpty() } ?: "分析完成"
        val secondary = content.trim().takeIf { it.isNotEmpty() }
        return CapsuleDisplayModel(
            shortText = primary,
            primaryText = primary,
            secondaryText = secondary,
            expandedText = secondary
        )
    }

    fun composeModelLoading(title: String, content: String): CapsuleDisplayModel {
        val primary = title.trim().takeIf { it.isNotEmpty() } ?: "本地模型加载中"
        val secondary = content.trim().takeIf { it.isNotEmpty() }
        return CapsuleDisplayModel(
            shortText = "模型加载中",
            primaryText = primary,
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
}
