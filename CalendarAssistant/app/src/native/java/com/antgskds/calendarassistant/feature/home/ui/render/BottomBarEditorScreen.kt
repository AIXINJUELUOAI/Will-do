package com.antgskds.calendarassistant.feature.home.ui.render

import androidx.compose.runtime.Composable
import com.antgskds.calendarassistant.ui.contract.BottomBarEditorUiAction
import com.antgskds.calendarassistant.ui.contract.BottomBarEditorUiState
import com.antgskds.calendarassistant.ui.page_display.settings.MaterialBottomBarEditorScreen

@Composable
fun BottomBarEditorScreen(state: BottomBarEditorUiState, uiSize: Int = 2, onAction: (BottomBarEditorUiAction) -> Unit) {
    MaterialBottomBarEditorScreen(state = state, uiSize = uiSize, onAction = onAction)
}
