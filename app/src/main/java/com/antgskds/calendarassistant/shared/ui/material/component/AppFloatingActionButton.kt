package com.antgskds.calendarassistant.shared.ui.material.component

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object AppFloatingActionButtonDefaults {
    val Size = 72.dp
    val IconSize = 34.dp
}

/** 独立悬浮按钮及可展开浮动操作栏共用的材质表面，尺寸由调用方布局决定。 */
@Composable
fun AppFloatingActionSurface(
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    elevation: Dp = 6.dp,
    content: @Composable () -> Unit,
) {
    if (LocalAppGlassSettings.current.active) {
        AppGlassSurface(modifier = modifier, shape = shape, fallbackColor = containerColor) {
            Surface(color = Color.Transparent, contentColor = contentColor, content = content)
        }
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = containerColor,
            contentColor = contentColor,
            shadowElevation = elevation,
            content = content,
        )
    }
}

/** 保持现有固定尺寸。触感及业务行为由调用方提供，长按不会在松手时再触发单击。 */
@Composable
fun AppFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    content: @Composable () -> Unit,
) {
    if (!LocalAppGlassSettings.current.active && onLongClick == null) {
        // 普通模式沿用原生 FAB 的按压阴影、涟漪与无障碍行为。
        androidx.compose.material3.FloatingActionButton(
            onClick = onClick,
            modifier = modifier.size(AppFloatingActionButtonDefaults.Size),
            shape = shape,
            containerColor = containerColor,
            contentColor = contentColor,
            content = content,
        )
        return
    }
    AppFloatingActionSurface(
        modifier = modifier.size(AppFloatingActionButtonDefaults.Size),
        shape = shape,
        containerColor = containerColor,
        // 玻璃底材是中性的明/暗磨砂，不再沿用 primary 实色底上的 onPrimary。
        // 仅统一独立悬浮按钮的图标/文字；共享表面及首页组合栏的配色保持由调用方决定。
        contentColor = if (LocalAppGlassSettings.current.active) {
            MaterialTheme.colorScheme.onSurface
        } else {
            contentColor
        },
    ) {
        Box(
            modifier = Modifier.fillMaxSize().combinedClickable(
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = onLongClickLabel,
                hapticFeedbackEnabled = false,
            ),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}
