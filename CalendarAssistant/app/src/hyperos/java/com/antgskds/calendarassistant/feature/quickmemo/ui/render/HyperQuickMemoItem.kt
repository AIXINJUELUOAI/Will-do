package com.antgskds.calendarassistant.feature.quickmemo.ui.render

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.antgskds.calendarassistant.feature.quickmemo.application.audio.AudioPlaybackState
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoEntity
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoTodoState
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoType
import com.antgskds.calendarassistant.shared.ui.interaction.rememberAppHaptics
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.icon.extended.Pin
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.icon.extended.Unpin
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun HyperQuickMemoItem(
    memo: QuickMemoEntity,
    hasSuggestions: Boolean,
    playbackState: AudioPlaybackState,
    isPinned: Boolean,
    onToggleTodo: () -> Unit,
    onToggleTodoMode: () -> Unit,
    onTogglePinned: () -> Unit,
    onDelete: () -> Unit,
    onToggleAudio: (String?) -> Unit,
    onOpenDetail: () -> Unit,
    onLongPress: () -> Unit,
    hapticEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberAppHaptics(hapticEnabled)
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val isTodo = memo.todoState != QuickMemoTodoState.NONE
    val isCompleted = memo.todoState == QuickMemoTodoState.COMPLETED
    val isVoice = memo.type == QuickMemoType.VOICE
    val isPlaying = memo.audioPath != null &&
        playbackState.audioPath == memo.audioPath &&
        playbackState.isPlaying
    val actionWidth = 174.dp
    val actionWidthPx = with(density) { actionWidth.toPx() }
    val offsetX = remember(memo.id) { Animatable(0f) }
    val swipeSpec = spring<Float>(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
    val revealedWidth = with(density) { (-offsetX.value).coerceIn(0f, actionWidthPx).toDp() }
    val body = memo.bodyText.ifBlank {
        when (memo.type) {
            QuickMemoType.VOICE -> "语音随口记"
            QuickMemoType.IMAGE -> "图片随口记"
            else -> "空白随口记"
        }
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Box(
            modifier = Modifier
                .width(revealedWidth)
                .fillMaxHeight()
                .clipToBounds(),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Row(
                modifier = Modifier
                    .width(actionWidth)
                    .fillMaxHeight()
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HyperMemoAction(
                    icon = if (isPinned) MiuixIcons.Normal.Unpin else MiuixIcons.Normal.Pin,
                    tint = MiuixTheme.colorScheme.primary,
                ) {
                    onTogglePinned()
                    scope.launch { offsetX.animateTo(0f, swipeSpec) }
                }
                HyperMemoAction(
                    icon = if (isTodo) MiuixIcons.Normal.Close else MiuixIcons.Normal.SelectAll,
                    tint = Color(0xFFFFB21A),
                ) {
                    onToggleTodoMode()
                    scope.launch { offsetX.animateTo(0f, swipeSpec) }
                }
                HyperMemoAction(
                    icon = MiuixIcons.Normal.Delete,
                    tint = MiuixTheme.colorScheme.error,
                ) {
                    onDelete()
                    scope.launch { offsetX.animateTo(0f, swipeSpec) }
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(memo.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                val target = if (-offsetX.value >= actionWidthPx * 0.35f) -actionWidthPx else 0f
                                if (target < 0f) haptics.threshold()
                                offsetX.animateTo(target, swipeSpec)
                            }
                        },
                        onDragCancel = { scope.launch { offsetX.animateTo(0f, swipeSpec) } },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            scope.launch {
                                offsetX.snapTo((offsetX.value + amount).coerceIn(-actionWidthPx, 0f))
                            }
                        },
                    )
                },
            insideMargin = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
            onClick = {
                haptics.click()
                if (offsetX.value < -1f) {
                    scope.launch { offsetX.animateTo(0f, swipeSpec) }
                } else {
                    onOpenDetail()
                }
            },
            onLongPress = {
                haptics.longPress()
                onLongPress()
            },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(5.dp)
                        .height(48.dp)
                        .background(
                            when {
                                isCompleted -> MiuixTheme.colorScheme.outline
                                isTodo -> Color(0xFFFFB21A)
                                else -> MiuixTheme.colorScheme.primary
                            },
                        ),
                )
                Spacer(Modifier.width(14.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = body,
                        color = if (isCompleted) {
                            MiuixTheme.colorScheme.onSurfaceContainerVariant
                        } else {
                            MiuixTheme.colorScheme.onSurfaceContainer
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        textDecoration = if (isCompleted) TextDecoration.LineThrough else null,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = buildString {
                            append(formatMemoTime(memo.createdAt))
                            if (hasSuggestions) append(" · 有日程推荐")
                            if (isPinned) append(" · 已挂起")
                        },
                        color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (isVoice) {
                    Spacer(Modifier.width(10.dp))
                    IconButton(
                        onClick = {
                            haptics.click()
                            onToggleAudio(memo.audioPath)
                        },
                        backgroundColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.12f),
                    ) {
                        Icon(
                            imageVector = if (isPlaying) MiuixIcons.Normal.Pause else MiuixIcons.Normal.Play,
                            contentDescription = if (isPlaying) "暂停" else "播放",
                            tint = MiuixTheme.colorScheme.primary,
                        )
                    }
                } else if (isTodo) {
                    Spacer(Modifier.width(10.dp))
                    IconButton(
                        onClick = onToggleTodo,
                        backgroundColor = Color(0xFFFFB21A).copy(alpha = 0.14f),
                    ) {
                        Icon(
                            imageVector = if (isCompleted) MiuixIcons.Normal.Close else MiuixIcons.Normal.SelectAll,
                            contentDescription = if (isCompleted) "标记未完成" else "标记完成",
                            tint = Color(0xFFFFA000),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HyperMemoAction(
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
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

private fun formatMemoTime(timestamp: Long): String =
    DateTimeFormatter.ofPattern("HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(timestamp))
