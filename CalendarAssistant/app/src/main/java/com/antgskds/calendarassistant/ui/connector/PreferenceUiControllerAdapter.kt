package com.antgskds.calendarassistant.ui.connector

import com.antgskds.calendarassistant.ui.contract.PreferenceUiController
import com.antgskds.calendarassistant.ui.viewmodel.SettingsViewModel

class PreferenceUiControllerAdapter(private val viewModel: SettingsViewModel) : PreferenceUiController {
    override val settings = viewModel.settings
    override val syncStatus = viewModel.syncStatus
    override val availableSyncCalendars = viewModel.availableSyncCalendars

    override fun refreshSyncStatus() = viewModel.refreshSyncStatus()
    override fun refreshSyncCalendars() = viewModel.refreshSyncCalendars()
    override fun toggleCalendarSync(enabled: Boolean) = viewModel.toggleCalendarSync(enabled)
    override fun enableCalendarSyncAndSyncNow(callback: suspend (Result<Unit>) -> Unit) = viewModel.enableCalendarSyncAndSyncNow(callback)
    override fun updateSourceCalendars(ids: List<Long>, callback: suspend (Result<Unit>) -> Unit) = viewModel.updateSourceCalendars(ids, callback)
    override fun updateSyncIntervalSeconds(seconds: Int, callback: suspend (Result<Unit>) -> Unit) = viewModel.updateSyncIntervalSeconds(seconds, callback)
    override fun hasDuplicateAdvanceReminder(minutes: Int) = viewModel.hasDuplicateAdvanceReminder(minutes)
    override fun updateUiSize(size: Int) = viewModel.updateUiSize(size)
    override fun updateScreenshotDelay(delay: Long) = viewModel.updateScreenshotDelay(delay)
    override fun updateDailySummaryTimes(morningMinuteOfDay: Int?, eveningMinuteOfDay: Int?, onUpdated: () -> Unit) =
        viewModel.updateDailySummaryTimes(morningMinuteOfDay = morningMinuteOfDay, eveningMinuteOfDay = eveningMinuteOfDay, onUpdated = onUpdated)
    override fun updateEdgeBarSettings(enabled: Boolean?, side: String?, yPercent: Float?, widthDp: Int?, heightDp: Int?, alpha: Float?, singleTapAction: Int?, doubleTapAction: Int?, longPressAction: Int?) =
        viewModel.updateEdgeBarSettings(enabled = enabled, side = side, yPercent = yPercent, widthDp = widthDp, heightDp = heightDp, alpha = alpha, singleTapAction = singleTapAction, doubleTapAction = doubleTapAction, longPressAction = longPressAction)
    override fun updatePreference(
        showTomorrow: Boolean?, dailySummary: Boolean?, liveCapsule: Boolean?, pickupAggregation: Boolean?,
        hapticFeedbackEnabled: Boolean?, edgeBarEnabled: Boolean?, networkSpeedCapsule: Boolean?,
        floatingWindow: Boolean?, advanceReminderEnabled: Boolean?, advanceReminderMinutes: Int?,
        autoArchive: Boolean?, recognitionMode: Int?, defaultEventDurationMinutes: Int?,
        useMultimodalAi: Boolean?, disableThinking: Boolean?, floatingEventRange: Int?,
        floatingExpandSide: String?, floatingBallEnabled: Boolean?, floatingBallXPercent: Float?,
        floatingBallYPercent: Float?, floatingBallSizeDp: Int?, floatingBallAlpha: Float?,
        floatingBallSingleTapAction: Int?, floatingBallDoubleTapAction: Int?, floatingBallLongPressAction: Int?,
        edgeBarSingleTapAction: Int?, edgeBarDoubleTapAction: Int?, edgeBarLongPressAction: Int?,
        volumeUpLongPressEnabled: Boolean?, volumeUpLongPressAction: Int?, smsMonitoring: Boolean?,
        courseFeatureEnabled: Boolean?
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
