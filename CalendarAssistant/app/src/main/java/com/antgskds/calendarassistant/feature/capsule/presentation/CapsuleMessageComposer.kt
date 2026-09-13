package com.antgskds.calendarassistant.feature.capsule.presentation

import android.content.Context
import com.antgskds.calendarassistant.shared.content.EventCapsulePresenter
import com.antgskds.calendarassistant.feature.schedule.domain.model.Event
import com.antgskds.calendarassistant.feature.schedule.domain.model.*
import com.antgskds.calendarassistant.feature.settings.data.model.LiveNotificationTemplateMode
import com.antgskds.calendarassistant.feature.weather.domain.model.WeatherAlertData
import com.antgskds.calendarassistant.feature.weather.domain.model.WeatherRiskAlert
import com.antgskds.calendarassistant.feature.capsule.domain.CapsuleActionSpec
import com.antgskds.calendarassistant.feature.capsule.domain.CapsuleDisplayModel
import com.antgskds.calendarassistant.platform.capsule.network.NetworkSpeedMonitor
import com.antgskds.calendarassistant.platform.receiver.EventActionReceiver
import com.antgskds.calendarassistant.shared.management.resource.notification.display.live.template.RecognitionLiveDisplay
import com.antgskds.calendarassistant.shared.management.resource.notification.display.live.template.SystemLiveDisplay

object CapsuleMessageComposer {
    // --- 非事件类胶囊 ---

    fun composeNetworkSpeed(speed: NetworkSpeedMonitor.NetworkSpeed): CapsuleDisplayModel {
        return SystemLiveDisplay.networkSpeed(speed.formattedSpeed)
    }

    fun composeOcrProgress(title: String, content: String): CapsuleDisplayModel {
        return RecognitionLiveDisplay.progress(title, content)
    }

    fun composeOcrResult(title: String, content: String): CapsuleDisplayModel {
        return RecognitionLiveDisplay.statusResult(title, content)
    }

    fun composeModelLoading(title: String, content: String): CapsuleDisplayModel {
        return SystemLiveDisplay.modelLoading(title, content)
    }

    fun composeVoiceTranscription(title: String): CapsuleDisplayModel {
        return SystemLiveDisplay.voiceTranscription(title)
    }

    fun composeTextQuickMemo(
        title: String,
        memoId: Long,
        fixedTitleEnabled: Boolean = false,
        removeAction: CapsuleActionSpec = CapsuleActionSpec(
            label = "移除",
            receiverAction = EventActionReceiver.ACTION_CLEAR_TEXT_QUICK_MEMO,
            extraLongKey = EventActionReceiver.EXTRA_QUICK_MEMO_ID,
            extraLongValue = memoId,
        ),
    ): CapsuleDisplayModel {
        return SystemLiveDisplay.textQuickMemo(title, fixedTitleEnabled).copy(
            tapQuickMemoId = memoId.toString(),
            action = removeAction,
        )
    }

    fun composeQuickMemoRecording(title: String, content: String): CapsuleDisplayModel {
        return SystemLiveDisplay.quickMemoRecording(title, content).copy(
            action = CapsuleActionSpec(
                label = "结束录音",
                receiverAction = EventActionReceiver.ACTION_STOP_QUICK_MEMO_RECORDING
            )
        )
    }

    fun composeWeatherAlert(
        locationName: String,
        alert: WeatherAlertData,
        templateMode: String = LiveNotificationTemplateMode.AUTO
    ): CapsuleDisplayModel {
        return NotificationTemplateComposer.composeOfficialWeatherAlert(locationName, alert, templateMode)
    }

    fun composeWeatherRisk(
        locationName: String,
        risk: WeatherRiskAlert,
        templateMode: String = LiveNotificationTemplateMode.AUTO
    ): CapsuleDisplayModel {
        return NotificationTemplateComposer.composeWeatherRisk(locationName, risk, templateMode)
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
