package com.antgskds.calendarassistant.feature.schedule.ui.render

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.antgskds.calendarassistant.data.model.ScheduleDisplayItem
import com.antgskds.calendarassistant.ui.page_display.MaterialScheduleView
import java.time.LocalDate

@Composable
fun ScheduleView(
    items: List<ScheduleDisplayItem>,
    semesterStartDateStr: String?,
    totalWeeks: Int,
    maxNodes: Int,
    selectedDate: LocalDate,
    modifier: Modifier = Modifier,
    onCourseClick: (ScheduleDisplayItem) -> Unit = {}
) {
    MaterialScheduleView(
        items = items,
        semesterStartDateStr = semesterStartDateStr,
        totalWeeks = totalWeeks,
        maxNodes = maxNodes,
        selectedDate = selectedDate,
        modifier = modifier,
        onCourseClick = onCourseClick
    )
}
