package com.antgskds.calendarassistant.feature.settings.about.ui.render

import androidx.compose.runtime.Composable
import com.antgskds.calendarassistant.feature.settings.about.ui.render.material.MaterialAboutScreen
import com.antgskds.calendarassistant.ui.contract.AboutUiAction
import com.antgskds.calendarassistant.ui.contract.AboutUiState

@Composable
fun AboutScreen(state: AboutUiState, onAction: (AboutUiAction) -> Unit) {
    MaterialAboutScreen(state = state, onAction = onAction)
}
