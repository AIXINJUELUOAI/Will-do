package com.antgskds.calendarassistant.feature.home.ui.contract

data class HomeShellUiState(
    val backgroundEnabled: Boolean,
    val backgroundMiuiBlurEnabled: Boolean,
    val backgroundCardAlphaPercent: Int,
    val isSidebarOpen: Boolean,
    val sidebarGestureEnabled: Boolean,
    val useNavigationRail: Boolean,
)

sealed interface HomeShellUiAction {
    data class SetSidebarOpen(val isOpen: Boolean) : HomeShellUiAction
}
