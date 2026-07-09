package com.antgskds.calendarassistant.ui.event_display

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.data.model.ScheduleDisplayItem
import com.antgskds.calendarassistant.calendar.models.EventTags
import com.antgskds.calendarassistant.core.course.CourseEventMapper
import com.antgskds.calendarassistant.core.util.stripSourceImageMarkers
import com.antgskds.calendarassistant.ui.haptic.rememberAppHaptics
import com.antgskds.calendarassistant.ui.page_display.settings.LocalAppBackgroundStyleEnabled
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SwipeableEventItem(
    item: ScheduleDisplayItem,
    isRevealed: Boolean,
    timeRefreshToken: Long = 0L,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    uiSize: Int = 2,
    isArchivePage: Boolean = false,
    onArchive: () -> Unit = {},
    onRestore: () -> Unit = {},
    onImportant: () -> Unit = {},
    hapticEnabled: Boolean = true
) {
    val actionButtonSize = when (uiSize) {
        1 -> 48.dp; 2 -> 52.dp; else -> 56.dp
    }

    // ✅ 重复日程和普通日程统一 3 个操作按钮
    val actionButtonCount = when {
        isArchivePage -> 2
        else -> 3
    }

    val actionMenuWidth = when (uiSize) {
        1 -> when (actionButtonCount) { 1 -> 78.dp; 2 -> 130.dp; else -> 170.dp }
        2 -> when (actionButtonCount) { 1 -> 86.dp; 2 -> 140.dp; else -> 185.dp }
        else -> when (actionButtonCount) { 1 -> 94.dp; 2 -> 150.dp; else -> 200.dp }
    }
    val density = LocalDensity.current
    val actionMenuWidthPx = with(density) { actionMenuWidth.toPx() }
    val haptics = rememberAppHaptics(hapticEnabled)
    val usesWallpaperText = LocalAppBackgroundStyleEnabled.current
    val primaryTextColor = if (usesWallpaperText) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = if (usesWallpaperText) {
        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var thresholdHapticPlayed by remember { mutableStateOf(false) }
    val revealedActionWidth = with(density) {
        (-offsetX.value).coerceIn(0f, actionMenuWidthPx).toDp()
    }

    val isExpired = remember(item.endTS, timeRefreshToken) {
        try {
            val endDateTime = LocalDateTime.of(item.endDate, item.endLocalTime)
            endDateTime.isBefore(LocalDateTime.now())
        } catch (_: Exception) { false }
    }

    LaunchedEffect(isRevealed) {
        if (isRevealed) offsetX.animateTo(-actionMenuWidthPx)
        else offsetX.animateTo(0f)
    }

    LaunchedEffect(isRevealed) {
        if (!isRevealed) thresholdHapticPlayed = false
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterEnd
    ) {
        // --- 背景层：操作菜单 ---
        Box(
            modifier = Modifier
                .width(revealedActionWidth)
                .fillMaxHeight()
                .clipToBounds(),
            contentAlignment = Alignment.CenterEnd
        ) {
            Row(
                modifier = Modifier
                    .width(actionMenuWidth)
                    .fillMaxHeight()
                    .padding(end = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isArchivePage) {
                    SwipeActionIcon(Icons.Outlined.Restore, Color(0xFF4CAF50), actionButtonSize, hapticEnabled) {
                        onCollapse(); onRestore()
                    }
                    SwipeActionIcon(Icons.Outlined.Delete, Color(0xFFF44336), actionButtonSize, hapticEnabled) {
                        onCollapse(); onDelete()
                    }
                } else {
                    // ✅ 所有日程（含重复）统一显示：编辑 / 重要 / 归档(或删除)
                    SwipeActionIcon(Icons.Outlined.Edit, Color(0xFF4CAF50), actionButtonSize, hapticEnabled) {
                        onCollapse(); onEdit()
                    }
                    SwipeActionIcon(Icons.Outlined.StarOutline, Color(0xFFFFC107), actionButtonSize, hapticEnabled) {
                        onCollapse(); onImportant()
                    }
                    if (item.tag == "__removed_course__") {
                        SwipeActionIcon(Icons.Outlined.Delete, Color(0xFFF44336), actionButtonSize, hapticEnabled) {
                            onCollapse(); onDelete()
                        }
                    } else {
                        SwipeActionIcon(Icons.Outlined.Archive, Color(0xFF2196F3), actionButtonSize, hapticEnabled) {
                            onCollapse(); onArchive()
                        }
                    }
                }
            }
        }

        // --- 前景层：日程卡片 ---
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (offsetX.value < -actionMenuWidthPx / 2) {
                                    if (!isRevealed) haptics.threshold()
                                    thresholdHapticPlayed = true
                                    offsetX.animateTo(-actionMenuWidthPx); onExpand()
                                } else {
                                    offsetX.animateTo(0f); onCollapse()
                                }
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            scope.launch {
                                val newOffset = (offsetX.value + dragAmount).coerceIn(-actionMenuWidthPx, 0f)
                                if (!thresholdHapticPlayed && newOffset < -actionMenuWidthPx / 2) {
                                    haptics.threshold()
                                    thresholdHapticPlayed = true
                                } else if (newOffset >= -actionMenuWidthPx / 2) {
                                    thresholdHapticPlayed = false
                                }
                                offsetX.snapTo(newOffset)
                            }
                        }
                    )
                }
                .combinedClickable(
                    onClick = {
                        haptics.click()
                        if (isRevealed) onCollapse() else onEdit()
                    },
                    onLongClick = onLongPress?.let { callback ->
                        {
                            haptics.longPress()
                            onCollapse()
                            callback()
                        }
                    }
                ),
            color = Color.Transparent,
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.alpha(if (isExpired) 0.6f else 1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp, start = 20.dp, end = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左侧彩色条
                    Box(
                        Modifier
                            .width(5.dp)
                            .height(40.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (isExpired) Color.LightGray else item.composeColor)
                    )
                    Spacer(Modifier.width(16.dp))

                    Column(Modifier.weight(1f)) {
                        // ✅ 标题行：去掉循环图标，干净显示标题
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isExpired) secondaryTextColor else primaryTextColor,
                            textDecoration = if (isExpired) TextDecoration.LineThrough else null
                        )

                        // 时间
                        val isSingleDay = item.startDate == item.endDate
                        val timeDisplayText = if (isSingleDay) {
                            "${item.startTime} - ${item.endTime}"
                        } else {
                            val crossYear = item.startDate.year != item.endDate.year
                            val startFmt = if (crossYear) String.format("%02d-%02d-%02d", item.startDate.year % 100, item.startDate.monthValue, item.startDate.dayOfMonth)
                                else String.format("%02d-%02d", item.startDate.monthValue, item.startDate.dayOfMonth)
                            val endFmt = if (crossYear) String.format("%02d-%02d-%02d", item.endDate.year % 100, item.endDate.monthValue, item.endDate.dayOfMonth)
                                else String.format("%02d-%02d", item.endDate.monthValue, item.endDate.dayOfMonth)
                            "$startFmt ${item.startTime} - $endFmt ${item.endTime}"
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = timeDisplayText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isExpired) Color.Gray else MaterialTheme.colorScheme.primary
                            )
                            if (isExpired) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("(已过期)", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                            }
                        }

                        // 描述
                        val displayDescription = if (item.tag == EventTags.COURSE) {
                            CourseEventMapper.displayDescription(item.description, item.location)
                        } else {
                            stripSourceImageMarkers(item.description)
                        }
                        if (displayDescription.isNotBlank()) {
                            Text(
                                text = displayDescription,
                                style = MaterialTheme.typography.bodyMedium,
                                color = secondaryTextColor,
                                maxLines = 2
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SwipeActionIcon(
    icon: ImageVector,
    tint: Color,
    size: androidx.compose.ui.unit.Dp,
    hapticEnabled: Boolean = true,
    onClick: () -> Unit
) {
    val haptics = rememberAppHaptics(hapticEnabled)
    Box(
        modifier = Modifier
            .size(size)
            .padding(4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.15f))
            .clickable {
                haptics.click()
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = tint)
    }
}
