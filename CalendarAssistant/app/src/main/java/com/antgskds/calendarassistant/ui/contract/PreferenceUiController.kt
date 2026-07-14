package com.antgskds.calendarassistant.ui.contract

import com.antgskds.calendarassistant.calendar.models.stubs.CalendarManager
import com.antgskds.calendarassistant.calendar.models.stubs.CalendarSyncManager
import com.antgskds.calendarassistant.data.model.MySettings
import kotlinx.coroutines.flow.StateFlow

interface PreferenceUiController {
    val settings: StateFlow<MySettings>
    val syncStatus: StateFlow<CalendarSyncManager.SyncStatus>
    val availableSyncCalendars: StateFlow<List<CalendarManager.CalendarInfo>>

    fun refreshSyncStatus()
    fun refreshSyncCalendars()
    fun toggleCalendarSync(enabled: Boolean)
    fun enableCalendarSyncAndSyncNow(callback: suspend (Result<Unit>) -> Unit = {})
    fun updateSourceCalendars(ids: List<Long>, callback: suspend (Result<Unit>) -> Unit = {})
    fun updateSyncIntervalSeconds(seconds: Int, callback: suspend (Result<Unit>) -> Unit = {})
    fun hasDuplicateAdvanceReminder(minutes: Int): Boolean
    fun updateUiSize(size: Int)
    fun updateScreenshotDelay(delay: Long)
    fun updateDailySummaryTimes(morningMinuteOfDay: Int? = null, eveningMinuteOfDay: Int? = null, onUpdated: () -> Unit = {})
    fun updateEdgeBarSettings(enabled: Boolean? = null, side: String? = null, yPercent: Float? = null, widthDp: Int? = null, heightDp: Int? = null, alpha: Float? = null, singleTapAction: Int? = null, doubleTapAction: Int? = null, longPressAction: Int? = null)
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
    )
}
