package com.antgskds.calendarassistant.platform.floating.ui.contract

data class PickupQrFloatingCardUiState(
    val qrPayload: String,
    val typeLabel: String,
    val title: String,
    val code: String,
    val location: String,
    val completeLabel: String
)

sealed interface PickupQrFloatingCardUiAction {
    data object Dismiss : PickupQrFloatingCardUiAction
    data object Complete : PickupQrFloatingCardUiAction
}
