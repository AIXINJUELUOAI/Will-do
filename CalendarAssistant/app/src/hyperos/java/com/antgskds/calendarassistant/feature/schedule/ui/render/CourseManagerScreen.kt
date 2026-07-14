package com.antgskds.calendarassistant.feature.schedule.ui.render

import androidx.compose.runtime.Composable
import com.antgskds.calendarassistant.ui.contract.CourseManagerUiAction
import com.antgskds.calendarassistant.ui.contract.CourseManagerUiState
import com.antgskds.calendarassistant.ui.page_display.settings.MaterialCourseManagerScreen

@Composable
fun CourseManagerContent(state: CourseManagerUiState, uiSize: Int = 2, onAction: (CourseManagerUiAction) -> Unit) {
    MaterialCourseManagerScreen(state = state, uiSize = uiSize, onAction = onAction)
}
