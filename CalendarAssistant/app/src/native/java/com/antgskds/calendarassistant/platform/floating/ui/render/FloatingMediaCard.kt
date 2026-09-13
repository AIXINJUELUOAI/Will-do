package com.antgskds.calendarassistant.platform.floating.ui.render

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.antgskds.calendarassistant.platform.floating.ui.contract.FloatingMediaCardUiAction
import com.antgskds.calendarassistant.platform.floating.ui.contract.FloatingMediaCardUiState
import com.antgskds.calendarassistant.platform.floating.ui.render.material.MaterialFloatingMediaCard

@Composable
fun FloatingMediaCardContent(
    state: FloatingMediaCardUiState,
    onAction: (FloatingMediaCardUiAction) -> Unit,
    modifier: Modifier = Modifier
) {
    MaterialFloatingMediaCard(state = state, onAction = onAction, modifier = modifier)
}
