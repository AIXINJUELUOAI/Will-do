package com.antgskds.calendarassistant.ui.floating

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.LocalTaxi
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.res.painterResource
import com.antgskds.calendarassistant.R
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import android.content.Context
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.*
import com.antgskds.calendarassistant.data.model.WeatherData
import com.antgskds.calendarassistant.data.model.WeatherDailyForecast
import com.antgskds.calendarassistant.data.model.WeatherHourlyForecast
import com.antgskds.calendarassistant.data.model.displayLocationName
import com.antgskds.calendarassistant.feature.weather.domain.WeatherForecastIconMapper
import com.antgskds.calendarassistant.feature.weather.domain.WeatherIconMapper
import com.antgskds.calendarassistant.core.rule.ActionIconType
import com.antgskds.calendarassistant.core.content.EventTimelinePresenter
import com.antgskds.calendarassistant.core.rule.StatusColor
import com.antgskds.calendarassistant.core.util.extractSourceImagePath
import com.antgskds.calendarassistant.core.util.mergeSourceImageMarker
import com.antgskds.calendarassistant.core.util.stripSourceImageMarkers
import com.antgskds.calendarassistant.data.model.EventPatch
import com.antgskds.calendarassistant.data.model.ScheduleDisplayItem
import com.antgskds.calendarassistant.core.quickmemo.QuickMemoEntity
import com.antgskds.calendarassistant.core.quickmemo.QuickMemoTodoState
import com.antgskds.calendarassistant.core.quickmemo.QuickMemoTranscriptionStatus
import com.antgskds.calendarassistant.core.quickmemo.QuickMemoType
import com.antgskds.calendarassistant.core.quickmemo.audio.AudioPlaybackState
import com.antgskds.calendarassistant.core.quickmemo.audio.QuickMemoVoiceCaptureState
import com.antgskds.calendarassistant.core.quickmemo.audio.QuickMemoVoiceCaptureStatus
import com.antgskds.calendarassistant.ui.components.WheelDatePicker
import com.antgskds.calendarassistant.ui.components.WheelTimePicker
import com.antgskds.calendarassistant.ui.haptic.rememberAppHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

private const val FLOATING_EXPAND_LEFT = "LEFT"
private val FloatingActionYellowContainer = Color(0xFFFFF3C4)
private val FloatingActionYellowIcon = Color(0xFF8A6200)
private val FloatingActionWarningIcon = Color(0xFFE08600)
private val FloatingVoiceWaveformWidth = 118.dp

enum class FloatingInputMode { SCHEDULE, NOTE }

@Composable
fun FloatingScheduleScreen(
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
    onConfirmVoiceCapture: (Boolean) -> Unit = {},
    onPostVoiceTranscription: (QuickMemoEntity) -> Unit = {},
    onStartVoiceCapture: () -> Unit = {},
    onStopVoiceCapture: () -> Unit = {},
    onToggleAudioPlayback: (String?) -> Unit = {},
    onLoadingChange: (Boolean) -> Unit = {},
    hapticEnabled: Boolean = true,
    reverseScheduleOrder: Boolean = true
) {
    var manualInputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var pickerRequest by remember { mutableStateOf<FloatingPickerRequest?>(null) }
    val expandFromLeft = expandSide == FLOATING_EXPAND_LEFT

    // 动画状态
    var isAppearing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 关闭函数：先播动画，再执行销毁
    val animateClose = {
        scope.launch {
            isAppearing = false
            delay(250)
            onClose()
        }
    }

    // 进入时立即触发动画
    LaunchedEffect(Unit) {
        isAppearing = true
    }

    // 动画曲线定义
    val fastOutSlowIn = remember { CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f) }
    val enterDuration = 280
    val exitDuration = 200

    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val haptics = rememberAppHaptics(hapticEnabled)
    val isImeVisible = WindowInsets.ime.getBottom(density) > 0
    val isPickerVisible = pickerRequest != null
    var currentMode by remember { mutableStateOf(initialMode) }
    val recentVoiceMemo = remember(quickMemos, recentVoiceMemoId) {
        recentVoiceMemoId?.let { id -> quickMemos.firstOrNull { it.id == id } }
    }
    val voiceTranscriptionUploadTarget = remember(quickMemos, recentVoiceMemo) {
        quickMemos
            .filter { memo ->
                memo.type == QuickMemoType.VOICE &&
                    memo.transcriptionStatus == QuickMemoTranscriptionStatus.SUCCESS &&
                    memo.bodyText.isNotBlank()
            }
            .maxByOrNull { it.updatedAt }
    }

    LaunchedEffect(voiceCaptureState.status) {
        if (voiceCaptureState.isActive) {
            currentMode = FloatingInputMode.NOTE
        }
    }

    LaunchedEffect(initialMode, initialModeRequestKey) {
        currentMode = initialMode
    }

    // 背景透明度动画
    val bgAlpha by animateFloatAsState(
        targetValue = if (isAppearing) 0.6f else 0f,
        animationSpec = tween(
            durationMillis = if (isAppearing) enterDuration else exitDuration,
            easing = androidx.compose.animation.core.LinearEasing
        ),
        label = "bg_dim"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = bgAlpha))
            .pointerInput(isImeVisible, isPickerVisible) {
                detectTapGestures(onTap = {
                    if (isPickerVisible) return@detectTapGestures
                    if (isImeVisible) {
                        keyboardController?.hide()
                        focusManager.clearFocus(force = true)
                    } else {
                        animateClose()
                    }
                })
            }
    ) {
        // 日程列表：根据设置从左侧或右侧滑入滑出
        AnimatedVisibility(
            visible = isAppearing,
            enter = fadeIn(
                animationSpec = tween(enterDuration, easing = fastOutSlowIn)
            ) + slideInHorizontally(
                animationSpec = tween(enterDuration, easing = fastOutSlowIn),
                initialOffsetX = { width -> if (expandFromLeft) -width else width }
            ),
            exit = fadeOut(
                animationSpec = tween(exitDuration, easing = fastOutSlowIn)
            ) + slideOutHorizontally(
                animationSpec = tween(exitDuration, easing = fastOutSlowIn),
                targetOffsetX = { width -> if (expandFromLeft) -width else width }
            ),
            modifier = Modifier.align(if (expandFromLeft) Alignment.TopStart else Alignment.TopEnd)
        ) {
            TimeWheelList(
                scheduleItems = scheduleItems,
                quickMemos = quickMemos,
                audioPlaybackState = audioPlaybackState,
                weatherData = weatherData,
                weatherForecastRange = weatherForecastRange,
                currentMode = currentMode,
                expandFromLeft = expandFromLeft,
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(),
                listState = listState,
                onUpdateScheduleItem = onUpdateScheduleItem,
                onArchiveScheduleItem = onArchiveScheduleItem,
                onStatusAction = onStatusAction,
                pendingStatusKeys = pendingStatusKeys,
                onMarkQuickMemoTodo = onMarkQuickMemoTodo,
                onRemoveQuickMemoTodo = onRemoveQuickMemoTodo,
                onToggleQuickMemoTodo = onToggleQuickMemoTodo,
                onDeleteQuickMemo = { memo, onComplete ->
                    onDeleteQuickMemo(memo, onComplete)
                },
                onSaveQuickMemo = onSaveQuickMemo,
                onReorderQuickMemos = onReorderQuickMemos,
                onToggleAudioPlayback = onToggleAudioPlayback,
                onRequestDatePicker = { initialDate, onConfirm ->
                    pickerRequest = FloatingPickerRequest.Date(initialDate, onConfirm)
                },
                onRequestTimePicker = { initialTime, onConfirm ->
                    pickerRequest = FloatingPickerRequest.Time(initialTime, onConfirm)
                },
                hapticEnabled = hapticEnabled,
                reverseScheduleOrder = reverseScheduleOrder
            )
        }

        // 顶部与底部遮罩
        Box(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().height(80.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = bgAlpha), Color.Transparent))))
        Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(150.dp).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = bgAlpha)))))

        // 底部输入框
        AnimatedVisibility(
            visible = isAppearing,
            enter = fadeIn(tween(enterDuration, easing = fastOutSlowIn)) + slideInVertically(tween(enterDuration, easing = fastOutSlowIn), initialOffsetY = { it }),
            exit = fadeOut(tween(exitDuration, easing = fastOutSlowIn)) + slideOutVertically(tween(exitDuration, easing = fastOutSlowIn), targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            BottomInteractionArea(
                modifier = Modifier,
                text = manualInputText,
                onTextChange = { manualInputText = it },
                voiceCaptureState = voiceCaptureState,
                onConfirmVoiceCapture = onConfirmVoiceCapture,
                recentVoiceMemo = recentVoiceMemo,
                voiceTranscriptionUploadTarget = voiceTranscriptionUploadTarget,
                onPostVoiceTranscription = onPostVoiceTranscription,
                onStartVoiceCapture = onStartVoiceCapture,
                onStopVoiceCapture = onStopVoiceCapture,
                onManualSubmit = { text ->
                    if (text.isNotBlank()) {
                        isLoading = true
                        onLoadingChange(true)
                        haptics.confirm()
                        onManualInput(text, currentMode == FloatingInputMode.NOTE) {
                            isLoading = false
                            onLoadingChange(false)
                        }
                        manualInputText = ""
                    }
                },
                onPickImage = {
                    if (isLoading) return@BottomInteractionArea
                    isLoading = true
                    onLoadingChange(true)
                    haptics.click()
                    onPickImageRequest(currentMode == FloatingInputMode.NOTE) {
                        isLoading = false
                        onLoadingChange(false)
                    }
                },
                onSwipeUpClose = { animateClose() },
                isLoading = isLoading,
                currentMode = currentMode,
                onModeChange = { currentMode = it },
                hapticEnabled = hapticEnabled
            )
        }

        pickerRequest?.let { request ->
            when (request) {
                is FloatingPickerRequest.Date -> FloatingDatePickerOverlay(request.initialDate, { pickerRequest = null }, { date -> request.onConfirm(date); pickerRequest = null })
                is FloatingPickerRequest.Time -> FloatingTimePickerOverlay(request.initialTime, { pickerRequest = null }, { time -> request.onConfirm(time); pickerRequest = null })
            }
        }
    }
}

