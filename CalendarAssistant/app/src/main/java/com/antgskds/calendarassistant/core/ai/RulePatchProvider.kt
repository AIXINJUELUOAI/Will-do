package com.antgskds.calendarassistant.core.ai

import android.content.Context
import com.antgskds.calendarassistant.core.rule.RuleMatchingEngine
import com.antgskds.calendarassistant.data.db.AppDatabase
import com.antgskds.calendarassistant.data.db.entity.EventRuleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RulePatchProvider {
    suspend fun loadSchedulePatch(context: Context): String {
        if (!RulePatchPrefs.isEnabled(context.applicationContext)) return ""
        return withContext(Dispatchers.IO) {
            val ruleDao = AppDatabase.getInstance(context.applicationContext).eventRuleDao()
            ensureBuiltins(ruleDao)
            val rules = ruleDao.getEnabledForSchedule()
            buildRulePatch(rules)
        }
    }

    suspend fun ensureBuiltins(context: Context) {
        withContext(Dispatchers.IO) {
            val ruleDao = AppDatabase.getInstance(context.applicationContext).eventRuleDao()
            ensureBuiltins(ruleDao)
        }
    }

    private suspend fun ensureBuiltins(ruleDao: com.antgskds.calendarassistant.data.db.dao.EventRuleDao) {
        val existing = ruleDao.getAll()
        val existingIds = existing.map { it.ruleId }.toSet()
        val missing = builtinRules().filter { it.ruleId !in existingIds }
        if (missing.isNotEmpty()) {
            ruleDao.insertAll(missing)
        }
    }

    private fun builtinRules(): List<EventRuleEntity> {
        return listOf(
            EventRuleEntity(
                ruleId = RuleMatchingEngine.RULE_GENERAL,
                name = "普通日程",
                isEnabled = true,
                appliesToSchedule = true,
                aiTag = RuleMatchingEngine.RULE_GENERAL,
                aiPrompt = "普通日程描述",
                aiTitlePrompt = "日程",
                extractionConfigJson = "{}",
                iconSourceJson = "{}",
                initialStateId = ""
            ),
            EventRuleEntity(
                ruleId = RuleMatchingEngine.RULE_TRAIN,
                name = "列车",
                isEnabled = true,
                appliesToSchedule = true,
                aiTag = RuleMatchingEngine.RULE_TRAIN,
                aiPrompt = "车次|检票口|座位号",
                aiTitlePrompt = "车次 路线",
                extractionConfigJson = "{}",
                iconSourceJson = "{}",
                initialStateId = ""
            ),
            EventRuleEntity(
                ruleId = RuleMatchingEngine.RULE_TAXI,
                name = "打车",
                isEnabled = true,
                appliesToSchedule = true,
                aiTag = RuleMatchingEngine.RULE_TAXI,
                aiPrompt = "颜色|车型|车牌",
                aiTitlePrompt = "车型 车牌",
                extractionConfigJson = "{}",
                iconSourceJson = "{}",
                initialStateId = ""
            ),
            EventRuleEntity(
                ruleId = RuleMatchingEngine.RULE_FLIGHT,
                name = "航班",
                isEnabled = true,
                appliesToSchedule = true,
                aiTag = RuleMatchingEngine.RULE_FLIGHT,
                aiPrompt = "航班号|登机口|座位号",
                aiTitlePrompt = "航班号 航线",
                extractionConfigJson = "{}",
                iconSourceJson = "{}",
                initialStateId = ""
            ),
            EventRuleEntity(
                ruleId = RuleMatchingEngine.RULE_PICKUP,
                name = "取件",
                isEnabled = true,
                appliesToSchedule = true,
                aiTag = RuleMatchingEngine.RULE_PICKUP,
                aiPrompt = "取件码|品牌|位置",
                aiTitlePrompt = "取件 品牌",
                extractionConfigJson = "{}",
                iconSourceJson = "{}",
                initialStateId = ""
            ),
            EventRuleEntity(
                ruleId = RuleMatchingEngine.RULE_FOOD,
                name = "取餐",
                isEnabled = true,
                appliesToSchedule = true,
                aiTag = RuleMatchingEngine.RULE_FOOD,
                aiPrompt = "取餐码|品牌|位置",
                aiTitlePrompt = "取餐 品牌",
                extractionConfigJson = "{}",
                iconSourceJson = "{}",
                initialStateId = ""
            ),
            EventRuleEntity(
                ruleId = RuleMatchingEngine.RULE_TICKET,
                name = "取票",
                isEnabled = true,
                appliesToSchedule = true,
                aiTag = RuleMatchingEngine.RULE_TICKET,
                aiPrompt = "取票码|取票地点|取票时间",
                aiTitlePrompt = "取票 地点",
                extractionConfigJson = "{}",
                iconSourceJson = "{}",
                initialStateId = ""
            ),
            EventRuleEntity(
                ruleId = RuleMatchingEngine.RULE_SENDER,
                name = "寄件",
                isEnabled = true,
                appliesToSchedule = true,
                aiTag = RuleMatchingEngine.RULE_SENDER,
                aiPrompt = "寄件码|品牌|地点",
                aiTitlePrompt = "寄件 品牌",
                extractionConfigJson = "{}",
                iconSourceJson = "{}",
                initialStateId = ""
            )
        )
    }

    private fun buildRulePatch(rules: List<EventRuleEntity>): String {
        if (rules.isEmpty()) return ""
        val titleLines = rules.mapNotNull { rule ->
            val ruleId = rule.ruleId.trim()
            val titlePrompt = rule.aiTitlePrompt.trim()
            if (titlePrompt.isNotBlank()) "- 【$ruleId】$titlePrompt" else null
        }
        val descriptionLines = rules.map { rule ->
            val ruleId = rule.ruleId.trim()
            val prompt = rule.aiPrompt.trim()
            val normalizedPrompt = RuleMatchingEngine.extractPayloadText(prompt) ?: prompt
            val name = rule.name.trim().ifBlank { ruleId }
            if (normalizedPrompt.isNotBlank()) {
                "- 【$ruleId】$normalizedPrompt"
            } else {
                "- 【$ruleId】$name"
            }
        }

        val sections = mutableListOf<String>()
        if (titleLines.isNotEmpty()) {
            sections.add("Title 规则：\n" + titleLines.joinToString("\n"))
        }
        sections.add("Description 规则：\n" + descriptionLines.joinToString("\n"))
        return sections.joinToString("\n\n")
    }
}
