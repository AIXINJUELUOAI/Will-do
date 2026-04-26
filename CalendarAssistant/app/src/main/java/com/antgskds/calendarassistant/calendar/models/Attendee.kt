package com.antgskds.calendarassistant.calendar.models

data class Attendee(
    val contactId: Int = 0,
    var name: String = "",
    val email: String = "",
    var status: Int = 0,
    var photoUri: String = "",
    var isMe: Boolean = false,
    var relationship: Int = 0
) {
    fun getPublicName(): String = if (name.isBlank()) email else name
}
