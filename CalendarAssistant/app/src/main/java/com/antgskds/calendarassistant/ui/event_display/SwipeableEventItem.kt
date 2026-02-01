package com.antgskds.calendarassistant.ui.event_display

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.core.util.DateCalculator
import com.antgskds.calendarassistant.data.model.MyEvent
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableEventItem(
    event: MyEvent,
    isRevealed: Boolean,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onDelete: (MyEvent) -> Unit,
    onImportant: (MyEvent) -> Unit,
    onEdit: (MyEvent) -> Unit,
    uiSize: Int = 2, // 1=小, 2=中, 3=大
    // 归档相关参数
    isArchivePage: Boolean = false, // 是否为归档页模式
    onArchive: (MyEvent) -> Unit = {}, // 归档回调（主页用）
    onRestore: (MyEvent) -> Unit = {}  // 还原回调（归档页用）
) {
    // 根据 uiSize 计算按钮大小和菜单宽度
    val actionButtonSize = when (uiSize) {
        1 -> 48.dp  // 小
        2 -> 52.dp  // 中
        else -> 56.dp // 大
    }

    // 根据 uiSize 计算菜单宽度 (3个按钮 + 间距 + 右侧内边距)
    // 归档页模式只有2个按钮，宽度稍小
    val actionMenuWidth = when (uiSize) {
        1 -> if (isArchivePage) 130.dp else 170.dp  // 小
        2 -> if (isArchivePage) 140.dp else 185.dp  // 中
        else -> if (isArchivePage) 150.dp else 200.dp // 大
    }
    val density = LocalDensity.current
    val actionMenuWidthPx = with(density) { actionMenuWidth.toPx() }

    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    // 调用 DateCalculator 中的工具函数
    // 移除 remember 缓存，让 isExpired 每次重组时重新计算
    // 这样 _timeTrigger 触发重组时，过期状态能实时更新
    val isExpired = DateCalculator.isEventExpired(event)

    LaunchedEffect(isRevealed) {
        if (isRevealed) {
            offsetX.animateTo(-actionMenuWidthPx)
        } else {
            offsetX.animateTo(0f)
        }
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterEnd
    ) {
        // --- 背景层：操作菜单 ---
        Row(
            modifier = Modifier
                .width(actionMenuWidth)
                .fillMaxHeight()
                .padding(end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 归档页模式：显示还原和删除按钮
            if (isArchivePage) {
                SwipeActionIcon(Icons.Outlined.Restore, Color(0xFF4CAF50), actionButtonSize) {
                    onCollapse()
                    onRestore(event)
                }
                SwipeActionIcon(Icons.Outlined.Delete, Color(0xFFF44336), actionButtonSize) {
                    onCollapse()
                    onDelete(event)
                }
            } else {
                // 正常模式：显示编辑、星标、归档/删除按钮
                SwipeActionIcon(Icons.Outlined.Edit, Color(0xFF4CAF50), actionButtonSize) {
                    onCollapse()
                    onEdit(event)
                }
                SwipeActionIcon(
                    if (event.isImportant) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    Color(0xFFFFC107),
                    actionButtonSize
                ) {
                    onCollapse()
                    onImportant(event)
                }
                // 🔥 修复：课程(course) 和 临时取件码(temp) 显示删除按钮，普通日程(event) 显示归档按钮
                if (event.eventType == "course" || event.eventType == "temp") {
                    SwipeActionIcon(Icons.Outlined.Delete, Color(0xFFF44336), actionButtonSize) {
                        onCollapse()
                        onDelete(event)
                    }
                } else {
                    SwipeActionIcon(Icons.Outlined.Archive, Color(0xFF2196F3), actionButtonSize) {
                        onCollapse()
                        onArchive(event)
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
                                    offsetX.animateTo(-actionMenuWidthPx)
                                    onExpand()
                                } else {
                                    offsetX.animateTo(0f)
                                    onCollapse()
                                }
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            scope.launch {
                                val newOffset = (offsetX.value + dragAmount).coerceIn(-actionMenuWidthPx, 0f)
                                offsetX.snapTo(newOffset)
                            }
                        }
                    )
                }
                .clickable {
                    if (isRevealed) onCollapse() else onEdit(event)
                },
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.alpha(if (isExpired) 0.6f else 1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左侧彩色条
                    Box(
                        Modifier
                            .width(if (event.isImportant) 8.dp else 5.dp)
                            .height(40.dp) // 这里的固定高度可能需要根据内容自适应，但在Row中通常没问题
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (isExpired) Color.LightGray else event.color)
                    )
                    Spacer(Modifier.width(16.dp))

                    // 右侧内容区域：使用 when 进行布局分流
                    Column(Modifier.weight(1f)) {

                        when (event.eventType) {
                            // =================================================
                            // case 1: 临时取件码模式 (倒序排列：平台 -> 码 -> 地点)
                            // =================================================
                            "temp" -> {
                                // 1. 顶部：平台名称 (Description)
                                if (event.description.isNotBlank()) {
                                    Text(
                                        text = event.description, // 例如 "菜鸟驿站"
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // 2. 中间：取件码 (Title) - 视觉重心
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = event.title, // 例如 "A-1-9915"
                                        style = MaterialTheme.typography.headlineSmall, // 更大更醒目
                                        fontWeight = FontWeight.Black,
                                        textDecoration = if (isExpired) TextDecoration.LineThrough else null
                                    )
                                    // 星标紧跟大标题
                                    if (event.isImportant) {
                                        Icon(
                                            Icons.Default.Star,
                                            null,
                                            Modifier.size(18.dp).padding(start = 4.dp), // 稍微大一点
                                            tint = Color(0xFFFFC107)
                                        )
                                    }
                                }

                                // 3. 底部：地点
                                if (event.location.isNotBlank()) {
                                    Text(
                                        text = event.location,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray
                                    )
                                }
                            }

                            // =================================================
                            // case 2 (Default): 普通日程模式 (标准顺序)
                            // =================================================
                            else -> {
                                // 1. 顶部：标题 (Title)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = event.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        textDecoration = if (isExpired) TextDecoration.LineThrough else null
                                    )
                                    if (event.isImportant) {
                                        Icon(
                                            Icons.Default.Star,
                                            null,
                                            Modifier.size(16.dp).padding(start = 4.dp),
                                            tint = Color(0xFFFFC107)
                                        )
                                    }
                                }

                                // 2. 日期范围（仅多日事件显示）
                                if (event.startDate != event.endDate) {
                                    Text(
                                        text = "${event.startDate.monthValue}/${event.startDate.dayOfMonth} - ${event.endDate.monthValue}/${event.endDate.dayOfMonth}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                // 3. 时间信息
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${event.startTime} - ${event.endTime}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isExpired) Color.Gray else MaterialTheme.colorScheme.primary
                                    )
                                    if (isExpired) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "(已过期)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // 3. 描述 (Description)
                                if (event.description.isNotBlank()) {
                                    Text(
                                        text = event.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2
                                    )
                                }

                                // 4. 地点
                                if (event.location.isNotBlank()) {
                                    Text(
                                        text = event.location,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray
                                    )
                                }
                            }
                        } // End when
                    }
                }
            }
        }
    }
}

@Composable
fun SwipeActionIcon(icon: ImageVector, tint: Color, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(size)
            .padding(4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.15f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = tint)
    }
}