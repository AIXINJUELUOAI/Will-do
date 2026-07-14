package com.antgskds.calendarassistant.ui.flavor

import androidx.compose.runtime.Composable
import com.antgskds.calendarassistant.ui.contract.WidgetConfigureUiAction
import com.antgskds.calendarassistant.ui.contract.WidgetConfigureUiState
import com.antgskds.calendarassistant.ui.page_display.settings.MaterialWidgetConfigureScreen

@Composable
fun WidgetConfigureScreen(
    state: WidgetConfigureUiState,
    onAction: (WidgetConfigureUiAction) -> Unit
) {
    MaterialWidgetConfigureScreen(state, onAction)
}
