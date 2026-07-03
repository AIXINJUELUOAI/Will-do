package com.antgskds.calendarassistant.feature.api.notification.model

import com.antgskds.calendarassistant.feature.api.schedule.model.ScheduleInstanceKey

data class NotificationKey(
    val value: String
) {
    init {
        require(value.isNotBlank()) { "NotificationKey must not be blank." }
    }

    companion object {
        fun scheduleReminder(
            instanceKey: ScheduleInstanceKey,
            offsetMinutes: Int
        ): NotificationKey = NotificationKey("schedule:${instanceKey.stableKey}:offset:$offsetMinutes")

        fun scheduleAction(
            instanceKey: ScheduleInstanceKey,
            action: String
        ): NotificationKey = NotificationKey("schedule:${instanceKey.stableKey}:action:$action")

        fun recognition(id: String): NotificationKey = NotificationKey("recognition:$id")

        fun weatherAlert(id: String): NotificationKey = NotificationKey("weather:$id")

        fun systemStatus(id: String): NotificationKey = NotificationKey("system:$id")

        fun debug(id: String): NotificationKey = NotificationKey("debug:$id")
    }
}
