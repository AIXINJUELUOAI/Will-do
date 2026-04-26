package com.antgskds.calendarassistant.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.antgskds.calendarassistant.core.course.TimeTableLayoutUtils
import com.antgskds.calendarassistant.data.model.Course
import com.antgskds.calendarassistant.ui.components.CenteredDialogTitle
import com.antgskds.calendarassistant.ui.components.WheelDatePickerDialog
import com.antgskds.calendarassistant.ui.components.WheelPicker
import com.antgskds.calendarassistant.ui.theme.getRandomEventColor
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.roundToInt

@Composable
fun CourseEditDialog(
    course: Course?,
    maxNodes: Int = 12,
    timeTableJson: String = "",
    onDismiss: () -> Unit,
    onConfirm: (Course) -> Unit
) {
    val safeMaxNodes = maxNodes.coerceAtLeast(1)
    var name by remember { mutableStateOf(course?.name ?: "") }
    var location by remember { mutableStateOf(course?.location ?: "") }
    var teacher by remember { mutableStateOf(course?.teacher ?: "") }
    var dayOfWeek by remember { mutableIntStateOf(course?.dayOfWeek ?: 1) }
    var startNode by remember { mutableIntStateOf((course?.startNode ?: 1).coerceIn(1, safeMaxNodes)) }
    var endNode by remember { mutableIntStateOf((course?.endNode ?: minOf(2, safeMaxNodes)).coerceIn(1, safeMaxNodes)) }
    var startWeek by remember { mutableIntStateOf(course?.startWeek ?: 1) }
    var endWeek by remember { mutableIntStateOf(course?.endWeek ?: 18) }
    var weekType by remember { mutableIntStateOf(course?.weekType ?: 0) }
    var color by remember { mutableIntStateOf(course?.color ?: getRandomEventColor().toArgb()) }

    var showDayPicker by remember { mutableStateOf(false) }
    var showNodeRangePicker by remember { mutableStateOf(false) }
    var showWeekRangePicker by remember { mutableStateOf(false) }
    var showWeekTypePicker by remember { mutableStateOf(false) }
    val weekTypeOptions = listOf("每周", "单周", "双周")
    val dayLabels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    val timeNodes = remember(timeTableJson) {
        TimeTableLayoutUtils.parseNodes(timeTableJson).takeIf { it.isNotEmpty() }
            ?: TimeTableLayoutUtils.generateNodes(TimeTableLayoutUtils.defaultConfig())
    }
    val startTimePreview = timeNodes.firstOrNull { it.index == startNode }?.startTime ?: "--:--"
    val endTimePreview = timeNodes.firstOrNull { it.index == endNode }?.endTime ?: "--:--"
    val dayLabel = dayLabels.getOrElse(dayOfWeek - 1) { "周$dayOfWeek" }
    val weekTypeLabel = weekTypeOptions.getOrElse(weekType) { "每周" }
    val previewTitle = buildString {
        append(if (name.isBlank()) "课程名称" else name)
        if (location.isNotBlank()) append(" · $location")
        if (teacher.isNotBlank()) append(" · $teacher")
    }
    val previewMeta = buildString {
        append("第${startWeek}-${endWeek}周 · $weekTypeLabel")
    }
    val sectionLineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)

    LaunchedEffect(safeMaxNodes) {
        startNode = startNode.coerceIn(1, safeMaxNodes)
        endNode = endNode.coerceIn(startNode, safeMaxNodes)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = Modifier.fillMaxWidth(0.85f).heightIn(max = 670.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (course == null) "添加课程" else "编辑课程",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("课程名称") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        label = { Text("地点") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = teacher,
                        onValueChange = { teacher = it },
                        label = { Text("教师") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    HorizontalDivider(color = sectionLineColor)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ButtonSelector(dayLabel, Modifier.weight(1f)) { showDayPicker = true }
                        ButtonSelector("第 $startNode - $endNode 节", Modifier.weight(1.2f)) { showNodeRangePicker = true }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ButtonSelector("第 $startWeek - $endWeek 周", Modifier.weight(1.5f)) { showWeekRangePicker = true }
                        ButtonSelector(weekTypeLabel, Modifier.weight(1f)) { showWeekTypePicker = true }
                    }

                    HorizontalDivider(color = sectionLineColor)

                    CoursePreviewCard(
                        color = Color(color),
                        borderColor = sectionLineColor,
                        title = previewTitle,
                        schedule = "$dayLabel 第${startNode}-${endNode}节",
                        time = "$startTimePreview - $endTimePreview",
                        meta = previewMeta
                    )
                }

                Row(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        if (name.isNotBlank()) {
                            onConfirm(
                                Course(
                                    id = course?.id ?: UUID.randomUUID().toString(),
                                    name = name,
                                    location = location,
                                    teacher = teacher,
                                    color = color,
                                    dayOfWeek = dayOfWeek,
                                    startNode = startNode,
                                    endNode = endNode,
                                    startWeek = startWeek,
                                    endWeek = endWeek,
                                    weekType = weekType
                                )
                            )
                        }
                    }) { Text("确定") }
                }
            }
        }
    }

    if (showDayPicker) {
        val days = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        AlertDialog(
            onDismissRequest = { showDayPicker = false },
            title = { CenteredDialogTitle("选择星期") },
            text = { WheelPicker(items = days, initialIndex = dayOfWeek - 1, onSelectionChanged = { dayOfWeek = it + 1 }) },
            confirmButton = { TextButton(onClick = { showDayPicker = false }) { Text("确定") } }
        )
    }

    if (showNodeRangePicker) {
        WheelRangePickerDialog(
            "选择节次范围",
            1..safeMaxNodes,
            startNode.coerceAtMost(safeMaxNodes),
            endNode.coerceAtMost(safeMaxNodes),
            { showNodeRangePicker = false },
            { s, e -> startNode = s; endNode = e },
            { "第 $it 节" }
        )
    }

    if (showWeekRangePicker) {
        WheelRangePickerDialog("选择周次范围", 1..30, startWeek, endWeek, { showWeekRangePicker = false }, { s, e -> startWeek = s; endWeek = e }, { "第 $it 周" })
    }

    if (showWeekTypePicker) {
        AlertDialog(
            onDismissRequest = { showWeekTypePicker = false },
            title = { CenteredDialogTitle("课程频率") },
            text = { WheelPicker(items = weekTypeOptions, initialIndex = weekType, onSelectionChanged = { weekType = it }) },
            confirmButton = { TextButton(onClick = { showWeekTypePicker = false }) { Text("确定") } }
        )
    }
}

