package com.antgskds.calendarassistant.data.model.external.wakeup

import kotlinx.serialization.Serializable

@Serializable
data class WakeUpSettingsDTO(
    val startDate: String = "",
    val maxWeek: Int = 20,
    val nodes: Int = 12
)

@Serializable
data class WakeUpCourseBaseDTO(
    val id: Int,
    val courseName: String,
    val teacher: String = "",
    val color: String = "#808080"
)

@Serializable
data class WakeUpScheduleDTO(
    val id: Int,
    val day: Int,
    val startNode: Int,
    val step: Int,
    val startWeek: Int,
    val endWeek: Int,
    val type: Int = 0,
    val teacher: String = "",
    val room: String = ""
)
