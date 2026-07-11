package com.antgskds.calendarassistant.ui.contract

import com.antgskds.calendarassistant.data.model.MySettings

data class ConfigEditorUiState(val settings: MySettings?)

sealed interface ConfigEditorUiAction {
    data class UpdateSettings(val settings: MySettings) : ConfigEditorUiAction
}
