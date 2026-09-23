package com.antgskds.calendarassistant.feature.schedule.ui.render

import androidx.compose.runtime.Composable
import com.antgskds.calendarassistant.feature.schedule.domain.course.Course
import com.antgskds.calendarassistant.feature.schedule.ui.render.material.dialog.MaterialCourseEditDialog
import com.antgskds.calendarassistant.feature.schedule.ui.render.material.dialog.MaterialCourseSingleEditDialog
import java.time.LocalDate

@Composable
fun CourseEditDialog(
    course: Course?,
    maxNodes: Int = 12,
    timeTableJson: String = "",
    hapticEnabled: Boolean = true,
    predictiveBackEnabled: Boolean = true,
    onSwitchType: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onConfirm: (Course) -> Unit
) {
    MaterialCourseEditDialog(
        course = course,
        maxNodes = maxNodes,
        timeTableJson = timeTableJson,
        hapticEnabled = hapticEnabled,
        predictiveBackEnabled = predictiveBackEnabled,
        onSwitchType = onSwitchType,
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}

@Composable
fun CourseSingleEditDialog(
    initialName: String,
    initialLocation: String,
    initialStartNode: Int,
    initialEndNode: Int,
    initialDate: LocalDate,
    maxNodes: Int = 12,
    predictiveBackEnabled: Boolean = true,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onConfirm: (String, String, Int, Int, LocalDate) -> Unit
) {
    MaterialCourseSingleEditDialog(
        initialName = initialName,
        initialLocation = initialLocation,
        initialStartNode = initialStartNode,
        initialEndNode = initialEndNode,
        initialDate = initialDate,
        maxNodes = maxNodes,
        predictiveBackEnabled = predictiveBackEnabled,
        onDismiss = onDismiss,
        onDelete = onDelete,
        onConfirm = onConfirm
    )
}
