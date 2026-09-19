package com.antgskds.calendarassistant.feature.schedule.ui.render

import androidx.compose.runtime.Composable
import com.antgskds.calendarassistant.feature.schedule.ui.connector.MaterialArchivesScreen
import com.antgskds.calendarassistant.feature.schedule.ui.contract.ArchivesUiAction
import com.antgskds.calendarassistant.feature.schedule.ui.contract.ArchivesUiState

@Composable
fun ArchivesScreenContent(
    state: ArchivesUiState,
    onAction: (ArchivesUiAction) -> Unit,
) = MaterialArchivesScreen(state, onAction)
