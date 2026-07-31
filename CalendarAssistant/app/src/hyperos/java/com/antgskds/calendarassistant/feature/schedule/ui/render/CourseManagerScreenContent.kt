package com.antgskds.calendarassistant.feature.schedule.ui.render

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.feature.schedule.domain.course.Course
import com.antgskds.calendarassistant.feature.schedule.ui.contract.CourseManagerUiAction
import com.antgskds.calendarassistant.feature.schedule.ui.contract.CourseManagerUiState
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun CourseManagerScreenContent(
    state: CourseManagerUiState,
    uiSize: Int,
    onAction: (CourseManagerUiAction) -> Unit,
) {
    var editingCourse by remember { mutableStateOf<Course?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(modifier = Modifier.fillMaxSize()) {
        if (state.courses.isEmpty()) {
            Text(
                text = "暂无课程，点击右下角添加",
                modifier = Modifier.align(Alignment.Center),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp + bottomInset),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.courses, key = { it.id }) { course ->
                    CourseCard(
                        course = course,
                        onClick = {
                            editingCourse = course
                            showEditor = true
                        },
                        onDelete = { onAction(CourseManagerUiAction.DeleteCourse(course)) },
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = {
                editingCourse = null
                showEditor = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 20.dp + bottomInset),
        ) {
            Icon(MiuixIcons.Normal.Add, "添加课程")
        }
    }

    if (showEditor) {
        CourseEditDialog(
            course = editingCourse,
            maxNodes = state.maxNodes,
            timeTableJson = state.timeTableJson,
            hapticEnabled = state.hapticEnabled,
            predictiveBackEnabled = state.predictiveBackEnabled,
            onDismiss = {
                showEditor = false
                editingCourse = null
            },
            onConfirm = { course ->
                onAction(
                    if (editingCourse == null) {
                        CourseManagerUiAction.AddCourse(course)
                    } else {
                        CourseManagerUiAction.UpdateCourse(course)
                    },
                )
                showEditor = false
                editingCourse = null
            },
        )
    }
}

@Composable
private fun CourseCard(
    course: Course,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(16.dp),
        onClick = onClick,
        onLongPress = onDelete,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = course.name,
                fontWeight = FontWeight.Bold,
                fontSize = MiuixTheme.textStyles.headline1.fontSize,
            )
            Text(
                text = buildString {
                    append("周${course.dayOfWeek} · 第${course.startNode}-${course.endNode}节")
                    if (course.location.isNotBlank()) append(" · ${course.location}")
                },
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = MiuixTheme.textStyles.body2.fontSize,
            )
            Text(
                text = "第${course.startWeek}-${course.endWeek}周${if (course.teacher.isNotBlank()) " · ${course.teacher}" else ""}",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
            )
        }
    }
}
