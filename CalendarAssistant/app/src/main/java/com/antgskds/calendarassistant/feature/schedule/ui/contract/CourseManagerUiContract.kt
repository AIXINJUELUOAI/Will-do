package com.antgskds.calendarassistant.feature.schedule.ui.contract

import com.antgskds.calendarassistant.data.model.Course

data class CourseManagerUiState(
    val courses: List<Course>,
    val maxNodes: Int,
    val timeTableJson: String,
    val hapticEnabled: Boolean,
    val predictiveBackEnabled: Boolean
)

sealed interface CourseManagerUiAction {
    data class AddCourse(val course: Course) : CourseManagerUiAction
    data class UpdateCourse(val course: Course) : CourseManagerUiAction
    data class DeleteCourse(val course: Course) : CourseManagerUiAction
}
