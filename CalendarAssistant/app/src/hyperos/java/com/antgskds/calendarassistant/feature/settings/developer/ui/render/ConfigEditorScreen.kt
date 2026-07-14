package com.antgskds.calendarassistant.feature.settings.developer.ui.render

import androidx.compose.runtime.Composable
import com.antgskds.calendarassistant.ui.contract.ConfigEditorUiAction
import com.antgskds.calendarassistant.ui.contract.ConfigEditorUiState
import com.antgskds.calendarassistant.ui.page_display.settings.MaterialConfigEditorScreen

@Composable
fun ConfigEditorScreen(state: ConfigEditorUiState, uiSize: Int = 2, onAction: (ConfigEditorUiAction) -> Unit) {
    MaterialConfigEditorScreen(state = state, uiSize = uiSize, onAction = onAction)
}
