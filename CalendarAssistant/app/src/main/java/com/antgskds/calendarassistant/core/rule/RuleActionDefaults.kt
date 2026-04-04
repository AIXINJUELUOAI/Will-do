package com.antgskds.calendarassistant.core.rule

import com.antgskds.calendarassistant.data.db.entity.EventStateEntity
import com.antgskds.calendarassistant.data.db.entity.EventTransitionEntity

object RuleActionDefaults {
    const val STATE_PENDING = "pending"
    const val STATE_DONE = "done"
    const val STATE_CHECKED_IN = "checked_in"

    data class StateDefinition(
        val suffix: String,
        val name: String,
        val isTerminal: Boolean,
        val displayTemplate: String
    )

    data class RuleDefaults(
        val pending: StateDefinition,
        val terminal: StateDefinition,
        val actionLabel: String,
        val undoLabel: String
    )

    fun defaultsFor(ruleId: String): RuleDefaults {
        return when (ruleId) {
            RuleMatchingEngine.RULE_TRAIN -> RuleDefaults(
                pending = StateDefinition(STATE_PENDING, "待检票", false, "检票口 检票"),
                terminal = StateDefinition(STATE_CHECKED_IN, "已检票", true, "座位号"),
                actionLabel = "已检票",
                undoLabel = "撤销检票"
            )
            RuleMatchingEngine.RULE_TAXI -> RuleDefaults(
                pending = StateDefinition(STATE_PENDING, "待用车", false, "车牌"),
                terminal = StateDefinition(STATE_DONE, "已用车", true, "标题"),
                actionLabel = "已用车",
                undoLabel = "撤销用车"
            )
            RuleMatchingEngine.RULE_PICKUP -> RuleDefaults(
                pending = StateDefinition(STATE_PENDING, "待取件", false, "取件码"),
                terminal = StateDefinition(STATE_DONE, "已取件", true, "标题"),
                actionLabel = "已取件",
                undoLabel = "撤销取件"
            )
            RuleMatchingEngine.RULE_FOOD -> RuleDefaults(
                pending = StateDefinition(STATE_PENDING, "待取餐", false, "取餐码"),
                terminal = StateDefinition(STATE_DONE, "已取餐", true, "标题"),
                actionLabel = "已取餐",
                undoLabel = "撤销取餐"
            )
            RuleMatchingEngine.RULE_FLIGHT -> RuleDefaults(
                pending = StateDefinition(STATE_PENDING, "待登机", false, "登机口 登机"),
                terminal = StateDefinition(STATE_DONE, "已登机", true, "座位号"),
                actionLabel = "已登机",
                undoLabel = "撤销登机"
            )
            RuleMatchingEngine.RULE_TICKET -> RuleDefaults(
                pending = StateDefinition(STATE_PENDING, "待取票", false, "取票码"),
                terminal = StateDefinition(STATE_DONE, "已取票", true, "标题"),
                actionLabel = "已取票",
                undoLabel = "撤销取票"
            )
            RuleMatchingEngine.RULE_SENDER -> RuleDefaults(
                pending = StateDefinition(STATE_PENDING, "待寄件", false, "寄件码"),
                terminal = StateDefinition(STATE_DONE, "已寄件", true, "标题"),
                actionLabel = "已寄件",
                undoLabel = "撤销寄件"
            )
            else -> RuleDefaults(
                pending = StateDefinition(STATE_PENDING, "待办", false, "标题"),
                terminal = StateDefinition(STATE_DONE, "已完成", true, "标题"),
                actionLabel = "已完成",
                undoLabel = "撤销完成"
            )
        }
    }

    fun resolveStateSuffix(ruleId: String, isCompleted: Boolean, isCheckedIn: Boolean): String {
        return if (ruleId == RuleMatchingEngine.RULE_TRAIN) {
            if (isCheckedIn) STATE_CHECKED_IN else STATE_PENDING
        } else {
            if (isCompleted) STATE_DONE else STATE_PENDING
        }
    }

    fun stateId(ruleId: String, suffix: String): String = "${ruleId}_${suffix}"

    fun transitionId(ruleId: String, fromSuffix: String, toSuffix: String): String {
        return "${ruleId}_${fromSuffix}_to_${toSuffix}"
    }

    fun buildStates(ruleId: String, defaults: RuleDefaults): List<EventStateEntity> {
        return listOf(
            EventStateEntity(
                stateId = stateId(ruleId, defaults.pending.suffix),
                ruleId = ruleId,
                name = defaults.pending.name,
                isTerminal = defaults.pending.isTerminal,
                displayTemplate = defaults.pending.displayTemplate
            ),
            EventStateEntity(
                stateId = stateId(ruleId, defaults.terminal.suffix),
                ruleId = ruleId,
                name = defaults.terminal.name,
                isTerminal = defaults.terminal.isTerminal,
                displayTemplate = defaults.terminal.displayTemplate
            )
        )
    }

    fun buildTransitions(ruleId: String, defaults: RuleDefaults): List<EventTransitionEntity> {
        return listOf(
            EventTransitionEntity(
                transitionId = transitionId(ruleId, defaults.pending.suffix, defaults.terminal.suffix),
                ruleId = ruleId,
                fromStateId = stateId(ruleId, defaults.pending.suffix),
                toStateId = stateId(ruleId, defaults.terminal.suffix),
                actionLabel = defaults.actionLabel,
                autoTransitionAfterMillis = null
            ),
            EventTransitionEntity(
                transitionId = transitionId(ruleId, defaults.terminal.suffix, defaults.pending.suffix),
                ruleId = ruleId,
                fromStateId = stateId(ruleId, defaults.terminal.suffix),
                toStateId = stateId(ruleId, defaults.pending.suffix),
                actionLabel = defaults.undoLabel,
                autoTransitionAfterMillis = null
            )
        )
    }
}
