package com.antgskds.calendarassistant.core.util

import com.antgskds.calendarassistant.calendar.models.Event

data class EventFingerprint(
    val title: String,
    val startTS: Long,
    val endTS: Long,
    val location: String
) {
    companion object {
        fun from(event: Event): EventFingerprint = EventFingerprint(
            title = event.title,
            startTS = event.startTS,
            endTS = event.endTS,
            location = event.location
        )
    }
}