@Composable
fun BottomInteractionArea(
    modifier: Modifier = Modifier,
    text: String,
    onTextChange: (String) -> Unit,
    voiceCaptureState: QuickMemoVoiceCaptureState = QuickMemoVoiceCaptureState(),
    onConfirmVoiceCapture: (Boolean) -> Unit = {},
    recentVoiceMemo: QuickMemoEntity? = null,
    voiceTranscriptionUploadTarget: QuickMemoEntity? = null,
    onPostVoiceTranscription: (QuickMemoEntity) -> Unit = {},
    onStartVoiceCapture: () -> Unit = {},
    onStopVoiceCapture: () -> Unit = {},
    onManualSubmit: (String) -> Unit,
    onPickImage: () -> Unit,
    onSwipeUpClose: () -> Unit,
    isLoading: Boolean = false,
    currentMode: FloatingInputMode = FloatingInputMode.SCHEDULE,
    onModeChange: (FloatingInputMode) -> Unit = {},
    hapticEnabled: Boolean = true
) {
    val haptics = rememberAppHaptics(hapticEnabled)

    val primaryColor = MaterialTheme.colorScheme.primary

    val isNote = currentMode == FloatingInputMode.NOTE
    val activeColor = primaryColor
    val voiceActive = voiceCaptureState.isActive
    val showRecentVoiceMemo = recentVoiceMemo != null && isNote && text.isBlank()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    var totalDragY = 0f
                    detectVerticalDragGestures(
                        onDragEnd = { totalDragY = 0f },
                        onDragCancel = { totalDragY = 0f },
                        onVerticalDrag = { change: PointerInputChange, dragAmount: Float ->
                            totalDragY += dragAmount
                            if (dragAmount < 0 && totalDragY < -20f) {
                                change.consume()
                                onSwipeUpClose()
                            }
                        }
                    )
                }
                .padding(horizontal = 28.dp, vertical = 16.dp)
        ) {
            Surface(
                // 【核心修改】：将原本的 RoundedCornerShape(26.dp) 改为纯粹的 CircleShape
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (voiceActive) {
                        VoiceCaptureContent(
                            state = voiceCaptureState,
                            activeColor = activeColor,
                            onConfirm = onConfirmVoiceCapture,
                            onStop = onStopVoiceCapture,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                    // 1. 左侧：日程输入图标
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(activeColor.copy(alpha = 0.15f))
                            .clickable(enabled = !isLoading) {
                                haptics.selection()
                                onModeChange(if (isNote) FloatingInputMode.SCHEDULE else FloatingInputMode.NOTE)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isNote) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_stat_quickmemo),
                                contentDescription = "随口记",
                                tint = activeColor,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_stat_event),
                                contentDescription = "日程",
                                tint = activeColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // 2. 中间：普通文本输入，或刚保存后的临时语音条
                    if (showRecentVoiceMemo) {
                        FloatingRecentVoiceMemoInput(
                            memo = recentVoiceMemo,
                            activeColor = activeColor,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 10.dp)
                        )
                    } else {
                        BasicTextField(
                            value = text,
                            onValueChange = onTextChange,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 10.dp),
                            textStyle = TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 15.sp,
                                lineHeight = 22.sp
                            ),
                            singleLine = false,
                            maxLines = 4,
                            enabled = !isLoading,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                            cursorBrush = SolidColor(activeColor),
                            decorationBox = { innerTextField ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (text.isEmpty()) {
                                        Text(
                                            text = if (isNote) "记一条随口记..." else "一句话安排日程...",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            fontSize = 15.sp
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    }

                    // 3. 右侧：功能区
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!showRecentVoiceMemo) {
                            IconButton(
                                onClick = { haptics.click(); onPickImage() },
                                enabled = !isLoading,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_stat_scan),
                                    contentDescription = "扫描图片",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(2.dp))
                        }

                        if (isLoading) {
                            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = activeColor
                                )
                            }
                        } else {
                            val isTextNotBlank = text.isNotBlank()
                            val recentUploadTarget = recentVoiceMemo
                                ?.takeIf {
                                    showRecentVoiceMemo &&
                                        it.transcriptionStatus == QuickMemoTranscriptionStatus.SUCCESS &&
                                        it.bodyText.isNotBlank()
                                }
                            val uploadTarget = recentUploadTarget
                            val canSend = isTextNotBlank || uploadTarget != null
                            val canRecord = isNote && !showRecentVoiceMemo && !isTextNotBlank && uploadTarget == null
                            val mainActionEnabled = canSend || canRecord
                            val sendBtnContainerColor by animateColorAsState(
                                targetValue = if (mainActionEnabled) activeColor else activeColor.copy(alpha = 0.15f),
                                animationSpec = tween(150), label = "send_bg"
                            )
                            val sendBtnIconColor by animateColorAsState(
                                targetValue = if (mainActionEnabled) Color.White else activeColor.copy(alpha = 0.6f),
                                animationSpec = tween(150), label = "send_icon"
                            )

                            Surface(
                                onClick = {
                                    when {
                                        isTextNotBlank -> {
                                            haptics.confirm()
                                            onManualSubmit(text)
                                        }
                                        uploadTarget != null -> {
                                            haptics.confirm()
                                            onPostVoiceTranscription(uploadTarget)
                                        }
                                        canRecord -> {
                                            haptics.click()
                                            onStartVoiceCapture()
                                        }
                                    }
                                },
                                shape = CircleShape,
                                color = sendBtnContainerColor,
                                enabled = mainActionEnabled,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (canRecord) Icons.Rounded.Mic else Icons.Rounded.ArrowUpward,
                                        contentDescription = when {
                                            canRecord -> "语音随口记"
                                            uploadTarget != null -> "发送到实况通知"
                                            else -> "发送"
                                        },
                                        tint = sendBtnIconColor,
                                        modifier = Modifier.size(if (canRecord) 22.dp else 20.dp)
                                    )
                                }
                            }
                        }
                    }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.ime.union(WindowInsets.navigationBars)))
    }
}

