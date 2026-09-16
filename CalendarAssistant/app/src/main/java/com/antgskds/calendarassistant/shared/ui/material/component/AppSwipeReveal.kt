package com.antgskds.calendarassistant.shared.ui.material.component

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import com.antgskds.calendarassistant.shared.ui.interaction.rememberAppHaptics
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 向左拖动显示右侧操作。业务只负责展开状态、动作与前景内容。
 * content 必须将收到的 Modifier 用在前景根节点；progress 可用于附属按钮淡出。
 * actions/content 的 close 同时通知业务并收回尚未越过阈值的拖动。
 * 保持透明，不新增背景表面，避免覆盖页面玻璃材质。
 */
@Composable
fun AppSwipeReveal(
    isRevealed: Boolean,
    actionWidth: Dp,
    onRevealedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    identity: Any? = Unit,
    revealThreshold: Float = 0.5f,
    hapticThreshold: Float = revealThreshold,
    dragOverflowPx: Float = 0f,
    animationSpec: AnimationSpec<Float> = spring(),
    hapticEnabled: Boolean = true,
    actions: @Composable (close: () -> Unit) -> Unit,
    content: @Composable (modifier: Modifier, progress: Float, close: () -> Unit) -> Unit,
) {
    require(revealThreshold > 0f && revealThreshold <= 1f)
    require(hapticThreshold >= revealThreshold && hapticThreshold <= 1f)
    require(dragOverflowPx >= 0f)
    val widthPx = with(LocalDensity.current) { actionWidth.toPx() }
    require(widthPx > 0f)
    val scope = rememberCoroutineScope()
    val haptics = rememberAppHaptics(hapticEnabled)
    val latestHaptics by rememberUpdatedState(haptics)
    val latestOnChange by rememberUpdatedState(onRevealedChange)
    val latestSpec by rememberUpdatedState(animationSpec)
    var offset by remember(identity) { mutableFloatStateOf(0f) }
    var thresholdPlayed by remember(identity) { mutableStateOf(false) }
    var animation by remember { mutableStateOf<Job?>(null) }

    fun settle(expanded: Boolean) {
        animation?.cancel()
        if (!expanded) thresholdPlayed = false
        animation = scope.launch {
            animate(offset, if (expanded) -widthPx else 0f, animationSpec = latestSpec) { value, _ ->
                offset = value
            }
        }
    }

    val close: () -> Unit = {
        latestOnChange(false)
        settle(false)
    }
    LaunchedEffect(identity, isRevealed, widthPx) {
        settle(isRevealed)
    }
    // 取消被替换条目的旧动画，防止列表复用节点时旧状态继续驱动新条目。
    DisposableEffect(identity) {
        onDispose { animation?.cancel() }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        // 布局始终使用完整动作宽度，只裁切绘制区域，避免拖动初期挤压按钮。
        Box(
            modifier = Modifier.matchParentSize().drawWithContent {
                val revealedWidth = (-offset).coerceIn(0f, widthPx)
                clipRect(left = (size.width - revealedWidth).coerceAtLeast(0f)) {
                    this@drawWithContent.drawContent()
                }
            },
            contentAlignment = Alignment.CenterEnd,
        ) {
            if (offset < -1f) {
                Box(Modifier.width(actionWidth), contentAlignment = Alignment.CenterEnd) {
                    actions(close)
                }
            }
        }
        val foreground = Modifier
            .fillMaxWidth()
            .offset { IntOffset(offset.roundToInt(), 0) }
            .pointerInput(identity, widthPx, revealThreshold, hapticThreshold, dragOverflowPx) {
                detectHorizontalDragGestures(
                    onDragStart = { animation?.cancel() },
                    onDragEnd = {
                        val expand = -offset >= widthPx * revealThreshold
                        if (expand && !thresholdPlayed) latestHaptics.threshold()
                        thresholdPlayed = expand
                        latestOnChange(expand)
                        settle(expand)
                    },
                    onDragCancel = {
                        latestOnChange(false)
                        settle(false)
                    },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        offset = (offset + amount).coerceIn(-widthPx - dragOverflowPx, 0f)
                        if (!thresholdPlayed && -offset >= widthPx * hapticThreshold) {
                            latestHaptics.threshold()
                            thresholdPlayed = true
                        } else if (-offset < widthPx * revealThreshold) {
                            thresholdPlayed = false
                        }
                    },
                )
            }
        content(foreground, (-offset / widthPx).coerceIn(0f, 1f), close)
    }
}
