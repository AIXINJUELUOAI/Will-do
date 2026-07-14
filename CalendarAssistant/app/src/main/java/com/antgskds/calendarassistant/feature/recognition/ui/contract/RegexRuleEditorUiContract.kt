package com.antgskds.calendarassistant.feature.recognition.ui.contract

import com.antgskds.calendarassistant.feature.recognition.domain.rule.RegexScheduleRule

data class RegexRuleEditorUiState(
    val rules: List<RegexScheduleRule>,
    val testMessage: String
)

sealed interface RegexRuleEditorUiAction {
    data class UpdateRule(val index: Int, val rule: RegexScheduleRule) : RegexRuleEditorUiAction
    data class RunTest(val input: String) : RegexRuleEditorUiAction
    data object ResetRules : RegexRuleEditorUiAction
}
