package com.antgskds.calendarassistant.ui.contract

data class SettingsDetailUiState(
    val initialRoute: String,
    val uiSize: Int,
    val backgroundEnabled: Boolean,
    val backgroundMiuiBlurEnabled: Boolean,
    val backgroundCardAlphaPercent: Int,
    val pageHasAppBackground: Boolean,
    val isDarkMode: Boolean,
    val hasAppUpdate: Boolean,
    val hapticEnabled: Boolean,
    val navigationPredictiveBackEnabled: Boolean,
    val confirmationPredictiveBackEnabled: Boolean,
    val courseCount: Int,
    val archiveCount: Int,
)

sealed interface SettingsDetailUiAction {
    data object ExitSettings : SettingsDetailUiAction
    data object Logout : SettingsDetailUiAction
    data class SetDarkMode(val isDark: Boolean) : SettingsDetailUiAction
    data object ClearCourses : SettingsDetailUiAction
    data object ClearArchives : SettingsDetailUiAction
}
