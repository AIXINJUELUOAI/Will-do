package com.antgskds.calendarassistant.ui.contract

data class WidgetConfigureUiState(
    val widgetName: String,
    val selectedTheme: WidgetConfigureThemeOption,
    val backgroundAlphaPercent: Int
)

enum class WidgetConfigureThemeOption {
    FOLLOW_APP,
    LIGHT,
    DARK
}

sealed interface WidgetConfigureUiAction {
    data class SelectTheme(val theme: WidgetConfigureThemeOption) : WidgetConfigureUiAction
    data class ChangeBackgroundAlpha(val percent: Int) : WidgetConfigureUiAction
    data object Save : WidgetConfigureUiAction
    data object Exit : WidgetConfigureUiAction
}
