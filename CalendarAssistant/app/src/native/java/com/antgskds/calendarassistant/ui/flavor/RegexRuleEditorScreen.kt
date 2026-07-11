package com.antgskds.calendarassistant.ui.flavor

import androidx.compose.runtime.Composable
import com.antgskds.calendarassistant.ui.contract.RegexRuleEditorUiAction
import com.antgskds.calendarassistant.ui.contract.RegexRuleEditorUiState
import com.antgskds.calendarassistant.ui.page_display.settings.MaterialRegexRuleEditorScreen

@Composable
fun RegexRuleEditorScreen(state: RegexRuleEditorUiState, uiSize: Int = 2, onAction: (RegexRuleEditorUiAction) -> Unit) {
    MaterialRegexRuleEditorScreen(state = state, uiSize = uiSize, onAction = onAction)
}
