package com.antgskds.calendarassistant.feature.settings.developer.ui.contract

import com.antgskds.calendarassistant.feature.settings.data.model.MySettings

data class ConfigEditorUiState(val settings: MySettings?)

sealed interface ConfigEditorUiAction {
    data class UpdateSettings(val settings: MySettings) : ConfigEditorUiAction
    data class UpdateWebDavRemoteRoot(val value: String) : ConfigEditorUiAction
}
