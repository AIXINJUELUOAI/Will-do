package com.antgskds.calendarassistant.core.rule

/**
 * 简化的规则数据模型。
 * 第一阶段不使用数据库驱动状态机，这些只是内存中的数据结构。
 */

data class EventRuleEntity(
    val ruleId: String,
    val name: String = "",
    val aiPrompt: String = "",
    val aiTitlePrompt: String = "",
    val aiTag: String = "",
    val isEnabled: Boolean = true,
    val appliesToSchedule: Boolean = true,
    val iconSourceJson: String = "",
    val extractionConfigJson: String = "",
    val initialStateId: String = ""
)

data class EventStateEntity(
    val stateId: String,
    val ruleId: String = "",
    val name: String = "",
    val displayTemplate: String = "",
    val isTerminal: Boolean = false,
    val actionLabel: String = ""
)

data class EventTransitionEntity(
    val transitionId: String,
    val ruleId: String = "",
    val fromStateId: String = "",
    val toStateId: String = "",
    val actionLabel: String = "",
    val autoTransitionAfterMillis: Long? = null
)