@Composable
private fun FloatingRecentVoiceMemoInput(
    memo: QuickMemoEntity,
    activeColor: Color,
    modifier: Modifier = Modifier
) {
    val statusText = floatingQuickMemoStatusText(memo)
    Row(
        modifier = modifier.fillMaxHeight(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FloatingSiriWaveform(
            isPlaying = memo.transcriptionStatus == QuickMemoTranscriptionStatus.PENDING ||
                memo.transcriptionStatus == QuickMemoTranscriptionStatus.PROCESSING,
            modifier = Modifier.width(FloatingVoiceWaveformWidth).height(22.dp),
            color = activeColor
        )
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun TimeWheelList(
    scheduleItems: List<ScheduleDisplayItem>,
    quickMemos: List<QuickMemoEntity> = emptyList(),
    audioPlaybackState: AudioPlaybackState = AudioPlaybackState(),
    weatherData: WeatherData? = null,
    weatherForecastRange: Int = 0,
    currentMode: FloatingInputMode = FloatingInputMode.SCHEDULE,
    expandFromLeft: Boolean = false,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    onUpdateScheduleItem: (ScheduleDisplayItem, EventPatch, () -> Unit) -> Unit = { _, _, onComplete -> onComplete() },
    onArchiveScheduleItem: (ScheduleDisplayItem) -> Unit = {},
    onStatusAction: (ScheduleDisplayItem) -> Unit = {},
    pendingStatusKeys: Set<String> = emptySet(),
    onMarkQuickMemoTodo: (QuickMemoEntity) -> Unit = {},
    onRemoveQuickMemoTodo: (QuickMemoEntity) -> Unit = {},
    onToggleQuickMemoTodo: (QuickMemoEntity) -> Unit = {},
    onDeleteQuickMemo: (QuickMemoEntity, () -> Unit) -> Unit = { _, onComplete -> onComplete() },
    onSaveQuickMemo: (QuickMemoEntity, String, () -> Unit) -> Unit = { _, _, onComplete -> onComplete() },
    onReorderQuickMemos: (List<Long>) -> Unit = {},
    onToggleAudioPlayback: (String?) -> Unit = {},
    onRequestDatePicker: (LocalDate, (LocalDate) -> Unit) -> Unit = { _, _ -> },
    onRequestTimePicker: (String, (String) -> Unit) -> Unit = { _, _ -> },
    hapticEnabled: Boolean = true,
    reverseScheduleOrder: Boolean = true
) {
    val now = LocalDateTime.now()
    val haptics = rememberAppHaptics(hapticEnabled)
    val density = LocalDensity.current
    val cardAlignment = if (expandFromLeft) Alignment.CenterStart else Alignment.CenterEnd
    val cardModifier = if (expandFromLeft) {
        Modifier.padding(start = 20.dp).width(260.dp)
    } else {
        Modifier.padding(end = 20.dp).width(260.dp)
    }
    val sortedScheduleItems = remember(scheduleItems, reverseScheduleOrder) {
        val distinct = scheduleItems.distinctBy { it.stableKey }
        if (reverseScheduleOrder) {
            distinct.sortedByDescending { it.startTS }
        } else {
            distinct.sortedBy { it.startTS }
        }
    }
    val sortedQuickMemos = remember(quickMemos) {
        quickMemos.sortedWith(compareBy<QuickMemoEntity> { it.sortRank }.thenByDescending { it.updatedAt })
    }
    fun quickMemoOrderKey(memo: QuickMemoEntity): String = memo.id?.let { "id_$it" } ?: "temp_${memo.hashCode()}"
    val sortedQuickMemoKeys = remember(sortedQuickMemos) { sortedQuickMemos.map(::quickMemoOrderKey) }
    val quickMemoOrder = remember { mutableStateListOf<String>() }
    var draggingQuickMemoKey by remember { mutableStateOf<String?>(null) }
    var draggedQuickMemoOffsetY by remember { mutableStateOf(0f) }
    val quickMemoDragStepPx = with(density) { 74.dp.toPx() }

    LaunchedEffect(sortedQuickMemoKeys) {
        if (quickMemoOrder.isEmpty()) {
            quickMemoOrder.addAll(sortedQuickMemoKeys)
        } else {
            quickMemoOrder.removeAll { it !in sortedQuickMemoKeys }
            sortedQuickMemoKeys.forEachIndexed { index, key ->
                if (key !in quickMemoOrder) {
                    quickMemoOrder.add(index.coerceAtMost(quickMemoOrder.size), key)
                }
            }
        }
    }
    val quickMemoOrderSnapshot = quickMemoOrder.toList()
    val displayQuickMemos = remember(sortedQuickMemos, quickMemoOrderSnapshot) {
        val byKey = sortedQuickMemos.associateBy(::quickMemoOrderKey)
        val ordered = quickMemoOrderSnapshot.mapNotNull(byKey::get)
        ordered + sortedQuickMemos.filter { quickMemoOrderKey(it) !in quickMemoOrderSnapshot }
    }
    fun currentQuickMemoIdsInOrder(): List<Long> {
        val byKey = sortedQuickMemos.associateBy(::quickMemoOrderKey)
        return quickMemoOrder.mapNotNull { byKey[it]?.id }
    }
    // 正序（从早到晚）时，悬浮窗打开后自动定位到第一个未结束的日程，
    // 让"今天/现在"出现在视野中，过去的日程在上方可往上滑查看。倒序保持默认（停在顶部）。
    LaunchedEffect(sortedScheduleItems, reverseScheduleOrder, currentMode) {
        if (reverseScheduleOrder) return@LaunchedEffect
        if (currentMode == FloatingInputMode.NOTE) return@LaunchedEffect
        val nowSeconds = System.currentTimeMillis() / 1000L
        val firstUpcoming = sortedScheduleItems.indexOfFirst { it.endTS >= nowSeconds }
        if (firstUpcoming > 0) {
            val headerOffset = if (weatherData != null) 1 else 0
            listState.scrollToItem(firstUpcoming + headerOffset)
        }
    }

    Box(modifier = modifier) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 60.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.ime)
        ) {
            if (weatherData != null) {
                item(key = "weather_header") {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = cardAlignment
                    ) {
                        FloatingWeatherCard(
                            weatherData = weatherData,
                            forecastRange = weatherForecastRange,
                            modifier = cardModifier
                        )
                    }
                }
            }
            if (currentMode == FloatingInputMode.NOTE) {
                items(displayQuickMemos, key = { "quick_memo_${it.id ?: it.hashCode()}" }) { memo ->
                    val memoKey = quickMemoOrderKey(memo)
                    val isDragging = draggingQuickMemoKey == memoKey
                    val dragScale by animateFloatAsState(
                        targetValue = if (isDragging) 1.035f else 1f,
                        animationSpec = spring(dampingRatio = 0.82f, stiffness = 520f),
                        label = "floating_quick_memo_drag_scale"
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (isDragging) Modifier else Modifier.animateItem())
                            .zIndex(if (isDragging) 1f else 0f)
                            .offset { IntOffset(0, if (isDragging) draggedQuickMemoOffsetY.roundToInt() else 0) }
                            .scale(dragScale)
                            .pointerInput(memoKey, displayQuickMemos.size) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        haptics.longPress()
                                        draggingQuickMemoKey = memoKey
                                        draggedQuickMemoOffsetY = 0f
                                    },
                                    onDragEnd = {
                                        onReorderQuickMemos(currentQuickMemoIdsInOrder())
                                        draggingQuickMemoKey = null
                                        draggedQuickMemoOffsetY = 0f
                                    },
                                    onDragCancel = {
                                        onReorderQuickMemos(currentQuickMemoIdsInOrder())
                                        draggingQuickMemoKey = null
                                        draggedQuickMemoOffsetY = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        if (quickMemoOrder.size <= 1 || draggingQuickMemoKey != memoKey) return@detectDragGesturesAfterLongPress
                                        draggedQuickMemoOffsetY += dragAmount.y
                                        val currentIndex = quickMemoOrder.indexOf(memoKey)
                                        if (currentIndex == -1) return@detectDragGesturesAfterLongPress
                                        val moveBy = (draggedQuickMemoOffsetY / quickMemoDragStepPx).roundToInt()
                                        if (moveBy == 0) return@detectDragGesturesAfterLongPress
                                        val targetIndex = (currentIndex + moveBy).coerceIn(0, quickMemoOrder.lastIndex)
                                        if (targetIndex != currentIndex) {
                                            quickMemoOrder.removeAt(currentIndex)
                                            quickMemoOrder.add(targetIndex, memoKey)
                                            draggedQuickMemoOffsetY -= (targetIndex - currentIndex) * quickMemoDragStepPx
                                        }
                                    }
                                )
                            },
                        contentAlignment = cardAlignment
                    ) {
                        FloatingQuickMemoCard(
                            memo = memo,
                            audioPlaybackState = audioPlaybackState,
                            modifier = cardModifier,
                            onMarkTodo = { onMarkQuickMemoTodo(memo) },
                            onRemoveTodo = { onRemoveQuickMemoTodo(memo) },
                            onToggleTodo = { onToggleQuickMemoTodo(memo) },
                            onDelete = { onDeleteQuickMemo(memo) {} },
                            onSave = { body -> onSaveQuickMemo(memo, body) {} },
                            onToggleAudioPlayback = onToggleAudioPlayback,
                            expandFromLeft = expandFromLeft,
                            hapticEnabled = hapticEnabled
                        )
                    }
                }
            } else {
            items(sortedScheduleItems, key = { it.stableKey }) { item ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = cardAlignment
                ) {
                    ScheduleCard(
                        item = item,
                        hasPendingStatus = item.stableKey in pendingStatusKeys,
                        listState = listState,
                        modifier = cardModifier,
                        expandFromLeft = expandFromLeft,
                            onUpdateScheduleItem = onUpdateScheduleItem,
                            onArchiveScheduleItem = onArchiveScheduleItem,
                            onStatusAction = onStatusAction,
                            onRequestDatePicker = onRequestDatePicker,
                            onRequestTimePicker = onRequestTimePicker,
                            hapticEnabled = hapticEnabled
                        )
                }
            }
            }
        }
    }
}

