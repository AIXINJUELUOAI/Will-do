package com.antgskds.calendarassistant.ui.flavor

import androidx.compose.runtime.Composable
import com.antgskds.calendarassistant.ui.contract.QuickMemoDetailUiState
import com.antgskds.calendarassistant.ui.contract.QuickMemoUiAction
import com.antgskds.calendarassistant.ui.page_display.MaterialQuickMemoDetailScreen

@Composable
fun QuickMemoDetailScreen(
    state: QuickMemoDetailUiState,
    onBack: () -> Unit,
    uiSize: Int,
    hapticEnabled: Boolean,
    backgroundMode: Boolean,
    miuiBlurEnabled: Boolean,
    cardAlphaPercent: Int,
    onAction: (QuickMemoUiAction) -> Unit
) {
    MaterialQuickMemoDetailScreen(
        state = state,
        onBack = onBack,
        uiSize = uiSize,
        hapticEnabled = hapticEnabled,
        backgroundMode = backgroundMode,
        miuiBlurEnabled = miuiBlurEnabled,
        cardAlphaPercent = cardAlphaPercent,
        onAction = onAction
    )
}
