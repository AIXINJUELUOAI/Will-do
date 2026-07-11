package com.antgskds.calendarassistant.ui.contract

import com.antgskds.calendarassistant.data.model.MySettings

data class AiSettingsUiState(val settings: MySettings)

sealed interface AiSettingsUiAction {
    data class SaveTextModel(val key: String, val name: String, val url: String) : AiSettingsUiAction
    data class SaveMultimodalModel(val key: String, val name: String, val url: String) : AiSettingsUiAction
}
