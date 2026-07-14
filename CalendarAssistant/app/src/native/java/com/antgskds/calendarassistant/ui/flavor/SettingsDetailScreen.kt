package com.antgskds.calendarassistant.ui.flavor

import androidx.compose.runtime.Composable
import com.antgskds.calendarassistant.ui.contract.SettingsDestination
import com.antgskds.calendarassistant.ui.contract.SettingsDetailUiAction
import com.antgskds.calendarassistant.ui.contract.SettingsDetailUiState
import com.antgskds.calendarassistant.ui.page_display.MaterialSettingsDetailScreen

@Composable
fun SettingsDetailScreenContent(
    state: SettingsDetailUiState,
    onAction: (SettingsDetailUiAction) -> Unit,
    pageContent: @Composable (
        route: String,
        destination: SettingsDestination,
        onNavigateTo: (SettingsDestination) -> Unit,
        onNavigateRoute: (String) -> Unit,
    ) -> Unit,
) {
    MaterialSettingsDetailScreen(
        state = state,
        onAction = onAction,
        pageContent = pageContent,
    )
}
