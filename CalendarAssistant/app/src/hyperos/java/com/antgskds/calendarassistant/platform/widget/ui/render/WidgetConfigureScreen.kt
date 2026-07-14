package com.antgskds.calendarassistant.platform.widget.ui.render

import androidx.compose.runtime.Composable
import com.antgskds.calendarassistant.platform.widget.ui.contract.WidgetConfigureUiAction
import com.antgskds.calendarassistant.platform.widget.ui.contract.WidgetConfigureUiState
import com.antgskds.calendarassistant.platform.widget.ui.render.material.MaterialWidgetConfigureScreen

@Composable
fun WidgetConfigureScreen(
    state: WidgetConfigureUiState,
    onAction: (WidgetConfigureUiAction) -> Unit
) {
    MaterialWidgetConfigureScreen(state, onAction)
}
