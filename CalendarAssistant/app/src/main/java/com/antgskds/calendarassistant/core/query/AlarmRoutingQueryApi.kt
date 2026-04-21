package com.antgskds.calendarassistant.core.query

enum class AlarmRoute {
    CAPSULE_START,
    CAPSULE_END,
    CAPSULE_REFRESH,
    REMINDER
}

data class AlarmRouteDecision(
    val route: AlarmRoute,
    val fromUnknownAction: Boolean
)

interface AlarmRoutingQueryApi {
    fun resolveRoute(action: String?): AlarmRouteDecision
}
