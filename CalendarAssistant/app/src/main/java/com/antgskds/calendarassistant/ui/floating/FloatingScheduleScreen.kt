package com.antgskds.calendarassistant.ui.floating

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.LocalTaxi
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antgskds.calendarassistant.data.model.EventTags
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.core.util.PickupUtils
import com.antgskds.calendarassistant.core.util.TransportUtils
import com.antgskds.calendarassistant.core.util.TransportType
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun FloatingScheduleScreen(
    events: List<MyEvent>,
    onClose: () -> Unit,
    onManualInput: (String, () -> Unit) -> Unit,
    onEventAction: (String, String) -> Unit = { _, _ -> },
    onUndo: (String, String) -> Unit = { _, _ -> }, // 改为接受 (eventId, tag)
    onLoadingChange: (Boolean) -> Unit = {}
) {
    var manualInputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClose() })
            }
    ) {
        TimeWheelList(
            events = events,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxHeight()
                .fillMaxWidth(),
            onEventAction = onEventAction,
            onUndo = { id, tag -> onUndo(id, tag) }
        )

        // 顶部遮罩
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(80.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
        )

        // 底部遮罩
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(150.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
        )

        BottomInteractionArea(
            modifier = Modifier.align(Alignment.BottomCenter),
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
            onSwipeUpClose = onClose,
            isLoading = isLoading
        )
    }
}

@Composable
fun BottomInteractionArea(
    modifier: Modifier = Modifier,
    text: String,
    onTextChange: (String) -> Unit,
    onManualSubmit: (String) -> Unit,
    onSwipeUpClose: () -> Unit,
    isLoading: Boolean = false
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
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
        )
    }
}

@Composable
fun TimeWheelList(
    events: List<MyEvent>,
    modifier: Modifier = Modifier,
    onEventAction: (String, String) -> Unit = { _, _ -> },
    onUndo: (String, String) -> Unit = { _, _ -> } // 改为接受 (eventId, tag)
) {
    val now = LocalDateTime.now()
    val sortedEvents = remember(events, now) {
        events
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

    val listState = rememberLazyListState()

    Box(modifier = modifier) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 60.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(sortedEvents, key = { it.id }) { event ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterEnd // 卡片默认靠右
                ) {
                    ScheduleCard(
                        event = event,
                        modifier = Modifier.width(260.dp).padding(end = 8.dp),
                        onEventAction = onEventAction,
                        onUndo = { id, tag -> onUndo(id, tag) }
                    )
                }
            }
        }
    }
}

