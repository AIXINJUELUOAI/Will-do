package com.antgskds.calendarassistant.feature.schedule.ui.render

import androidx.compose.runtime.Composable
import com.antgskds.calendarassistant.feature.schedule.ui.contract.ArchivesUiAction
import com.antgskds.calendarassistant.feature.schedule.ui.contract.ArchivesUiState
import com.antgskds.calendarassistant.ui.page_display.settings.MaterialArchivesScreen

@Composable
fun ArchivesScreen(state: ArchivesUiState, onAction: (ArchivesUiAction) -> Unit) {
    MaterialArchivesScreen(state = state, onAction = onAction)
}
