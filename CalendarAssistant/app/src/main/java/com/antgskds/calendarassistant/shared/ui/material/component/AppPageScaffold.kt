package com.antgskds.calendarassistant.shared.ui.material.component

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.app.ui.theme.material.background.LocalAppBackgroundStyleEnabled

/**
 * 页面骨架：统一处理顶部栏、系统栏、底栏及可选的键盘避让。
 * 传入 scrollState 时由骨架负责整页滚动，内容不可再嵌套同方向的滚动容器。
 * 无底栏时，滚动视口延伸到导航栏背后，底部安全留白随内容滚动，避免在导航栏上方截断。
 * 默认内容整体位于安全区内。自行滚动的列表可开启 edgeToEdgeContent，
 * 将 LocalAppPageBottomPadding 放入列表 contentPadding，浮动操作也使用同一留白。
 * 业务内容仅设置自身排版，不再添加 statusBarsPadding/navigationBarsPadding/imePadding。
 * 壁纸由主窗口绘制，这里跟随背景主题保持透明；不会再次创建玻璃采样层。
 * bottomBar 接收安全区内的空间，顶部推荐使用自行消费顶部安全区的 AppTopBar。
 */
@Composable
fun AppPageScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: (@Composable () -> Unit)? = null,
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    contentMaxWidth: Dp = 960.dp,
    avoidKeyboard: Boolean = true,
    scrollState: ScrollState? = null,
    edgeToEdgeContent: Boolean = false,
    containerColor: Color = if (LocalAppBackgroundStyleEnabled.current) {
        Color.Transparent
    } else {
        MaterialTheme.colorScheme.background
    },
    content: @Composable BoxScope.() -> Unit,
) {
    val systemInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    val layoutDirection = LocalLayoutDirection.current
    Scaffold(
        modifier = modifier.fillMaxSize().then(if (avoidKeyboard) Modifier.imePadding() else Modifier),
        containerColor = containerColor,
        contentColor = MaterialTheme.colorScheme.onBackground,
        contentWindowInsets = systemInsets,
        topBar = topBar,
        bottomBar = {
            bottomBar?.let { bar ->
                Box(
                    modifier = Modifier.fillMaxWidth().windowInsetsPadding(
                        systemInsets.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                    ),
                ) { bar() }
            }
        },
        floatingActionButton = floatingActionButton,
        snackbarHost = snackbarHost,
    ) { innerPadding ->
        // 固定底栏仍在滚动视口外；只有系统导航栏留白需要移到滚动内容末端。
        val deferredBottomPadding = if ((scrollState != null || edgeToEdgeContent) && bottomBar == null) {
            innerPadding.calculateBottomPadding()
        } else {
            0.dp
        }
        val viewportPadding = PaddingValues(
            start = innerPadding.calculateStartPadding(layoutDirection),
            top = innerPadding.calculateTopPadding(),
            end = innerPadding.calculateEndPadding(layoutDirection),
            bottom = innerPadding.calculateBottomPadding() - deferredBottomPadding,
        )
        Box(
            // 仅消费实际放在视口外的安全区；嵌套详情骨架仍能读取待处理的底部 inset。
            modifier = Modifier.fillMaxSize().padding(viewportPadding).consumeWindowInsets(viewportPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                modifier = Modifier.widthIn(max = contentMaxWidth).fillMaxSize()
                    .then(if (scrollState != null) Modifier.verticalScroll(scrollState) else Modifier)
                    .padding(bottom = if (scrollState != null) deferredBottomPadding else 0.dp)
                    .consumeWindowInsets(PaddingValues(bottom = if (scrollState != null) deferredBottomPadding else 0.dp)),
                // 保留短页面的可用最小高度，使关于页仍能在安全区内纵向居中。
                propagateMinConstraints = true,
            ) {
                CompositionLocalProvider(
                    LocalAppPageBottomPadding provides if (scrollState == null) deferredBottomPadding else 0.dp,
                ) {
                    content()
                }
            }
        }
    }
}