@Composable
fun ScheduleCard(
    event: MyEvent,
    modifier: Modifier = Modifier,
    onEventAction: (String, String) -> Unit = { _, _ -> },
    onUndo: (String, String) -> Unit = { _, _ -> } // 改为接受 (eventId, tag)
) {
    var isExpanded by remember { mutableStateOf(false) }

    // 动画位移状态
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    // 1. 修复震动：使用 HapticFeedback 替代 Vibrator，兼容性更好
    val haptic = LocalHapticFeedback.current

    val now = LocalDateTime.now()
    val startDateTime = remember(event) { try { LocalDateTime.of(event.startDate, LocalTime.parse(event.startTime)) } catch (e: Exception) { LocalDateTime.MIN } }
    val endDateTime = remember(event) { try { LocalDateTime.of(event.endDate, LocalTime.parse(event.endTime)) } catch (e: Exception) { LocalDateTime.MAX } }

    val isExpired = remember(now, endDateTime) { now.isAfter(endDateTime) }
    val isInProgress = remember(now, startDateTime, endDateTime) { !isExpired && now.isAfter(startDateTime) && now.isBefore(endDateTime) }
    val isComingSoon = remember(now, startDateTime, isExpired, isInProgress) {
        if (isExpired || isInProgress) false else Duration.between(now, startDateTime).toMinutes() in 0..30
    }

    // 样式
    val barColor = if (isExpired) MaterialTheme.colorScheme.outlineVariant else event.color
    val titleColor = if (isExpired) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    val contentColor = if (isExpired) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurfaceVariant
    val elevation = if (isInProgress) 6.dp else 2.dp

    // === 1. 解析交通/状态信息 ===
    val transportInfo = remember(event.description, event.isCheckedIn) {
        TransportUtils.parse(event.description, event.isCheckedIn)
    }

    // === 2. 核心交互逻辑判断 (Core Logic) ===
    // 规则：只要日程"没过期"，或者"处于已完成/已检票状态(可撤销)"，就允许操作。
    // 反之：如果"已过期"且"未完成/未检票"，则禁止操作(无图标、无震动)。
    val hasAction = remember(event.isRecurringParent, isExpired, event.isCompleted, event.isCheckedIn) {
        !event.isRecurringParent && (!isExpired || (event.isCompleted || event.isCheckedIn))
    }

    // === 3. 智能标题显示 ===
    // 火车：检票前=检票口(无则待检票)，检票后=车号+座位号，过期=默认title
    // 打车：用车前=车牌号，用车后/过期=默认title
    // 取件：已取前=取件码，已取后/过期=默认title
    // 日程：始终显示默认title
    val displayTitle = remember(event, transportInfo, isExpired) {
        when {
            event.tag == "train" -> {
                if (event.isCheckedIn) {
                    // 检票后：只显示座位号
                    transportInfo.mainDisplay
                } else if (isExpired) {
                    // 过期后：默认title
                    event.title
                } else {
                    // 检票前：检票口 或 "待检票"
                    transportInfo.mainDisplay.ifBlank { "待检票" }
                }
            }
            event.tag == "taxi" -> {
                if (event.isCompleted || isExpired) event.title else transportInfo.mainDisplay
            }
            event.tag == EventTags.PICKUP -> {
                if (event.isCompleted || isExpired) event.title else PickupUtils.parsePickupInfo(event).code
            }
            else -> event.title
        }
    }

    // === 交互逻辑调整 ===
    // 2. 修复阈值：从 -80dp 调整为 -110dp，防误触，同时配合阻尼感
    val density = LocalDensity.current
    val actionThresholdPx = with(density) { -110.dp.toPx() } // 阈值像素值

    val isPastThreshold by remember { derivedStateOf { offsetX.value < actionThresholdPx } }
    var hasVibrated by remember { mutableStateOf(false) }

    // === 5. 震动反馈逻辑 ===
    // 仅当 hasAction 为 true 时，过线才震动
    LaunchedEffect(isPastThreshold, hasAction) {
        if (hasAction && isPastThreshold && !hasVibrated) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            hasVibrated = true
        } else if (!isPastThreshold) {
            hasVibrated = false
        }
    }

    val actionInfo = remember(event.isCompleted, event.tag, event.eventType, event.isCheckedIn) {
        if (event.isCompleted || event.isCheckedIn) {
            Pair(Icons.Rounded.Undo, Color(0xFFFFA726))
        } else {
            when (event.tag) {
                "train" -> Pair(Icons.Rounded.ConfirmationNumber, Color(0xFF4CAF50))
                "taxi" -> Pair(Icons.Rounded.LocalTaxi, Color(0xFFFF9800))
                EventTags.PICKUP, "package" -> Pair(Icons.Rounded.ShoppingBag, Color(0xFF2196F3))
                else -> Pair(Icons.Rounded.CheckCircle, Color(0xFF4CAF50))
            }
        }
    }

    Box(modifier = modifier) {

        // === 背景层 (图标) ===
        // 仅当 hasAction 为 true 且正在左滑时显示
        if (hasAction && offsetX.value < 0) {
            // 计算拖拽进度 0.0 ~ 1.0 (达到阈值) ~ 1.5 (拉满)
            val dragProgress = (offsetX.value / actionThresholdPx).coerceIn(0f, 1.5f)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(end = 18.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Box(
                    modifier = Modifier
                        .scale(0.8f + (dragProgress * 0.2f).coerceAtMost(0.3f))
                        .alpha(dragProgress.coerceIn(0f, 1f))
                        .size(48.dp)
                        .background(
                            color = if (isPastThreshold) actionInfo.second else MaterialTheme.colorScheme.surfaceVariant,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = actionInfo.first,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = if (isPastThreshold) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // === 前景层 (卡片) ===
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(event.id, hasAction, event.isCompleted) {
                    detectHorizontalDragGestures(
                        onDragStart = { scope.launch { offsetX.stop() } },
                        onDragEnd = {
                            val thresholdMet = offsetX.value < actionThresholdPx
                            scope.launch {
                                // 触发条件：有操作权限 && 超过阈值
                                if (hasAction && thresholdMet) {
                                    // 直接调用 onUndo，让 handleUndo 内部判断是检票还是撤销
                                    // 不在这里判断 event.isCheckedIn，因为可能是旧状态
                                    onUndo(event.id, event.tag)
                                }
                                // 无论是否触发，始终回弹
                                offsetX.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessLow
                                    )
                                )
                            }
                        },
                        onDragCancel = { scope.launch { offsetX.animateTo(0f, spring()) } },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                val current = offsetX.value
                                // 2. 修复手感：增加阻尼效果 (Resistance)
                                // 越过阈值后，阻尼变大 (0.3系数)，模拟拉橡皮筋的感觉
                                val resistance = if (current < actionThresholdPx) 0.3f else 0.8f

                                val target = current + (dragAmount * resistance)
                                // 限制只能左滑，且不超过 -200dp (防止滑太远)
                                val limit = with(density) { -200.dp.toPx() }
                                if (target <= 0) {
                                    offsetX.snapTo(target.coerceAtLeast(limit))
                                }
                            }
                        }
                    )
                },
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = elevation,
            onClick = { isExpanded = !isExpanded }
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
                            color = titleColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        )
                        when {
                            event.isRecurringParent -> StatusLabel("重复", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                            event.isCheckedIn -> StatusLabel("已检票", Color(0xFF4CAF50), Color(0xFF4CAF50).copy(alpha = 0.2f))
                            event.isCompleted -> {
                                val completedText = when {
                                    event.tag == EventTags.PICKUP -> "已取件"
                                    event.tag == "taxi" -> "已用车"
                                    else -> "已完成"
                                }
                                StatusLabel(completedText, MaterialTheme.colorScheme.outline, MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            }
                            isInProgress -> StatusLabel("进行中", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                            isComingSoon -> StatusLabel("即将开始", Color(0xFFFF9800), Color(0xFFFF9800).copy(alpha = 0.2f))
                            isExpired -> StatusLabel("已结束", MaterialTheme.colorScheme.outline, MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        }
                    }
                    Row(
                        modifier = Modifier.padding(bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.CalendarToday, null, Modifier.size(12.dp), contentColor)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = event.startDate.format(DateTimeFormatter.ofPattern("MM-dd")),
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor
                        )
                        Spacer(Modifier.width(12.dp))
                        Icon(Icons.Rounded.Schedule, null, Modifier.size(12.dp), contentColor)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "${event.startTime} - ${event.endTime}",
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor
                        )
                    }
                    AnimatedVisibility(visible = isExpanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Column {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 1.dp)
                            Spacer(Modifier.height(8.dp))
                            if (event.location.isNotBlank()) {
                                Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(bottom = 6.dp)) {
                                    Icon(Icons.Default.LocationOn, "地点", Modifier.size(14.dp).padding(top = 2.dp), tint = if (isExpired) contentColor else MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = event.location,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = titleColor
                                    )
                                }
                            }
                            if (event.description.isNotBlank()) {
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(Icons.Outlined.Description, "备注", Modifier.size(14.dp).padding(top = 2.dp), tint = if (isExpired) contentColor else MaterialTheme.colorScheme.secondary)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = event.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = contentColor,
                                        lineHeight = 18.sp
                                    )
                                }
                            } else if (event.location.isBlank()) {
                                Text(
                                text = "无更多详情",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(start = 20.dp)
                            )
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
