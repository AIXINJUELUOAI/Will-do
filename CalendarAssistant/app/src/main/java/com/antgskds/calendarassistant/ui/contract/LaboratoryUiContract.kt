package com.antgskds.calendarassistant.ui.contract

import com.antgskds.calendarassistant.core.quickmemo.asr.QuickMemoAsrModelStatus
import com.antgskds.calendarassistant.data.model.MySettings

data class LaboratoryUiState(val settings: MySettings?, val asrModelStatus: QuickMemoAsrModelStatus)

sealed interface LaboratoryUiAction {
    data class SetVoiceInput(val enabled: Boolean) : LaboratoryUiAction
    data class SetFloatingLongPress(val enabled: Boolean) : LaboratoryUiAction
    data class SetRecordingDisplayMode(val mode: Int) : LaboratoryUiAction
    data class SetTextAutoPin(val enabled: Boolean) : LaboratoryUiAction
    data class SetVoiceAutoPin(val enabled: Boolean) : LaboratoryUiAction
    data class SetForceInstantCodeTime(val enabled: Boolean) : LaboratoryUiAction
    data class SetClipboardRecognition(val enabled: Boolean) : LaboratoryUiAction
    data class SetPredictiveBack(val enabled: Boolean) : LaboratoryUiAction
    data object ImportAsrModel : LaboratoryUiAction
    data object OpenDeveloper : LaboratoryUiAction
}
