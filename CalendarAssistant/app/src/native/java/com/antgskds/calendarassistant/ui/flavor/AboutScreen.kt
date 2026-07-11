package com.antgskds.calendarassistant.ui.flavor

import androidx.compose.runtime.Composable
import com.antgskds.calendarassistant.ui.contract.AboutUiAction
import com.antgskds.calendarassistant.ui.contract.AboutUiState

@Composable
fun AboutScreen(state: AboutUiState, onAction: (AboutUiAction) -> Unit) {
    MaterialAboutScreen(state = state, onAction = onAction)
}
