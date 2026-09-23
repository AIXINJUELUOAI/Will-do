package com.antgskds.calendarassistant.feature.schedule.data.store.reminder

import com.antgskds.calendarassistant.feature.schedule.domain.calendar.REMINDER_NOTIFICATION
import com.antgskds.calendarassistant.feature.schedule.domain.model.Event
import com.antgskds.calendarassistant.feature.schedule.domain.model.Reminder
import com.antgskds.calendarassistant.feature.settings.data.model.MySettings

object ReminderPolicy {
    fun effectiveReminders(event: Event, settings: MySettings): List<Reminder> {
        if (!com.antgskds.calendarassistant.feature.schedule.domain.course.CourseFeaturePolicy.allows(event, settings)) return emptyList()
        val reminders = linkedMapOf<Int, Reminder>()

        if (settings.isAdvanceReminderEnabled && settings.advanceReminderMinutes > 0) {
            val minutes = settings.advanceReminderMinutes.coerceAtLeast(0)
            reminders[minutes] = Reminder(minutes, REMINDER_NOTIFICATION)
        }

        event.getReminders().forEach { reminder ->
            if (reminder.minutes >= 0) {
                reminders[reminder.minutes] = reminder
            }
        }

        reminders.putIfAbsent(0, Reminder(0, REMINDER_NOTIFICATION))

        return reminders.values.sortedByDescending { it.minutes }
    }
}
