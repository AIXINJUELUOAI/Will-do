package com.antgskds.calendarassistant.feature.recognition.ui.render

import androidx.compose.runtime.Composable
import com.antgskds.calendarassistant.feature.recognition.application.ai.ModelListResult
import com.antgskds.calendarassistant.feature.recognition.ui.connector.MaterialAiSettingsScreen
import com.antgskds.calendarassistant.feature.recognition.ui.contract.AiSettingsUiAction
import com.antgskds.calendarassistant.feature.recognition.ui.contract.AiSettingsUiState

@Composable
fun AiSettingsScreen(
    state: AiSettingsUiState,
    uiSize: Int,
    onAction: (AiSettingsUiAction) -> Unit,
    fetchModels: suspend (String, String) -> ModelListResult,
) = MaterialAiSettingsScreen(
    state = state,
    uiSize = uiSize,
    onAction = onAction,
    fetchModels = fetchModels,
)
