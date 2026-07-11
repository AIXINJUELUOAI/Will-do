package com.antgskds.calendarassistant.ui.contract

import com.antgskds.calendarassistant.data.model.MySettings

data class ThemeSettingsUiState(val settings: MySettings, val isBackgroundImporting: Boolean)

sealed interface ThemeSettingsUiAction {
    data class UpdateThemeMode(val mode: Int) : ThemeSettingsUiAction
    data class UpdateUiStyle(val style: String) : ThemeSettingsUiAction
    data class UpdateColorScheme(val scheme: String) : ThemeSettingsUiAction
    data class UpdateCustomColor(val hex: String) : ThemeSettingsUiAction
    data class UpdateImageColor(val enabled: Boolean) : ThemeSettingsUiAction
    data class UpdateWallpaperBlur(val enabled: Boolean) : ThemeSettingsUiAction
    data object ImportBackground : ThemeSettingsUiAction
    data object ClearBackground : ThemeSettingsUiAction
}
