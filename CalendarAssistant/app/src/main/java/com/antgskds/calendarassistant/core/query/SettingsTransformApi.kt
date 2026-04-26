package com.antgskds.calendarassistant.core.query

import com.antgskds.calendarassistant.data.model.MySettings

interface SettingsTransformApi {
    fun applyPreferenceUpdate(
        current: MySettings,
        showTomorrow: Boolean? = null,
        dailySummary: Boolean? = null,
        liveCapsule: Boolean? = null,
        pickupAggregation: Boolean? = null,
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
        floatingEventRange: Int? = null,
        volumeUpLongPressEnabled: Boolean? = null,
        volumeUpLongPressAction: Int? = null,
        smsMonitoring: Boolean? = null,
        noteEnabled: Boolean? = null,
        homeBottomItems: List<String>? = null,
        homeStartPageKey: String? = null
    ): MySettings
}
