package com.antgskds.calendarassistant.feature.settings.about.ui.render

import androidx.compose.runtime.Composable
import com.antgskds.calendarassistant.ui.contract.DonateUiAction
import com.antgskds.calendarassistant.ui.contract.DonateUiState
import com.antgskds.calendarassistant.ui.page_display.settings.MaterialDonateScreen

@Composable
fun DonateScreen(state: DonateUiState, uiSize: Int = 2, onAction: (DonateUiAction) -> Unit) {
    MaterialDonateScreen(state = state, uiSize = uiSize, onAction = onAction)
}
