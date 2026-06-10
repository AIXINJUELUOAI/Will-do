package com.antgskds.calendarassistant.widget

import com.antgskds.calendarassistant.data.model.WeatherData
import com.antgskds.calendarassistant.data.model.TimeNode
import java.time.LocalDate

data class WeatherWidgetSnapshot(
    val weather: WeatherData?
)

data class CourseWidgetSnapshot(
    val today: LocalDate,
    val weekStart: LocalDate,
    val weekNumber: Int,
    val nodes: List<TimeNode>,
    val items: List<CourseWidgetItem>,
    val sections: List<CourseWidgetSection>
) {
    val todayItems: List<CourseWidgetItem> get() = items.filter { it.date == today }
    val upcomingItems: List<CourseWidgetItem> get() = items.filter { !it.date.isBefore(today) }
}

data class CourseWidgetItem(
    val title: String,
    val location: String,
    val teacher: String,
    val date: LocalDate,
    val dayOfWeek: Int,
    val startNode: Int,
    val endNode: Int,
    val startTime: String,
    val endTime: String,
    val color: Int
) {
    val nodeText: String
        get() = if (startNode == endNode) "第${startNode}节" else "第${startNode}-${endNode}节"
}

data class CourseWidgetSection(
    val segment: CourseWidgetSegment,
    val range: IntRange
)
