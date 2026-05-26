package com.antgskds.calendarassistant.core.rule

import com.antgskds.calendarassistant.calendar.models.EventTags

object RecognitionRuleCatalog {
    const val DEFAULT_TAG = EventTags.GENERAL

    private data class Rule(
        val tag: String,
        val promptName: String,
        val header: String,
        val aliases: Set<String>,
        val descriptionFormat: String? = null,
        val effectiveExamples: List<String> = emptyList(),
        val useCurrentTimeWhenMissing: Boolean = false,
        val includeInLocalPrompt: Boolean = true
    )

    private val rules = listOf(
        Rule(
            tag = EventTags.GENERAL,
            promptName = "普通日程",
            header = "日程",
            aliases = setOf("general", "日程", "普通日程"),
            descriptionFormat = "【日程】备注"
        ),
        Rule(
            tag = EventTags.TRAIN,
            promptName = "列车",
            header = "列车",
            aliases = setOf("train", "列车", "火车", "高铁"),
            descriptionFormat = "【列车】车次|检票口|座位号",
            effectiveExamples = listOf("火车/高铁订单")
        ),
        Rule(
            tag = EventTags.FLIGHT,
            promptName = "航班",
            header = "航班",
            aliases = setOf("flight", "航班", "飞机"),
            descriptionFormat = "【航班】航班号|登机口|座位号",
            effectiveExamples = listOf("航班订单")
        ),
        Rule(
            tag = EventTags.TAXI,
            promptName = "打车",
            header = "用车",
            aliases = setOf("taxi", "用车", "打车", "网约车", "滴滴", "高德", "出租车"),
            descriptionFormat = "【用车】颜色|车型|车牌",
            effectiveExamples = listOf("滴滴/高德/网约车/出租车/快车/专车/预约单/行程中页面"),
            useCurrentTimeWhenMissing = true
        ),
        Rule(
            tag = EventTags.PICKUP,
            promptName = "取件",
            header = "取件",
            aliases = setOf("pickup", "取件", "快递", "菜鸟", "驿站"),
            descriptionFormat = "【取件】取件码|品牌|位置",
            effectiveExamples = listOf("快递/菜鸟驿站/取件码"),
            useCurrentTimeWhenMissing = true
        ),
        Rule(
            tag = EventTags.FOOD,
            promptName = "取餐",
            header = "取餐",
            aliases = setOf("food", "取餐", "外卖"),
            descriptionFormat = "【取餐】取餐码|品牌|位置",
            effectiveExamples = listOf("外卖取餐码"),
            useCurrentTimeWhenMissing = true
        ),
        Rule(
            tag = EventTags.TICKET,
            promptName = "取票",
            header = "取票",
            aliases = setOf("ticket", "取票", "票券"),
            descriptionFormat = "【取票】取票码|品牌|位置",
            effectiveExamples = listOf("票券取票码"),
            useCurrentTimeWhenMissing = true
        ),
        Rule(
            tag = EventTags.SENDER,
            promptName = "寄件",
            header = "寄件",
            aliases = setOf("sender", "寄件"),
            descriptionFormat = "【寄件】寄件码|品牌|地点",
            effectiveExamples = listOf("寄件码"),
            useCurrentTimeWhenMissing = true
        ),
        Rule(
            tag = EventTags.COURSE,
            promptName = "课程",
            header = "课程",
            aliases = setOf("course", "课程"),
            includeInLocalPrompt = false
        ),
        Rule(
            tag = EventTags.NOTE,
            promptName = "便签",
            header = "便签",
            aliases = setOf("note", "便签"),
            includeInLocalPrompt = false
        )
    )

    private val rulesByTag = rules.associateBy { it.tag }
    private val aliasesByKey = rules.flatMap { rule ->
        (rule.aliases + rule.tag + rule.header + rule.promptName).map { alias -> normalizeKey(alias) to rule.tag }
    }.toMap()

    private val localPromptRules = rules.filter { it.includeInLocalPrompt }

    fun normalizeKnownTag(raw: String?): String? {
        val key = normalizeKey(raw.orEmpty())
        if (key.isBlank()) return null
        return aliasesByKey[key]
    }

    fun normalizeTag(raw: String?): String {
        return normalizeKnownTag(raw) ?: DEFAULT_TAG
    }

    fun headerFor(tag: String): String {
        return rulesByTag[normalizeTag(tag)]?.header ?: rulesByTag.getValue(EventTags.GENERAL).header
    }

    fun formatDescription(tag: String, payload: String): String {
        val header = headerFor(tag)
        val cleanPayload = payload.trim()
        return if (cleanPayload.isBlank()) "【$header】" else "【$header】$cleanPayload"
    }

    fun localPromptRecognitionTypes(): String {
        return localPromptRules.joinToString("、") { "${it.promptName} ${it.tag}" }
    }

    fun localPromptEffectiveEvents(): String {
        return localPromptRules.flatMap { it.effectiveExamples }.joinToString("、")
    }

    fun localPromptDescriptionRules(): String {
        return localPromptRules
            .mapNotNull { rule -> rule.descriptionFormat?.let { format -> "${rule.promptName}用$format" } }
            .joinToString("；")
    }

    fun localPromptCurrentTimeFallbackRules(): String {
        return localPromptRules
            .filter { it.useCurrentTimeWhenMissing }
            .joinToString("/") { it.promptName }
    }

    fun localPromptTaxiHint(): String {
        return "打车页面只要出现车牌、司机、车型、快车、专车、出租车、前往目的地、距目的地、我没上车、行程分享之一，就创建 taxi 事件；车牌必须放在【用车】第三个字段。"
    }

    private fun normalizeKey(value: String): String {
        return value.trim().lowercase()
    }
}
