package com.antgskds.calendarassistant.app.ui.prompt.render

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import com.antgskds.calendarassistant.app.ui.prompt.contract.GlobalPromptUiAction
import com.antgskds.calendarassistant.app.ui.prompt.contract.GlobalPromptUiState
import com.antgskds.calendarassistant.app.ui.prompt.render.material.MaterialGlobalPromptHost

@Composable
fun GlobalPromptHost(
    state: GlobalPromptUiState,
    floatingBottomPadding: Dp,
    onAction: (GlobalPromptUiAction) -> Unit
) {
    MaterialGlobalPromptHost(state, floatingBottomPadding, onAction)
}
