package com.antgskds.calendarassistant.core.content

data class NoteTimelineItem(
    override val stableId: String,
    override val title: String,
    override val subtitle: String? = null,
    override val detail: String? = null,
    override val timeRange: String? = null
) : TimelineItem {
    override val sourceType: ContentSourceType = ContentSourceType.NOTE
}

data class WeatherTimelineItem(
    override val stableId: String,
    override val title: String,
    override val subtitle: String? = null,
    override val detail: String? = null,
    override val timeRange: String? = null
) : TimelineItem {
    override val sourceType: ContentSourceType = ContentSourceType.WEATHER
}

data class VoiceCaptureTimelineItem(
    override val stableId: String,
    override val title: String,
    override val subtitle: String? = null,
    override val detail: String? = null,
    override val timeRange: String? = null
) : TimelineItem {
    override val sourceType: ContentSourceType = ContentSourceType.VOICE_CAPTURE
}
