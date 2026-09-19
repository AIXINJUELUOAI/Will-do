package com.antgskds.calendarassistant.feature.recognition.ingest.instantcode

import com.antgskds.calendarassistant.feature.schedule.domain.model.EventTags

enum class InstantCodeType(
    val tag: String,
    val header: String,
    val displayLabel: String,
    val titleName: String,
    val emoji: String
) {
    PICKUP(EventTags.PICKUP, "取件", "取件码", "取件", "📦"),
    FOOD(EventTags.FOOD, "取餐", "取餐码", "取餐", "🍔"),
    TICKET(EventTags.TICKET, "取票", "取票码", "取票", "🎫"),
    SENDER(EventTags.SENDER, "寄件", "寄件码", "寄件", "📮")
}

data class InstantCodeCandidate(
    val type: InstantCodeType,
    val code: String,
    val company: String = "",
    val location: String = "",
    val sourceText: String = ""
)
