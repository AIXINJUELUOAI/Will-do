package com.antgskds.calendarassistant.ui.floating

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LocalTaxi
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antgskds.calendarassistant.data.model.EventTags
import com.antgskds.calendarassistant.data.model.EventType
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.core.util.PickupUtils
import com.antgskds.calendarassistant.core.util.TransportUtils
import com.antgskds.calendarassistant.ui.components.WheelDatePicker
import com.antgskds.calendarassistant.ui.components.WheelTimePicker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.CompositionLocalProvider // 需要确保有这个导入
import androidx.compose.ui.unit.Density // 需要确保有这个导入

@Composable
fun FloatingScheduleScreen(
    events: List<MyEvent>,
    onClose: () -> Unit,
    onManualInput: (String, () -> Unit) -> Unit,
    onPickImageRequest: ((() -> Unit) -> Unit),
    onUpdateEvent: (MyEvent, () -> Unit) -> Unit = { _, onComplete -> onComplete() },
    onEventAction: (String, String) -> Unit = { _, _ -> },
    onUndo: (String, String) -> Unit = { _, _ -> },
    onLoadingChange: (Boolean) -> Unit = {}
) {
    var manualInputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var pickerRequest by remember { mutableStateOf<FloatingPickerRequest?>(null) }

    // 动画状态
    var isAppearing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 关闭函数：先播动画，再执行销毁
    val animateClose = {
        scope.launch {
            isAppearing = false
            delay(250) // 等待退出动画播完
            onClose()
        }
    }

    // 进入时立即触发动画
    LaunchedEffect(Unit) {
        isAppearing = true
    }

    // 动画曲线定义
    val fastOutSlowIn = remember { CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f) }
    val enterDuration = 280 // 毫秒
    val exitDuration = 200 // 毫秒

    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val isImeVisible = WindowInsets.ime.getBottom(density) > 0
    val isPickerVisible = pickerRequest != null

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
        // 日程列表：淡入淡出
        AnimatedVisibility(
            visible = isAppearing,
            enter = fadeIn(animationSpec = tween(enterDuration, easing = fastOutSlowIn)),
            exit = fadeOut(animationSpec = tween(exitDuration, easing = fastOutSlowIn)),
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            TimeWheelList(
                events = events,
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(),
                listState = listState,
                onUpdateEvent = onUpdateEvent,
                onEventAction = onEventAction,
                onUndo = { id, tag -> onUndo(id, tag) },
                onRequestDatePicker = { initialDate, onConfirm ->
                    pickerRequest = FloatingPickerRequest.Date(initialDate, onConfirm)
                },
                onRequestTimePicker = { initialTime, onConfirm ->
                    pickerRequest = FloatingPickerRequest.Time(initialTime, onConfirm)
                }
            )
        }

        // 顶部遮罩
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(80.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = bgAlpha), Color.Transparent)))
        )

        // 底部遮罩
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(150.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = bgAlpha))))
        )

        // 底部输入框：淡入淡出
        AnimatedVisibility(
            visible = isAppearing,
            enter = fadeIn(animationSpec = tween(enterDuration, easing = fastOutSlowIn)),
            exit = fadeOut(animationSpec = tween(exitDuration, easing = fastOutSlowIn)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            BottomInteractionArea(
                modifier = Modifier,
                text = manualInputText,
                onTextChange = { manualInputText = it },
                onManualSubmit = { text ->
                    if (text.isNotBlank()) {
                        isLoading = true
                        onLoadingChange(true)
                        onManualInput(text) {
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
                    onPickImageRequest {
                        isLoading = false
                        onLoadingChange(false)
                    }
                },
                onSwipeUpClose = { animateClose() },
                isLoading = isLoading
            )
        }

        pickerRequest?.let { request ->
            when (request) {
                is FloatingPickerRequest.Date -> FloatingDatePickerOverlay(
                    initialDate = request.initialDate,
                    onDismiss = { pickerRequest = null },
                    onConfirm = { date ->
                        request.onConfirm(date)
                        pickerRequest = null
                    }
                )
                is FloatingPickerRequest.Time -> FloatingTimePickerOverlay(
                    initialTime = request.initialTime,
                    onDismiss = { pickerRequest = null },
                    onConfirm = { time ->
                        request.onConfirm(time)
                        pickerRequest = null
                    }
                )
            }
        }
    }
}

@Composable
fun BottomInteractionArea(
    modifier: Modifier = Modifier,
    text: String,
    onTextChange: (String) -> Unit,
    onManualSubmit: (String) -> Unit,
    onPickImage: () -> Unit,
    onSwipeUpClose: () -> Unit,
    isLoading: Boolean = false
) {
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
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                placeholder = { Text(text = "输入日程...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledContainerColor = MaterialTheme.colorScheme.surface,
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = Color.Transparent,
                    disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onManualSubmit(text) }),
                singleLine = true,
                enabled = !isLoading,
                trailingIcon = {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 8.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(onClick = onPickImage) {
                                Icon(
                                    imageVector = Icons.Rounded.Image,
                                    contentDescription = "上传图片",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { onManualSubmit(text) },
                                enabled = text.isNotBlank()
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Send,
                                    contentDescription = "发送",
                                    tint = if (text.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            )
        }

        Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.ime.union(WindowInsets.navigationBars)))
    }
}

