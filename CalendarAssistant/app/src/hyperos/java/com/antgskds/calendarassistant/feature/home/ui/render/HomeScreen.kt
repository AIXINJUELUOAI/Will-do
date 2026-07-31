package com.antgskds.calendarassistant.feature.home.ui.render

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.antgskds.calendarassistant.app.ui.theme.material.background.AppBackgroundStyleTheme
import com.antgskds.calendarassistant.feature.home.ui.contract.HomeShellUiAction
import com.antgskds.calendarassistant.feature.home.ui.contract.HomeShellUiState

@Composable
fun HomeScreenContent(
    state: HomeShellUiState,
    onAction: (HomeShellUiAction) -> Unit,
    sidebar: @Composable () -> Unit,
    content: @Composable () -> Unit,
    chrome: @Composable BoxScope.() -> Unit,
    overlay: @Composable BoxScope.() -> Unit,
) {
    AppBackgroundStyleTheme(
        enabled = state.backgroundEnabled,
        miuiBlurEnabled = false,
        cardAlphaPercent = 100,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            content()
            overlay()
        }
    }
}
