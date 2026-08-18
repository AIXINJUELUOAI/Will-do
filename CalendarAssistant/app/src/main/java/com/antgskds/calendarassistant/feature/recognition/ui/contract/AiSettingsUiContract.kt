package com.antgskds.calendarassistant.feature.recognition.ui.contract

import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import com.antgskds.calendarassistant.feature.cloudsync.domain.SyncV2RuntimeStatus

data class AiSettingsUiState(
    val settings: MySettings,
    val webDavPasswordStored: Boolean,
    val syncPassphraseStored: Boolean,
    val syncStatus: SyncV2RuntimeStatus,
)

sealed interface AiSettingsUiAction {
    data class SaveTextModel(val key: String, val name: String, val url: String) : AiSettingsUiAction
    data class SaveMultimodalModel(val key: String, val name: String, val url: String) : AiSettingsUiAction
    data class SetAgentAccess(val enabled: Boolean) : AiSettingsUiAction
    data class SetAgentConnectionManagement(val enabled: Boolean) : AiSettingsUiAction
}
