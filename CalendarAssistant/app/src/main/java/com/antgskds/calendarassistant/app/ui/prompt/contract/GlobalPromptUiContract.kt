package com.antgskds.calendarassistant.app.ui.prompt.contract

data class GlobalPromptUiState(
    val prompt: GlobalPromptUiModel? = null,
    val predictiveBackEnabled: Boolean = true
)

data class GlobalPromptUiModel(
    val kind: GlobalPromptKind,
    val title: String,
    val content: String,
    val confirmText: String,
    val dismissText: String,
    val isDestructive: Boolean = false,
    val useFloatingBottomPadding: Boolean = false
)

enum class GlobalPromptKind {
    PROMPT_UPDATE,
    LOCAL_MODEL_RESIDUE,
    CLIPBOARD_CODE,
    CRASH_REPORT,
    CLEANUP_REPORT
}

sealed interface GlobalPromptUiAction {
    data class Confirm(val kind: GlobalPromptKind) : GlobalPromptUiAction
    data class Dismiss(val kind: GlobalPromptKind) : GlobalPromptUiAction
}
