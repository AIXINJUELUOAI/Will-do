package com.antgskds.calendarassistant.shared.ui.material.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

data class AppMenuItem(
    val text: String,
    val onClick: () -> Unit,
    val icon: ImageVector? = null,
    val selected: Boolean = false,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
)

/**
 * 放在按钮所在的 Box 中，Popup 自动以该 Box 为锚点。
 * 独立窗口不进入宿主的背景采样图层，避免同窗口菜单采到包含自己的场景。
 * 业务负责点击/长按与触感；公共组件负责关闭后执行菜单动作。
 */
@Composable
fun AppDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    items: List<AppMenuItem>,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    selectionColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val transition = remember { MutableTransitionState(false) }
    transition.targetState = expanded
    if (!transition.currentState && !transition.targetState) return

    val density = LocalDensity.current
    val safeInsets = WindowInsets.safeDrawing.asPaddingValues()
    val windowHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    val maxHeight = (windowHeight - safeInsets.calculateTopPadding() -
        safeInsets.calculateBottomPadding() - 16.dp).coerceAtLeast(0.dp)
    val positionProvider = remember(density, safeInsets.calculateTopPadding(), safeInsets.calculateBottomPadding()) {
        with(density) {
            MenuPositionProvider(8.dp.roundToPx(), 4.dp.roundToPx(),
                safeInsets.calculateTopPadding().roundToPx(), safeInsets.calculateBottomPadding().roundToPx())
        }
    }
    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true, usePlatformDefaultWidth = false),
    ) {
        AnimatedVisibility(
            visibleState = transition,
            enter = fadeIn(tween(180)) + scaleIn(tween(220), initialScale = 0.86f,
                transformOrigin = TransformOrigin(1f, 0f)),
            exit = fadeOut(tween(140)) + scaleOut(tween(180), targetScale = 0.9f,
                transformOrigin = TransformOrigin(1f, 0f)),
        ) {
            val shape = RoundedCornerShape(24.dp)
            AppOverlayGlassSurface(
                modifier = modifier.width(208.dp).heightIn(max = maxHeight)
                    .shadow(8.dp, shape, clip = false),
                shape = shape,
                fallbackColor = containerColor,
            ) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
                    items.forEach { item ->
                        val tint = if (item.destructive) MaterialTheme.colorScheme.error else contentColor
                        DropdownMenuItem(
                            text = { Text(item.text, style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium) },
                            onClick = { onDismissRequest(); item.onClick() },
                            enabled = expanded && item.enabled,
                            leadingIcon = item.icon?.let { icon -> {
                                Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
                            } },
                            trailingIcon = if (item.selected) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp)) }
                            } else null,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                .fillMaxWidth().heightIn(min = 52.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (item.selected) selectionColor else Color.Transparent)
                                .semantics { selected = item.selected },
                            colors = MenuDefaults.itemColors(textColor = tint, leadingIconColor = tint, trailingIconColor = tint),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 优先锚点下方、靠结束边；底部空间不足时翻到上方，并限制在窗口内。 */
private data class MenuPositionProvider(
    val margin: Int,
    val gap: Int,
    val topInset: Int,
    val bottomInset: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val desiredX = if (layoutDirection == LayoutDirection.Ltr)
            anchorBounds.right - popupContentSize.width else anchorBounds.left
        val minX = margin.coerceAtMost((windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val maxX = (windowSize.width - popupContentSize.width - margin).coerceAtLeast(minX)
        val minY = (topInset + margin).coerceAtMost((windowSize.height - popupContentSize.height).coerceAtLeast(0))
        val maxY = (windowSize.height - bottomInset - margin - popupContentSize.height).coerceAtLeast(minY)
        val below = anchorBounds.bottom + gap
        val desiredY = if (below <= maxY) below else anchorBounds.top - gap - popupContentSize.height
        return IntOffset(desiredX.coerceIn(minX, maxX), desiredY.coerceIn(minY, maxY))
    }
}
