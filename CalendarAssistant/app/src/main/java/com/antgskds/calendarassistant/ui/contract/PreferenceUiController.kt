package com.antgskds.calendarassistant.ui.contract

import com.antgskds.calendarassistant.ui.viewmodel.SettingsViewModel

class PreferenceUiController(private val viewModel: SettingsViewModel) {
    val settings = viewModel.settings
    val syncStatus = viewModel.syncStatus
    val availableSyncCalendars = viewModel.availableSyncCalendars

    fun refreshSyncStatus() = viewModel.refreshSyncStatus()
    fun refreshSyncCalendars() = viewModel.refreshSyncCalendars()
    fun toggleCalendarSync(enabled: Boolean) = viewModel.toggleCalendarSync(enabled)
    fun enableCalendarSyncAndSyncNow(callback: suspend (Result<Unit>) -> Unit = {}) = viewModel.enableCalendarSyncAndSyncNow(callback)
    fun updateSourceCalendars(ids: List<Long>, callback: suspend (Result<Unit>) -> Unit = {}) = viewModel.updateSourceCalendars(ids, callback)
    fun updateSyncIntervalSeconds(seconds: Int, callback: suspend (Result<Unit>) -> Unit = {}) = viewModel.updateSyncIntervalSeconds(seconds, callback)
    fun hasDuplicateAdvanceReminder(minutes: Int) = viewModel.hasDuplicateAdvanceReminder(minutes)
    fun updateUiSize(size: Int) = viewModel.updateUiSize(size)
    fun updateScreenshotDelay(delay: Long) = viewModel.updateScreenshotDelay(delay)
    fun updateDailySummaryTimes(morningMinuteOfDay: Int? = null, eveningMinuteOfDay: Int? = null, onUpdated: () -> Unit = {}) =
        viewModel.updateDailySummaryTimes(
            morningMinuteOfDay = morningMinuteOfDay,
            eveningMinuteOfDay = eveningMinuteOfDay,
            onUpdated = onUpdated
        )
    fun updateEdgeBarSettings(enabled: Boolean? = null, side: String? = null, yPercent: Float? = null, widthDp: Int? = null, heightDp: Int? = null, alpha: Float? = null, singleTapAction: Int? = null, doubleTapAction: Int? = null, longPressAction: Int? = null) =
        viewModel.updateEdgeBarSettings(
            enabled = enabled,
            side = side,
            yPercent = yPercent,
            widthDp = widthDp,
            heightDp = heightDp,
            alpha = alpha,
            singleTapAction = singleTapAction,
            doubleTapAction = doubleTapAction,
            longPressAction = longPressAction
        )
    fun updatePreference(
        showTomorrow: Boolean? = null, dailySummary: Boolean? = null, liveCapsule: Boolean? = null,
        pickupAggregation: Boolean? = null, hapticFeedbackEnabled: Boolean? = null,
        edgeBarEnabled: Boolean? = null, networkSpeedCapsule: Boolean? = null,
        floatingWindow: Boolean? = null, advanceReminderEnabled: Boolean? = null,
        advanceReminderMinutes: Int? = null, autoArchive: Boolean? = null,
        recognitionMode: Int? = null, defaultEventDurationMinutes: Int? = null,
        useMultimodalAi: Boolean? = null, disableThinking: Boolean? = null,
        floatingEventRange: Int? = null, floatingExpandSide: String? = null,
        floatingBallEnabled: Boolean? = null, floatingBallXPercent: Float? = null,
        floatingBallYPercent: Float? = null, floatingBallSizeDp: Int? = null,
        floatingBallAlpha: Float? = null, floatingBallSingleTapAction: Int? = null,
        floatingBallDoubleTapAction: Int? = null, floatingBallLongPressAction: Int? = null,
        edgeBarSingleTapAction: Int? = null, edgeBarDoubleTapAction: Int? = null,
        edgeBarLongPressAction: Int? = null, volumeUpLongPressEnabled: Boolean? = null,
        volumeUpLongPressAction: Int? = null, smsMonitoring: Boolean? = null,
        courseFeatureEnabled: Boolean? = null
    ) = viewModel.updatePreference(
        showTomorrow = showTomorrow, dailySummary = dailySummary, liveCapsule = liveCapsule,
        pickupAggregation = pickupAggregation, hapticFeedbackEnabled = hapticFeedbackEnabled,
        edgeBarEnabled = edgeBarEnabled, networkSpeedCapsule = networkSpeedCapsule,
        floatingWindow = floatingWindow, advanceReminderEnabled = advanceReminderEnabled,
        advanceReminderMinutes = advanceReminderMinutes, autoArchive = autoArchive,
        recognitionMode = recognitionMode, defaultEventDurationMinutes = defaultEventDurationMinutes,
        useMultimodalAi = useMultimodalAi, disableThinking = disableThinking,
        floatingEventRange = floatingEventRange, floatingExpandSide = floatingExpandSide,
        floatingBallEnabled = floatingBallEnabled, floatingBallXPercent = floatingBallXPercent,
        floatingBallYPercent = floatingBallYPercent, floatingBallSizeDp = floatingBallSizeDp,
        floatingBallAlpha = floatingBallAlpha, floatingBallSingleTapAction = floatingBallSingleTapAction,
        floatingBallDoubleTapAction = floatingBallDoubleTapAction, floatingBallLongPressAction = floatingBallLongPressAction,
        edgeBarSingleTapAction = edgeBarSingleTapAction, edgeBarDoubleTapAction = edgeBarDoubleTapAction,
        edgeBarLongPressAction = edgeBarLongPressAction, volumeUpLongPressEnabled = volumeUpLongPressEnabled,
        volumeUpLongPressAction = volumeUpLongPressAction, smsMonitoring = smsMonitoring,
        courseFeatureEnabled = courseFeatureEnabled
    )
}
