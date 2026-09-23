package com.antgskds.calendarassistant.feature.schedule.ui.render.material
import com.antgskds.calendarassistant.shared.ui.edition.EditionIconButton

import com.antgskds.calendarassistant.app.ui.theme.material.background.LocalAppBackgroundStyleEnabled
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import com.antgskds.calendarassistant.shared.ui.material.component.LocalAppPageBottomPadding
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antgskds.calendarassistant.feature.schedule.domain.course.CourseEventMapper
import com.antgskds.calendarassistant.feature.schedule.domain.course.hasConfiguredSemesterAnchor
import com.antgskds.calendarassistant.feature.schedule.domain.course.resolveSemesterAnchor
import com.antgskds.calendarassistant.feature.schedule.presentation.model.ScheduleDisplayItem
import androidx.compose.material3.Card
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private val HeaderHeight = 50.dp
private val TopBarHeight = 56.dp
private val SidebarWidth = 35.dp
private val HeaderIconSize = 28.dp

@Composable
fun MaterialScheduleView(
    items: List<ScheduleDisplayItem>,
    semesterStartDateStr: String?,
    totalWeeks: Int,
    maxNodes: Int,
    selectedDate: LocalDate,
    modifier: Modifier = Modifier,
    embeddedInCalendarWorkspace: Boolean = false,
    onSelectDate: (LocalDate) -> Unit = {},
    onCourseClick: (ScheduleDisplayItem) -> Unit = {}
) {
    val semesterStart = remember(semesterStartDateStr) { resolveSemesterAnchor(semesterStartDateStr) }
    val systemCurrentWeek = remember(semesterStart, semesterStartDateStr) {
        if (!hasConfiguredSemesterAnchor(semesterStartDateStr)) {
            1
        } else {
            val daysDiff = ChronoUnit.DAYS.between(semesterStart, LocalDate.now())
            (daysDiff / 7).toInt() + 1
        }
    }
    var viewingWeek by remember(semesterStart) { mutableIntStateOf(systemCurrentWeek.coerceIn(1, totalWeeks.coerceAtLeast(1))) }
    val viewingWeekMonday = if (embeddedInCalendarWorkspace) {
        selectedDate.minusDays((selectedDate.dayOfWeek.value - 1).toLong())
    } else {
        remember(semesterStart, viewingWeek) { semesterStart.plusWeeks((viewingWeek - 1).toLong()) }
    }
    val today = remember { LocalDate.now() }

    val displayItems = remember(items, viewingWeekMonday) {
        val weekEnd = viewingWeekMonday.plusDays(6)
        items.filter { item ->
            item.tag == com.antgskds.calendarassistant.feature.schedule.domain.model.EventTags.COURSE &&
                !item.startDate.isBefore(viewingWeekMonday) &&
                !item.startDate.isAfter(weekEnd) &&
                CourseEventMapper.parseMeta(item.description) != null
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
            .background(if (embeddedInCalendarWorkspace || LocalAppBackgroundStyleEnabled.current) Color.Transparent else MaterialTheme.colorScheme.surface)
            .padding(horizontal = if (embeddedInCalendarWorkspace) 12.dp else 0.dp)
            .padding(top = if (embeddedInCalendarWorkspace) 4.dp else 0.dp),
    ) {
        val density = LocalDensity.current
        val sidebarWidth = if (embeddedInCalendarWorkspace) 58.dp else SidebarWidth
        val daysWidthPx = (constraints.maxWidth - with(density) { sidebarWidth.roundToPx() }).coerceAtLeast(0)
        val columnEdges = List(8) { calendarGridBoundary(daysWidthPx, it, 7) }
        val columnWidths = columnEdges.zipWithNext { left, right -> with(density) { (right - left).toDp() } }
        Column(Modifier.fillMaxSize()) {
            if (!embeddedInCalendarWorkspace) {
                WeekControllerBar(
                    currentWeek = systemCurrentWeek,
                    viewingWeek = viewingWeek,
                    viewingMonth = viewingWeekMonday.monthValue,
                    totalWeeks = totalWeeks.coerceAtLeast(1),
                    onWeekChange = { viewingWeek = it },
                    onReset = { viewingWeek = systemCurrentWeek.coerceIn(1, totalWeeks.coerceAtLeast(1)) }
                )
            }
            WeekHeaderRow(
                monday = viewingWeekMonday,
                today = today,
                selectedDate = selectedDate,
                embeddedInCalendarWorkspace = embeddedInCalendarWorkspace,
                onSelectDate = onSelectDate,
                columnWidths = columnWidths,
            )

            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val safeMaxNodes = maxNodes.coerceAtLeast(1)
                val gridHeightPx = if (embeddedInCalendarWorkspace) {
                    maxOf(constraints.maxHeight, with(density) { 44.dp.roundToPx() } * safeMaxNodes)
                } else constraints.maxHeight
                val rowEdges = List(safeMaxNodes + 1) { calendarGridBoundary(gridHeightPx, it, safeMaxNodes) }
                val rowHeights = rowEdges.zipWithNext { top, bottom -> with(density) { (bottom - top).toDp() } }
                val gridHeight = with(density) { gridHeightPx.toDp() } + if (embeddedInCalendarWorkspace) 0.dp else 32.dp
                val bottomInset = if (embeddedInCalendarWorkspace) LocalAppPageBottomPadding.current else 0.dp
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("course_viewport")
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = bottomInset)
                        .height(gridHeight)
                ) {
                    SidebarColumn(rowHeights, sidebarWidth, embeddedInCalendarWorkspace)
                    Box(modifier = Modifier.weight(1f).height(gridHeight)) {
                        if (embeddedInCalendarWorkspace) {
                            CourseGrid(columnWidths, rowHeights)
                        }
                        displayItems.forEach { item ->
                            CourseCard(
                                item = item,
                                viewingWeekMonday = viewingWeekMonday,
                                columnEdges = columnEdges,
                                rowEdges = rowEdges,
                                maxNodes = safeMaxNodes,
                                embeddedInCalendarWorkspace = embeddedInCalendarWorkspace,
                                onCourseClick = onCourseClick
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekControllerBar(
    currentWeek: Int,
    viewingWeek: Int,
    viewingMonth: Int,
    totalWeeks: Int,
    onWeekChange: (Int) -> Unit,
    onReset: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(TopBarHeight).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        EditionIconButton(onClick = { if (viewingWeek > 1) onWeekChange(viewingWeek - 1) }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Prev", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(HeaderIconSize))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onReset() }) {
            Text(
                text = buildAnnotatedString {
                    append("${viewingMonth}月 第${viewingWeek}周")
                    if (viewingWeek == currentWeek) {
                        append(" ")
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("(本周)") }
                    }
                },
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        EditionIconButton(onClick = { if (viewingWeek < totalWeeks) onWeekChange(viewingWeek + 1) }) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(HeaderIconSize))
        }
    }
}

@Composable
private fun WeekHeaderRow(
    monday: LocalDate,
    today: LocalDate,
    selectedDate: LocalDate,
    embeddedInCalendarWorkspace: Boolean,
    onSelectDate: (LocalDate) -> Unit,
    columnWidths: List<Dp>,
) {
    val days = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    val sidebarWidth = if (embeddedInCalendarWorkspace) 58.dp else SidebarWidth
    val headerHeight = if (embeddedInCalendarWorkspace) 62.dp else HeaderHeight
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    Row(modifier = Modifier.fillMaxWidth().height(headerHeight)) {
        androidx.compose.foundation.layout.Spacer(Modifier.width(sidebarWidth))
        days.forEachIndexed { index, name ->
            val date = monday.plusDays(index.toLong())
            val isToday = date == today
            val selected = date == selectedDate
            val color = when {
                isToday -> MaterialTheme.colorScheme.primary
                embeddedInCalendarWorkspace && selected -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onSurface
            }
            Column(
                modifier = Modifier.width(columnWidths[index]).fillMaxHeight()
                    .then(
                        if (embeddedInCalendarWorkspace) {
                            Modifier
                                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .border(0.5.dp, lineColor)
                                .clickable { onSelectDate(date) }
                        } else Modifier
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    name,
                    style = if (embeddedInCalendarWorkspace) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
                    color = color,
                )
                Text(
                    date.dayOfMonth.toString(),
                    style = if (embeddedInCalendarWorkspace) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                    color = color,
                    fontWeight = if (isToday || (embeddedInCalendarWorkspace && selected)) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun SidebarColumn(
    rowHeights: List<Dp>,
    width: Dp,
    embeddedInCalendarWorkspace: Boolean,
) {
    Column(
        modifier = Modifier.width(width).height(rowHeights.fold(0.dp) { sum, height -> sum + height })
            .background(if (embeddedInCalendarWorkspace || LocalAppBackgroundStyleEnabled.current) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        rowHeights.forEachIndexed { index, height ->
            Box(
                modifier = Modifier.fillMaxWidth().height(height)
                    .then(if (embeddedInCalendarWorkspace) Modifier.border(0.5.dp, MaterialTheme.colorScheme.outlineVariant) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    (index + 1).toString(),
                    style = if (embeddedInCalendarWorkspace) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CourseGrid(columnWidths: List<Dp>, rowHeights: List<Dp>) {
    Column(Modifier.fillMaxSize()) {
        rowHeights.forEachIndexed { row, height ->
            Row(Modifier.fillMaxWidth().height(height)) {
                columnWidths.forEachIndexed { column, width ->
                    Box(Modifier.width(width).fillMaxHeight()
                        .testTag("course_cell_${row}_${column}")
                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant))
                }
            }
        }
    }
}

@Composable
private fun CourseCard(
    item: ScheduleDisplayItem,
    viewingWeekMonday: LocalDate,
    columnEdges: List<Int>,
    rowEdges: List<Int>,
    maxNodes: Int,
    embeddedInCalendarWorkspace: Boolean,
    onCourseClick: (ScheduleDisplayItem) -> Unit
) {
    val meta = CourseEventMapper.parseMeta(item.description) ?: return
    val dayIndex = (item.startDate.toEpochDay() - viewingWeekMonday.toEpochDay()).toInt().coerceIn(0, 6)
    val startNode = meta.startNode.coerceIn(1, maxNodes)
    val endNode = meta.endNode.coerceIn(startNode, maxNodes)
    val density = LocalDensity.current
    val xOffset = columnEdges[dayIndex]
    val yOffset = rowEdges[startNode - 1]
    val colWidthDp = with(density) { (columnEdges[dayIndex + 1] - xOffset).toDp() }
    val height = with(density) { (rowEdges[endNode] - yOffset).toDp() } -
        if (embeddedInCalendarWorkspace) 0.dp else 4.dp
    val eventColor = if (item.color == 0) {
        MaterialTheme.colorScheme.primary
    } else Color(item.color)
    val shape = RoundedCornerShape(if (embeddedInCalendarWorkspace) 6.dp else 8.dp)

    // 课程自带颜色，不叠加通用卡片的壁纸磨砂层；壁纸只替换页面底图。
    Card(
        modifier = Modifier
            .offset { IntOffset(xOffset, yOffset) }
            .width(colWidthDp)
            .height(height)
            .testTag("course_${item.stableKey}")
            .then(
                if (embeddedInCalendarWorkspace) Modifier.border(1.dp, eventColor.copy(alpha = 0.55f), shape)
                else Modifier
            )
            .clickable { onCourseClick(item) },
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (embeddedInCalendarWorkspace) eventColor.copy(alpha = 0.18f) else eventColor,
        )
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(3.dp)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (embeddedInCalendarWorkspace) MaterialTheme.colorScheme.onSurface else Color.White,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (item.location.isNotBlank()) {
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "@${item.location}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (embeddedInCalendarWorkspace) MaterialTheme.colorScheme.onSurfaceVariant else Color.White.copy(alpha = 0.9f),
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
            }
        }
    }
}
