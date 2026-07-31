package com.antgskds.calendarassistant.feature.schedule.ui.render

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antgskds.calendarassistant.feature.schedule.domain.course.CourseEventMapper
import com.antgskds.calendarassistant.feature.schedule.domain.model.EventTags
import com.antgskds.calendarassistant.feature.schedule.presentation.model.ScheduleDisplayItem
import com.antgskds.calendarassistant.shared.ui.interaction.rememberAppHaptics
import com.antgskds.calendarassistant.shared.util.stripSourceImageMarkers
import java.time.LocalDateTime
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Backup
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.Update
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun HyperEventItem(
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
    hapticEnabled: Boolean = true,
) {
    val actionButtonSize = when (uiSize) {
        1 -> 44.dp
        3 -> 52.dp
        else -> 48.dp
    }
    val actionCount = if (isArchivePage) 2 else 3
    val actionMenuWidth = when (actionCount) {
        2 -> 126.dp
        else -> 182.dp
    }
    val density = LocalDensity.current
    val actionMenuWidthPx = with(density) { actionMenuWidth.toPx() }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = rememberAppHaptics(hapticEnabled)
    var thresholdHapticPlayed by remember { mutableStateOf(false) }
    val revealedActionWidth = with(density) {
        (-offsetX.value).coerceIn(0f, actionMenuWidthPx).toDp()
    }
    val isExpired = remember(item.endTS, timeRefreshToken) {
        runCatching {
            LocalDateTime.of(item.endDate, item.endLocalTime).isBefore(LocalDateTime.now())
        }.getOrDefault(false)
    }

    LaunchedEffect(isRevealed) {
        offsetX.animateTo(if (isRevealed) -actionMenuWidthPx else 0f)
        if (!isRevealed) thresholdHapticPlayed = false
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Box(
            modifier = Modifier
                .width(revealedActionWidth)
                .fillMaxHeight()
                .clipToBounds(),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Row(
                modifier = Modifier
                    .width(actionMenuWidth)
                    .fillMaxHeight()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isArchivePage) {
                    HyperEventAction(MiuixIcons.Normal.Update, Color(0xFF35A854), actionButtonSize) {
                        onCollapse()
                        onRestore()
                    }
                    HyperEventAction(MiuixIcons.Normal.Delete, MiuixTheme.colorScheme.error, actionButtonSize) {
                        onCollapse()
                        onDelete()
                    }
                } else {
                    HyperEventAction(MiuixIcons.Normal.Edit, Color(0xFF35A854), actionButtonSize) {
                        onCollapse()
                        onEdit()
                    }
                    HyperEventAction(MiuixIcons.Normal.Favorites, Color(0xFFFFB21A), actionButtonSize) {
                        onCollapse()
                        onImportant()
                    }
                    val removeCourse = item.tag == "__removed_course__"
                    HyperEventAction(
                        icon = if (removeCourse) MiuixIcons.Normal.Delete else MiuixIcons.Normal.Backup,
                        tint = if (removeCourse) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary,
                        size = actionButtonSize,
                    ) {
                        onCollapse()
                        if (removeCourse) onDelete() else onArchive()
                    }
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(actionMenuWidthPx) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (offsetX.value < -actionMenuWidthPx / 2f) {
                                    if (!isRevealed) haptics.threshold()
                                    offsetX.animateTo(-actionMenuWidthPx)
                                    onExpand()
                                } else {
                                    offsetX.animateTo(0f)
                                    onCollapse()
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch { offsetX.animateTo(if (isRevealed) -actionMenuWidthPx else 0f) }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                val next = (offsetX.value + dragAmount).coerceIn(-actionMenuWidthPx, 0f)
                                if (!thresholdHapticPlayed && next < -actionMenuWidthPx / 2f) {
                                    haptics.threshold()
                                    thresholdHapticPlayed = true
                                } else if (next >= -actionMenuWidthPx / 2f) {
                                    thresholdHapticPlayed = false
                                }
                                offsetX.snapTo(next)
                            }
                        },
                    )
                },
            insideMargin = PaddingValues(0.dp),
            colors = CardDefaults.defaultColors(),
            onClick = {
                haptics.click()
                if (isRevealed) onCollapse() else onEdit()
            },
            onLongPress = onLongPress?.let { callback ->
                {
                    haptics.longPress()
                    onCollapse()
                    callback()
                }
            },
        ) {
            HyperEventContent(item = item, isExpired = isExpired)
        }
    }
}

@Composable
private fun HyperEventContent(
    item: ScheduleDisplayItem,
    isExpired: Boolean,
) {
    val timeText = if (item.startDate == item.endDate) {
        "${item.startTime} - ${item.endTime}"
    } else {
        val showYear = item.startDate.year != item.endDate.year
        val startDate = if (showYear) {
            "%02d-%02d-%02d".format(item.startDate.year % 100, item.startDate.monthValue, item.startDate.dayOfMonth)
        } else {
            "%02d-%02d".format(item.startDate.monthValue, item.startDate.dayOfMonth)
        }
        val endDate = if (showYear) {
            "%02d-%02d-%02d".format(item.endDate.year % 100, item.endDate.monthValue, item.endDate.dayOfMonth)
        } else {
            "%02d-%02d".format(item.endDate.monthValue, item.endDate.dayOfMonth)
        }
        "$startDate ${item.startTime} - $endDate ${item.endTime}"
    }
    val description = if (item.tag == EventTags.COURSE) {
        CourseEventMapper.displayDescription(item.description, item.location)
    } else {
        stripSourceImageMarkers(item.description)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isExpired) 0.58f else 1f)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(5.dp)
                .height(44.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(3.dp))
                .background(if (isExpired) MiuixTheme.colorScheme.outline else item.composeColor),
        )
        Spacer(Modifier.width(14.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = item.title,
                color = MiuixTheme.colorScheme.onSurfaceContainer,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                textDecoration = if (isExpired) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (isExpired) "$timeText · 已过期" else timeText,
                color = if (isExpired) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (description.isNotBlank()) {
                Text(
                    text = description,
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun HyperEventAction(
    icon: ImageVector,
    tint: Color,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(size),
        backgroundColor = tint.copy(alpha = 0.14f),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
    }
}
