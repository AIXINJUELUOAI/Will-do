package com.antgskds.calendarassistant.shared.event.events

enum class CapsuleRefreshPriority {
    LOW,
    NORMAL,
    HIGH
}

data class CapsuleRefreshRequestedEvent(
    val reason: String,
    val priority: CapsuleRefreshPriority = CapsuleRefreshPriority.NORMAL
)
