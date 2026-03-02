package com.antgskds.calendarassistant.ui.floating

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antgskds.calendarassistant.data.model.MyEvent
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun FloatingScheduleScreen(
    events: List<MyEvent>,
    onClose: () -> Unit,
    onManualInput: (String) -> Unit
) {
    var manualInputText by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClose() }
                )
            }
    ) {
        TimeWheelList(
            events = events,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxHeight()
                .width(280.dp) // 保持原有宽度
        )

        // 顶部渐变遮罩
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(80.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                    )
                )
        )

        // 底部渐变遮罩
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(150.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                    )
                )
        )

        BottomInteractionArea(
            modifier = Modifier.align(Alignment.BottomCenter),
            text = manualInputText,
            onTextChange = { manualInputText = it },
            onManualSubmit = { text ->
                if (text.isNotBlank()) {
                    onManualInput(text)
                    manualInputText = ""
                }
            },
            onSwipeUpClose = onClose
        )
    }
}

@Composable
fun BottomInteractionArea(
    modifier: Modifier = Modifier,
    text: String,
    onTextChange: (String) -> Unit,
    onManualSubmit: (String) -> Unit,
    onSwipeUpClose: () -> Unit
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text("输入日程...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onManualSubmit(text) }),
                singleLine = true
            )
        }
    }
}

@Composable
fun TimeWheelList(events: List<MyEvent>, modifier: Modifier = Modifier) {
    val now = LocalDateTime.now()
    val sortedEvents = remember(events, now) {
        events.sortedByDescending { event ->
            try {
                LocalDateTime.parse(
                    "${event.startDate} ${event.startTime}",
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                )
            } catch (e: Exception) {
                LocalDateTime.MIN
            }
        }
    }

    val listState = rememberLazyListState()

    Box(modifier = modifier) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 60.dp, bottom = 100.dp, start = 8.dp, end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(sortedEvents, key = { it.id }) { event ->
                ScheduleCard(
                    event = event,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun ScheduleCard(
    event: MyEvent,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    val now = LocalDateTime.now()

    // 1. 解析时间
    val startDateTime = remember(event) {
        try {
            LocalDateTime.of(event.startDate, LocalTime.parse(event.startTime))
        } catch (e: Exception) { LocalDateTime.MIN }
    }
    val endDateTime = remember(event) {
        try {
            LocalDateTime.of(event.endDate, LocalTime.parse(event.endTime))
        } catch (e: Exception) { LocalDateTime.MAX }
    }

    // 2. 计算状态
    val isExpired = remember(now, endDateTime) { now.isAfter(endDateTime) }
    val isInProgress = remember(now, startDateTime, endDateTime) {
        !isExpired && now.isAfter(startDateTime) && now.isBefore(endDateTime)
    }
    // "即将开始"定义：未开始且在30分钟内
    val isComingSoon = remember(now, startDateTime, isExpired, isInProgress) {
        if (isExpired || isInProgress) false
        else {
            val minutes = Duration.between(now, startDateTime).toMinutes()
            minutes in 0..30
        }
    }

    // 3. 视觉样式配置
    val barColor = if (isExpired) MaterialTheme.colorScheme.outlineVariant else event.color
    val titleColor = if (isExpired) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    val contentColor = if (isExpired) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurfaceVariant
    val elevation = if (isInProgress) 6.dp else 2.dp

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = elevation,
        onClick = { isExpanded = !isExpanded }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // === 左侧色条 ===
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(48.dp)
                    .padding(vertical = 8.dp)
                    .background(barColor, RoundedCornerShape(2.dp))
            )

            Spacer(modifier = Modifier.width(10.dp))

            // === 右侧内容区域 ===
            Column(modifier = Modifier.weight(1f)) {
                // --- 标题行 + 状态标签 (两端对齐) ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween // 关键：将标题和标签推向两端
                ) {
                    // 标题：使用 weight(1f) 占据剩余空间，防止挤压右侧标签
                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isInProgress) FontWeight.Bold else FontWeight.Medium,
                        color = titleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )

                    // 状态标签：统一胶囊样式
                    if (isInProgress) {
                        StatusLabel(
                            text = "进行中",
                            textColor = MaterialTheme.colorScheme.primary,
                            backgroundColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        )
                    } else if (isComingSoon) {
                        StatusLabel(
                            text = "即将开始",
                            textColor = Color(0xFFFF9800),
                            backgroundColor = Color(0xFFFF9800).copy(alpha = 0.2f)
                        )
                    } else if (isExpired) {
                        StatusLabel(
                            text = "已结束",
                            textColor = MaterialTheme.colorScheme.outline,
                            backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }

                // --- 时间行 ---
                Row(
                    modifier = Modifier.padding(bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CalendarToday,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = contentColor
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = event.startDate.format(DateTimeFormatter.ofPattern("MM-dd")),
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = contentColor
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${event.startTime} - ${event.endTime}",
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor
                    )
                }

                // --- 展开详情区域 ---
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            thickness = 1.dp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (event.location.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier.padding(bottom = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = "地点",
                                    modifier = Modifier
                                        .size(14.dp)
                                        .padding(top = 2.dp),
                                    tint = if (isExpired) contentColor else MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = event.location,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = titleColor
                                )
                            }
                        }

                        if (event.description.isNotBlank()) {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    imageVector = Icons.Outlined.Description,
                                    contentDescription = "备注",
                                    modifier = Modifier
                                        .size(14.dp)
                                        .padding(top = 2.dp),
                                    tint = if (isExpired) contentColor else MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
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
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun StatusLabel(
    text: String,
    textColor: Color,
    backgroundColor: Color
) {
    Box(
        modifier = Modifier
            .background(backgroundColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.Bold
        )
    }
}