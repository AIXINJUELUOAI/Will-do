package com.antgskds.calendarassistant.platform.floating.ui.contract

sealed interface FloatingMediaPage {
    data class QrCode(
        val payload: String,
        val contentDescription: String
    ) : FloatingMediaPage

    data class ImageFile(
        val path: String,
        val contentDescription: String
    ) : FloatingMediaPage
}

data class FloatingMediaCardUiState(
    val typeLabel: String,
    val title: String,
    val detailText: String = "",
    val pages: List<FloatingMediaPage>,
    val completeLabel: String? = null
)

sealed interface FloatingMediaCardUiAction {
    data object Dismiss : FloatingMediaCardUiAction
    data object Complete : FloatingMediaCardUiAction
    data object MediaUnavailable : FloatingMediaCardUiAction
}
