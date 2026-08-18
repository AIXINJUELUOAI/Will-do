package com.antgskds.calendarassistant.feature.settings.developer.ui.contract

import com.antgskds.calendarassistant.feature.settings.data.model.MySettings

data class DeveloperActionUi(val id: String, val label: String, val category: String, val dangerous: Boolean)
data class DeveloperUiState(val settings: MySettings, val actions: List<DeveloperActionUi>)
data class DebugBatchResult(val successCount: Int, val failedMessages: List<String>) {
    val failedCount: Int get() = failedMessages.size
}

enum class DeveloperListKind { HOME, ALL_EVENTS, FLOATING, ARCHIVES }
enum class DeveloperDragField { TITLE, TIME, LOCATION, DESCRIPTION }

sealed interface DeveloperUiAction {
    data class SetEnabled(val enabled: Boolean) : DeveloperUiAction
    data class SetSimulateRoot(val enabled: Boolean) : DeveloperUiAction
    data class SetQuickMemoPinnedFixedTitle(val enabled: Boolean) : DeveloperUiAction
    data class SetSmsPickupDedup(val enabled: Boolean) : DeveloperUiAction
    data class SetLiveTemplateMode(val mode: String) : DeveloperUiAction
    data class SetListReverse(val kind: DeveloperListKind, val enabled: Boolean) : DeveloperUiAction
    data class SetDragField(val field: DeveloperDragField, val enabled: Boolean) : DeveloperUiAction
    data class SetDragHotZone(val percent: Int) : DeveloperUiAction
    data class SetWebDavRemoteRoot(val value: String) : DeveloperUiAction
    data class ApplyUiScale(val small: Float, val medium: Float, val large: Float) : DeveloperUiAction
    data object ResetUiScale : DeveloperUiAction
    data object ResetListOrder : DeveloperUiAction
    data object OpenConfig : DeveloperUiAction
    data object OpenRegexRules : DeveloperUiAction
    data object OpenOnboardingGuide : DeveloperUiAction
}
