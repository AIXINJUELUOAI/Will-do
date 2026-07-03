package com.antgskds.calendarassistant.shared.management.resource.notification.display.normal

object TransportNormalDisplay {
    private val transportRuleIds = setOf("train", "flight")

    fun isTransportRule(ruleId: String?): Boolean {
        return ruleId in transportRuleIds
    }

    fun title(renderedTitle: String, fallbackTitle: String): String {
        return renderedTitle.ifBlank { fallbackTitle }
    }
}
