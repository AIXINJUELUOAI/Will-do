package com.antgskds.calendarassistant.feature.recognition.ui.render

import androidx.compose.runtime.Composable
import com.antgskds.calendarassistant.feature.recognition.application.ai.ModelListResult
import com.antgskds.calendarassistant.feature.recognition.ui.connector.MaterialAiSettingsScreen
import com.antgskds.calendarassistant.feature.recognition.ui.contract.AiSettingsUiAction
import com.antgskds.calendarassistant.feature.recognition.ui.contract.AiSettingsUiState
import com.antgskds.calendarassistant.feature.cloudsync.domain.WebDavConnectionInput
import com.antgskds.calendarassistant.feature.cloudsync.domain.WebDavConnectionTestResult

@Composable
fun AiSettingsScreen(
    state: AiSettingsUiState,
    uiSize: Int,
    onAction: (AiSettingsUiAction) -> Unit,
    fetchModels: suspend (String, String) -> ModelListResult,
    testWebDavConnection: suspend (WebDavConnectionInput) -> WebDavConnectionTestResult,
    readStoredWebDavPassword: () -> String,
    readStoredSyncPassphrase: () -> String,
    onSyncEnabledChange: (Boolean) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
    onForegroundSyncIntervalChange: (Int) -> Unit,
    onSyncNow: () -> Unit,
) = MaterialAiSettingsScreen(
    state = state,
    uiSize = uiSize,
    onAction = onAction,
    fetchModels = fetchModels,
    testWebDavConnection = testWebDavConnection,
    readStoredWebDavPassword = readStoredWebDavPassword,
    readStoredSyncPassphrase = readStoredSyncPassphrase,
    onSyncEnabledChange = onSyncEnabledChange,
    onWifiOnlyChange = onWifiOnlyChange,
    onForegroundSyncIntervalChange = onForegroundSyncIntervalChange,
    onSyncNow = onSyncNow,
)
