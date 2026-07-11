package com.antgskds.calendarassistant.ui.flavor

import androidx.compose.runtime.Composable
import com.antgskds.calendarassistant.ui.contract.WidgetSettingsUiAction
import com.antgskds.calendarassistant.ui.contract.WidgetSettingsUiState
import com.antgskds.calendarassistant.ui.page_display.settings.MaterialWidgetSettingsScreen

@Composable
fun WidgetSettingsScreen(state: WidgetSettingsUiState, uiSize: Int = 2, onAction: (WidgetSettingsUiAction) -> Unit) {
    MaterialWidgetSettingsScreen(state, uiSize, onAction)
}
