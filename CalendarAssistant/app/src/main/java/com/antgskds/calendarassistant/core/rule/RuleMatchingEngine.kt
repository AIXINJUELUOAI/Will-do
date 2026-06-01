package com.antgskds.calendarassistant.core.rule

import com.antgskds.calendarassistant.calendar.models.EventTags
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.*
import com.antgskds.calendarassistant.core.util.stripSourceImageMarkers

object RuleMatchingEngine {
    const val RULE_GENERAL = "general"
    const val RULE_PICKUP = "pickup"
    const val RULE_FOOD = "food"
    const val RULE_TRAIN = "train"
    const val RULE_TAXI = "taxi"
    const val RULE_FLIGHT = "flight"
    const val RULE_TICKET = "ticket"
    const val RULE_SENDER = "sender"
    const val RULE_COURSE = "course"

    data class RulePayload(
        val ruleId: String,
        val payload: String,
        val rawHeader: String?
    )

    fun resolvePayload(event: Event): RulePayload? {
        val fallbackRuleId = when (event.tag) {
            EventTags.PICKUP -> RULE_PICKUP
            EventTags.FOOD -> RULE_FOOD
            EventTags.TRAIN -> RULE_TRAIN
            EventTags.TAXI -> RULE_TAXI
            EventTags.FLIGHT -> RULE_FLIGHT
            EventTags.TICKET -> RULE_TICKET
            EventTags.SENDER -> RULE_SENDER
            EventTags.COURSE -> RULE_COURSE
            else -> null
        }
        return resolvePayload(event.description, fallbackRuleId)
    }

    fun resolvePayload(description: String?, fallbackRuleId: String? = null): RulePayload? {
        val clean = stripSourceImageMarkers(description)
        if (clean.isBlank()) {
            return fallbackRuleId?.let { RulePayload(it, "", null) }
        }

        val headerInfo = extractHeader(clean)
        if (headerInfo != null) {
            val normalized = normalizeRuleId(headerInfo.header)
            if (normalized != null) {
                return RulePayload(normalized, headerInfo.payload, headerInfo.header)
            }
        }

        return fallbackRuleId?.let { RulePayload(it, clean, null) }
    }

    fun splitFields(payload: String, size: Int): List<String> {
        if (size <= 0) return emptyList()
        val parts = payload.split("|").map { it.trim() }
        return if (parts.size >= size) {
            parts.take(size)
        } else {
            parts + List(size - parts.size) { "" }
        }
    }

    fun extractPayloadText(description: String?): String? {
        val clean = stripSourceImageMarkers(description)
        if (clean.isBlank()) return null
        val payload = resolvePayload(clean, null) ?: return clean
        return payload.payload.trim().ifBlank { null }
    }

    fun isInstantCodeRule(ruleId: String?): Boolean {
        return when (ruleId) {
            RULE_PICKUP,
            RULE_FOOD,
            RULE_TICKET,
            RULE_SENDER -> true
            else -> false
        }
    }

    fun stripInstantCodeLabel(ruleId: String?, value: String): String {
        val clean = stripSourceImageMarkers(value).trim()
        if (!isInstantCodeRule(ruleId) || clean.isBlank()) return clean

        val labels = when (ruleId) {
            RULE_PICKUP -> listOf("取件码", "提货号", "取货码", "提货码", "签收码", "签收编号")
            RULE_FOOD -> listOf("取餐码", "取餐号", "餐码", "外卖码")
            RULE_TICKET -> listOf("取票码", "取票号", "票码", "票号", "凭证码", "验票码")
            RULE_SENDER -> listOf("寄件码", "寄件号", "运单码")
            else -> emptyList()
        }
        if (labels.isEmpty()) return clean

        val labelPattern = labels.joinToString("|") { Regex.escape(it) }
        return clean
            .replace(Regex("^\\s*(?:$labelPattern)\\s*(?:[:：=\\-]|为|是)?\\s*"), "")
            .trim()
            .trim(':', '：', ',', '，', '。', ' ')
    }

    private data class HeaderInfo(
        val header: String,
        val payload: String
    )

    private fun extractHeader(description: String): HeaderInfo? {
        val cnStart = description.indexOf('【')
        val cnEnd = if (cnStart != -1) description.indexOf('】', cnStart + 1) else -1
        if (cnStart != -1 && cnEnd != -1) {
            val header = description.substring(cnStart + 1, cnEnd).trim()
            val payload = description.substring(cnEnd + 1).trim()
            return HeaderInfo(header, payload)
        }

        val enStart = description.indexOf('[')
        val enEnd = if (enStart != -1) description.indexOf(']', enStart + 1) else -1
        if (enStart != -1 && enEnd != -1) {
            val header = description.substring(enStart + 1, enEnd).trim()
            val payload = description.substring(enEnd + 1).trim()
            return HeaderInfo(header, payload)
        }

        return null
    }

    private fun normalizeRuleId(rawHeader: String?): String? {
        val normalized = RecognitionRuleCatalog.normalizeKnownTag(rawHeader)
        if (normalized != null) return normalized

        val lower = rawHeader?.trim()?.lowercase().orEmpty()
        return if (lower.matches(Regex("[a-z0-9_]+"))) lower else null
    }
}
