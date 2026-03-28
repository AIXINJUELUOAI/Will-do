package com.antgskds.calendarassistant.core.rule

import com.antgskds.calendarassistant.data.model.EventTags
import com.antgskds.calendarassistant.data.model.MyEvent

object RuleMatchingEngine {
    const val RULE_GENERAL = "general"
    const val RULE_PICKUP = "pickup"
    const val RULE_FOOD = "food"
    const val RULE_TRAIN = "train"
    const val RULE_TAXI = "taxi"
    const val RULE_FLIGHT = "flight"
    const val RULE_TICKET = "ticket"
    const val RULE_SENDER = "sender"

    data class RulePayload(
        val ruleId: String,
        val payload: String,
        val rawHeader: String?
    )

    fun resolvePayload(event: MyEvent): RulePayload? {
        val fallbackRuleId = when (event.tag) {
            EventTags.PICKUP -> RULE_PICKUP
            EventTags.FOOD -> RULE_FOOD
            EventTags.TRAIN -> RULE_TRAIN
            EventTags.TAXI -> RULE_TAXI
            EventTags.FLIGHT -> RULE_FLIGHT
            EventTags.TICKET -> RULE_TICKET
            EventTags.SENDER -> RULE_SENDER
            else -> null
        }
        return resolvePayload(event.description, fallbackRuleId)
    }

    fun resolvePayload(description: String?, fallbackRuleId: String? = null): RulePayload? {
        val clean = description?.trim().orEmpty()
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
        val clean = description?.trim().orEmpty()
        if (clean.isBlank()) return null
        val payload = resolvePayload(clean, null) ?: return clean
        return payload.payload.trim().ifBlank { null }
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
        val header = rawHeader?.trim().orEmpty()
        if (header.isBlank()) return null
        return when (header) {
            "pickup", "取件" -> RULE_PICKUP
            "food", "取餐", "外卖" -> RULE_FOOD
            "train", "列车" -> RULE_TRAIN
            "taxi", "用车", "打车" -> RULE_TAXI
            "flight", "航班", "飞机" -> RULE_FLIGHT
            "ticket", "取票" -> RULE_TICKET
            "sender", "寄件" -> RULE_SENDER
            "general", "日程" -> RULE_GENERAL
            else -> {
                val lower = header.lowercase()
                if (lower.matches(Regex("[a-z0-9_]+"))) lower else null
            }
        }
    }
}
