package com.antgskds.calendarassistant.data.model

import androidx.compose.ui.graphics.Color
import com.antgskds.calendarassistant.data.model.serializers.ColorSerializer
import kotlinx.serialization.Serializable

@Serializable
data class Course(
    val id: String,
    val name: String,
    val location: String = "",
    val teacher: String = "",
    @Serializable(with = ColorSerializer::class)
    val color: Color,
    val dayOfWeek: Int,        // 1=Mon, 7=Sun
    val startNode: Int,
    val endNode: Int,
    val startWeek: Int,
    val endWeek: Int,
    val weekType: Int = 0,     // 0=All, 1=Odd, 2=Even
    val excludedDates: List<String> = emptyList(),
    val isTemp: Boolean = false,           // 影子课程标记
    val parentCourseId: String? = null     // 父课程关联
)