@Composable
private fun FloatingQuickMemoCard(
    memo: QuickMemoEntity,
    audioPlaybackState: AudioPlaybackState = AudioPlaybackState(),
    modifier: Modifier = Modifier,
    onMarkTodo: () -> Unit,
    onRemoveTodo: () -> Unit,
    onToggleTodo: () -> Unit,
    onDelete: () -> Unit,
    onSave: (String) -> Unit,
    onToggleAudioPlayback: (String?) -> Unit = {},
    expandFromLeft: Boolean = false,
    hapticEnabled: Boolean = true
) {
    val haptics = rememberAppHaptics(hapticEnabled)
    var isExpanded by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }
    var draftBody by remember(memo.id, memo.updatedAt) { mutableStateOf(memo.bodyText) }
    val isTodo = memo.todoState == QuickMemoTodoState.ACTIVE || memo.todoState == QuickMemoTodoState.COMPLETED
    val isCompleted = memo.todoState == QuickMemoTodoState.COMPLETED
    val isVoice = memo.type == QuickMemoType.VOICE
    val isPlaying = memo.audioPath != null && audioPlaybackState.audioPath == memo.audioPath && audioPlaybackState.isPlaying
    val displayBody = memo.bodyText.ifBlank { floatingQuickMemoFallbackText(memo) }
    val todoAccentColor = Color(0xFFF2B705)
    val accentColor = when {
        isCompleted -> MaterialTheme.colorScheme.outlineVariant
        isTodo -> todoAccentColor
        else -> MaterialTheme.colorScheme.primary
    }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val actionButtonSize = 46.dp
    val actionButtonSpacing = 10.dp
    val actionAreaSidePadding = 14.dp
    val cardToButtonGap = 12.dp
    val actionDirection = if (expandFromLeft) 1f else -1f
    val deleteDirection = -actionDirection
    val actionButtonCount = 2
    val actionAreaWidthPx = with(density) {
        (actionAreaSidePadding + (actionButtonSize * actionButtonCount) + (actionButtonSpacing * (actionButtonCount - 1)) + cardToButtonGap).toPx()
    }
    val revealOffsetPx = actionDirection * actionAreaWidthPx
    val revealSnapThresholdPx = actionAreaWidthPx * 0.35f
    val fullSwipeTriggerPx = with(density) { 150.dp.toPx() }
    val deleteTriggerPx = with(density) { 110.dp.toPx() }
    val screenWidthPx = with(density) { 400.dp.toPx() }
    val dragLimitPx = actionDirection * with(density) { 190.dp.toPx() }
    val swipeSpringSpec = spring<Float>(dampingRatio = 0.85f, stiffness = 600f)

    @Composable
    fun TodoActionButton() {
        Surface(
            modifier = Modifier.size(actionButtonSize),
            shape = CircleShape,
            color = todoAccentColor.copy(alpha = 0.92f),
            onClick = {
                scope.launch {
                    haptics.confirm()
                    if (isTodo) onToggleTodo() else onMarkTodo()
                    offsetX.animateTo(0f, swipeSpringSpec)
                }
            }
        ) {
            Box(contentAlignment = Alignment.Center) {
                val actionIcon = if (isCompleted) Icons.Rounded.Undo else Icons.Rounded.CheckCircle
                Icon(
                    actionIcon,
                    when {
                        !isTodo -> "转为待办"
                        isCompleted -> "撤回待办"
                        else -> "完成待办"
                    },
                    Modifier.size(22.dp),
                    tint = Color.White
                )
            }
        }
    }

    @Composable
    fun EditActionButton() {
        Surface(
            modifier = Modifier.size(actionButtonSize),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            onClick = {
                haptics.click()
                draftBody = memo.bodyText
                isExpanded = true
                isEditing = true
                scope.launch { offsetX.animateTo(0f, swipeSpringSpec) }
            }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Edit, "编辑", Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }

    Box(modifier = modifier) {
        if (offsetX.value * actionDirection > 1f) {
            val revealProgress = ((offsetX.value * actionDirection) / actionAreaWidthPx).coerceIn(0f, 1f)
            Box(
                modifier = if (expandFromLeft) Modifier.matchParentSize().padding(start = actionAreaSidePadding) else Modifier.matchParentSize().padding(end = actionAreaSidePadding),
                contentAlignment = if (expandFromLeft) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(actionButtonSpacing),
                    modifier = Modifier.alpha(revealProgress)
                ) {
                    if (expandFromLeft) {
                        EditActionButton()
                        TodoActionButton()
                    } else {
                        TodoActionButton()
                        EditActionButton()
                    }
                }
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .then(if (!isEditing) Modifier.pointerInput(memo.id, memo.todoState) {
                detectHorizontalDragGestures(
                    onDragStart = { scope.launch { offsetX.stop() } },
                    onDragEnd = {
                        val fullSwipeAction = offsetX.value * actionDirection >= fullSwipeTriggerPx
                        val shouldRevealAction = offsetX.value * actionDirection >= revealSnapThresholdPx
                        val fullSwipeDelete = offsetX.value * deleteDirection >= deleteTriggerPx
                        scope.launch {
                            when {
                                fullSwipeAction -> {
                                    haptics.confirm()
                                    if (isTodo) onToggleTodo() else onMarkTodo()
                                    offsetX.animateTo(0f, swipeSpringSpec)
                                }
                                fullSwipeDelete -> {
                                    haptics.warning()
                                    offsetX.animateTo(deleteDirection * screenWidthPx, tween(200))
                                    onDelete()
                                }
                                shouldRevealAction -> offsetX.animateTo(revealOffsetPx, swipeSpringSpec)
                                else -> offsetX.animateTo(0f, swipeSpringSpec)
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            if (offsetX.value * actionDirection >= revealSnapThresholdPx) offsetX.animateTo(revealOffsetPx, swipeSpringSpec)
                            else offsetX.animateTo(0f, swipeSpringSpec)
                        }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            val current = offsetX.value
                            val draggingAction = dragAmount * actionDirection > 0f
                            val draggingDelete = dragAmount * deleteDirection > 0f
                            val resistance = when {
                                draggingAction && current * actionDirection >= fullSwipeTriggerPx -> 0.25f
                                draggingAction && current * actionDirection >= actionAreaWidthPx -> 0.45f
                                draggingDelete && current * deleteDirection >= deleteTriggerPx -> 0.95f
                                else -> 0.85f
                            }
                            val next = current + dragAmount * resistance
                            offsetX.snapTo(if (expandFromLeft) next.coerceAtMost(dragLimitPx) else next.coerceAtLeast(dragLimitPx))
                        }
                    }
                )
            } else Modifier)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    haptics.click()
                    scope.launch {
                        if (offsetX.value * actionDirection > 10f) offsetX.animateTo(0f, swipeSpringSpec)
                        else if (!isEditing) isExpanded = !isExpanded
                    }
            },
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp
        ) {
            if (!isEditing) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    FloatingQuickMemoCompactContent(
                        accentColor = accentColor,
                        text = displayBody,
                        isCompleted = isCompleted,
                        isVoice = isVoice,
                        isPlaying = isPlaying,
                        onContentClick = {
                            haptics.click()
                            isExpanded = !isExpanded
                        },
                        onPlayClick = {
                            haptics.click()
                            onToggleAudioPlayback(memo.audioPath)
                        }
                    )
                    AnimatedVisibility(
                        visible = isExpanded,
                        enter = fadeIn(tween(120)) + expandVertically(tween(180), expandFrom = Alignment.Top),
                        exit = fadeOut(tween(90)) + shrinkVertically(tween(160), shrinkTowards = Alignment.Top)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                        ) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 1.dp)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = displayBody,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                                textDecoration = if (isCompleted) TextDecoration.LineThrough else null
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(48.dp)
                            .padding(vertical = 8.dp)
                            .background(accentColor, RoundedCornerShape(2.dp))
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                    AnimatedVisibility(
                        visible = isExpanded,
                        enter = fadeIn(tween(120)) + expandVertically(tween(180), expandFrom = Alignment.Top),
                        exit = fadeOut(tween(90)) + shrinkVertically(tween(160), shrinkTowards = Alignment.Top)
                    ) {
                        Column(modifier = Modifier.padding(bottom = 12.dp)) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 1.dp)
                            Spacer(Modifier.height(8.dp))
                            AnimatedContent(targetState = isEditing, label = "quick_memo_edit_transition") { editing ->
                                if (editing) {
                                    Column {
                                        CompactTextField(value = draftBody, onValueChange = { draftBody = it }, placeholder = "正文", singleLine = false, maxLines = 6)
                                        Spacer(Modifier.height(8.dp))
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                                            if (isTodo) {
                                                FloatingCompactTextButton(
                                                    text = "移除代办",
                                                    onClick = {
                                                        haptics.confirm()
                                                        onRemoveTodo()
                                                        isEditing = false
                                                    }
                                                )
                                                Spacer(Modifier.width(8.dp))
                                            }
                                            FloatingCompactTextButton(
                                                text = "取消",
                                                onClick = { draftBody = memo.bodyText; isEditing = false }
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            FloatingCompactPrimaryButton(
                                                text = "保存",
                                                onClick = { haptics.confirm(); onSave(draftBody); isEditing = false }
                                            )
                                        }
                                    }
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = displayBody,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 4,
                                            overflow = TextOverflow.Ellipsis,
                                            textDecoration = if (isCompleted) TextDecoration.LineThrough else null
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (!isExpanded) Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingQuickMemoCompactContent(
    accentColor: Color,
    text: String,
    isCompleted: Boolean,
    isVoice: Boolean,
    isPlaying: Boolean,
    onContentClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    val contentColor = if (isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    val waveformColor = if (isCompleted) MaterialTheme.colorScheme.outline else accentColor
    val playContainerColor = if (isCompleted) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.86f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    }
    val playIconColor = if (isCompleted) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(48.dp)
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accentColor)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(if (isVoice) 48.dp else 40.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onContentClick
                ),
            contentAlignment = Alignment.CenterStart
        ) {
            if (isVoice) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = contentColor,
                        textDecoration = if (isCompleted) TextDecoration.LineThrough else null,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                    FloatingSiriWaveform(
                        isPlaying = isPlaying,
                        modifier = Modifier.fillMaxWidth().height(18.dp),
                        color = waveformColor
                    )
                }
            } else {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = contentColor,
                    textDecoration = if (isCompleted) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (isVoice) {
            FloatingVoicePlayButton(
                isPlaying = isPlaying,
                onClick = onPlayClick,
                buttonSize = 40.dp,
                iconSize = 20.dp,
                containerColor = playContainerColor,
                iconColor = playIconColor
            )
        }
    }
}

