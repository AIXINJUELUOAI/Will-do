package com.antgskds.calendarassistant.feature.settings.laboratory.ui.render

import androidx.compose.runtime.Composable
import com.antgskds.calendarassistant.feature.settings.laboratory.ui.contract.LaboratoryUiAction
import com.antgskds.calendarassistant.feature.settings.laboratory.ui.contract.LaboratoryUiState
import com.antgskds.calendarassistant.ui.page_display.settings.MaterialLaboratoryScreen

@Composable
fun LaboratoryScreen(state: LaboratoryUiState, uiSize: Int = 2, onAction: (LaboratoryUiAction) -> Unit) {
    MaterialLaboratoryScreen(state = state, uiSize = uiSize, onAction = onAction)
}
