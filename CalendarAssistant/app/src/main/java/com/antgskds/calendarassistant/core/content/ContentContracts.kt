package com.antgskds.calendarassistant.core.content

enum class ContentSourceType {
    SCHEDULE,
    WEATHER,
    VOICE_CAPTURE,
    IMAGE_SHARE
}

interface TimelineItem {
    val stableId: String
    val sourceType: ContentSourceType
    val title: String
    val subtitle: String?
    val detail: String?
    val timeRange: String?
}

interface CapsuleContentItem {
    val stableId: String
    val sourceType: ContentSourceType
    val shortText: String
    val primaryText: String
    val secondaryText: String?
    val expandedText: String?
}
