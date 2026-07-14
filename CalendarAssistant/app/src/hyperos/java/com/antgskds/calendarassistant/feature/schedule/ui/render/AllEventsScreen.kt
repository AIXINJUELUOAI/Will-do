package com.antgskds.calendarassistant.feature.schedule.ui.render

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import com.antgskds.calendarassistant.ui.contract.AllEventsUiAction
import com.antgskds.calendarassistant.ui.contract.AllEventsUiState
import com.antgskds.calendarassistant.ui.page_display.MaterialAllEventsScreen

@Composable
fun AllEventsScreen(
    state: AllEventsUiState,
    uiSize: Int,
    extraBottomPadding: Dp,
    hapticEnabled: Boolean,
    onAction: (AllEventsUiAction) -> Unit
) {
    MaterialAllEventsScreen(
        state = state,
        uiSize = uiSize,
        extraBottomPadding = extraBottomPadding,
        hapticEnabled = hapticEnabled,
        onAction = onAction
    )
}
