package com.antgskds.calendarassistant.calendar.models.stubs

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RecurringEventUtils {
    fun formatMillis(millis: Long): String {
        return try {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
        } catch (_: Exception) {
            ""
        }
    }
}