@Composable
fun TimeWheelList(
    events: List<MyEvent>,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    onUpdateEvent: (MyEvent, () -> Unit) -> Unit = { _, onComplete -> onComplete() },
    onEventAction: (String, String) -> Unit = { _, _ -> },
    onUndo: (String, String) -> Unit = { _, _ -> },
    onRequestDatePicker: (LocalDate, (LocalDate) -> Unit) -> Unit = { _, _ -> },
    onRequestTimePicker: (String, (String) -> Unit) -> Unit = { _, _ -> }
) {
    val now = LocalDateTime.now()
    val sortedEvents = remember(events, now) {
        events
            .filter { it.archivedAt == null }
            .filter { event -> !event.isRecurring || event.isRecurringParent }
            .distinctBy { it.id }
            .sortedByDescending { event ->
                try {
                    if (event.isRecurringParent && event.nextOccurrenceStartMillis != null) {
                        LocalDateTime.ofInstant(
                            java.time.Instant.ofEpochMilli(event.nextOccurrenceStartMillis),
                            java.time.ZoneId.systemDefault()
                        )
                    } else {
                        LocalDateTime.parse("${event.startDate} ${event.startTime}", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                    }
                } catch (e: Exception) { LocalDateTime.MIN }
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
            items(sortedEvents, key = { it.id }) { event ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    ScheduleCard(
                        event = event,
                        listState = listState,
                        modifier = Modifier.padding(end = 20.dp).width(260.dp),
                        onUpdateEvent = onUpdateEvent,
                        onEventAction = onEventAction,
                        onUndo = { id, tag -> onUndo(id, tag) },
                        onRequestDatePicker = onRequestDatePicker,
                        onRequestTimePicker = onRequestTimePicker
                    )
                }
            }
        }
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
    // 聚焦时边框高亮变色，保持和原生一样的交互体验
    val borderColor = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val borderWidth = if (isFocused) 1.5.dp else 1.dp

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
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
                if (value.isEmpty()) {
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
    event: MyEvent,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    onUpdateEvent: (MyEvent, () -> Unit) -> Unit = { _, onComplete -> onComplete() },
    onEventAction: (String, String) -> Unit = { _, _ -> },
    onUndo: (String, String) -> Unit = { _, _ -> },
    onRequestDatePicker: (LocalDate, (LocalDate) -> Unit) -> Unit = { _, _ -> },
    onRequestTimePicker: (String, (String) -> Unit) -> Unit = { _, _ -> }
) {
    var isExpanded by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val canEdit = remember(event.eventType, event.isRecurring, event.isRecurringParent) {
        event.eventType != EventType.COURSE && !event.isRecurring && !event.isRecurringParent
    }

    var isEditing by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    var draftTitle by remember { mutableStateOf(event.title) }
    var draftStartDate by remember { mutableStateOf(event.startDate) }
    var draftStartTime by remember { mutableStateOf(event.startTime) }
    var draftEndDate by remember { mutableStateOf(event.endDate) }
    var draftEndTime by remember { mutableStateOf(event.endTime) }
    var draftLocation by remember { mutableStateOf(event.location) }
    var draftDescription by remember { mutableStateOf(event.description) }

    LaunchedEffect(event.id, event.lastModified) {
        if (!isEditing && !isSaving) {
            draftTitle = event.title
            draftStartDate = event.startDate
            draftStartTime = event.startTime
            draftEndDate = event.endDate
            draftLocation = event.location
            draftDescription = event.description
        }
    }

    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

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
    val startDateTime = remember(event) { try { LocalDateTime.of(event.startDate, LocalTime.parse(event.startTime)) } catch (e: Exception) { LocalDateTime.MIN } }
    val endDateTime = remember(event) { try { LocalDateTime.of(event.endDate, LocalTime.parse(event.endTime)) } catch (e: Exception) { LocalDateTime.MAX } }

    val isExpired = remember(now, endDateTime) { now.isAfter(endDateTime) }
    val isInProgress = remember(now, startDateTime, endDateTime) { !isExpired && now.isAfter(startDateTime) && now.isBefore(endDateTime) }
    val isComingSoon = remember(now, startDateTime, isExpired, isInProgress) {
        if (isExpired || isInProgress) false else Duration.between(now, startDateTime).toMinutes() in 0..30
    }

    val barColor = if (isExpired) MaterialTheme.colorScheme.outlineVariant else event.color
    val titleColor = if (isExpired) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    val contentColor = if (isExpired) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurfaceVariant
    val elevation = if (isInProgress) 6.dp else 2.dp

    val transportInfo = remember(event.description, event.isCheckedIn) { TransportUtils.parse(event.description, event.isCheckedIn) }

    val hasAction = remember(event.isRecurringParent, isExpired, event.isCompleted, event.isCheckedIn) {
        !event.isRecurringParent && (!isExpired || (event.isCompleted || event.isCheckedIn))
    }

    val displayTitle = remember(event, transportInfo, isExpired) {
        when {
            event.tag == "train" -> if (event.isCheckedIn) transportInfo.mainDisplay else if (isExpired) event.title else transportInfo.mainDisplay.ifBlank { "待检票" }
            event.tag == "taxi" -> if (event.isCompleted || isExpired) event.title else transportInfo.mainDisplay
            event.tag == EventTags.PICKUP -> if (event.isCompleted || isExpired) event.title else PickupUtils.parsePickupInfo(event).code
            else -> event.title
        }
    }

    val density = LocalDensity.current
    val actionButtonSize = 46.dp
    val actionButtonSpacing = 10.dp
    val actionAreaEndPadding = 14.dp
    val cardToButtonGap = 12.dp
    val actionButtonCount = if (hasAction) 2 else 1

    val actionAreaWidthDp = actionAreaEndPadding + (actionButtonSize * actionButtonCount) + (actionButtonSpacing * (actionButtonCount - 1)) + cardToButtonGap
    val revealOffsetPx = with(density) { -actionAreaWidthDp.toPx() }
    val revealSnapThresholdPx = revealOffsetPx * 0.35f
    val fullSwipeTriggerPx = with(density) { -150.dp.toPx() }
    val dragLimitPx = with(density) { -190.dp.toPx() }

    // 【归档相关参数】右滑触发阈值和飞出距离
    val archiveTriggerPx = with(density) { 110.dp.toPx() }
    val screenWidthPx = with(density) { 400.dp.toPx() }

    val swipeSpringSpec = spring<Float>(dampingRatio = 0.85f, stiffness = 600f)

    val isPastFullSwipe by remember { derivedStateOf { hasAction && offsetX.value <= fullSwipeTriggerPx } }
    var hasVibrated by remember { mutableStateOf(false) }

    // 【核心新增】右滑（归档）震动状态
    val isPastArchiveSwipe by remember { derivedStateOf { offsetX.value >= archiveTriggerPx } }
    var hasVibratedArchive by remember { mutableStateOf(false) }

    LaunchedEffect(isPastFullSwipe, hasAction) {
        if (hasAction && isPastFullSwipe && !hasVibrated) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            hasVibrated = true
        } else if (!isPastFullSwipe) {
            hasVibrated = false
        }
    }

    // 【核心新增】右滑阈值的震动控制
    LaunchedEffect(isPastArchiveSwipe) {
        if (isPastArchiveSwipe && !hasVibratedArchive) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            hasVibratedArchive = true
        } else if (!isPastArchiveSwipe) {
            hasVibratedArchive = false
        }
    }

    val actionInfo = remember(event.isCompleted, event.tag, event.eventType, event.isCheckedIn) {
        if (event.isCompleted || event.isCheckedIn) Pair(Icons.Rounded.Undo, Color(0xFFFFA726))
        else when (event.tag) {
            "train" -> Pair(Icons.Rounded.ConfirmationNumber, Color(0xFF4CAF50))
            "taxi" -> Pair(Icons.Rounded.LocalTaxi, Color(0xFFFF9800))
            EventTags.PICKUP, "package" -> Pair(Icons.Rounded.ShoppingBag, Color(0xFF2196F3))
            else -> Pair(Icons.Rounded.CheckCircle, Color(0xFF4CAF50))
        }
    }

    Box(modifier = modifier) {
        // 背景滑动按钮
        if (offsetX.value < -1f) {
            val revealProgress = ((-offsetX.value) / abs(revealOffsetPx)).coerceIn(0f, 1f)
            Box(
                modifier = Modifier.matchParentSize().padding(end = actionAreaEndPadding),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(actionButtonSpacing),
                    modifier = Modifier.alpha(revealProgress)
                ) {
                    if (hasAction) {
                        Surface(
                            modifier = Modifier.size(actionButtonSize), shape = CircleShape,
                            color = if (isPastFullSwipe) actionInfo.second else actionInfo.second.copy(alpha = 0.92f),
                            onClick = { scope.launch { onUndo(event.id, event.tag); offsetX.animateTo(0f, swipeSpringSpec) } }
                        ) {
                            Box(contentAlignment = Alignment.Center) { Icon(actionInfo.first, null, Modifier.size(22.dp), tint = Color.White) }
                        }
                    }

                    Surface(
                        modifier = Modifier.size(actionButtonSize), shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        onClick = {
                            scope.launch {
                                if (!canEdit) android.widget.Toast.makeText(context, "暂不支持在悬浮窗编辑", android.widget.Toast.LENGTH_SHORT).show()
                                else {
                                    draftTitle = event.title
                                    draftStartDate = event.startDate; draftStartTime = event.startTime
                                    draftEndDate = event.endDate; draftEndTime = event.endTime
                                    draftLocation = event.location; draftDescription = event.description
                                    isExpanded = true; isEditing = true
                                }
                                offsetX.animateTo(0f, swipeSpringSpec)
                            }
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Edit, "编辑", Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onPrimary) }
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
                        Modifier.pointerInput(event.id, hasAction, event.isCompleted) {
                            detectHorizontalDragGestures(
                                onDragStart = { scope.launch { offsetX.stop() } },
                                onDragEnd = {
                                    val fullSwipeLeft = hasAction && offsetX.value <= fullSwipeTriggerPx
                                    val shouldRevealLeft = offsetX.value <= revealSnapThresholdPx
                                    val fullSwipeRight = offsetX.value >= archiveTriggerPx

                                    scope.launch {
                                        if (fullSwipeLeft) { 
                                            onUndo(event.id, event.tag)
                                            offsetX.animateTo(0f, swipeSpringSpec) 
                                        } else if (fullSwipeRight) {
                                            // 【核心】触发归档飞出动画，然后调用更新（配合 animateItemPlacement 实现缝隙弥合）
                                            offsetX.animateTo(
                                                targetValue = screenWidthPx, 
                                                animationSpec = tween(durationMillis = 200)
                                            )
                                            onEventAction(event.id, "archive")
                                        } else if (shouldRevealLeft) {
                                            offsetX.animateTo(revealOffsetPx, swipeSpringSpec)
                                        } else {
                                            offsetX.animateTo(0f, swipeSpringSpec)
                                        }
                                    }
                                },
                                onDragCancel = {
                                    scope.launch {
                                        if (offsetX.value <= revealSnapThresholdPx) offsetX.animateTo(revealOffsetPx, swipeSpringSpec)
                                        else offsetX.animateTo(0f, swipeSpringSpec)
                                    }
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    scope.launch {
                                        val current = offsetX.value
                                        // 【阻尼感调校】向右滑过阈值后瞬间解除阻力，鼓励直接飞出去
                                        val resistance = when { 
                                            dragAmount < 0 && current <= fullSwipeTriggerPx -> 0.25f
                                            dragAmount < 0 && current <= revealOffsetPx -> 0.45f
                                            dragAmount > 0 && current >= archiveTriggerPx -> 0.95f
                                            else -> 0.85f 
                                        }
                                        // 【修改】去除了上限 0f，允许卡片向右无限制拖拽
                                        offsetX.snapTo((current + (dragAmount * resistance)).coerceAtLeast(dragLimitPx))
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
                scope.launch {
                    if (offsetX.value < -10f) offsetX.animateTo(0f, swipeSpringSpec)
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
                            event.isRecurringParent -> StatusLabel("重复", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                            event.isCheckedIn -> StatusLabel("已检票", Color(0xFF4CAF50), Color(0xFF4CAF50).copy(alpha = 0.2f))
                            event.isCompleted -> StatusLabel(when(event.tag) { EventTags.PICKUP -> "已取件"; "taxi" -> "已用车"; else -> "已完成" }, MaterialTheme.colorScheme.outline, MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            isInProgress -> StatusLabel("进行中", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                            isComingSoon -> StatusLabel("即将开始", Color(0xFFFF9800), Color(0xFFFF9800).copy(alpha = 0.2f))
                            isExpired -> StatusLabel("已结束", MaterialTheme.colorScheme.outline, MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        }
                    }
                    Row(modifier = Modifier.padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CalendarToday, null, Modifier.size(12.dp), contentColor)
                        Spacer(Modifier.width(4.dp))
                        Text(text = event.startDate.format(DateTimeFormatter.ofPattern("MM-dd")), style = MaterialTheme.typography.bodySmall, color = contentColor)
                        Spacer(Modifier.width(12.dp))
                        Icon(Icons.Rounded.Schedule, null, Modifier.size(12.dp), contentColor)
                        Spacer(Modifier.width(4.dp))
                        Text(text = "${event.startTime} - ${event.endTime}", style = MaterialTheme.typography.bodySmall, color = contentColor)
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
                                                        android.widget.Toast.makeText(context, "标题不能为空", android.widget.Toast.LENGTH_SHORT).show()
                                                        return@Button
                                                    }
                                                    val startDt = try { LocalDateTime.of(draftStartDate, LocalTime.parse(draftStartTime)) } catch (e: Exception) { null }
                                                    val endDt = try { LocalDateTime.of(draftEndDate, LocalTime.parse(draftEndTime)) } catch (e: Exception) { null }

                                                    if (startDt == null || endDt == null) {
                                                        android.widget.Toast.makeText(context, "时间格式错误", android.widget.Toast.LENGTH_SHORT).show()
                                                        return@Button
                                                    }
                                                    if (endDt.isBefore(startDt)) {
                                                        android.widget.Toast.makeText(context, "结束时间不能早于开始时间", android.widget.Toast.LENGTH_SHORT).show()
                                                        return@Button
                                                    }

                                                    focusManager.clearFocus(force = true)
                                                    isSaving = true
                                                    onUpdateEvent(event.copy(
                                                        title = title, startDate = draftStartDate, startTime = draftStartTime,
                                                        endDate = draftEndDate, endTime = draftEndTime, location = draftLocation.trim(),
                                                        description = draftDescription.trim(), lastModified = System.currentTimeMillis()
                                                    )) { isSaving = false; isEditing = false }
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
                                        if (event.location.isNotBlank()) {
                                            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(bottom = 6.dp)) {
                                                Icon(Icons.Default.LocationOn, "地点", Modifier.size(14.dp).padding(top = 2.dp), tint = if (isExpired) contentColor else MaterialTheme.colorScheme.primary)
                                                Spacer(Modifier.width(6.dp))
                                                Text(event.location, style = MaterialTheme.typography.bodySmall, color = titleColor)
                                            }
                                        }
                                        if (event.description.isNotBlank()) {
                                            Row(verticalAlignment = Alignment.Top) {
                                                Icon(Icons.Outlined.Description, "备注", Modifier.size(14.dp).padding(top = 2.dp), tint = if (isExpired) contentColor else MaterialTheme.colorScheme.secondary)
                                                Spacer(Modifier.width(6.dp))
                                                Text(event.description, style = MaterialTheme.typography.bodySmall, color = contentColor, lineHeight = 18.sp)
                                            }
                                        } else if (event.location.isBlank()) {
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

@Composable
fun StatusLabel(text: String, textColor: Color, backgroundColor: Color) {
    Box(
        modifier = Modifier.background(backgroundColor, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = textColor, fontWeight = FontWeight.Bold)
    }
}
