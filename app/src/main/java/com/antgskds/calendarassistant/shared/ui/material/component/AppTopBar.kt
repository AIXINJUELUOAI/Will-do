package com.antgskds.calendarassistant.shared.ui.material.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.app.ui.theme.material.background.LocalAppBackgroundStyleEnabled
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog

/**
 * 手机标题居中，平板可沿内容边界左对齐，左右操作可以独立省略。
 * 标题两侧预留较宽操作区的同等空间；长标题省略，不能被单侧按钮推偏。
 * 顶部安全区由这里消费，页面内容交给 AppPageScaffold 处理。
 */
@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    containerColor: Color = if (LocalAppBackgroundStyleEnabled.current) {
        Color.Transparent
    } else {
        MaterialTheme.colorScheme.background
    },
    contentColor: Color = MaterialTheme.colorScheme.onBackground,
    centered: Boolean = true,
    windowInsets: WindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
) {
    Surface(modifier = modifier.fillMaxWidth(), color = containerColor, contentColor = contentColor) {
        Layout(
            modifier = Modifier
                .windowInsetsPadding(windowInsets)
                .padding(horizontal = if (centered) 4.dp else ConfigCatalog.ADAPTIVE_CONTENT_PADDING_DP.dp)
                .fillMaxWidth()
                .heightIn(min = 64.dp),
            content = {
                Box(contentAlignment = Alignment.Center) { navigationIcon() }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                Row(verticalAlignment = Alignment.CenterVertically, content = actions)
            },
        ) { measurables, constraints ->
            val width = constraints.maxWidth
            val sideConstraints = constraints.copy(minWidth = 0, minHeight = 0, maxWidth = width / 2)
            val leading = measurables[0].measure(sideConstraints)
            val trailing = measurables[2].measure(sideConstraints)
            val sideWidth = maxOf(leading.width, trailing.width)
            val titleGap = if (sideWidth > 0) 8.dp.roundToPx() else 0
            val titleStart = leading.width + if (leading.width > 0) titleGap else 0
            val titleWidth = (if (centered) width - 2 * (sideWidth + titleGap)
                else width - titleStart - trailing.width - titleGap).coerceAtLeast(0)
            val titlePlaceable = measurables[1].measure(
                constraints.copy(minWidth = 0, minHeight = 0, maxWidth = titleWidth)
            )
            val height = maxOf(constraints.minHeight, leading.height, trailing.height, titlePlaceable.height)
                .coerceAtMost(constraints.maxHeight)
            layout(width, height) {
                leading.placeRelative(0, (height - leading.height) / 2)
                titlePlaceable.placeRelative(if (centered) (width - titlePlaceable.width) / 2 else titleStart, (height - titlePlaceable.height) / 2)
                trailing.placeRelative(width - trailing.width, (height - trailing.height) / 2)
            }
        }
    }
}

/** 返回行为和触感由调用方提供；此处只统一按钮热区、图标及无障碍描述。 */
@Composable
fun AppTopBarBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 28.dp,
) {
    IconButton(onClick = onClick, modifier = modifier.size(48.dp)) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", modifier = Modifier.size(iconSize))
    }
}
