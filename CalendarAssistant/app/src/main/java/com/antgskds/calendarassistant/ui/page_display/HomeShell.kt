package com.antgskds.calendarassistant.ui.page_display

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.antgskds.calendarassistant.ui.contract.HomeShellUiAction
import com.antgskds.calendarassistant.ui.contract.HomeShellUiState
import com.antgskds.calendarassistant.ui.layout.PushSlideLayout
import com.antgskds.calendarassistant.ui.page_display.settings.AppBackgroundStyleTheme

@Composable
fun MaterialHomeScreen(
    state: HomeShellUiState,
    onAction: (HomeShellUiAction) -> Unit,
    sidebar: @Composable () -> Unit,
    content: @Composable () -> Unit,
    chrome: @Composable BoxScope.() -> Unit,
    overlay: @Composable BoxScope.() -> Unit,
) {
    AppBackgroundStyleTheme(
        enabled = state.backgroundEnabled,
        miuiBlurEnabled = state.backgroundMiuiBlurEnabled,
        cardAlphaPercent = state.backgroundCardAlphaPercent,
    ) {
        Box(modifier = Modifier) {
            BackHandler(enabled = state.isSidebarOpen) {
                onAction(HomeShellUiAction.SetSidebarOpen(false))
            }

            PushSlideLayout(
                isOpen = state.isSidebarOpen,
                onOpenChange = { isOpen ->
                    onAction(HomeShellUiAction.SetSidebarOpen(isOpen))
                },
                enableGesture = state.sidebarGestureEnabled,
                contentContainerColor = if (state.backgroundEnabled) {
                    Color.Transparent
                } else {
                    MaterialTheme.colorScheme.background
                },
                sidebar = sidebar,
                bottomBar = {},
                content = content,
            )

            chrome()
            overlay()
        }
    }
}
