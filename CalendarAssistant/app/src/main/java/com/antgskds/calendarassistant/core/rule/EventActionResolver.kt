package com.antgskds.calendarassistant.core.rule

import com.antgskds.calendarassistant.data.model.MyEvent

enum class RuleActionType {
    COMPLETE,
    CHECKIN,
    UNDO
}

data class RuleActionDecision(
    val ruleId: String,
    val actionLabel: String,
    val actionType: RuleActionType
)

object EventActionResolver {
    fun resolve(event: MyEvent): RuleActionDecision? {
        val resolvedRuleId = RuleMatchingEngine.resolvePayload(event)?.ruleId
        val ruleId = resolvedRuleId?.ifBlank { null } ?: event.tag.ifBlank { RuleMatchingEngine.RULE_GENERAL }

        val defaults = RuleActionDefaults.defaultsFor(ruleId)
        val currentStateId = RuleRegistry.resolveCurrentStateId(ruleId, event.isCompleted, event.isCheckedIn)
        val chosenTransition = RuleRegistry.getPrimaryTransition(ruleId, currentStateId)

        if (chosenTransition == null) {
            val isUndo = currentStateId != RuleActionDefaults.stateId(ruleId, RuleActionDefaults.STATE_PENDING)
            val actionType = when {
                isUndo -> RuleActionType.UNDO
                ruleId == RuleMatchingEngine.RULE_TRAIN -> RuleActionType.CHECKIN
                else -> RuleActionType.COMPLETE
            }
            val label = if (isUndo) defaults.undoLabel else defaults.actionLabel
            return RuleActionDecision(ruleId, label, actionType)
        }

        val targetState = RuleRegistry.getState(chosenTransition.toStateId)
        val isUndo = targetState?.isTerminal == false
        val actionType = when {
            isUndo -> RuleActionType.UNDO
            ruleId == RuleMatchingEngine.RULE_TRAIN -> RuleActionType.CHECKIN
            else -> RuleActionType.COMPLETE
        }
        val label = chosenTransition.actionLabel.ifBlank {
            if (isUndo) defaults.undoLabel else defaults.actionLabel
        }
        return RuleActionDecision(ruleId, label, actionType)
    }
}
