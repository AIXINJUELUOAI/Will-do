package com.antgskds.calendarassistant.shared.ui.material.component

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

/**
 * AppPageScaffold 在 edgeToEdgeContent 模式下提供的底部安全留白。
 * 放入 LazyColumn.contentPadding 或 verticalScroll 之后的 padding/末尾 Spacer；
 * 浮动按钮也使用此值避让。不要加在滚动容器外，否则会提前裁切滚动内容。
 * 固定布局和骨架整页滚动模式已经处理安全区，此值为零。
 * Sheet/Dialog 属于独立窗口，仍由其自身容器处理系统栏。
 */
val LocalAppPageBottomPadding = staticCompositionLocalOf { 0.dp }
