package com.antgskds.calendarassistant.data.query

import com.antgskds.calendarassistant.core.query.AlarmRoute
import com.antgskds.calendarassistant.core.query.AlarmRouteDecision
import com.antgskds.calendarassistant.core.query.AlarmRoutingQueryApi
import com.antgskds.calendarassistant.service.notification.NotificationScheduler

class LocalAlarmRoutingQueryApi : AlarmRoutingQueryApi {
    override fun resolveRoute(action: String?): AlarmRouteDecision {
        return when (action) {
            NotificationScheduler.ACTION_CAPSULE_START -> AlarmRouteDecision(AlarmRoute.CAPSULE_START, false)
            NotificationScheduler.ACTION_CAPSULE_END -> AlarmRouteDecision(AlarmRoute.CAPSULE_END, false)
            NotificationScheduler.ACTION_REFRESH_CAPSULE -> AlarmRouteDecision(AlarmRoute.CAPSULE_REFRESH, false)
            NotificationScheduler.ACTION_REMINDER,
            null -> AlarmRouteDecision(AlarmRoute.REMINDER, false)
            else -> AlarmRouteDecision(AlarmRoute.REMINDER, true)
        }
    }
}
