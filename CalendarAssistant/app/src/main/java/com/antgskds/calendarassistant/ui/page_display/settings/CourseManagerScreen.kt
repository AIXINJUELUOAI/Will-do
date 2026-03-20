package com.antgskds.calendarassistant.ui.page_display.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import com.antgskds.calendarassistant.core.course.TimeTableLayoutUtils
import com.antgskds.calendarassistant.data.model.Course
import com.antgskds.calendarassistant.ui.dialogs.CourseEditDialog
import com.antgskds.calendarassistant.ui.dialogs.CourseItem
import com.antgskds.calendarassistant.ui.viewmodel.MainViewModel

@Composable
fun CourseManagerScreen(
    viewModel: MainViewModel,
    uiSize: Int = 2 // 1=小, 2=中, 3=大
) {
    val uiState by viewModel.uiState.collectAsState()
    val courses = uiState.courses
    val maxNodes = remember(uiState.settings.timeTableJson) {
        TimeTableLayoutUtils.nodeCountFromJson(uiState.settings.timeTableJson)
    }

    var showEditDialog by remember { mutableStateOf(false) }
    var courseToEdit by remember { mutableStateOf<Course?>(null) }

    // 统一 FAB 尺寸为 72.dp，图标 34.dp
    val fabSize = 72.dp
    val fabIconSize = 34.dp

    // 过滤影子课程
    val displayCourses = remember(courses) {
        courses.filter { !it.isTemp }
    }

    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(modifier = Modifier.fillMaxSize()) {
        if (displayCourses.isEmpty()) {
            Box(modifier = Modifier.align(Alignment.Center)) {
                Text("暂无课程，点击右下角添加", color = MaterialTheme.colorScheme.secondary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 16.dp,
                    end = 16.dp,
                    bottom = 80.dp + bottomInset
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(displayCourses, key = { it.id }) { course ->
                    CourseItem(
                        course = course,
                        onDelete = { viewModel.deleteCourse(course) },
                        onClick = { courseToEdit = course; showEditDialog = true },
                        uiSize = uiSize
                    )
                }
            }
        }

        // 悬浮添加按钮 - 圆形，大小受 uiSize 影响
        FloatingActionButton(
            onClick = { courseToEdit = null; showEditDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 24.dp + bottomInset)
                .size(fabSize),
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "添加课程",
                modifier = Modifier.size(fabIconSize)
            )
        }
    }

    // 复用原有的 CourseEditDialog (它是一个纯表单弹窗，适合保留为 Dialog)
    if (showEditDialog) {
        CourseEditDialog(
            course = courseToEdit,
            maxNodes = maxNodes,
            onDismiss = { showEditDialog = false; courseToEdit = null },
            onConfirm = { newCourse: Course ->
                if (courseToEdit != null) {
                    viewModel.updateCourse(newCourse)
                } else {
                    viewModel.addCourse(newCourse)
                }
                showEditDialog = false
                courseToEdit = null
            }
        )
    }
}
