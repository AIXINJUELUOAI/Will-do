package com.antgskds.calendarassistant.shared.util

import com.antgskds.calendarassistant.feature.schedule.domain.model.Event

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
