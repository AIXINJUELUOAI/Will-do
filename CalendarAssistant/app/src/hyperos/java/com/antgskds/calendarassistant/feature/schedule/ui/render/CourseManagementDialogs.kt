package com.antgskds.calendarassistant.feature.schedule.ui.render

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.app.ui.theme.material.getRandomEventColor
import com.antgskds.calendarassistant.feature.schedule.domain.course.Course
import com.antgskds.calendarassistant.shared.ui.material.component.WheelDatePickerDialog
import java.time.LocalDate
import java.util.UUID
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

private val weekDayLabels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
private val weekTypeLabels = listOf("每周", "单周", "双周")

@Composable
fun CourseEditDialog(
    course: Course?,
    maxNodes: Int = 12,
    timeTableJson: String = "",
    hapticEnabled: Boolean = true,
    predictiveBackEnabled: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: (Course) -> Unit,
) {
    val key = course?.id.orEmpty()
    val safeMaxNodes = maxNodes.coerceAtLeast(1)
    val nodeLabels = remember(safeMaxNodes) { (1..safeMaxNodes).map { "第 $it 节" } }
    val weekLabels = remember { (1..30).map { "第 $it 周" } }
    var name by remember(key) { mutableStateOf(course?.name.orEmpty()) }
    var location by remember(key) { mutableStateOf(course?.location.orEmpty()) }
    var teacher by remember(key) { mutableStateOf(course?.teacher.orEmpty()) }
    var dayOfWeek by remember(key) { mutableStateOf((course?.dayOfWeek ?: 1).coerceIn(1, 7)) }
    var startNode by remember(key) { mutableStateOf((course?.startNode ?: 1).coerceIn(1, safeMaxNodes)) }
    var endNode by remember(key) { mutableStateOf((course?.endNode ?: 2).coerceIn(startNode, safeMaxNodes)) }
    var startWeek by remember(key) { mutableStateOf((course?.startWeek ?: 1).coerceIn(1, 30)) }
    var endWeek by remember(key) { mutableStateOf((course?.endWeek ?: 16).coerceIn(startWeek, 30)) }
    var weekType by remember(key) { mutableStateOf((course?.weekType ?: 0).coerceIn(0, 2)) }

    WindowBottomSheet(
        show = true,
        title = if (course == null) "新增课程" else "编辑课程",
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 700.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TextField(
                value = name,
                onValueChange = { name = it },
                label = "课程名称",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = location,
                onValueChange = { location = it },
                label = "上课地点",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = teacher,
                onValueChange = { teacher = it },
                label = "教师",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Card(insideMargin = PaddingValues(0.dp)) {
                WindowDropdownPreference(
                    items = weekDayLabels,
                    selectedIndex = dayOfWeek - 1,
                    title = "星期",
                    onSelectedIndexChange = { dayOfWeek = it + 1 },
                )
                WindowDropdownPreference(
                    items = nodeLabels,
                    selectedIndex = startNode - 1,
                    title = "开始节次",
                    onSelectedIndexChange = {
                        startNode = it + 1
                        if (endNode < startNode) endNode = startNode
                    },
                )
                WindowDropdownPreference(
                    items = nodeLabels,
                    selectedIndex = endNode - 1,
                    title = "结束节次",
                    onSelectedIndexChange = { endNode = (it + 1).coerceAtLeast(startNode) },
                )
            }

            Card(insideMargin = PaddingValues(0.dp)) {
                WindowDropdownPreference(
                    items = weekLabels,
                    selectedIndex = startWeek - 1,
                    title = "开始周",
                    onSelectedIndexChange = {
                        startWeek = it + 1
                        if (endWeek < startWeek) endWeek = startWeek
                    },
                )
                WindowDropdownPreference(
                    items = weekLabels,
                    selectedIndex = endWeek - 1,
                    title = "结束周",
                    onSelectedIndexChange = { endWeek = (it + 1).coerceAtLeast(startWeek) },
                )
                WindowDropdownPreference(
                    items = weekTypeLabels,
                    selectedIndex = weekType,
                    title = "重复周",
                    onSelectedIndexChange = { weekType = it },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("取消")
                }
                Button(
                    onClick = {
                        onConfirm(
                            Course(
                                id = course?.id ?: UUID.randomUUID().toString(),
                                name = name.trim(),
                                location = location.trim(),
                                teacher = teacher.trim(),
                                color = course?.color ?: getRandomEventColor().toArgb(),
                                dayOfWeek = dayOfWeek,
                                startNode = startNode,
                                endNode = endNode,
                                startWeek = startWeek,
                                endWeek = endWeek,
                                weekType = weekType,
                                excludedDates = course?.excludedDates.orEmpty(),
                                isTemp = course?.isTemp ?: false,
                                parentCourseId = course?.parentCourseId,
                            ),
                        )
                    },
                    modifier = Modifier.weight(1f),
                    enabled = name.isNotBlank(),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(if (course == null) "创建" else "保存")
                }
            }
        }
    }
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
    onConfirm: (String, String, Int, Int, LocalDate) -> Unit,
) {
    val safeMaxNodes = maxNodes.coerceAtLeast(1)
    val nodeLabels = remember(safeMaxNodes) { (1..safeMaxNodes).map { "第 $it 节" } }
    var name by remember(initialName) { mutableStateOf(initialName) }
    var location by remember(initialLocation) { mutableStateOf(initialLocation) }
    var startNode by remember(initialStartNode) { mutableStateOf(initialStartNode.coerceIn(1, safeMaxNodes)) }
    var endNode by remember(initialEndNode) { mutableStateOf(initialEndNode.coerceIn(startNode, safeMaxNodes)) }
    var date by remember(initialDate) { mutableStateOf(initialDate) }
    var showDatePicker by remember { mutableStateOf(false) }

    WindowBottomSheet(
        show = true,
        title = "编辑本次课程",
        onDismissRequest = onDismiss,
        allowDismiss = !showDatePicker,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TextField(
                value = name,
                onValueChange = { name = it },
                label = "课程名称",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = location,
                onValueChange = { location = it },
                label = "上课地点",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Card(insideMargin = PaddingValues(0.dp)) {
                WindowDropdownPreference(
                    items = nodeLabels,
                    selectedIndex = startNode - 1,
                    title = "开始节次",
                    onSelectedIndexChange = {
                        startNode = it + 1
                        if (endNode < startNode) endNode = startNode
                    },
                )
                WindowDropdownPreference(
                    items = nodeLabels,
                    selectedIndex = endNode - 1,
                    title = "结束节次",
                    onSelectedIndexChange = { endNode = (it + 1).coerceAtLeast(startNode) },
                )
            }
            Button(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("调整日期 · $date")
            }
            Button(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    color = MiuixTheme.colorScheme.error,
                    contentColor = MiuixTheme.colorScheme.onError,
                ),
            ) {
                Text("删除本次课程")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("取消")
                }
                Button(
                    onClick = { onConfirm(name.trim(), location.trim(), startNode, endNode, date) },
                    modifier = Modifier.weight(1f),
                    enabled = name.isNotBlank(),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text("保存")
                }
            }
        }
    }

    if (showDatePicker) {
        WheelDatePickerDialog(
            initialDate = date,
            onDismiss = { showDatePicker = false },
            title = "调整日期",
            onConfirm = {
                date = it
                showDatePicker = false
            },
        )
    }
}
