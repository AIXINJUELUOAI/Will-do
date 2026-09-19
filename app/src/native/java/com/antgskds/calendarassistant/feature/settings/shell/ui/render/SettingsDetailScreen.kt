package com.antgskds.calendarassistant.feature.settings.shell.ui.render

import androidx.compose.runtime.Composable
import com.antgskds.calendarassistant.app.ui.navigation.SettingsDestination
import com.antgskds.calendarassistant.feature.settings.shell.ui.contract.SettingsDetailUiAction
import com.antgskds.calendarassistant.feature.settings.shell.ui.contract.SettingsDetailUiState
import com.antgskds.calendarassistant.feature.settings.shell.ui.render.material.MaterialSettingsDetailScreen

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
