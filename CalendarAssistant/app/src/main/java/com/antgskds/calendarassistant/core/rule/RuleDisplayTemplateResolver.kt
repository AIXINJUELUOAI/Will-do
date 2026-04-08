package com.antgskds.calendarassistant.core.rule

import android.content.Context
import com.antgskds.calendarassistant.data.db.AppDatabase
import com.antgskds.calendarassistant.data.model.MyEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RuleDisplayTemplateResolver {
    @Volatile
    private var templateMap: Map<String, String> = emptyMap()

    private val builtInFieldNames = mapOf(
        RuleMatchingEngine.RULE_TRAIN to listOf("车次", "检票口", "座位号"),
        RuleMatchingEngine.RULE_TAXI to listOf("颜色", "车型", "车牌"),
        RuleMatchingEngine.RULE_FLIGHT to listOf("航班号", "登机口", "座位号"),
        RuleMatchingEngine.RULE_PICKUP to listOf("取件码", "品牌", "位置"),
        RuleMatchingEngine.RULE_FOOD to listOf("取餐码", "品牌", "位置"),
        RuleMatchingEngine.RULE_TICKET to listOf("取票码", "取票地点", "取票时间"),
        RuleMatchingEngine.RULE_SENDER to listOf("寄件码", "品牌", "地点")
    )

    suspend fun refresh(context: Context) {
        withContext(Dispatchers.IO) {
            val states = AppDatabase.getInstance(context.applicationContext).eventStateDao().getAll()
            templateMap = states.associate { it.stateId to it.displayTemplate }
        }
    }

    fun renderTitle(event: MyEvent): String? {
        val ruleId = RuleMatchingEngine.resolvePayload(event)?.ruleId
            ?: event.tag.ifBlank { RuleMatchingEngine.RULE_GENERAL }
        val defaults = RuleActionDefaults.defaultsFor(ruleId)
        val suffix = RuleActionDefaults.resolveStateSuffix(ruleId, event.isCompleted, event.isCheckedIn)
        val stateId = RuleActionDefaults.stateId(ruleId, suffix)
        val template = templateMap[stateId]?.trim().orEmpty()
        val fallback = if (suffix == defaults.terminal.suffix) {
            defaults.terminal.displayTemplate
        } else {
            defaults.pending.displayTemplate
        }
        val resolvedTemplate = if (template.isNotBlank()) template else fallback
        val rendered = applyTemplate(resolvedTemplate, event)
        val title = rendered.trim().ifBlank { null } ?: return null
        return title.takeUnless { looksUnresolved(it, resolvedTemplate, ruleId) }
    }

    private fun applyTemplate(template: String, event: MyEvent): String {
        val ruleId = RuleMatchingEngine.resolvePayload(event)?.ruleId
            ?: event.tag.ifBlank { null }
        val processed = convertFriendlyPlaceholders(template, ruleId)
        val payload = RuleMatchingEngine.extractPayloadText(event.description).orEmpty()
        val fields = RuleMatchingEngine.splitFields(payload, 5)
        val replacements = mapOf(
            "{title}" to event.title,
            "{location}" to event.location,
            "{startTime}" to event.startTime,
            "{endTime}" to event.endTime,
            "{startDate}" to event.startDate.toString(),
            "{endDate}" to event.endDate.toString(),
            "{date}" to event.startDate.toString(),
            "{description}" to event.description,
            "{payload}" to payload,
            "{field1}" to fields.getOrNull(0).orEmpty(),
            "{field2}" to fields.getOrNull(1).orEmpty(),
            "{field3}" to fields.getOrNull(2).orEmpty(),
            "{field4}" to fields.getOrNull(3).orEmpty(),
            "{field5}" to fields.getOrNull(4).orEmpty()
        )
        var result = processed
        replacements.forEach { (key, value) ->
            result = result.replace(key, value)
        }
        return result.replace(Regex("\\s{2,}"), " ").trim()
    }

    /**
     * 将用户友好的中文字段名转换为 {fieldN} / {title} 占位符。
     * 同时兼容带花括号和不带花括号的写法。
     */
    private fun convertFriendlyPlaceholders(template: String, ruleId: String?): String {
        var result = template

        // 标题相关
        result = result.replace("标题", "{title}")

        // 从规则的 aiPrompt 解析字段名映射
        resolveFieldNames(ruleId).forEachIndexed { index, name ->
            if (name.isNotBlank()) {
                val fieldKey = "{field${index + 1}}"
                result = result.replace(name, fieldKey)
            }
        }

        return result
    }

    private fun resolveFieldNames(ruleId: String?): List<String> {
        if (ruleId == null) return emptyList()
        val aiPrompt = RuleRegistry.getRule(ruleId)?.aiPrompt?.trim()
        if (!aiPrompt.isNullOrBlank()) {
            return aiPrompt.split("|").map { it.trim() }
        }
        return builtInFieldNames[ruleId].orEmpty()
    }

    private fun looksUnresolved(rendered: String, template: String, ruleId: String?): Boolean {
        if (rendered != template.trim()) return false
        if (template.contains("标题")) return true
        return resolveFieldNames(ruleId).any { fieldName ->
            fieldName.isNotBlank() && template.contains(fieldName)
        }
    }
}
