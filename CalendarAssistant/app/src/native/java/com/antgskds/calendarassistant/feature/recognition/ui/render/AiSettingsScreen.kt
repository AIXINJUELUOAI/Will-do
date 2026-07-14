package com.antgskds.calendarassistant.feature.recognition.ui.render

import androidx.compose.runtime.Composable
import com.antgskds.calendarassistant.feature.recognition.application.ai.ModelListResult
import com.antgskds.calendarassistant.ui.contract.AiSettingsUiAction
import com.antgskds.calendarassistant.ui.contract.AiSettingsUiState
import com.antgskds.calendarassistant.ui.page_display.settings.MaterialAiSettingsScreen

@Composable
fun AiSettingsScreen(state: AiSettingsUiState, uiSize: Int = 2, onAction: (AiSettingsUiAction) -> Unit, fetchModels: suspend (String, String) -> ModelListResult) {
    MaterialAiSettingsScreen(state, uiSize, onAction, fetchModels)
}
