package com.antgskds.calendarassistant.service.capsule

import android.content.Context
import com.antgskds.calendarassistant.core.content.EventCapsulePresenter
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.*
import com.antgskds.calendarassistant.data.model.LiveNotificationTemplateMode
import com.antgskds.calendarassistant.data.model.WeatherAlertData
import com.antgskds.calendarassistant.data.model.WeatherRiskAlert
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

    fun composeTextQuickMemo(title: String, memoId: Long): CapsuleDisplayModel {
        return SystemLiveDisplay.textQuickMemo(title).copy(
            action = CapsuleActionSpec(
                label = "移除",
                receiverAction = EventActionReceiver.ACTION_CLEAR_TEXT_QUICK_MEMO,
                extraLongKey = EventActionReceiver.EXTRA_QUICK_MEMO_ID,
                extraLongValue = memoId
            )
        )
    }

    fun composeQuickMemoRecording(title: String, content: String): CapsuleDisplayModel {
        return SystemLiveDisplay.quickMemoRecording(title, content)
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
