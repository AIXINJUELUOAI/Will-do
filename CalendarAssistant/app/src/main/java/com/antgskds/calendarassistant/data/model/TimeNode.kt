package com.antgskds.calendarassistant.data.model

import kotlinx.serialization.Serializable

@Serializable
data class TimeNode(
    val index: Int,
    val startTime: String, // HH:mm
    val endTime: String    // HH:mm
)