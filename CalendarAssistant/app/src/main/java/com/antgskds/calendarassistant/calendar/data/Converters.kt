package com.antgskds.calendarassistant.calendar.data

import androidx.room.TypeConverter
import com.antgskds.calendarassistant.calendar.models.Attendee
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    private val gson = Gson()
    private val stringType = object : TypeToken<List<String>>() {}.type
    private val attendeeType = object : TypeToken<List<Attendee>>() {}.type

    @TypeConverter
    fun jsonToStringList(value: String): List<String> {
        val normalized = if (value.isNotBlank() && !value.startsWith("[")) "[$value]" else value
        return try {
            gson.fromJson(normalized, stringType) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun stringListToJson(list: List<String>): String = gson.toJson(list)

    @TypeConverter
    fun attendeeListToJson(list: List<Attendee>): String = gson.toJson(list)

    @TypeConverter
    fun jsonToAttendeeList(value: String): List<Attendee> {
        if (value.isBlank()) return emptyList()
        return try {
            gson.fromJson(value, attendeeType) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }
}
