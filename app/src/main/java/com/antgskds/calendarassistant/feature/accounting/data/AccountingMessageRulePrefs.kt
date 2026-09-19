package com.antgskds.calendarassistant.feature.accounting.data

import android.content.Context
import com.antgskds.calendarassistant.feature.accounting.domain.AccountingMessageRule
import com.antgskds.calendarassistant.feature.accounting.domain.AccountingMessageRules
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object AccountingMessageRulePrefs {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private fun prefs(context: Context) = context.getSharedPreferences("accounting_message_rules", Context.MODE_PRIVATE)
    fun load(context: Context): List<AccountingMessageRule> {
        val raw = prefs(context).getString("rules", null) ?: return AccountingMessageRules.defaults()
        // 已编辑规则损坏时停止识别，不偷偷切回默认规则；显式保存空列表也保持为空。
        return runCatching { json.decodeFromString<List<AccountingMessageRule>>(raw) }.getOrDefault(emptyList())
    }
    fun save(context: Context, rules: List<AccountingMessageRule>) {
        require(rules.size <= ConfigCatalog.ACCOUNTING_MESSAGE_MAX_RULES) { "规则数量过多" }
        rules.filter { it.enabled }.forEach { rule ->
            require(AccountingMessageRules.validate(rule) == null) { "${rule.name}：${AccountingMessageRules.validate(rule)}" }
        }
        check(prefs(context).edit().putString("rules", json.encodeToString(rules)).commit()) { "规则保存失败" }
    }
}
