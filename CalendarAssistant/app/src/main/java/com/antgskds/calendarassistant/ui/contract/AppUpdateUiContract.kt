package com.antgskds.calendarassistant.ui.contract

data class AppUpdateScreenState(
    val hasUpdate: Boolean,
    val isChecking: Boolean,
    val errorMessage: String?,
    val versions: List<AppUpdateVersionUi>
)

data class AppUpdateVersionUi(
    val versionName: String,
    val downloadUrl: String,
    val downloadPassword: String,
    val sections: List<AppUpdateSectionUi>
)

data class AppUpdateSectionUi(val title: String, val items: List<String>)

sealed interface AppUpdateUiAction {
    data object CheckForUpdates : AppUpdateUiAction
    data class OpenDownload(val url: String) : AppUpdateUiAction
}
