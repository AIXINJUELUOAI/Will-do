package com.antgskds.calendarassistant.store.reminder

import com.antgskds.calendarassistant.calendar.helpers.REMINDER_NOTIFICATION
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.Reminder
import com.antgskds.calendarassistant.data.model.MySettings

object ReminderPolicy {
    fun effectiveReminders(event: Event, settings: MySettings): List<Reminder> {
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
