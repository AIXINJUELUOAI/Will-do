package com.antgskds.calendarassistant.ui.contract

import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.core.quickmemo.QuickMemoEntity
import com.antgskds.calendarassistant.core.quickmemo.audio.AudioPlaybackState
import com.antgskds.calendarassistant.feature.quickmemo.domain.model.QuickMemoVoiceCaptureState
import com.antgskds.calendarassistant.feature.schedule.application.model.EventPatch
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.feature.schedule.presentation.model.ScheduleDisplayItem
import com.antgskds.calendarassistant.data.model.WeatherData

enum class FloatingInputMode { SCHEDULE, NOTE }

data class FloatingDragTextOptions(
    val includeTitle: Boolean = true,
    val includeTime: Boolean = false,
    val includeLocation: Boolean = false,
    val includeDescription: Boolean = true
)

data class FloatingScheduleUiState(
    val scheduleItems: List<ScheduleDisplayItem>,
    val quickMemos: List<QuickMemoEntity> = emptyList(),
    val voiceCaptureState: QuickMemoVoiceCaptureState = QuickMemoVoiceCaptureState(),
    val recentVoiceMemoId: Long? = null,
    val audioPlaybackState: AudioPlaybackState = AudioPlaybackState(),
    val weatherData: WeatherData? = null,
    val weatherForecastRange: Int = 0,
    val expandSide: String = "RIGHT",
    val initialMode: FloatingInputMode = FloatingInputMode.SCHEDULE,
    val initialModeRequestKey: Long = 0L,
    val pendingStatusKeys: Set<String> = emptySet(),
    val undoPendingLabel: String? = null,
    val floatingScheduleOrderKeys: List<String> = emptyList(),
    val dragHotZonePercent: Int = MySettings.FLOATING_DRAG_HOT_ZONE_DEFAULT_PERCENT,
    val dragTextOptions: FloatingDragTextOptions = FloatingDragTextOptions(),
    val hapticEnabled: Boolean = true,
    val reverseScheduleOrder: Boolean = true
)

class FloatingScheduleUiActions(
    val onClose: () -> Unit,
    val onManualInput: (text: String, isQuickMemo: Boolean, onComplete: () -> Unit) -> Unit,
    val onPickImageRequest: (isQuickMemo: Boolean, onComplete: () -> Unit) -> Unit,
    val onUpdateEvent: (Event, () -> Unit) -> Unit = { _, onComplete -> onComplete() },
    val onUpdateScheduleItem: (ScheduleDisplayItem, EventPatch, () -> Unit) -> Unit = { _, _, onComplete -> onComplete() },
    val onArchiveScheduleItem: (ScheduleDisplayItem) -> Unit = {},
    val onStatusAction: (ScheduleDisplayItem) -> Unit = {},
    val onUndoAction: () -> Unit = {},
    val onMarkQuickMemoTodo: (QuickMemoEntity) -> Unit = {},
    val onRemoveQuickMemoTodo: (QuickMemoEntity) -> Unit = {},
    val onToggleQuickMemoTodo: (QuickMemoEntity) -> Unit = {},
    val onDeleteQuickMemo: (QuickMemoEntity, () -> Unit) -> Unit = { _, onComplete -> onComplete() },
    val onSaveQuickMemo: (QuickMemoEntity, String, () -> Unit) -> Unit = { _, _, onComplete -> onComplete() },
    val onReorderQuickMemos: (List<Long>) -> Unit = {},
    val onReorderScheduleItems: (List<String>) -> Unit = {},
    val onStartPlainTextDrag: (String, String, () -> Unit) -> Boolean = { _, _, _ -> false },
    val onConfirmVoiceCapture: (Boolean) -> Unit = {},
    val onPostVoiceTranscription: (QuickMemoEntity) -> Unit = {},
    val onStartVoiceCapture: () -> Unit = {},
    val onStopVoiceCapture: () -> Unit = {},
    val onToggleAudioPlayback: (String?) -> Unit = {},
    val onLoadingChange: (Boolean) -> Unit = {}
)
