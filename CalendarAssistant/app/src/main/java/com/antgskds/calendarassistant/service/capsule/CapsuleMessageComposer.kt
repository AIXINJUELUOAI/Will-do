package com.antgskds.calendarassistant.service.capsule

import android.content.Context
import com.antgskds.calendarassistant.core.content.EventCapsulePresenter
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.*

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

    fun composeWeatherAlert(title: String, locationName: String, content: String): CapsuleDisplayModel {
        val secondary = listOf(locationName, content.lineSequence().firstOrNull().orEmpty())
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        return CapsuleDisplayModel(
            shortText = title,
            primaryText = title,
            secondaryText = secondary.ifBlank { null },
            expandedText = content.ifBlank { secondary }
        )
    }

    fun composeWeatherRisk(title: String, locationName: String, content: String): CapsuleDisplayModel {
        val cleanTitle = title.removePrefix("天气风险提醒：").ifBlank { "天气风险提醒" }
        val secondary = listOf(locationName, content.lineSequence().firstOrNull().orEmpty())
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        return CapsuleDisplayModel(
            shortText = cleanTitle,
            primaryText = cleanTitle,
            secondaryText = secondary.ifBlank { null },
            expandedText = content.ifBlank { secondary }
        )
    }

    // --- 事件类胶囊 (委托 EventPresenter) ---

    fun composeSchedule(context: Context, event: Event, isExpired: Boolean): CapsuleDisplayModel {
        return EventCapsulePresenter.present(context, event, isExpired).displayModel
    }

    fun composePickup(context: Context, event: Event, isExpired: Boolean): CapsuleDisplayModel {
        return EventCapsulePresenter.present(context, event, isExpired).displayModel
    }

    fun composeAggregatePickup(context: Context, pickupEvents: List<Event>): CapsuleDisplayModel {
        return EventCapsulePresenter.present(context, pickupEvents).displayModel
    }
}
