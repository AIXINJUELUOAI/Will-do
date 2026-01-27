package com.antgskds.calendarassistant.ui.layout

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch

@Composable
fun PushSlideLayout(
    isOpen: Boolean,
    onOpenChange: (Boolean) -> Unit,
    sidebar: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit,
    content: @Composable () -> Unit,
    enableGesture: Boolean = true
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 动画进度：0 = 关闭，1 = 打开
    val progress = remember { Animatable(0f) }

    // 监听外部状态变化，驱动动画
    LaunchedEffect(isOpen) {
        val target = if (isOpen) 1f else 0f
        progress.animateTo(target, animationSpec = tween(300))
    }

    // 侧边栏宽度：使用屏幕实际物理像素的50%，确保在任何 DPI 下都是真正的 50%
    val displayMetrics = context.resources.displayMetrics
    val screenWidthPx = displayMetrics.widthPixels.toFloat()
    val sidebarWidthDp = with(density) { (screenWidthPx / 2f).toDp() }

    // 遮罩层透明度：随进度变化
    val scrimAlpha = progress.value * 0.5f

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 上半部分：内容区域
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // 侧边栏：固定宽度50%，从左边滑入（offset从-50%到0）
                Box(
                    modifier = Modifier
                        .width(sidebarWidthDp)
                        .fillMaxHeight()
                        .offset {
                            val offsetDp = (progress.value - 1f) * sidebarWidthDp.value
                            IntOffset(
                                x = (offsetDp * density.density).toInt(),
                                y = 0
                            )
                        }
                ) {
                    sidebar()
                }

                // 主内容：始终100%宽度，整体向右平移
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset {
                            val offsetDp = progress.value * sidebarWidthDp.value
                            IntOffset(
                                x = (offsetDp * density.density).toInt(),
                                y = 0
                            )
                        }
                        .background(MaterialTheme.colorScheme.background)
                        .draggable(
                            enabled = enableGesture,
                            state = rememberDraggableState { delta ->
                                // 将像素转换为进度变化
                                val sidebarWidthPx = with(density) { sidebarWidthDp.toPx() }
                                val deltaProgress = delta / sidebarWidthPx
                                val newProgress = (progress.value + deltaProgress).coerceIn(0f, 1f)
                                scope.launch { progress.snapTo(newProgress) }
                            },
                            orientation = Orientation.Horizontal,
                            onDragStopped = { velocity ->
                                // 根据速度和当前进度决定最终状态
                                val targetProgress = if (velocity > 0 || progress.value > 0.5f) 1f else 0f
                                onOpenChange(targetProgress == 1f)
                                scope.launch { progress.animateTo(targetProgress, animationSpec = tween(300)) }
                            }
                        )
                ) {
                    content()
                }

                // 遮罩层：侧边栏打开时覆盖主内容，点击或左滑关闭侧边栏（跟随主内容移动）
                if (scrimAlpha > 0.01f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .offset {
                                val offsetDp = progress.value * sidebarWidthDp.value
                                IntOffset(
                                    x = (offsetDp * density.density).toInt(),
                                    y = 0
                                )
                            }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onOpenChange(false) }
                            )
                            .draggable(
                                enabled = enableGesture,
                                state = rememberDraggableState { delta ->
                                    // 将像素转换为进度变化
                                    val sidebarWidthPx = with(density) { sidebarWidthDp.toPx() }
                                    val deltaProgress = delta / sidebarWidthPx
                                    val newProgress = (progress.value + deltaProgress).coerceIn(0f, 1f)
                                    scope.launch { progress.snapTo(newProgress) }
                                },
                                orientation = Orientation.Horizontal,
                                onDragStopped = { velocity ->
                                    // 根据速度和当前进度决定最终状态
                                    val targetProgress = if (velocity > 0 || progress.value > 0.5f) 1f else 0f
                                    onOpenChange(targetProgress == 1f)
                                    scope.launch { progress.animateTo(targetProgress, animationSpec = tween(300)) }
                                }
                            )
                            .zIndex(1f)
                    )
                }
            }

            // 下半部分：固定底栏
            Box(modifier = Modifier.zIndex(2f)) {
                bottomBar()
            }
        }
    }
}
