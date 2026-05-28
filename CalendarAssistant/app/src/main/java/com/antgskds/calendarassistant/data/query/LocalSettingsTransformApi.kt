package com.antgskds.calendarassistant.data.query

import com.antgskds.calendarassistant.core.query.SettingsTransformApi
import com.antgskds.calendarassistant.data.model.HomeEntryKey
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.data.model.sanitizeHomeBottomItems
import com.antgskds.calendarassistant.data.model.sanitizeHomeStartPageKey

class LocalSettingsTransformApi : SettingsTransformApi {
    override fun applyPreferenceUpdate(
        current: MySettings,
        showTomorrow: Boolean?,
        dailySummary: Boolean?,
        liveCapsule: Boolean?,
        pickupAggregation: Boolean?,
        hapticFeedbackEnabled: Boolean?,
        edgeBarEnabled: Boolean?,
        networkSpeedCapsule: Boolean?,
        floatingWindow: Boolean?,
        advanceReminderEnabled: Boolean?,
        advanceReminderMinutes: Int?,
        autoArchive: Boolean?,
        defaultEventDurationMinutes: Int?,
        useMultimodalAi: Boolean?,
        disableThinking: Boolean?,
        localSemanticEnabled: Boolean?,
        selectedLocalModelId: String?,
        floatingEventRange: Int?,
        floatingExpandSide: String?,
        volumeUpLongPressEnabled: Boolean?,
        volumeUpLongPressAction: Int?,
        smsMonitoring: Boolean?,
        forceInstantCodeTimeToNow: Boolean?,
        noteEnabled: Boolean?,
        predictiveBackEnabled: Boolean?,
        clipboardCodeRecognitionEnabled: Boolean?,
        widgetThemeMode: Int?,
        widgetBackgroundAlpha: Float?,
        developerOptionsUnlocked: Boolean?,
        developerOptionsEnabled: Boolean?,
        developerOptionsDisabledAtMillis: Long?,
        homeBottomItems: List<String>?,
        homeStartPageKey: String?
    ): MySettings {
        var updated = current
        val noteEnabledBefore = current.noteEnabled
        if (showTomorrow != null) updated = updated.copy(showTomorrowEvents = showTomorrow)
        if (dailySummary != null) updated = updated.copy(isDailySummaryEnabled = dailySummary)
        if (liveCapsule != null) updated = updated.copy(isLiveCapsuleEnabled = liveCapsule)
        if (pickupAggregation != null) updated = updated.copy(isPickupAggregationEnabled = pickupAggregation)
        if (hapticFeedbackEnabled != null) updated = updated.copy(hapticFeedbackEnabled = hapticFeedbackEnabled)
        if (edgeBarEnabled != null) updated = updated.copy(edgeBarEnabled = edgeBarEnabled)
        if (networkSpeedCapsule != null) updated = updated.copy(isNetworkSpeedCapsuleEnabled = networkSpeedCapsule)
        if (floatingWindow != null) updated = updated.copy(isFloatingWindowEnabled = floatingWindow)
        if (advanceReminderEnabled != null) updated = updated.copy(isAdvanceReminderEnabled = advanceReminderEnabled)
        if (advanceReminderMinutes != null) updated = updated.copy(advanceReminderMinutes = advanceReminderMinutes)
        if (autoArchive != null) updated = updated.copy(autoArchiveEnabled = autoArchive)
        if (defaultEventDurationMinutes != null) updated = updated.copy(defaultEventDurationMinutes = defaultEventDurationMinutes)
        if (useMultimodalAi != null) updated = updated.copy(useMultimodalAi = useMultimodalAi)
        if (disableThinking != null) updated = updated.copy(disableThinking = disableThinking)
        if (localSemanticEnabled != null) updated = updated.copy(isLocalSemanticEnabled = localSemanticEnabled)
        if (selectedLocalModelId != null) updated = updated.copy(selectedLocalModelId = selectedLocalModelId)
        if (floatingEventRange != null) updated = updated.copy(floatingEventRange = floatingEventRange)
        if (floatingExpandSide != null) updated = updated.copy(floatingExpandSide = sanitizeFloatingSide(floatingExpandSide))
        if (volumeUpLongPressEnabled != null) updated = updated.copy(volumeUpLongPressEnabled = volumeUpLongPressEnabled)
        if (volumeUpLongPressAction != null) updated = updated.copy(volumeUpLongPressAction = volumeUpLongPressAction)
        if (smsMonitoring != null) updated = updated.copy(isSmsMonitoringEnabled = smsMonitoring)
        if (forceInstantCodeTimeToNow != null) updated = updated.copy(forceInstantCodeTimeToNow = forceInstantCodeTimeToNow)
        if (noteEnabled != null) updated = updated.copy(noteEnabled = noteEnabled)
        if (predictiveBackEnabled != null) updated = updated.copy(predictiveBackEnabled = predictiveBackEnabled)
        if (clipboardCodeRecognitionEnabled != null) updated = updated.copy(clipboardCodeRecognitionEnabled = clipboardCodeRecognitionEnabled)
        if (widgetThemeMode != null) updated = updated.copy(widgetThemeMode = widgetThemeMode.coerceIn(0, 2))
        if (widgetBackgroundAlpha != null) updated = updated.copy(widgetBackgroundAlpha = widgetBackgroundAlpha.coerceIn(0.6f, 1f))
        if (developerOptionsUnlocked != null) updated = updated.copy(developerOptionsUnlocked = developerOptionsUnlocked)
        if (developerOptionsEnabled != null) updated = updated.copy(developerOptionsEnabled = developerOptionsEnabled)
        if (developerOptionsDisabledAtMillis != null) updated = updated.copy(developerOptionsDisabledAtMillis = developerOptionsDisabledAtMillis)
        if (homeBottomItems != null) updated = updated.copy(homeBottomItems = homeBottomItems)
        if (homeStartPageKey != null) updated = updated.copy(homeStartPageKey = homeStartPageKey)

        if (!noteEnabledBefore && updated.noteEnabled && HomeEntryKey.NOTE !in updated.homeBottomItems) {
            val allIndex = updated.homeBottomItems.indexOf(HomeEntryKey.ALL)
            val mergedItems = updated.homeBottomItems.toMutableList()
            if (allIndex >= 0) {
                mergedItems.add(allIndex, HomeEntryKey.NOTE)
            } else {
                mergedItems.add(HomeEntryKey.NOTE)
            }
            updated = updated.copy(homeBottomItems = mergedItems)
        }

        val sanitizedBottomItems = sanitizeHomeBottomItems(updated.homeBottomItems, updated.noteEnabled)
        val sanitizedStartPage = sanitizeHomeStartPageKey(updated.homeStartPageKey, sanitizedBottomItems)
        return updated.copy(homeBottomItems = sanitizedBottomItems, homeStartPageKey = sanitizedStartPage)
    }

    private fun sanitizeFloatingSide(side: String): String {
        return if (side == "LEFT") "LEFT" else "RIGHT"
    }
}
