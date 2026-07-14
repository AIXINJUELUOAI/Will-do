package com.antgskds.calendarassistant.ui.contract

data class HomeShellUiState(
    val backgroundEnabled: Boolean,
    val backgroundMiuiBlurEnabled: Boolean,
    val backgroundCardAlphaPercent: Int,
    val isSidebarOpen: Boolean,
    val sidebarGestureEnabled: Boolean,
)

sealed interface HomeShellUiAction {
    data class SetSidebarOpen(val isOpen: Boolean) : HomeShellUiAction
}
