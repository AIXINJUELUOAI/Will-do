package com.antgskds.calendarassistant.ui.dialogs

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.antgskds.calendarassistant.data.model.Course
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.antgskds.calendarassistant.ui.components.WheelDatePickerDialog
import com.antgskds.calendarassistant.ui.components.WheelPicker
import com.antgskds.calendarassistant.ui.viewmodel.MainViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 1. 课程编辑/添加弹窗 (完整版)
 * 包含名称、地点、老师、周次、节次、颜色等
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CourseEditDialog(
    course: Course?,
    onDismiss: () -> Unit,
    onConfirm: (Course) -> Unit
) {
    var name by remember { mutableStateOf(course?.name ?: "") }
    var location by remember { mutableStateOf(course?.location ?: "") }
    var teacher by remember { mutableStateOf(course?.teacher ?: "") }
    var dayOfWeek by remember { mutableIntStateOf(course?.dayOfWeek ?: 1) }
    var startNode by remember { mutableIntStateOf(course?.startNode ?: 1) }
    var endNode by remember { mutableIntStateOf(course?.endNode ?: 2) }
    var startWeek by remember { mutableIntStateOf(course?.startWeek ?: 1) }
    var endWeek by remember { mutableIntStateOf(course?.endWeek ?: 18) }
    var weekType by remember { mutableIntStateOf(course?.weekType ?: 0) }
    var color by remember { mutableStateOf(course?.color ?: Color(0xFF6200EE)) }

    var showDayPicker by remember { mutableStateOf(false) }
    var showNodeRangePicker by remember { mutableStateOf(false) }
    var showWeekRangePicker by remember { mutableStateOf(false) }
    var showWeekTypePicker by remember { mutableStateOf(false) }

    val weekTypeOptions = listOf("每周", "单周", "双周")
    val colors = listOf(
        Color(0xFFF44336), Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF673AB7),
        Color(0xFF3F51B5), Color(0xFF2196F3), Color(0xFF03A9F4), Color(0xFF00BCD4),
        Color(0xFF009688), Color(0xFF4CAF50), Color(0xFF8BC34A), Color(0xFFCDDC39),
        Color(0xFFFFEB3B), Color(0xFFFFC107), Color(0xFFFF9800), Color(0xFFFF5722),
        Color(0xFF795548), Color(0xFF9E9E9E), Color(0xFF607D8B), Color(0xFF000000)
    )

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = Modifier.fillMaxWidth(0.85f).heightIn(max = 670.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(if (course == null) "添加课程" else "编辑课程", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }

                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("课程名称") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("地点") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = teacher, onValueChange = { teacher = it }, label = { Text("教师") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

                    HorizontalDivider()

                    // 时间选择
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ButtonSelector("周$dayOfWeek", Modifier.weight(1f)) { showDayPicker = true }
                        ButtonSelector("第 $startNode - $endNode 节", Modifier.weight(1.2f)) { showNodeRangePicker = true }
                    }
                    // 周次选择
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ButtonSelector("第 $startWeek - $endWeek 周", Modifier.weight(1.5f)) { showWeekRangePicker = true }
                        ButtonSelector(weekTypeOptions.getOrElse(weekType){""}, Modifier.weight(1f)) { showWeekTypePicker = true }
                    }

                    HorizontalDivider()
                    Text("颜色标签", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        colors.forEach { c ->
                            Box(modifier = Modifier.size(32.dp).background(c, CircleShape).clickable { color = c }.padding(4.dp)) {
                                if (color == c) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.align(Alignment.Center))
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                Row(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        if (name.isNotBlank()) {
                            onConfirm(Course(
                                id = course?.id ?: UUID.randomUUID().toString(),
                                name = name, location = location, teacher = teacher, color = color,
                                dayOfWeek = dayOfWeek, startNode = startNode, endNode = endNode,
                                startWeek = startWeek, endWeek = endWeek, weekType = weekType,
                                excludedDates = course?.excludedDates ?: emptyList(),
                                isTemp = false, parentCourseId = null
                            ))
                        }
                    }) { Text("确定") }
                }
            }
        }
    }

    // 弹窗逻辑
    if (showDayPicker) {
        val days = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        AlertDialog(
            onDismissRequest = { showDayPicker = false },
            title = { Text("选择星期") },
            text = { WheelPicker(items = days, initialIndex = dayOfWeek - 1, onSelectionChanged = { dayOfWeek = it + 1 }) },
            confirmButton = { TextButton(onClick = { showDayPicker = false }) { Text("确定") } }
        )
    }

    if (showNodeRangePicker) {
        WheelRangePickerDialog("选择节次范围", 1..12, startNode, endNode, { showNodeRangePicker = false }, { s, e -> startNode = s; endNode = e }, { "第 $it 节" })
    }

    if (showWeekRangePicker) {
        WheelRangePickerDialog("选择周次范围", 1..25, startWeek, endWeek, { showWeekRangePicker = false }, { s, e -> startWeek = s; endWeek = e }, { "第 $it 周" })
    }

    if (showWeekTypePicker) {
        AlertDialog(
            onDismissRequest = { showWeekTypePicker = false },
            title = { Text("课程频率") },
            text = { WheelPicker(items = weekTypeOptions, initialIndex = weekType, onSelectionChanged = { weekType = it }) },
            confirmButton = { TextButton(onClick = { showWeekTypePicker = false }) { Text("确定") } }
        )
    }
}


