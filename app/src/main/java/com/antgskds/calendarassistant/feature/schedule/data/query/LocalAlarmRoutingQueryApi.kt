package com.antgskds.calendarassistant.feature.schedule.data.query

import com.antgskds.calendarassistant.shared.query.AlarmRoute
import com.antgskds.calendarassistant.shared.query.AlarmRouteDecision
import com.antgskds.calendarassistant.shared.query.AlarmRoutingQueryApi
import com.antgskds.calendarassistant.platform.notification.alarmlegacy.NotificationScheduler

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
