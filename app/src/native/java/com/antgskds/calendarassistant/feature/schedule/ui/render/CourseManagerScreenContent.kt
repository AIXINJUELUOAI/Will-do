package com.antgskds.calendarassistant.feature.schedule.ui.render

import androidx.compose.runtime.Composable
import com.antgskds.calendarassistant.feature.schedule.ui.connector.MaterialCourseManagerScreen
import com.antgskds.calendarassistant.feature.schedule.ui.contract.CourseManagerUiAction
import com.antgskds.calendarassistant.feature.schedule.ui.contract.CourseManagerUiState

@Composable
fun CourseManagerScreenContent(
    state: CourseManagerUiState,
    uiSize: Int,
    onAction: (CourseManagerUiAction) -> Unit,
) = MaterialCourseManagerScreen(state, uiSize, onAction)
