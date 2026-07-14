package com.antgskds.calendarassistant.app.ui.prompt.render.material

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.padding
import com.antgskds.calendarassistant.ui.components.PredictiveFloatingActionCard
import com.antgskds.calendarassistant.ui.contract.GlobalPromptUiAction
import com.antgskds.calendarassistant.ui.contract.GlobalPromptUiState

@Composable
fun MaterialGlobalPromptHost(
    state: GlobalPromptUiState,
    floatingBottomPadding: Dp,
    onAction: (GlobalPromptUiAction) -> Unit
) {
    val prompt = state.prompt ?: return
    PredictiveFloatingActionCard(
        visible = true,
        title = prompt.title,
        content = prompt.content,
        confirmText = prompt.confirmText,
        dismissText = prompt.dismissText,
        isDestructive = prompt.isDestructive,
        isLoading = false,
        predictiveBackEnabled = state.predictiveBackEnabled,
        onConfirm = { onAction(GlobalPromptUiAction.Confirm(prompt.kind)) },
        onDismiss = { onAction(GlobalPromptUiAction.Dismiss(prompt.kind)) },
        modifier = if (prompt.useFloatingBottomPadding) {
            Modifier.padding(bottom = floatingBottomPadding)
        } else {
            Modifier
        }
    )
}
