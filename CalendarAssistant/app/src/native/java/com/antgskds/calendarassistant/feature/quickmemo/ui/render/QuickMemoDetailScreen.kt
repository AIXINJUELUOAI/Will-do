package com.antgskds.calendarassistant.feature.quickmemo.ui.render

import androidx.compose.runtime.Composable
import com.antgskds.calendarassistant.feature.quickmemo.ui.contract.QuickMemoDetailUiState
import com.antgskds.calendarassistant.feature.quickmemo.ui.contract.QuickMemoUiAction
import com.antgskds.calendarassistant.feature.quickmemo.ui.render.material.MaterialQuickMemoDetailScreen

@Composable
fun QuickMemoDetailScreen(
    state: QuickMemoDetailUiState,
    onBack: () -> Unit,
    uiSize: Int,
    hapticEnabled: Boolean,
    backgroundMode: Boolean,
    miuiBlurEnabled: Boolean,
    cardAlphaPercent: Int,
    autoStopDurationMs: Long?,
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
        autoStopDurationMs = autoStopDurationMs,
        onAction = onAction
    )
}