/**
 * 3. 单次课程编辑弹窗 (影子课程)
 * 用于在课程表中直接点击某节课进行临时调整
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseSingleEditDialog(
    initialName: String,
    initialLocation: String,
    initialStartNode: Int,
    initialEndNode: Int,
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onConfirm: (String, String, Int, Int, LocalDate) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var location by remember { mutableStateOf(initialLocation) }
    var startNode by remember { mutableIntStateOf(initialStartNode) }
    var endNode by remember { mutableIntStateOf(initialEndNode) }
    var date by remember { mutableStateOf(initialDate) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showNodeRangePicker by remember { mutableStateOf(false) }

    // 日期选择器
    if (showDatePicker) {
        WheelDatePickerDialog(date, { showDatePicker = false }) {
            date = it
            showDatePicker = false
        }
    }

    // 节次选择器
    if (showNodeRangePicker) {
        WheelRangePickerDialog(
            title = "调整节次范围",
            range = 1..12,
            initialStart = startNode,
            initialEnd = endNode,
            onDismiss = { showNodeRangePicker = false },
            onConfirm = { s, e ->
                startNode = s
                endNode = e
                showNodeRangePicker = false
            },
            labelMapper = { "第 $it 节" }
        )
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = Modifier.fillMaxWidth(0.85f).heightIn(max = 670.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("编辑单次课程", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }

                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                Text(
                    "注意：此修改仅对本次生效，并在课表中作为独立块显示。\n(生成影子课程)",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )

                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("课程名称") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("地点") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

                // 日期选择框
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                        onValueChange = {},
                        label = { Text("日期") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false,
                        readOnly = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { showDatePicker = true }
                    )
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("节次调整")
                    OutlinedButton(onClick = { showNodeRangePicker = true }) {
                        Text("第 $startNode - $endNode 节")
                    }
                }
                }

                Row(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDelete) {
                        Text("本节停课/删除", color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        onConfirm(name, location, startNode, endNode, date)
                    }) { Text("确定") }
                }
            }
        }
    }
}

/**
 * 4. 辅助组件：范围选择器 (解决 Unresolved reference 'WheelRangePickerDialog' 问题)
 */
