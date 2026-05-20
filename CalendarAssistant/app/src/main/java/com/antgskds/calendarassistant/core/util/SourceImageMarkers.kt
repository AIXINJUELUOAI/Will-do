package com.antgskds.calendarassistant.core.util

private val SOURCE_IMAGE_MARKER_REGEX = Regex("\\[img:([^\\]]+)]")

fun extractSourceImagePath(description: String?): String? {
    return SOURCE_IMAGE_MARKER_REGEX.find(description.orEmpty())
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.takeIf { it.isNotBlank() }
}

fun stripSourceImageMarkers(description: String?): String {
    return description.orEmpty()
        .replace(SOURCE_IMAGE_MARKER_REGEX, "")
        .trim()
}

fun mergeSourceImageMarker(description: String?, sourceImagePath: String?): String {
    val cleanDescription = stripSourceImageMarkers(description)
    val cleanPath = sourceImagePath?.trim()?.takeIf { it.isNotBlank() } ?: return cleanDescription
    return if (cleanDescription.isBlank()) {
        "[img:$cleanPath]"
    } else {
        "$cleanDescription\n[img:$cleanPath]"
    }
}
