package com.antgskds.calendarassistant.data.model

import androidx.compose.ui.graphics.Color
import com.antgskds.calendarassistant.data.model.serializers.ColorSerializer
import com.antgskds.calendarassistant.data.model.serializers.LocalDateSerializer
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.util.UUID

@Serializable
data class MyEvent(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    @Serializable(with = LocalDateSerializer::class)
    val startDate: LocalDate,
    @Serializable(with = LocalDateSerializer::class)
    val endDate: LocalDate,
    val startTime: String, // HH:mm
    val endTime: String,   // HH:mm
    val location: String,
    val description: String,
    @Serializable(with = ColorSerializer::class)
    val color: Color,
    val isImportant: Boolean = false,
    val sourceImagePath: String? = null,
    val reminders: List<Int> = emptyList(),
    val eventType: String = "event" // "event", "temp", "course"
)