package com.antgskds.calendarassistant.ui.flavor

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import com.antgskds.calendarassistant.ui.contract.HomeShellUiAction
import com.antgskds.calendarassistant.ui.contract.HomeShellUiState
import com.antgskds.calendarassistant.ui.page_display.MaterialHomeScreen

@Composable
fun HomeScreenContent(
    state: HomeShellUiState,
    onAction: (HomeShellUiAction) -> Unit,
    sidebar: @Composable () -> Unit,
    content: @Composable () -> Unit,
    chrome: @Composable BoxScope.() -> Unit,
    overlay: @Composable BoxScope.() -> Unit,
) {
    MaterialHomeScreen(
        state = state,
        onAction = onAction,
        sidebar = sidebar,
        content = content,
        chrome = chrome,
        overlay = overlay,
    )
}