@Composable
fun WheelRangePickerDialog(
    title: String,
    range: IntRange,
    initialStart: Int,
    initialEnd: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
    labelMapper: (Int) -> String = { it.toString() }
) {
    var start by remember { mutableIntStateOf(initialStart) }
    var end by remember { mutableIntStateOf(initialEnd) }
    val list = range.toList().map { labelMapper(it) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Box(Modifier.weight(1f)) {
                    // 这里依赖 ui/components/WheelPicker.kt
                    WheelPicker(
                        items = list,
                        initialIndex = (start - range.first).coerceIn(0, list.size - 1),
                        onSelectionChanged = { idx -> start = range.first + idx }
                    )
                }
                Text("-", modifier = Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.headlineMedium)
                Box(Modifier.weight(1f)) {
                    WheelPicker(
                        items = list,
                        initialIndex = (end - range.first).coerceIn(0, list.size - 1),
                        onSelectionChanged = { idx -> end = range.first + idx }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val finalStart = minOf(start, end)
                val finalEnd = maxOf(start, end)
                onConfirm(finalStart, finalEnd)
                onDismiss()
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

// 辅助：列表项 UI - 使用 SwipeableCourseItem 包装
@Composable
fun CourseItem(course: Course, onDelete: () -> Unit, onClick: () -> Unit, uiSize: Int = 2) {
    var isRevealed by remember { mutableStateOf(false) }

    SwipeableCourseItem(
        course = course,
        isRevealed = isRevealed,
        onExpand = { isRevealed = true },
        onCollapse = { isRevealed = false },
        onDelete = { onDelete() },
        onClick = { onClick() },
        uiSize = uiSize
    )
}

// 可侧滑的课程列表项 - 复用 SwipeableEventItem 的 UI 风格
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableCourseItem(
    course: Course,
    isRevealed: Boolean,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    uiSize: Int = 2 // 1=小, 2=中, 3=大
) {
    // 根据 uiSize 计算按钮大小和菜单宽度，与 SwipeableEventItem 保持一致
    val actionButtonSize = when (uiSize) {
        1 -> 48.dp  // 小
        2 -> 52.dp  // 中
        else -> 56.dp // 大
    }

    // 根据 uiSize 计算菜单宽度 (2个按钮 + 间距 + 右侧内边距)
    val actionMenuWidth = when (uiSize) {
        1 -> 130.dp  // 小: 48*2 + 约34dp间距
        2 -> 140.dp  // 中: 52*2 + 约36dp间距
        else -> 150.dp // 大: 56*2 + 约38dp间距
    }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val actionMenuWidthPx = with(density) { actionMenuWidth.toPx() }

    val offsetX = remember { androidx.compose.animation.core.Animatable(0f) }
    val scope = rememberCoroutineScope()

    androidx.compose.runtime.LaunchedEffect(isRevealed) {
        if (isRevealed) {
            offsetX.animateTo(-actionMenuWidthPx)
        } else {
            offsetX.animateTo(0f)
        }
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterEnd
    ) {
        // 背景层：操作菜单
        Row(
            modifier = Modifier
                .width(actionMenuWidth)
                .fillMaxHeight()
                .padding(end = 16.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 编辑按钮
            SwipeActionIcon(Icons.Outlined.Edit, Color(0xFF4CAF50), actionButtonSize) {
                onCollapse()
                onClick()
            }
            Spacer(Modifier.width(12.dp))
            // 删除按钮
            SwipeActionIcon(Icons.Outlined.Delete, Color(0xFFF44336), actionButtonSize) {
                onCollapse()
                onDelete()
            }
        }

        // 前景层：课程卡片
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (offsetX.value < -actionMenuWidthPx / 2) {
                                    offsetX.animateTo(-actionMenuWidthPx)
                                    onExpand()
                                } else {
                                    offsetX.animateTo(0f)
                                    onCollapse()
                                }
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            scope.launch {
                                val newOffset = (offsetX.value + dragAmount).coerceIn(-actionMenuWidthPx, 0f)
                                offsetX.snapTo(newOffset)
                            }
                        }
                    )
                }
                .clickable {
                    if (isRevealed) onCollapse() else onClick()
                },
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 0.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .width(5.dp)
                        .height(40.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(course.color)
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        course.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "周${course.dayOfWeek} 第${course.startNode}-${course.endNode}节",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    val weekInfo = "第${course.startWeek}-${course.endWeek}周" + if(course.weekType == 1) " (单)" else if(course.weekType == 2) " (双)" else ""
                    val locInfo = if (course.location.isNotBlank()) " @${course.location}" else ""
                    if (weekInfo.isNotEmpty() || locInfo.isNotEmpty()) {
                        Text(
                            text = "$weekInfo$locInfo",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SwipeActionIcon(icon: ImageVector, tint: Color, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(size)
            .padding(4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.15f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = tint)
    }
}

// 辅助：按钮选择器
@Composable
fun ButtonSelector(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
        Text(text, fontSize = 13.sp)
    }
}