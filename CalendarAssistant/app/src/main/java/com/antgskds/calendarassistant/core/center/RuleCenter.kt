package com.antgskds.calendarassistant.core.center

import android.content.Context
import com.antgskds.calendarassistant.core.rule.EventRuleEntity
import com.antgskds.calendarassistant.core.rule.EventStateEntity
import com.antgskds.calendarassistant.core.rule.RuleRegistry

/**
 * 规则管理中心。
 * 第一阶段：规则保存在内存中（由 RuleRegistry 硬编码提供），不走数据库。
 */
class RuleCenter(appContext: Context) {
    private val applicationContext = appContext.applicationContext

    suspend fun getAllRules(): List<EventRuleEntity> {
        return RuleRegistry.getAllRules().toList()
    }

    suspend fun upsertRule(rule: EventRuleEntity) {
        // 第一阶段不支持动态添加规则
    }

    suspend fun updateRuleEnabled(rule: EventRuleEntity, enabled: Boolean) {
        // 第一阶段不支持动态启停规则
    }

    suspend fun deleteRule(ruleId: String) {
        // 第一阶段不支持删除规则
    }

    suspend fun getStatesByRuleId(ruleId: String): List<EventStateEntity> {
        return RuleRegistry.getStates(ruleId)
    }

    suspend fun refreshRegistry() {
        RuleRegistry.refresh(applicationContext)
    }
}
