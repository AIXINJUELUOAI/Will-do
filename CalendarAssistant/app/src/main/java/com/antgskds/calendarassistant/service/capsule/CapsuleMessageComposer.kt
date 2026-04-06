package com.antgskds.calendarassistant.service.capsule

import android.content.Context
import com.antgskds.calendarassistant.core.content.EventCapsulePresenter
import com.antgskds.calendarassistant.data.model.MyEvent

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

    // --- 事件类胶囊 (委托 EventPresenter) ---

    fun composeSchedule(context: Context, event: MyEvent, isExpired: Boolean): CapsuleDisplayModel {
        return EventCapsulePresenter.present(context, event, isExpired).displayModel
    }

    fun composePickup(context: Context, event: MyEvent, isExpired: Boolean): CapsuleDisplayModel {
        return EventCapsulePresenter.present(context, event, isExpired).displayModel
    }

    fun composeAggregatePickup(context: Context, pickupEvents: List<MyEvent>): CapsuleDisplayModel {
        return EventCapsulePresenter.present(context, pickupEvents).displayModel
    }
}
