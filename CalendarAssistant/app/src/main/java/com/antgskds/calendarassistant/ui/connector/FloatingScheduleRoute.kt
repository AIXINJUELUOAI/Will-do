package com.antgskds.calendarassistant.ui.connector

import androidx.compose.runtime.Composable
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.core.quickmemo.QuickMemoEntity
import com.antgskds.calendarassistant.core.quickmemo.audio.AudioPlaybackState
import com.antgskds.calendarassistant.core.quickmemo.audio.QuickMemoVoiceCaptureState
import com.antgskds.calendarassistant.data.model.EventPatch
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.data.model.ScheduleDisplayItem
import com.antgskds.calendarassistant.data.model.WeatherData
import com.antgskds.calendarassistant.ui.contract.FloatingScheduleUiActions
import com.antgskds.calendarassistant.ui.contract.FloatingDragTextOptions
import com.antgskds.calendarassistant.ui.contract.FloatingInputMode
import com.antgskds.calendarassistant.ui.contract.FloatingScheduleUiState
import com.antgskds.calendarassistant.platform.floating.ui.render.FloatingScheduleScreen

@Composable
fun FloatingScheduleRoute(
    scheduleItems: List<ScheduleDisplayItem>,
    quickMemos: List<QuickMemoEntity> = emptyList(),
    voiceCaptureState: QuickMemoVoiceCaptureState = QuickMemoVoiceCaptureState(),
    recentVoiceMemoId: Long? = null,
    audioPlaybackState: AudioPlaybackState = AudioPlaybackState(),
    weatherData: WeatherData? = null,
    weatherForecastRange: Int = 0,
    expandSide: String = "RIGHT",
    initialMode: FloatingInputMode = FloatingInputMode.SCHEDULE,
    initialModeRequestKey: Long = 0L,
    onClose: () -> Unit,
    onManualInput: (text: String, isQuickMemo: Boolean, onComplete: () -> Unit) -> Unit,
    onPickImageRequest: (isQuickMemo: Boolean, onComplete: () -> Unit) -> Unit,
    onUpdateEvent: (Event, () -> Unit) -> Unit = { _, onComplete -> onComplete() },
    onUpdateScheduleItem: (ScheduleDisplayItem, EventPatch, () -> Unit) -> Unit = { _, _, onComplete -> onComplete() },
    onArchiveScheduleItem: (ScheduleDisplayItem) -> Unit = {},
    onStatusAction: (ScheduleDisplayItem) -> Unit = {},
    pendingStatusKeys: Set<String> = emptySet(),
    undoPendingLabel: String? = null,
    onUndoAction: () -> Unit = {},
    onMarkQuickMemoTodo: (QuickMemoEntity) -> Unit = {},
    onRemoveQuickMemoTodo: (QuickMemoEntity) -> Unit = {},
    onToggleQuickMemoTodo: (QuickMemoEntity) -> Unit = {},
    onDeleteQuickMemo: (QuickMemoEntity, () -> Unit) -> Unit = { _, onComplete -> onComplete() },
    onSaveQuickMemo: (QuickMemoEntity, String, () -> Unit) -> Unit = { _, _, onComplete -> onComplete() },
    onReorderQuickMemos: (List<Long>) -> Unit = {},
    floatingScheduleOrderKeys: List<String> = emptyList(),
    onReorderScheduleItems: (List<String>) -> Unit = {},
    dragHotZonePercent: Int = MySettings.FLOATING_DRAG_HOT_ZONE_DEFAULT_PERCENT,
    dragTextOptions: FloatingDragTextOptions = FloatingDragTextOptions(),
    onStartPlainTextDrag: (String, String, () -> Unit) -> Boolean = { _, _, _ -> false },
    onConfirmVoiceCapture: (Boolean) -> Unit = {},
    onPostVoiceTranscription: (QuickMemoEntity) -> Unit = {},
    onStartVoiceCapture: () -> Unit = {},
    onStopVoiceCapture: () -> Unit = {},
    onToggleAudioPlayback: (String?) -> Unit = {},
    onLoadingChange: (Boolean) -> Unit = {},
    hapticEnabled: Boolean = true,
    reverseScheduleOrder: Boolean = true
) {
    FloatingScheduleScreen(
        state = FloatingScheduleUiState(
            scheduleItems = scheduleItems,
            quickMemos = quickMemos,
            voiceCaptureState = voiceCaptureState,
            recentVoiceMemoId = recentVoiceMemoId,
            audioPlaybackState = audioPlaybackState,
            weatherData = weatherData,
            weatherForecastRange = weatherForecastRange,
            expandSide = expandSide,
            initialMode = initialMode,
            initialModeRequestKey = initialModeRequestKey,
            pendingStatusKeys = pendingStatusKeys,
            undoPendingLabel = undoPendingLabel,
            floatingScheduleOrderKeys = floatingScheduleOrderKeys,
            dragHotZonePercent = dragHotZonePercent,
            dragTextOptions = dragTextOptions,
            hapticEnabled = hapticEnabled,
            reverseScheduleOrder = reverseScheduleOrder
        ),
        actions = FloatingScheduleUiActions(
            onClose = onClose,
            onManualInput = onManualInput,
            onPickImageRequest = onPickImageRequest,
            onUpdateEvent = onUpdateEvent,
            onUpdateScheduleItem = onUpdateScheduleItem,
            onArchiveScheduleItem = onArchiveScheduleItem,
            onStatusAction = onStatusAction,
            onUndoAction = onUndoAction,
            onMarkQuickMemoTodo = onMarkQuickMemoTodo,
            onRemoveQuickMemoTodo = onRemoveQuickMemoTodo,
            onToggleQuickMemoTodo = onToggleQuickMemoTodo,
            onDeleteQuickMemo = onDeleteQuickMemo,
            onSaveQuickMemo = onSaveQuickMemo,
            onReorderQuickMemos = onReorderQuickMemos,
            onReorderScheduleItems = onReorderScheduleItems,
            onStartPlainTextDrag = onStartPlainTextDrag,
            onConfirmVoiceCapture = onConfirmVoiceCapture,
            onPostVoiceTranscription = onPostVoiceTranscription,
            onStartVoiceCapture = onStartVoiceCapture,
            onStopVoiceCapture = onStopVoiceCapture,
            onToggleAudioPlayback = onToggleAudioPlayback,
            onLoadingChange = onLoadingChange
        )
    )
}
