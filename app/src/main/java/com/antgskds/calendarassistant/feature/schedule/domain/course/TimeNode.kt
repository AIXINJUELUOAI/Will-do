package com.antgskds.calendarassistant.feature.schedule.domain.course

import kotlinx.serialization.Serializable

@Serializable
data class TimeNode(
    val index: Int,
    val startTime: String, // HH:mm
    val endTime: String,   // HH:mm
    val period: String = ""
)
