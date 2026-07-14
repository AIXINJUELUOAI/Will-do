package com.antgskds.calendarassistant.platform.floating.ui.render

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.antgskds.calendarassistant.ui.contract.PickupQrFloatingCardUiAction
import com.antgskds.calendarassistant.ui.contract.PickupQrFloatingCardUiState
import com.antgskds.calendarassistant.ui.floating.MaterialPickupQrFloatingCard

@Composable
fun PickupQrFloatingCardContent(
    state: PickupQrFloatingCardUiState,
    onAction: (PickupQrFloatingCardUiAction) -> Unit,
    modifier: Modifier = Modifier
) {
    MaterialPickupQrFloatingCard(state = state, onAction = onAction, modifier = modifier)
}
