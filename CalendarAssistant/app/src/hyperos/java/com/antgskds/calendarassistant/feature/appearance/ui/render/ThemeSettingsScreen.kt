package com.antgskds.calendarassistant.feature.appearance.ui.render

import androidx.compose.runtime.Composable
import com.antgskds.calendarassistant.feature.appearance.ui.contract.ThemeSettingsUiAction
import com.antgskds.calendarassistant.feature.appearance.ui.contract.ThemeSettingsUiState
import com.antgskds.calendarassistant.ui.page_display.settings.MaterialThemeSettingsScreen

@Composable
fun ThemeSettingsScreen(state: ThemeSettingsUiState, uiSize: Int = 2, onAction: (ThemeSettingsUiAction) -> Unit) {
    MaterialThemeSettingsScreen(state = state, uiSize = uiSize, onAction = onAction)
}
