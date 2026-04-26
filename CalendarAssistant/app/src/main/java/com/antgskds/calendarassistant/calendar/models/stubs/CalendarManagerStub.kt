package com.antgskds.calendarassistant.calendar.models.stubs

object CalendarManager {
    data class CalendarInfo(
        val id: Long,
        val name: String,
        val accountName: String = "",
        val accountType: String = "",
        val isVisible: Boolean = true,
        val syncEvents: Boolean = true,
        val isWritable: Boolean = false
    )
}