@Composable
private fun CoursePreviewCard(
    color: Color,
    borderColor: Color,
    title: String,
    schedule: String,
    time: String,
    meta: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 38.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = schedule,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = time,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.85f))
            )
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
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onConfirm: (String, String, Int, Int, LocalDate) -> Unit
) {
    val safeMaxNodes = maxNodes.coerceAtLeast(1)
    var name by remember(initialName) { mutableStateOf(initialName) }
    var location by remember(initialLocation) { mutableStateOf(initialLocation) }
    var startNode by remember(initialStartNode, safeMaxNodes) { mutableIntStateOf(initialStartNode.coerceIn(1, safeMaxNodes)) }
    var endNode by remember(initialEndNode, safeMaxNodes) { mutableIntStateOf(initialEndNode.coerceIn(1, safeMaxNodes)) }
    var date by remember(initialDate) { mutableStateOf(initialDate) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showNodeRangePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        WheelDatePickerDialog(date, { showDatePicker = false }, title = "调整日期") {
            date = it
            showDatePicker = false
        }
    }
    if (showNodeRangePicker) {
        WheelRangePickerDialog(
            title = "调整节次范围",
            range = 1..safeMaxNodes,
            initialStart = startNode,
            initialEnd = endNode,
            onDismiss = { showNodeRangePicker = false },
            onConfirm = { s, e -> startNode = s; endNode = e },
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
                    Text("此修改仅对本次生效，并会脱离重复课程。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("课程名称") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("地点") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                            onValueChange = {},
                            label = { Text("日期") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = false,
                            readOnly = true
                        )
                        Box(modifier = Modifier.matchParentSize().clickable { showDatePicker = true })
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("节次调整")
                        OutlinedButton(onClick = { showNodeRangePicker = true }) { Text("第 $startNode - $endNode 节") }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDelete) { Text("本节停课/删除", color = MaterialTheme.colorScheme.error) }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onConfirm(name, location, startNode, endNode, date) }) { Text("确定") }
                }
            }
        }
    }
}

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
        title = { CenteredDialogTitle(title) },
        text = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Box(Modifier.weight(1f)) {
                    WheelPicker(items = list, initialIndex = (start - range.first).coerceIn(0, list.size - 1), onSelectionChanged = { start = range.first + it })
                }
                Text("-", modifier = Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.headlineMedium)
                Box(Modifier.weight(1f)) {
                    WheelPicker(items = list, initialIndex = (end - range.first).coerceIn(0, list.size - 1), onSelectionChanged = { end = range.first + it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(minOf(start, end), maxOf(start, end))
                onDismiss()
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun CourseItem(course: Course, onDelete: () -> Unit, onClick: () -> Unit, uiSize: Int = 2) {
    var isRevealed by remember { mutableStateOf(false) }
    SwipeableCourseItem(
        course = course,
        isRevealed = isRevealed,
        onExpand = { isRevealed = true },
        onCollapse = { isRevealed = false },
        onDelete = onDelete,
        onClick = onClick,
        uiSize = uiSize
    )
}

@Composable
private fun SwipeableCourseItem(
    course: Course,
    isRevealed: Boolean,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    uiSize: Int = 2
) {
    val actionButtonSize = when (uiSize) { 1 -> 48.dp; 2 -> 52.dp; else -> 56.dp }
    val actionMenuWidth = when (uiSize) { 1 -> 130.dp; 2 -> 140.dp; else -> 150.dp }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val actionMenuWidthPx = with(density) { actionMenuWidth.toPx() }
    val offsetX = remember { androidx.compose.animation.core.Animatable(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(isRevealed) {
        if (isRevealed) offsetX.animateTo(-actionMenuWidthPx) else offsetX.animateTo(0f)
    }

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Row(
            modifier = Modifier.width(actionMenuWidth).fillMaxHeight().padding(end = 16.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SwipeActionIcon(Icons.Outlined.Edit, Color(0xFF4CAF50), actionButtonSize) { onCollapse(); onClick() }
            Spacer(Modifier.width(12.dp))
            SwipeActionIcon(Icons.Outlined.Delete, Color(0xFFF44336), actionButtonSize) { onCollapse(); onDelete() }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (offsetX.value < -actionMenuWidthPx / 2) {
                                    offsetX.animateTo(-actionMenuWidthPx); onExpand()
                                } else {
                                    offsetX.animateTo(0f); onCollapse()
                                }
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            scope.launch { offsetX.snapTo((offsetX.value + dragAmount).coerceIn(-actionMenuWidthPx, 0f)) }
                        }
                    )
                }
                .clickable { if (isRevealed) onCollapse() else onClick() },
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 0.dp
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(5.dp).height(40.dp).clip(RoundedCornerShape(3.dp)).background(Color(course.color)))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(course.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("周${course.dayOfWeek} 第${course.startNode}-${course.endNode}节", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    val weekInfo = "第${course.startWeek}-${course.endWeek}周" + when (course.weekType) { 1 -> " (单)"; 2 -> " (双)"; else -> "" }
                    val locInfo = if (course.location.isNotBlank()) " @${course.location}" else ""
                    Text("$weekInfo$locInfo", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SwipeActionIcon(icon: ImageVector, tint: Color, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(size).padding(4.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.15f)).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = tint)
    }
}

@Composable
fun ButtonSelector(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
