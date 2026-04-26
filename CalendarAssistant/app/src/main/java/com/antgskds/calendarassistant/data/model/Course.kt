package com.antgskds.calendarassistant.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Course(
    val id: String,
    val name: String,
    val location: String = "",
    val teacher: String = "",
    val color: Int,
    val dayOfWeek: Int,
    val startNode: Int,
    val endNode: Int,
    val startWeek: Int,
    val endWeek: Int,
    val weekType: Int = 0,
    val excludedDates: List<String> = emptyList(),
    val isTemp: Boolean = false,
    val parentCourseId: String? = null
)
