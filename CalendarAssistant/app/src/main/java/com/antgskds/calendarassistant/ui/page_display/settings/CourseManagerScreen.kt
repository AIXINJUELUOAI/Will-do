package com.antgskds.calendarassistant.ui.page_display.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.core.course.CourseEventMapper
import com.antgskds.calendarassistant.core.course.TimeTableLayoutUtils
import com.antgskds.calendarassistant.data.model.Course
import com.antgskds.calendarassistant.ui.dialogs.CourseEditDialog
import com.antgskds.calendarassistant.ui.dialogs.CourseItem
import com.antgskds.calendarassistant.ui.viewmodel.MainViewModel

@Composable
fun CourseManagerScreen(
    viewModel: MainViewModel,
    uiSize: Int = 2
) {
    val uiState by viewModel.uiState.collectAsState()
    val courses = remember(uiState.rawEvents, uiState.settings) {
        CourseEventMapper.extractParentCourses(uiState.rawEvents, uiState.settings)
    }
    val maxNodes = remember(uiState.settings.timeTableJson) {
        TimeTableLayoutUtils.nodeCountFromJson(uiState.settings.timeTableJson)
    }

    var showEditDialog by remember { mutableStateOf(false) }
    var courseToEdit by remember { mutableStateOf<Course?>(null) }
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(modifier = Modifier.fillMaxSize()) {
        if (courses.isEmpty()) {
            Box(modifier = Modifier.align(Alignment.Center)) {
                Text("暂无课程，点击右下角添加", color = MaterialTheme.colorScheme.secondary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 80.dp + bottomInset),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(courses, key = { it.id }) { course ->
                    CourseItem(
                        course = course,
                        onDelete = { viewModel.deleteCourse(course) },
                        onClick = { courseToEdit = course; showEditDialog = true },
                        uiSize = uiSize
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = { courseToEdit = null; showEditDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 24.dp + bottomInset).size(72.dp),
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(Icons.Default.Add, contentDescription = "添加课程", modifier = Modifier.size(34.dp))
        }
    }

    if (showEditDialog) {
        CourseEditDialog(
            course = courseToEdit,
            maxNodes = maxNodes,
            timeTableJson = uiState.settings.timeTableJson,
            onDismiss = { showEditDialog = false; courseToEdit = null },
            onConfirm = { course ->
                if (courseToEdit == null) viewModel.addCourse(course) else viewModel.updateCourse(course)
                showEditDialog = false
                courseToEdit = null
            }
        )
    }
}
