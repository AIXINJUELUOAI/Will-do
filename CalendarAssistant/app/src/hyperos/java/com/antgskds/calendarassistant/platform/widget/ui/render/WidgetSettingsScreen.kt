package com.antgskds.calendarassistant.platform.widget.ui.render

import androidx.compose.runtime.Composable
import com.antgskds.calendarassistant.platform.widget.ui.contract.WidgetSettingsUiAction
import com.antgskds.calendarassistant.platform.widget.ui.contract.WidgetSettingsUiState
import com.antgskds.calendarassistant.ui.page_display.settings.MaterialWidgetSettingsScreen

@Composable
fun WidgetSettingsScreen(state: WidgetSettingsUiState, uiSize: Int = 2, onAction: (WidgetSettingsUiAction) -> Unit) {
    MaterialWidgetSettingsScreen(state, uiSize, onAction)
}
