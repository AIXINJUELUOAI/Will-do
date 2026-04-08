package com.antgskds.calendarassistant.core.content

interface ContentProvider<T> {
    val sourceType: ContentSourceType
    fun toTimelineItem(input: T): TimelineItem
}

data class ContentDefinition(
    val sourceType: ContentSourceType,
    val displayName: String,
    val supportsTimeline: Boolean = true,
    val supportsCapsule: Boolean = false
)

object ContentRegistry {
    private val definitions = linkedMapOf<ContentSourceType, ContentDefinition>()

    fun register(definition: ContentDefinition) {
        definitions[definition.sourceType] = definition
    }

    fun register(sourceType: ContentSourceType) {
        register(
            ContentDefinition(
                sourceType = sourceType,
                displayName = sourceType.name.lowercase().replace('_', ' ')
            )
        )
    }

    fun getRegisteredSourceTypes(): List<ContentSourceType> = definitions.keys.toList()

    fun getDefinitions(): List<ContentDefinition> = definitions.values.toList()
}
