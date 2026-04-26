package com.antgskds.calendarassistant.core.rule

import android.content.Context

/**
 * 规则默认值填充器。
 * 第一阶段：规则和状态全部内存硬编码，不走数据库。
 * ensureDefaults 会把内置规则的状态和转换注册到 RuleRegistry 的内存缓存中。
 */
object RuleActionSeeder {
    suspend fun ensureDefaults(context: Context) {
        // 第一阶段：全部由 RuleRegistry.refresh() 在启动时处理
        RuleRegistry.refresh(context)
    }

    suspend fun ensureRuleDefaults(context: Context, ruleId: String) {
        // 第一阶段不需要逐条处理
    }

    suspend fun updateDisplayTemplates(
        context: Context,
        ruleId: String,
        pendingTemplate: String,
        terminalTemplate: String
    ) {
        // 第一阶段不支持持久化自定义展示模板
    }
}
