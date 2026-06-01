package com.antgskds.calendarassistant.core.util

import com.antgskds.calendarassistant.calendar.models.Event

object EventDuplicateSignature {
    data class Key(
        val title: String,
        val startTS: Long,
        val endTS: Long,
        val description: String,
        val rrule: String,
        val type: Int,
        val parentId: Long
    )

    fun key(event: Event): Key = Key(
        title = event.title.trim(),
        startTS = event.startTS,
        endTS = event.endTS,
        description = stripSourceImageMarkers(event.description).trim(),
        rrule = normalizeRRule(event.rrule),
        type = event.type,
        parentId = event.parentId
    )

    private fun normalizeRRule(rrule: String): String {
        if (rrule.isBlank()) return ""
        return rrule.split(';')
            .map { it.trim().uppercase() }
            .filter { it.isNotBlank() }
            .sorted()
            .joinToString(";")
    }
}
