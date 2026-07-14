package com.antgskds.calendarassistant.feature.settings.developer.ui.render

import androidx.compose.runtime.Composable
import com.antgskds.calendarassistant.feature.settings.developer.ui.contract.*
import com.antgskds.calendarassistant.ui.page_display.settings.MaterialDeveloperScreen

@Composable
fun DeveloperScreen(state: DeveloperUiState, uiSize: Int = 2, onAction: (DeveloperUiAction) -> Unit, runDebugActions: suspend (List<String>) -> DebugBatchResult, exportLogs: (Int?, (Result<String>) -> Unit) -> Unit) {
    MaterialDeveloperScreen(state, uiSize, onAction, runDebugActions, exportLogs)
}
