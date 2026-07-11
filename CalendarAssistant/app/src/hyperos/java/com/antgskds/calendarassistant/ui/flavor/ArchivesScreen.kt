package com.antgskds.calendarassistant.ui.flavor

import androidx.compose.runtime.Composable
import com.antgskds.calendarassistant.ui.contract.ArchivesUiAction
import com.antgskds.calendarassistant.ui.contract.ArchivesUiState
import com.antgskds.calendarassistant.ui.page_display.settings.MaterialArchivesScreen

@Composable
fun ArchivesScreen(state: ArchivesUiState, onAction: (ArchivesUiAction) -> Unit) {
    MaterialArchivesScreen(state = state, onAction = onAction)
}
