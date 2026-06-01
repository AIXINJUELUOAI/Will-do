package com.antgskds.calendarassistant.core.query

import com.antgskds.calendarassistant.data.model.MySettings

interface SettingsTransformApi {
    fun applyPreferenceUpdate(
        current: MySettings,
        showTomorrow: Boolean? = null,
        dailySummary: Boolean? = null,
        liveCapsule: Boolean? = null,
        pickupAggregation: Boolean? = null,
        hapticFeedbackEnabled: Boolean? = null,
        edgeBarEnabled: Boolean? = null,
        networkSpeedCapsule: Boolean? = null,
        floatingWindow: Boolean? = null,
        advanceReminderEnabled: Boolean? = null,
        advanceReminderMinutes: Int? = null,
        autoArchive: Boolean? = null,
        defaultEventDurationMinutes: Int? = null,
        useMultimodalAi: Boolean? = null,
        disableThinking: Boolean? = null,
        localSemanticEnabled: Boolean? = null,
        selectedLocalModelId: String? = null,
        floatingEventRange: Int? = null,
        floatingExpandSide: String? = null,
        volumeUpLongPressEnabled: Boolean? = null,
        volumeUpLongPressAction: Int? = null,
        smsMonitoring: Boolean? = null,
        forceInstantCodeTimeToNow: Boolean? = null,
        predictiveBackEnabled: Boolean? = null,
        clipboardCodeRecognitionEnabled: Boolean? = null,
        widgetThemeMode: Int? = null,
        widgetBackgroundAlpha: Float? = null,
        developerOptionsUnlocked: Boolean? = null,
        developerOptionsEnabled: Boolean? = null,
        developerOptionsDisabledAtMillis: Long? = null,
        homeBottomItems: List<String>? = null,
        homeStartPageKey: String? = null
    ): MySettings
}
