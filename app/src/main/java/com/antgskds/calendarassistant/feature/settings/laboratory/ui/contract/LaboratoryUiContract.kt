package com.antgskds.calendarassistant.feature.settings.laboratory.ui.contract

import com.antgskds.calendarassistant.feature.settings.data.model.MySettings

data class LaboratoryUiState(val settings: MySettings?)

sealed interface LaboratoryUiAction {
    data class SetBraceletMode(val enabled: Boolean) : LaboratoryUiAction
    data class SetForceInstantCodeTime(val enabled: Boolean) : LaboratoryUiAction
    data class SetClipboardRecognition(val enabled: Boolean) : LaboratoryUiAction
    data class SetPredictiveBack(val enabled: Boolean) : LaboratoryUiAction
    data object OpenDeveloper : LaboratoryUiAction
}
