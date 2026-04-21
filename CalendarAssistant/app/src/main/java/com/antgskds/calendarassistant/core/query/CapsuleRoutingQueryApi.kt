package com.antgskds.calendarassistant.core.query

enum class CapsuleRouteMode {
    MIUI_ISLAND,
    LIVE_CAPSULE,
    STANDARD_NOTIFICATION
}

interface CapsuleRoutingQueryApi {
    fun resolveMode(liveCapsuleEnabled: Boolean): CapsuleRouteMode
}