@Composable
private fun VoiceCaptureContent(
    state: QuickMemoVoiceCaptureState,
    activeColor: Color,
    onConfirm: (Boolean) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when (state.status) {
            QuickMemoVoiceCaptureStatus.RECORDING -> {
                FloatingSiriWaveform(isPlaying = true, modifier = Modifier.weight(1f), color = activeColor)
                Surface(
                    onClick = onStop,
                    shape = CircleShape,
                    color = activeColor,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = "停止录音", tint = Color.White, modifier = Modifier.size(21.dp))
                    }
                }
            }
            QuickMemoVoiceCaptureStatus.CONFIRMING -> {
                FloatingVoiceStatusLine(
                    text = "保存中...",
                    color = activeColor,
                    leading = { CircularProgressIndicator(modifier = Modifier.size(17.dp), strokeWidth = 2.dp, color = activeColor) }
                )
            }
            QuickMemoVoiceCaptureStatus.SAVING -> {
                FloatingVoiceStatusLine(
                    text = "保存中...",
                    color = activeColor,
                    leading = { CircularProgressIndicator(modifier = Modifier.size(17.dp), strokeWidth = 2.dp, color = activeColor) }
                )
            }
            QuickMemoVoiceCaptureStatus.SAVED -> {
                FloatingVoiceStatusLine(
                    text = "已保存到随口记",
                    color = activeColor,
                    leading = { Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = activeColor, modifier = Modifier.size(18.dp)) }
                )
            }
            QuickMemoVoiceCaptureStatus.TOO_SHORT -> {
                FloatingVoiceStatusLine(
                    text = "录音太短",
                    color = FloatingActionWarningIcon,
                    leading = {
                        Icon(
                            imageVector = Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = FloatingActionWarningIcon,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
            QuickMemoVoiceCaptureStatus.ERROR -> {
                FloatingVoiceStatusLine(
                    text = state.message.ifBlank { "录音失败" },
                    color = MaterialTheme.colorScheme.error,
                    leading = {
                        Icon(
                            imageVector = Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
            QuickMemoVoiceCaptureStatus.IDLE -> Unit
        }
    }
}

@Composable
private fun RowScope.FloatingVoiceStatusLine(
    text: String,
    color: Color,
    leading: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .weight(1f)
            .height(40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            leading()
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun FloatingWaveform(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val transition = rememberInfiniteTransition(label = "floating_waveform")
    Row(
        modifier = modifier.height(28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(13) { index ->
            val phase by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 560 + index * 22),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "wave_$index"
            )
            val height = (8 + ((index % 5) + 1) * 3).dp * phase.coerceIn(0.35f, 1f)
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(height)
                    .clip(RoundedCornerShape(999.dp))
                    .background(color.copy(alpha = 0.42f + 0.38f * phase))
            )
        }
    }
}

@Composable
private fun FloatingVoiceMemoPlayerContent(
    accentColor: Color,
    isPlaying: Boolean,
    onWaveClick: () -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(48.dp)
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accentColor)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(34.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onWaveClick
                )
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            FloatingSiriWaveform(
                isPlaying = isPlaying,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary
            )
        }
        FloatingVoicePlayButton(
            isPlaying = isPlaying,
            onClick = onPlayClick,
            buttonSize = 40.dp,
            iconSize = 20.dp
        )
    }
}

@Composable
private fun FloatingSiriWaveform(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val transition = rememberInfiniteTransition(label = "floating_siri_waveform")
    val timePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "floating_siri_wave_phase"
    )
    val amplitudeFactor by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.15f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "floating_siri_wave_amplitude"
    )
    val paths = remember { List(3) { Path() } }

    Canvas(modifier = modifier.height(32.dp)) {
        val width = size.width
        val centerY = size.height / 2f
        val maxAmplitude = size.height / 2f * 0.85f
        val step = (width / 120f).coerceAtLeast(1f)
        val waveParams = listOf(
            FloatingWaveParam(1.5f, 1.0f, 1.0f, 0.88f, 2.4.dp.toPx()),
            FloatingWaveParam(2.2f, -1.2f, 0.7f, 0.46f, 1.9.dp.toPx()),
            FloatingWaveParam(3.0f, 0.8f, 0.4f, 0.28f, 1.4.dp.toPx())
        )

        waveParams.forEachIndexed { index, param ->
            val path = paths[index]
            path.reset()
            path.moveTo(0f, centerY)

            var x = 0f
            while (x <= width) {
                val progress = x / width
                val envelope = sin(progress * PI).pow(2).toFloat()
                val waveX = progress * PI.toFloat() * 2f * param.frequency
                val currentPhase = timePhase * param.speedMultiplier
                val sineValue = sin(waveX + currentPhase)
                val y = centerY + sineValue * maxAmplitude * param.amplitudeWeight * amplitudeFactor * envelope

                path.lineTo(x, y)
                x += step
            }

            drawPath(
                path = path,
                color = color.copy(alpha = param.alpha),
                style = Stroke(
                    width = param.strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

private data class FloatingWaveParam(
    val frequency: Float,
    val speedMultiplier: Float,
    val amplitudeWeight: Float,
    val alpha: Float,
    val strokeWidth: Float
)

@Composable
private fun FloatingVoicePlayButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 34.dp,
    iconSize: Dp = 18.dp,
    containerColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
    iconColor: Color = MaterialTheme.colorScheme.primary
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = containerColor,
        modifier = modifier.size(buttonSize)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "暂停语音" else "播放语音",
                tint = iconColor,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Composable
private fun FloatingQuickMemoTodoLabel(done: Boolean) {
    Text(
        text = if (done) "已完成" else "待办",
        modifier = Modifier
            .background(Color(0xFFF2B705).copy(alpha = 0.18f), RoundedCornerShape(999.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
        style = MaterialTheme.typography.labelSmall,
        color = Color(0xFF8A6200),
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun FloatingQuickMemoDefaultLabel() {
    Text(
        text = "随口记",
        modifier = Modifier
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), RoundedCornerShape(999.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun FloatingQuickMemoTodoButton(checked: Boolean, onClick: () -> Unit) {
    val accent = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFFF2B705)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = if (checked) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.86f) else Color(0xFFF2B705).copy(alpha = 0.18f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = accent
            )
            Text(
                text = if (checked) "撤回" else "完成",
                style = MaterialTheme.typography.labelSmall,
                color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF8A6200),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun FloatingQuickMemoTodoMark(checked: Boolean, onClick: () -> Unit) {
    val accent = if (checked) Color.Gray else Color(0xFFF2B705)
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier = Modifier
            .width(24.dp)
            .height(24.dp)
            .clickable { onClick() },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .background(if (checked) accent.copy(alpha = 0.14f) else Color.Transparent, shape)
                .border(1.dp, accent.copy(alpha = if (checked) 0.52f else 0.36f), shape),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
            }
        }
    }
}

private fun floatingQuickMemoFallbackText(memo: QuickMemoEntity): String {
    return when {
        memo.type == QuickMemoType.IMAGE -> "图片随口记"
        memo.type != QuickMemoType.VOICE -> "空白随口记"
        memo.transcriptionStatus == QuickMemoTranscriptionStatus.PENDING -> "转写中"
        memo.transcriptionStatus == QuickMemoTranscriptionStatus.PROCESSING -> "转写中"
        memo.transcriptionStatus == QuickMemoTranscriptionStatus.FAILED -> "转写失败，可重试"
        else -> "仅音频"
    }
}

private fun floatingQuickMemoStatusText(memo: QuickMemoEntity): String {
    return when {
        memo.type == QuickMemoType.IMAGE -> "图片随口记"
        memo.type != QuickMemoType.VOICE -> "随口记"
        memo.transcriptionStatus == QuickMemoTranscriptionStatus.PENDING -> "转写中"
        memo.transcriptionStatus == QuickMemoTranscriptionStatus.PROCESSING -> "转写中"
        memo.transcriptionStatus == QuickMemoTranscriptionStatus.FAILED -> "转写失败"
        memo.transcriptionStatus == QuickMemoTranscriptionStatus.SUCCESS -> "已完成"
        else -> "仅音频"
    }
}

@Composable
private fun FloatingWeatherCard(
    weatherData: WeatherData,
    forecastRange: Int,
    modifier: Modifier = Modifier
) {
    val now = remember { LocalDateTime.now() }
    var isExpanded by remember { mutableStateOf(false) }
    val haptics = rememberAppHaptics()
    val forecastMode = remember(forecastRange) { FloatingWeatherForecastMode.fromValue(forecastRange) }
    val weekText = remember(now) {
        now.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.CHINESE)
    }
    Surface(
        modifier = modifier.clickable { haptics.click(); isExpanded = !isExpanded },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(WeatherIconMapper.iconRes(weatherData)),
                    contentDescription = weatherData.text.ifBlank { "天气" },
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = weatherData.text.ifBlank { "天气" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${weatherData.temperature.ifBlank { "--" }}°C",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                val locationName = weatherData.displayLocationName(short = true)
                if (locationName.isNotBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = locationName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }
            Text(
                text = "${now.dayOfMonth}号 $weekText · ${weatherData.windDir.ifBlank { "--" }}${weatherData.windScale.ifBlank { "--" }}级 · 湿度 ${weatherData.humidity.ifBlank { "--" }}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                AnimatedContent(
                    targetState = forecastMode,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "floating_weather_forecast_content"
                ) { mode ->
                    when (mode) {
                        FloatingWeatherForecastMode.Hourly24 -> FloatingHourlyForecastRow(weatherData.hourlyForecast.take(24))
                        FloatingWeatherForecastMode.Daily3 -> FloatingDailyForecastRow(weatherData.dailyForecast.take(3))
                        FloatingWeatherForecastMode.Daily5 -> FloatingDailyForecastRow(weatherData.dailyForecast.take(5))
                    }
                }
            }
        }
    }
}

private enum class FloatingWeatherForecastMode(
    val value: Int,
    val label: String
) {
    Hourly24(0, "24小时"),
    Daily3(1, "未来3天"),
    Daily5(2, "未来5天");

    companion object {
        fun fromValue(value: Int): FloatingWeatherForecastMode {
            return entries.firstOrNull { it.value == value } ?: Hourly24
        }
    }
}

@Composable
private fun FloatingHourlyForecastRow(hours: List<WeatherHourlyForecast>) {
    if (hours.isEmpty()) {
        FloatingWeatherEmptyText("暂无24小时预报")
        return
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(hours) { hour ->
            FloatingHourlyWeather(hour)
        }
    }
}

@Composable
private fun FloatingDailyForecastRow(days: List<WeatherDailyForecast>) {
    if (days.isEmpty()) {
        FloatingWeatherEmptyText("暂无未来天气预报")
        return
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(days) { day ->
            FloatingDailyWeather(day)
        }
    }
}

@Composable
private fun FloatingWeatherEmptyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun FloatingHourlyWeather(hour: WeatherHourlyForecast) {
    Column(
        modifier = Modifier
            .width(58.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = formatWeatherHour(hour.fxTime),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Icon(
            painter = painterResource(WeatherForecastIconMapper.iconRes(hour.text, hour.icon)),
            contentDescription = hour.text,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "${hour.temp.ifBlank { "--" }}°",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun FloatingDailyWeather(day: WeatherDailyForecast) {
    Column(
        modifier = Modifier
            .width(74.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = formatWeatherDay(day.fxDate),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Icon(
            painter = painterResource(WeatherForecastIconMapper.iconRes(day.textDay, day.iconDay)),
            contentDescription = day.textDay,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = compactFloatingDayWeather(day),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${day.tempMin.ifBlank { "--" }}°/${day.tempMax.ifBlank { "--" }}°",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1
        )
    }
}

private fun formatWeatherHour(value: String): String {
    return runCatching { OffsetDateTime.parse(value).format(DateTimeFormatter.ofPattern("HH")) }.getOrDefault("--")
}

private fun formatWeatherDay(value: String): String {
    return runCatching {
        val date = LocalDate.parse(value)
        date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.CHINESE)
    }.getOrDefault("--")
}

private fun compactFloatingDayWeather(day: WeatherDailyForecast): String {
    val dayText = day.textDay.ifBlank { "--" }
    val nightText = day.textNight.ifBlank { "" }
    return if (nightText.isBlank() || dayText == nightText) dayText else "${dayText}转$nightText"
}

@Composable
private fun FloatingCompactTextButton(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = Color.Transparent
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun FloatingCompactPrimaryButton(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.primary
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun CompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    maxLines: Int = 1,
    enabled: Boolean = true
) {
    var isFocused by remember { mutableStateOf(false) }
    var fieldValue by remember { mutableStateOf(TextFieldValue(value, selection = TextRange(value.length))) }

    LaunchedEffect(value) {
        if (value != fieldValue.text) {
            fieldValue = TextFieldValue(value, selection = TextRange(value.length))
        }
    }

    // 聚焦时边框高亮变色，保持和原生一样的交互体验
    val borderColor = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val borderWidth = if (isFocused) 1.5.dp else 1.dp

    BasicTextField(
        value = fieldValue,
        onValueChange = { next ->
            fieldValue = next
            if (next.text != value) onValueChange(next.text)
        },
        singleLine = singleLine,
        maxLines = maxLines,
        enabled = enabled,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(borderWidth, borderColor, RoundedCornerShape(8.dp))
                    .background(if (enabled) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp), // 极小的内边距
                contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart
            ) {
                if (fieldValue.text.isEmpty()) {
                    Text(placeholder, style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)))
                }
                innerTextField()
            }
        },
        // 这里去掉了全路径，直接使用 onFocusChanged
        modifier = modifier.onFocusChanged { isFocused = it.isFocused }
    )
}

private sealed class FloatingPickerRequest {
    data class Date(val initialDate: LocalDate, val onConfirm: (LocalDate) -> Unit) : FloatingPickerRequest()
    data class Time(val initialTime: String, val onConfirm: (String) -> Unit) : FloatingPickerRequest()
}

@Composable
private fun FloatingPickerOverlay(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 带动画的关闭函数
    val animateDismiss = {
        scope.launch {
            isVisible = false
            delay(250) // 等待退出动画播完
            onDismiss()
        }
    }

    // 带动画的确认函数
    val animateConfirm = {
        scope.launch {
            isVisible = false
            delay(250)
            onConfirm()
        }
    }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    // 动画曲线
    val fastOutSlowIn = remember { CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f) }
    val enterDuration = 280
    val exitDuration = 200

    // 【核心魔法】：获取当前屏幕密度，并创建一个缩小到 80% 的新密度
    val currentDensity = LocalDensity.current
    val customDensity = remember(currentDensity) {
        Density(
            density = currentDensity.density * 0.8f,     // 间距、高度缩小 20%
            fontScale = currentDensity.fontScale * 1.05f  // 字体缩小 20%
        )
    }

    // 背景透明度动画
    val bgAlpha by animateFloatAsState(
        targetValue = if (isVisible) 0.4f else 0f,
        animationSpec = tween(
            durationMillis = if (isVisible) enterDuration else exitDuration,
            easing = androidx.compose.animation.core.LinearEasing
        ),
        label = "picker_bg_dim"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = bgAlpha))
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    if (isVisible) animateDismiss()
                })
            },
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = scaleIn(
                initialScale = 0.9f,
                animationSpec = tween(enterDuration, easing = fastOutSlowIn)
            ) + fadeIn(animationSpec = tween(enterDuration)),
            exit = scaleOut(
                targetScale = 0.9f,
                animationSpec = tween(exitDuration, easing = fastOutSlowIn)
            ) + fadeOut(animationSpec = tween(exitDuration))
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp), // 圆角稍微收敛，显得更干练
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .padding(horizontal = 48.dp) // 左右边距加大，让弹窗变窄，像 iOS 风格的小组件
                    .fillMaxWidth()
                    .pointerInput(Unit) { detectTapGestures(onTap = { }) }
            ) {
                Column(
                    modifier = Modifier.padding(top = 20.dp, bottom = 12.dp, start = 16.dp, end = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 【高度压制】：把整体高度压回 170dp
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // 让里面的内容使用缩小的密度进行渲染，完美解决重叠问题！
                        CompositionLocalProvider(LocalDensity provides customDensity) {
                            content()
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // 底部按钮也做得稍微紧凑一点，呼应整体的精致感
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { animateDismiss() },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier
                                .defaultMinSize(minWidth = 1.dp, minHeight = 1.dp)
                                .height(32.dp)
                        ) {
                            Text("取消", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.width(4.dp))
                        Button(
                            onClick = { animateConfirm() },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier
                                .defaultMinSize(minWidth = 1.dp, minHeight = 1.dp)
                                .height(32.dp)
                        ) {
                            Text("确定", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingDatePickerOverlay(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit
) {
    var selectedDate by remember(initialDate) { mutableStateOf(initialDate) }
    FloatingPickerOverlay(
        onDismiss = onDismiss,
        onConfirm = { onConfirm(selectedDate) }
    ) {
        WheelDatePicker(initialDate) { selectedDate = it }
    }
}

@Composable
private fun FloatingTimePickerOverlay(
    initialTime: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val (initialHour, initialMinute) = remember(initialTime) {
        val parts = initialTime.split(":")
        val hour = parts.getOrElse(0) { "09" }.toIntOrNull()?.coerceIn(0, 23) ?: 9
        val minute = parts.getOrElse(1) { "00" }.toIntOrNull()?.coerceIn(0, 59) ?: 0
        hour to minute
    }
    var selectedHour by remember(initialTime) { mutableStateOf(initialHour) }
    var selectedMinute by remember(initialTime) { mutableStateOf(initialMinute) }

    FloatingPickerOverlay(
        onDismiss = onDismiss,
        onConfirm = { onConfirm(String.format("%02d:%02d", selectedHour, selectedMinute)) }
    ) {
        WheelTimePicker(initialHour, initialMinute) { hh, mm ->
            selectedHour = hh
            selectedMinute = mm
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun ScheduleCard(
    item: ScheduleDisplayItem,
    hasPendingStatus: Boolean,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    expandFromLeft: Boolean = false,
    onUpdateScheduleItem: (ScheduleDisplayItem, EventPatch, () -> Unit) -> Unit = { _, _, onComplete -> onComplete() },
    onArchiveScheduleItem: (ScheduleDisplayItem) -> Unit = {},
    onStatusAction: (ScheduleDisplayItem) -> Unit = {},
    onRequestDatePicker: (LocalDate, (LocalDate) -> Unit) -> Unit = { _, _ -> },
    onRequestTimePicker: (String, (String) -> Unit) -> Unit = { _, _ -> },
    hapticEnabled: Boolean = true
) {
    var isExpanded by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val canEdit = remember(item.tag) {
        item.tag != EventTags.COURSE
    }
    val renderEvent = remember(item) { item.toRenderEvent() }

    var isEditing by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    var draftTitle by remember { mutableStateOf(item.title) }
    var draftStartDate by remember { mutableStateOf(item.startDate) }
    var draftStartTime by remember { mutableStateOf(item.startTime) }
    var draftEndDate by remember { mutableStateOf(item.endDate) }
    var draftEndTime by remember { mutableStateOf(item.endTime) }
    var draftLocation by remember { mutableStateOf(item.location) }
    var draftDescription by remember { mutableStateOf(stripSourceImageMarkers(item.description)) }

    LaunchedEffect(item.stableKey, item.title, item.startTS, item.endTS, item.location, item.description, item.tag, item.color, item.state) {
        if (!isEditing && !isSaving) {
            draftTitle = item.title
            draftStartDate = item.startDate
            draftStartTime = item.startTime
            draftEndDate = item.endDate
            draftEndTime = item.endTime
            draftLocation = item.location
            draftDescription = stripSourceImageMarkers(item.description)
        }
    }

    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = rememberAppHaptics(hapticEnabled)

    val titleBringIntoViewRequester = remember { BringIntoViewRequester() }
    val locationBringIntoViewRequester = remember { BringIntoViewRequester() }
    val descriptionBringIntoViewRequester = remember { BringIntoViewRequester() }

    var restoreScrollPosition by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    var isTitleFieldFocused by remember { mutableStateOf(false) }
    var isLocationFieldFocused by remember { mutableStateOf(false) }
    var isDescriptionFieldFocused by remember { mutableStateOf(false) }
    val anyEditingFieldFocused = isTitleFieldFocused || isLocationFieldFocused || isDescriptionFieldFocused
    val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    LaunchedEffect(isImeVisible, anyEditingFieldFocused) {
        if (restoreScrollPosition != null && !isImeVisible && !anyEditingFieldFocused) {
            val pos = restoreScrollPosition ?: return@LaunchedEffect
            restoreScrollPosition = null
            try {
                // Let IME hide / layout settle a bit, then slide back.
                delay(120)
                listState.animateScrollToItem(pos.first, pos.second)
            } catch (_: Exception) {
            }
        }
    }

    val now = LocalDateTime.now()
    val startDateTime = remember(item.startTS) { try { LocalDateTime.of(item.startDate, item.startLocalTime) } catch (e: Exception) { LocalDateTime.MIN } }
    val endDateTime = remember(item.endTS) { try { LocalDateTime.of(item.endDate, item.endLocalTime) } catch (e: Exception) { LocalDateTime.MAX } }

    val isExpired = remember(now, endDateTime) { now.isAfter(endDateTime) }
    val isInProgress = remember(now, startDateTime, endDateTime) { !isExpired && now.isAfter(startDateTime) && now.isBefore(endDateTime) }
    val isComingSoon = remember(now, startDateTime, isExpired, isInProgress) {
        if (isExpired || isInProgress) false else Duration.between(now, startDateTime).toMinutes() in 0..30
    }

    val barColor = if (isExpired) MaterialTheme.colorScheme.outlineVariant else item.composeColor
    val titleColor = if (isExpired) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    val contentColor = if (isExpired) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurfaceVariant
    val elevation = if (isInProgress) 6.dp else 2.dp

    val model = remember(renderEvent.title, renderEvent.location, renderEvent.description, renderEvent.tag, renderEvent.state, renderEvent.startTS, renderEvent.endTS) {
        EventTimelinePresenter.present(context, renderEvent).renderModel
    }

    val hasAction = remember(item.tag, isExpired, item.isCompleted, item.isCheckedIn, hasPendingStatus) {
        item.tag != EventTags.COURSE && (
            hasPendingStatus || (!isExpired && !item.isCompleted && !item.isCheckedIn)
        )
    }

    val displayTitle = if (isExpired) item.title else model.title

    val density = LocalDensity.current
    val actionButtonSize = 46.dp
    val actionButtonSpacing = 10.dp
    val actionAreaSidePadding = 14.dp
    val cardToButtonGap = 12.dp
    val actionButtonCount = if (hasAction) 2 else 1

    val actionDirection = if (expandFromLeft) 1f else -1f
    val archiveDirection = -actionDirection
    val actionAreaWidthDp = actionAreaSidePadding + (actionButtonSize * actionButtonCount) + (actionButtonSpacing * (actionButtonCount - 1)) + cardToButtonGap
    val actionAreaWidthPx = with(density) { actionAreaWidthDp.toPx() }
    val revealOffsetPx = actionDirection * actionAreaWidthPx
    val revealSnapThresholdPx = actionAreaWidthPx * 0.35f
    val fullSwipeTriggerPx = with(density) { 150.dp.toPx() }
    val dragLimitPx = with(density) { actionDirection * 190.dp.toPx() }

    // 【归档相关参数】反向滑动触发阈值和飞出距离
    val archiveTriggerPx = with(density) { 110.dp.toPx() }
    val screenWidthPx = with(density) { 400.dp.toPx() }

    val swipeSpringSpec = spring<Float>(dampingRatio = 0.85f, stiffness = 600f)

    val isPastFullSwipe by remember(hasAction, actionDirection, fullSwipeTriggerPx) {
        derivedStateOf { hasAction && offsetX.value * actionDirection >= fullSwipeTriggerPx }
    }
    var hasVibrated by remember { mutableStateOf(false) }

    // 【核心新增】反向滑动（归档）震动状态
    val isPastArchiveSwipe by remember(archiveDirection, archiveTriggerPx) {
        derivedStateOf { offsetX.value * archiveDirection >= archiveTriggerPx }
    }
    var hasVibratedArchive by remember { mutableStateOf(false) }

    LaunchedEffect(isPastFullSwipe, hasAction) {
        if (hasAction && isPastFullSwipe && !hasVibrated) {
            haptics.threshold()
            hasVibrated = true
        } else if (!isPastFullSwipe) {
            hasVibrated = false
        }
    }

    // 【核心新增】归档阈值的震动控制
    LaunchedEffect(isPastArchiveSwipe) {
        if (isPastArchiveSwipe && !hasVibratedArchive) {
            haptics.threshold()
            hasVibratedArchive = true
        } else if (!isPastArchiveSwipe) {
            hasVibratedArchive = false
        }
    }

    val actionIcon = when (model.actionIcon.type) {
        ActionIconType.UNDO -> Icons.Rounded.Undo
        ActionIconType.CHECKIN -> Icons.Rounded.ConfirmationNumber
        ActionIconType.RIDE -> Icons.Rounded.LocalTaxi
        ActionIconType.PICKUP -> Icons.Rounded.ShoppingBag
        ActionIconType.COMPLETE -> Icons.Rounded.CheckCircle
    }
    val effectiveActionIcon = if (hasPendingStatus) Icons.Rounded.Undo else actionIcon
    val actionColor = if (hasPendingStatus) Color(0xFFFFA726) else Color(model.actionIcon.color)

    @Composable
    fun StatusActionButton() {
        if (hasAction) {
            Surface(
                modifier = Modifier.size(actionButtonSize), shape = CircleShape,
                color = if (isPastFullSwipe) actionColor else actionColor.copy(alpha = 0.92f),
                onClick = { scope.launch { haptics.confirm(); onStatusAction(item); offsetX.animateTo(0f, swipeSpringSpec) } }
            ) {
                Box(contentAlignment = Alignment.Center) { Icon(effectiveActionIcon, null, Modifier.size(22.dp), tint = Color.White) }
            }
        }
    }

    @Composable
    fun EditActionButton() {
        Surface(
            modifier = Modifier.size(actionButtonSize), shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            onClick = {
                scope.launch {
                    haptics.click()
                    if (!canEdit) android.widget.Toast.makeText(context, "暂不支持在悬浮窗编辑", android.widget.Toast.LENGTH_SHORT).show()
                    else {
                        draftTitle = item.title
                        draftStartDate = item.startDate; draftStartTime = item.startTime
                        draftEndDate = item.endDate; draftEndTime = item.endTime
                        draftLocation = item.location; draftDescription = stripSourceImageMarkers(item.description)
                        isExpanded = true; isEditing = true
                    }
                    offsetX.animateTo(0f, swipeSpringSpec)
                }
            }
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Edit, "编辑", Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onPrimary) }
        }
    }

    Box(modifier = modifier) {
        // 背景滑动按钮
        if (offsetX.value * actionDirection > 1f) {
            val revealProgress = ((offsetX.value * actionDirection) / actionAreaWidthPx).coerceIn(0f, 1f)
            Box(
                modifier = if (expandFromLeft) {
                    Modifier.matchParentSize().padding(start = actionAreaSidePadding)
                } else {
                    Modifier.matchParentSize().padding(end = actionAreaSidePadding)
                },
                contentAlignment = if (expandFromLeft) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(actionButtonSpacing),
                    modifier = Modifier.alpha(revealProgress)
                ) {
                    if (expandFromLeft) {
                        EditActionButton()
                        StatusActionButton()
                    } else {
                        StatusActionButton()
                        EditActionButton()
                    }
                }
            }
        }

        // 前景卡片
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .then(
                    if (!isEditing && !isSaving) {
                        Modifier.pointerInput(item.stableKey, hasAction, item.state, hasPendingStatus) {
                            detectHorizontalDragGestures(
                                onDragStart = { scope.launch { offsetX.stop() } },
                                onDragEnd = {
                                    val fullSwipeAction = hasAction && offsetX.value * actionDirection >= fullSwipeTriggerPx
                                    val shouldRevealAction = offsetX.value * actionDirection >= revealSnapThresholdPx
                                    val fullSwipeArchive = offsetX.value * archiveDirection >= archiveTriggerPx

                                    scope.launch {
                                        if (fullSwipeAction) {
                                            haptics.confirm()
                                            onStatusAction(item)
                                            offsetX.animateTo(0f, swipeSpringSpec)
                                        } else if (fullSwipeArchive) {
                                            haptics.warning()
                                            // 【核心】触发归档飞出动画，然后调用更新（配合 animateItemPlacement 实现缝隙弥合）
                                            offsetX.animateTo(
                                                targetValue = archiveDirection * screenWidthPx,
                                                animationSpec = tween(durationMillis = 200)
                                            )
                                            onArchiveScheduleItem(item)
                                        } else if (shouldRevealAction) {
                                            offsetX.animateTo(revealOffsetPx, swipeSpringSpec)
                                        } else {
                                            offsetX.animateTo(0f, swipeSpringSpec)
                                        }
                                    }
                                },
                                onDragCancel = {
                                    scope.launch {
                                        if (offsetX.value * actionDirection >= revealSnapThresholdPx) offsetX.animateTo(revealOffsetPx, swipeSpringSpec)
                                        else offsetX.animateTo(0f, swipeSpringSpec)
                                    }
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    scope.launch {
                                        val current = offsetX.value
                                        val draggingAction = dragAmount * actionDirection > 0f
                                        val draggingArchive = dragAmount * archiveDirection > 0f
                                        // 【阻尼感调校】滑过阈值后调整阻力，鼓励反向归档直接飞出去
                                        val resistance = when {
                                            draggingAction && current * actionDirection >= fullSwipeTriggerPx -> 0.25f
                                            draggingAction && current * actionDirection >= actionAreaWidthPx -> 0.45f
                                            draggingArchive && current * archiveDirection >= archiveTriggerPx -> 0.95f
                                            else -> 0.85f
                                        }
                                        val next = current + (dragAmount * resistance)
                                        offsetX.snapTo(if (expandFromLeft) next.coerceAtMost(dragLimitPx) else next.coerceAtLeast(dragLimitPx))
                                    }
                                }
                            )
                        }
                    } else Modifier
                ),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = elevation,
            onClick = {
                haptics.click()
                scope.launch {
                    if (offsetX.value * actionDirection > 10f) offsetX.animateTo(0f, swipeSpringSpec)
                    else if (!isEditing && !isSaving) isExpanded = !isExpanded
                }
            }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.width(4.dp).height(48.dp).padding(vertical = 8.dp).background(barColor, RoundedCornerShape(2.dp)))
                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = displayTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isInProgress) FontWeight.Bold else FontWeight.Medium,
                            color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        )
                        when {
                            model.statusLabel != null -> StatusLabel(
                                model.statusLabel,
                                when (model.statusColor) {
                                    StatusColor.PRIMARY -> MaterialTheme.colorScheme.primary
                                    StatusColor.SUCCESS -> Color(0xFF4CAF50)
                                    StatusColor.WARNING -> Color(0xFFFF9800)
                                    StatusColor.MUTED -> MaterialTheme.colorScheme.outline
                                },
                                when (model.statusColor) {
                                    StatusColor.PRIMARY -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                    StatusColor.SUCCESS -> Color(0xFF4CAF50).copy(alpha = 0.2f)
                                    StatusColor.WARNING -> Color(0xFFFF9800).copy(alpha = 0.2f)
                                    StatusColor.MUTED -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                }
                            )
                        }
                    }
                    Row(modifier = Modifier.padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CalendarToday, null, Modifier.size(12.dp), contentColor)
                        Spacer(Modifier.width(4.dp))
                        Text(text = item.startDate.format(DateTimeFormatter.ofPattern("MM-dd")), style = MaterialTheme.typography.bodySmall, color = contentColor)
                        Spacer(Modifier.width(12.dp))
                        Icon(Icons.Rounded.Schedule, null, Modifier.size(12.dp), contentColor)
                        Spacer(Modifier.width(4.dp))
                        Text(text = "${item.startTime} - ${item.endTime}", style = MaterialTheme.typography.bodySmall, color = contentColor)
                    }

                    AnimatedVisibility(
                        visible = isExpanded,
                        enter = fadeIn(tween(durationMillis = 120)) +
                                expandVertically(
                                    animationSpec = tween(durationMillis = 180),
                                    expandFrom = Alignment.Top
                                ),
                        exit = fadeOut(tween(durationMillis = 90)) +
                                shrinkVertically(
                                    animationSpec = tween(durationMillis = 160),
                                    shrinkTowards = Alignment.Top
                                )
                    ) {
                        Column {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 1.dp)
                            Spacer(Modifier.height(8.dp))

                            AnimatedContent(
                                targetState = isEditing,
                                transitionSpec = {
                                    fadeIn(tween(durationMillis = 120)) togetherWith fadeOut(tween(durationMillis = 90))
                                },
                                label = "edit_transition"
                            ) { editingState ->
                                if (editingState) {
                                    // 【核心修改点】：全面替换为自研的 CompactTextField 极致小巧框
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        CompactTextField(
                                            value = draftTitle,
                                            onValueChange = { draftTitle = it },
                                            placeholder = "标题",
                                            enabled = !isSaving,
                                            modifier = Modifier
                                                .bringIntoViewRequester(titleBringIntoViewRequester)
                                                .onFocusChanged { state ->
                                                    isTitleFieldFocused = state.isFocused
                                                    if (state.isFocused) {
                                                        if (restoreScrollPosition == null) {
                                                            restoreScrollPosition = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                                                        }
                                                        scope.launch {
                                                            delay(80)
                                                            titleBringIntoViewRequester.bringIntoView()
                                                        }
                                                    }
                                                }
                                        )

                                        Spacer(Modifier.height(8.dp))

                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Surface(
                                                onClick = {
                                                    if (isSaving) return@Surface
                                                    haptics.click()
                                                    focusManager.clearFocus(force = true)
                                                    onRequestDatePicker(draftStartDate) { draftStartDate = it }
                                                },
                                                enabled = !isSaving,
                                                shape = CircleShape,
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                                color = if (!isSaving) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                                modifier = Modifier.weight(1.4f).height(32.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = draftStartDate.toString(),
                                                        fontSize = 13.sp,
                                                        color = if (!isSaving) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                                    )
                                                }
                                            }
                                            Surface(
                                                onClick = {
                                                    if (isSaving) return@Surface
                                                    haptics.click()
                                                    focusManager.clearFocus(force = true)
                                                    onRequestTimePicker(draftStartTime) { draftStartTime = it }
                                                },
                                                enabled = !isSaving,
                                                shape = CircleShape,
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                                color = if (!isSaving) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                                modifier = Modifier.weight(1f).height(32.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = draftStartTime,
                                                        fontSize = 13.sp,
                                                        color = if (!isSaving) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(Modifier.height(6.dp))

                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Surface(
                                                onClick = {
                                                    if (isSaving) return@Surface
                                                    haptics.click()
                                                    focusManager.clearFocus(force = true)
                                                    onRequestDatePicker(draftEndDate) { draftEndDate = it }
                                                },
                                                enabled = !isSaving,
                                                shape = CircleShape,
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                                color = if (!isSaving) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                                modifier = Modifier.weight(1.4f).height(32.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = draftEndDate.toString(),
                                                        fontSize = 13.sp,
                                                        color = if (!isSaving) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                                    )
                                                }
                                            }
                                            Surface(
                                                onClick = {
                                                    if (isSaving) return@Surface
                                                    haptics.click()
                                                    focusManager.clearFocus(force = true)
                                                    onRequestTimePicker(draftEndTime) { draftEndTime = it }
                                                },
                                                enabled = !isSaving,
                                                shape = CircleShape,
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                                color = if (!isSaving) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                                modifier = Modifier.weight(1f).height(32.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = draftEndTime,
                                                        fontSize = 13.sp,
                                                        color = if (!isSaving) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(Modifier.height(8.dp))

                                        CompactTextField(
                                            value = draftLocation,
                                            onValueChange = { draftLocation = it },
                                            placeholder = "地点",
                                            enabled = !isSaving,
                                            modifier = Modifier
                                                .bringIntoViewRequester(locationBringIntoViewRequester)
                                                .onFocusChanged { state ->
                                                    isLocationFieldFocused = state.isFocused
                                                    if (state.isFocused) {
                                                        if (restoreScrollPosition == null) {
                                                            restoreScrollPosition = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                                                        }
                                                        scope.launch {
                                                            delay(80)
                                                            locationBringIntoViewRequester.bringIntoView()
                                                        }
                                                    }
                                                }
                                        )

                                        Spacer(Modifier.height(6.dp))

                                        CompactTextField(
                                            value = draftDescription,
                                            onValueChange = { draftDescription = it },
                                            placeholder = "备注",
                                            singleLine = false,
                                            maxLines = 3,
                                            enabled = !isSaving,
                                            modifier = Modifier
                                                .bringIntoViewRequester(descriptionBringIntoViewRequester)
                                                .onFocusChanged { state ->
                                                    isDescriptionFieldFocused = state.isFocused
                                                    if (state.isFocused) {
                                                        if (restoreScrollPosition == null) {
                                                            restoreScrollPosition = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                                                        }
                                                        scope.launch {
                                                            delay(80)
                                                            descriptionBringIntoViewRequester.bringIntoView()
                                                        }
                                                    }
                                                }
                                        )

                                        Spacer(Modifier.height(6.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            TextButton(
                                                onClick = {
                                                    if (isSaving) return@TextButton
                                                    focusManager.clearFocus(force = true)
                                                    isEditing = false
                                                },
                                                enabled = !isSaving,
                                                modifier = Modifier
                                                    .defaultMinSize(minWidth = 1.dp, minHeight = 1.dp)
                                                    .height(32.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                            ) {
                                                Text("取消", fontSize = 13.sp)
                                            }
                                            Spacer(Modifier.width(8.dp))
                                            Button(
                                                onClick = {
                                                    if (isSaving) return@Button
                                                    val title = draftTitle.trim()
                                                    if (title.isBlank()) {
                                                        haptics.error()
                                                        android.widget.Toast.makeText(context, "标题不能为空", android.widget.Toast.LENGTH_SHORT).show()
                                                        return@Button
                                                    }
                                                    val startDt = try { LocalDateTime.of(draftStartDate, LocalTime.parse(draftStartTime)) } catch (e: Exception) { null }
                                                    val endDt = try { LocalDateTime.of(draftEndDate, LocalTime.parse(draftEndTime)) } catch (e: Exception) { null }

                                                    if (startDt == null || endDt == null) {
                                                        haptics.error()
                                                        android.widget.Toast.makeText(context, "时间格式错误", android.widget.Toast.LENGTH_SHORT).show()
                                                        return@Button
                                                    }
                                                    if (endDt.isBefore(startDt)) {
                                                        haptics.error()
                                                        android.widget.Toast.makeText(context, "结束时间不能早于开始时间", android.widget.Toast.LENGTH_SHORT).show()
                                                        return@Button
                                                    }

                                                    focusManager.clearFocus(force = true)
                                                    haptics.confirm()
                                                    isSaving = true
                                                    onUpdateScheduleItem(
                                                        item,
                                                        EventPatch(
                                                            title = title,
                                                            startTS = toEpochSeconds(draftStartDate, draftStartTime),
                                                            endTS = toEpochSeconds(draftEndDate, draftEndTime),
                                                            location = draftLocation.trim(),
                                                            description = mergeSourceImageMarker(
                                                                draftDescription.trim(),
                                                                extractSourceImagePath(item.description)
                                                            ),
                                                            tag = item.tag,
                                                            color = item.color
                                                        )
                                                    ) { isSaving = false; isEditing = false }
                                                },
                                                enabled = !isSaving,
                                                modifier = Modifier
                                                    .defaultMinSize(minWidth = 1.dp, minHeight = 1.dp)
                                                    .height(32.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                            ) {
                                                if (isSaving) CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                                else Text("保存", fontSize = 13.sp)
                                            }
                                        }
                                    }
                                } else {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        val expandedSubtitle = model.subtitle?.takeIf { it.isNotBlank() }
                                        val expandedDetail = model.detail?.takeIf { it.isNotBlank() }
                                        if (!expandedSubtitle.isNullOrBlank()) {
                                            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(bottom = if (!expandedDetail.isNullOrBlank()) 6.dp else 0.dp)) {
                                                Icon(Icons.Default.LocationOn, "地点", Modifier.size(14.dp).padding(top = 2.dp), tint = if (isExpired) contentColor else MaterialTheme.colorScheme.primary)
                                                Spacer(Modifier.width(6.dp))
                                                Text(expandedSubtitle, style = MaterialTheme.typography.bodySmall, color = titleColor)
                                            }
                                        }
                                        if (!expandedDetail.isNullOrBlank()) {
                                            Row(verticalAlignment = Alignment.Top) {
                                                Icon(Icons.Outlined.Description, "备注", Modifier.size(14.dp).padding(top = 2.dp), tint = if (isExpired) contentColor else MaterialTheme.colorScheme.secondary)
                                                Spacer(Modifier.width(6.dp))
                                                Text(expandedDetail, style = MaterialTheme.typography.bodySmall, color = contentColor, lineHeight = 18.sp)
                                            }
                                        } else if (expandedSubtitle == null) {
                                            Text("无更多详情", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(start = 20.dp))
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }

    }
}

private fun ScheduleDisplayItem.toRenderEvent(): Event {
    return Event(
        id = eventId,
        startTS = startTS,
        endTS = endTS,
        title = title,
        location = location,
        description = stripSourceImageMarkers(description),
        timeZone = timeZone,
        color = color,
        state = state,
        tag = tag,
        parentId = parentId ?: 0L
    )
}

@Composable
fun StatusLabel(text: String, textColor: Color, backgroundColor: Color) {
    Box(
        modifier = Modifier.background(backgroundColor, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = textColor, fontWeight = FontWeight.Bold)
    }
}
