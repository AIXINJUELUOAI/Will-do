package com.antgskds.calendarassistant.data.query

import com.antgskds.calendarassistant.core.query.SettingsTransformApi
import com.antgskds.calendarassistant.data.model.FloatingBallGestureAction
import com.antgskds.calendarassistant.data.model.FloatingEntryStyle
import com.antgskds.calendarassistant.data.model.LiveNotificationTemplateMode
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.data.model.QuickMemoRecordingDisplayMode
import com.antgskds.calendarassistant.data.model.sanitizeHomeBottomItems
import com.antgskds.calendarassistant.data.model.sanitizeHomeStartPageKey

class LocalSettingsTransformApi : SettingsTransformApi {
    override fun applyPreferenceUpdate(
        current: MySettings,
        showTomorrow: Boolean?,
        dailySummary: Boolean?,
        dailySummaryMorningMinuteOfDay: Int?,
        dailySummaryEveningMinuteOfDay: Int?,
        liveCapsule: Boolean?,
        pickupAggregation: Boolean?,
        hapticFeedbackEnabled: Boolean?,
        edgeBarEnabled: Boolean?,
        networkSpeedCapsule: Boolean?,
        floatingWindow: Boolean?,
        advanceReminderEnabled: Boolean?,
        advanceReminderMinutes: Int?,
        autoArchive: Boolean?,
        recognitionMode: Int?,
        defaultEventDurationMinutes: Int?,
        useMultimodalAi: Boolean?,
        disableThinking: Boolean?,
        localSemanticEnabled: Boolean?,
        selectedLocalModelId: String?,
        floatingEventRange: Int?,
        floatingExpandSide: String?,
        quickMemoRecordingDisplayMode: Int?,
        floatingEntryStyle: Int?,
        floatingBallEnabled: Boolean?,
        floatingBallXPercent: Float?,
        floatingBallYPercent: Float?,
        floatingBallSizeDp: Int?,
        floatingBallAlpha: Float?,
        floatingBallSingleTapAction: Int?,
        floatingBallDoubleTapAction: Int?,
        floatingBallLongPressAction: Int?,
        volumeUpLongPressEnabled: Boolean?,
        volumeUpLongPressAction: Int?,
        smsMonitoring: Boolean?,
        forceInstantCodeTimeToNow: Boolean?,
        predictiveBackEnabled: Boolean?,
        clipboardCodeRecognitionEnabled: Boolean?,
        voiceInputEnabled: Boolean?,
        floatingVoiceLongPressEnabled: Boolean?,
        floatingTextQuickMemoAutoPinEnabled: Boolean?,
        voiceQuickMemoAutoPinEnabled: Boolean?,
        appBackgroundCardAlphaPercent: Int?,
        widgetThemeMode: Int?,
        widgetBackgroundAlpha: Float?,
        developerOptionsUnlocked: Boolean?,
        developerOptionsEnabled: Boolean?,
        developerOptionsDisabledAtMillis: Long?,
        homeBottomItems: List<String>?,
        homeStartPageKey: String?,
        weatherLocationStabilityRequiredHits: Int?,
        liveNotificationTemplateMode: String?,
        courseFeatureEnabled: Boolean?
    ): MySettings {
        var updated = current
        if (showTomorrow != null) updated = updated.copy(showTomorrowEvents = showTomorrow)
        if (dailySummary != null) updated = updated.copy(isDailySummaryEnabled = dailySummary)
        if (dailySummaryMorningMinuteOfDay != null) {
            updated = updated.copy(
                dailySummaryMorningMinuteOfDay = MySettings.normalizeDailySummaryMinuteOfDay(dailySummaryMorningMinuteOfDay)
            )
        }
        if (dailySummaryEveningMinuteOfDay != null) {
            updated = updated.copy(
                dailySummaryEveningMinuteOfDay = MySettings.normalizeDailySummaryMinuteOfDay(dailySummaryEveningMinuteOfDay)
            )
        }
        if (liveCapsule != null) updated = updated.copy(isLiveCapsuleEnabled = liveCapsule)
        if (pickupAggregation != null) updated = updated.copy(isPickupAggregationEnabled = pickupAggregation)
        if (hapticFeedbackEnabled != null) updated = updated.copy(hapticFeedbackEnabled = hapticFeedbackEnabled)
        if (edgeBarEnabled != null) updated = updated.copy(edgeBarEnabled = edgeBarEnabled)
        if (networkSpeedCapsule != null) updated = updated.copy(isNetworkSpeedCapsuleEnabled = networkSpeedCapsule)
        if (floatingWindow != null) updated = updated.copy(isFloatingWindowEnabled = floatingWindow)
        if (advanceReminderEnabled != null) updated = updated.copy(isAdvanceReminderEnabled = advanceReminderEnabled)
        if (advanceReminderMinutes != null) updated = updated.copy(advanceReminderMinutes = advanceReminderMinutes)
        if (autoArchive != null) updated = updated.copy(autoArchiveEnabled = autoArchive)
        if (recognitionMode != null) updated = updated.copy(recognitionMode = MySettings.normalizeRecognitionMode(recognitionMode))
        if (defaultEventDurationMinutes != null) updated = updated.copy(defaultEventDurationMinutes = defaultEventDurationMinutes)
        if (useMultimodalAi != null) updated = updated.copy(useMultimodalAi = useMultimodalAi)
        if (disableThinking != null) updated = updated.copy(disableThinking = disableThinking)
        if (localSemanticEnabled != null) updated = updated.copy(isLocalSemanticEnabled = localSemanticEnabled)
        if (selectedLocalModelId != null) updated = updated.copy(selectedLocalModelId = selectedLocalModelId)
        if (floatingEventRange != null) updated = updated.copy(floatingEventRange = floatingEventRange)
        if (floatingExpandSide != null) updated = updated.copy(floatingExpandSide = sanitizeFloatingSide(floatingExpandSide))
        if (quickMemoRecordingDisplayMode != null) {
            updated = updated.copy(
                quickMemoRecordingDisplayMode = QuickMemoRecordingDisplayMode.normalize(quickMemoRecordingDisplayMode)
            )
        }
        if (floatingEntryStyle != null) {
            updated = updated.copy(floatingEntryStyle = FloatingEntryStyle.normalize(floatingEntryStyle))
        }
        if (floatingBallEnabled != null) updated = updated.copy(floatingBallEnabled = floatingBallEnabled)
        if (floatingBallXPercent != null) updated = updated.copy(floatingBallXPercent = floatingBallXPercent.coerceIn(0f, 100f))
        if (floatingBallYPercent != null) updated = updated.copy(floatingBallYPercent = floatingBallYPercent.coerceIn(0f, 100f))
        if (floatingBallSizeDp != null) updated = updated.copy(floatingBallSizeDp = floatingBallSizeDp.coerceIn(40, 80))
        if (floatingBallAlpha != null) updated = updated.copy(floatingBallAlpha = floatingBallAlpha.coerceIn(0.2f, 1f))
        if (floatingBallSingleTapAction != null) {
            updated = updated.copy(floatingBallSingleTapAction = FloatingBallGestureAction.normalize(floatingBallSingleTapAction))
        }
        if (floatingBallDoubleTapAction != null) {
            updated = updated.copy(floatingBallDoubleTapAction = FloatingBallGestureAction.normalize(floatingBallDoubleTapAction))
        }
        if (floatingBallLongPressAction != null) {
            updated = updated.copy(floatingBallLongPressAction = FloatingBallGestureAction.normalize(floatingBallLongPressAction))
        }
        if (volumeUpLongPressEnabled != null) updated = updated.copy(volumeUpLongPressEnabled = volumeUpLongPressEnabled)
        if (volumeUpLongPressAction != null) updated = updated.copy(volumeUpLongPressAction = volumeUpLongPressAction.coerceIn(1, 3))
        if (smsMonitoring != null) updated = updated.copy(isSmsMonitoringEnabled = smsMonitoring)
        if (forceInstantCodeTimeToNow != null) updated = updated.copy(forceInstantCodeTimeToNow = forceInstantCodeTimeToNow)
        if (predictiveBackEnabled != null) updated = updated.copy(predictiveBackEnabled = predictiveBackEnabled)
        if (clipboardCodeRecognitionEnabled != null) updated = updated.copy(clipboardCodeRecognitionEnabled = clipboardCodeRecognitionEnabled)
        if (voiceInputEnabled != null) updated = updated.copy(voiceInputEnabled = voiceInputEnabled)
        if (floatingVoiceLongPressEnabled != null) updated = updated.copy(floatingVoiceLongPressEnabled = floatingVoiceLongPressEnabled)
        if (floatingTextQuickMemoAutoPinEnabled != null) {
            updated = updated.copy(floatingTextQuickMemoAutoPinEnabled = floatingTextQuickMemoAutoPinEnabled)
        }
        if (voiceQuickMemoAutoPinEnabled != null) {
            updated = updated.copy(voiceQuickMemoAutoPinEnabled = voiceQuickMemoAutoPinEnabled)
        }
        if (appBackgroundCardAlphaPercent != null) {
            updated = updated.copy(
                appBackgroundCardAlphaPercent = MySettings.normalizeAppBackgroundCardAlphaPercent(appBackgroundCardAlphaPercent)
            )
        }
        if (widgetThemeMode != null) updated = updated.copy(widgetThemeMode = widgetThemeMode.coerceIn(0, 2))
        if (widgetBackgroundAlpha != null) updated = updated.copy(widgetBackgroundAlpha = widgetBackgroundAlpha.coerceIn(0.6f, 1f))
        if (developerOptionsUnlocked != null) updated = updated.copy(developerOptionsUnlocked = developerOptionsUnlocked)
        if (developerOptionsEnabled != null) updated = updated.copy(developerOptionsEnabled = developerOptionsEnabled)
        if (developerOptionsDisabledAtMillis != null) updated = updated.copy(developerOptionsDisabledAtMillis = developerOptionsDisabledAtMillis)
        if (homeBottomItems != null) updated = updated.copy(homeBottomItems = homeBottomItems)
        if (homeStartPageKey != null) updated = updated.copy(homeStartPageKey = homeStartPageKey)
        if (weatherLocationStabilityRequiredHits != null) {
            updated = updated.copy(weatherLocationStabilityRequiredHits = weatherLocationStabilityRequiredHits.coerceIn(1, 3))
        }
        if (liveNotificationTemplateMode != null) {
            updated = updated.copy(liveNotificationTemplateMode = LiveNotificationTemplateMode.normalize(liveNotificationTemplateMode))
        }
        if (courseFeatureEnabled != null) updated = updated.copy(courseFeatureEnabled = courseFeatureEnabled)

        val sanitizedBottomItems = sanitizeHomeBottomItems(updated.homeBottomItems)
        val sanitizedStartPage = sanitizeHomeStartPageKey(updated.homeStartPageKey, sanitizedBottomItems)
        return updated.copy(
            homeBottomItems = sanitizedBottomItems,
            homeStartPageKey = sanitizedStartPage,
            volumeUpLongPressAction = updated.volumeUpLongPressAction.coerceIn(1, 3)
        )
    }

    private fun sanitizeFloatingSide(side: String): String {
        return if (side == "LEFT") "LEFT" else "RIGHT"
    }
}
