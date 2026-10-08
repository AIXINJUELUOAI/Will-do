package com.antgskds.calendarassistant.feature.quickmemo.ui.render

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import com.antgskds.calendarassistant.feature.quickmemo.ui.contract.QuickMemoListUiState
import com.antgskds.calendarassistant.feature.quickmemo.ui.contract.QuickMemoUiAction
import com.antgskds.calendarassistant.feature.quickmemo.ui.render.material.MaterialQuickMemoScreen

@Composable
fun QuickMemoScreen(
    state: QuickMemoListUiState,
    searchQuery: String,
    uiSize: Int,
    extraBottomPadding: Dp,
    selectedMemoId: Long? = null,
    selectedMemoIds: Set<Long> = emptySet(),
    selectionMode: Boolean = false,
    reserveFloatingBarSpace: Boolean = true,
    hapticEnabled: Boolean,
    onAction: (QuickMemoUiAction) -> Unit
) {
    MaterialQuickMemoScreen(
        state = state,
        searchQuery = searchQuery,
        uiSize = uiSize,
        extraBottomPadding = extraBottomPadding,
        selectedMemoId = selectedMemoId,
        selectedMemoIds = selectedMemoIds,
        selectionMode = selectionMode,
        reserveFloatingBarSpace = reserveFloatingBarSpace,
        hapticEnabled = hapticEnabled,
        onAction = onAction
    )
}
