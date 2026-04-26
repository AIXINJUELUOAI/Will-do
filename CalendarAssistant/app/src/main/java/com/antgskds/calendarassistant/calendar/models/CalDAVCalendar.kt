package com.antgskds.calendarassistant.calendar.models

data class CalDAVCalendar(
    val id: Int,
    val displayName: String,
    val accountName: String,
    val accountType: String,
    val ownerName: String,
    var color: Int,
    val accessLevel: Int
) {
    fun canWrite(): Boolean = accessLevel >= 500

    fun getFullTitle(): String = "$displayName ($accountName)"
}
