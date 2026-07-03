package com.antgskds.calendarassistant.ui.page_display

import androidx.compose.foundation.background
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
import com.antgskds.calendarassistant.core.course.CourseEventMapper
import com.antgskds.calendarassistant.core.course.hasConfiguredSemesterAnchor
import com.antgskds.calendarassistant.core.course.resolveSemesterAnchor
import com.antgskds.calendarassistant.data.model.ScheduleDisplayItem
import com.antgskds.calendarassistant.ui.components.AppCard
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private val HeaderHeight = 50.dp
private val TopBarHeight = 56.dp
private val SidebarWidth = 35.dp
private val HeaderIconSize = 28.dp

@Composable
fun ScheduleView(
    items: List<ScheduleDisplayItem>,
    semesterStartDateStr: String?,
    totalWeeks: Int,
    maxNodes: Int,
    selectedDate: LocalDate,
    modifier: Modifier = Modifier,
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
    val viewingWeekMonday = remember(semesterStart, viewingWeek) { semesterStart.plusWeeks((viewingWeek - 1).toLong()) }
    val today = remember { LocalDate.now() }

    val displayItems = remember(items, viewingWeekMonday) {
        val weekEnd = viewingWeekMonday.plusDays(6)
        items.filter { item ->
            item.tag == com.antgskds.calendarassistant.calendar.models.EventTags.COURSE &&
                !item.startDate.isBefore(viewingWeekMonday) &&
                !item.startDate.isAfter(weekEnd) &&
                CourseEventMapper.parseMeta(item.description) != null
        }
    }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        WeekControllerBar(
            currentWeek = systemCurrentWeek,
            viewingWeek = viewingWeek,
            viewingMonth = viewingWeekMonday.monthValue,
            totalWeeks = totalWeeks.coerceAtLeast(1),
            onWeekChange = { viewingWeek = it },
            onReset = { viewingWeek = systemCurrentWeek.coerceIn(1, totalWeeks.coerceAtLeast(1)) }
        )
        WeekHeaderRow(viewingWeekMonday, today)

        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val safeMaxNodes = maxNodes.coerceAtLeast(1)
            val dynamicNodeHeight = maxHeight / safeMaxNodes
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dynamicNodeHeight * safeMaxNodes + 32.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                SidebarColumn(safeMaxNodes, dynamicNodeHeight)
                Box(modifier = Modifier.weight(1f).height(dynamicNodeHeight * safeMaxNodes + 32.dp)) {
                    val colWidth = (this@BoxWithConstraints.maxWidth - SidebarWidth) / 7
                    displayItems.forEach { item ->
                        CourseCard(
                            item = item,
                            viewingWeekMonday = viewingWeekMonday,
                            colWidthDp = colWidth,
                            nodeHeight = dynamicNodeHeight,
                            maxNodes = safeMaxNodes,
                            onCourseClick = onCourseClick
                        )
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
        IconButton(onClick = { if (viewingWeek > 1) onWeekChange(viewingWeek - 1) }) {
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
        IconButton(onClick = { if (viewingWeek < totalWeeks) onWeekChange(viewingWeek + 1) }) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(HeaderIconSize))
        }
    }
}

@Composable
private fun WeekHeaderRow(monday: LocalDate, today: LocalDate) {
    val days = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    Row(modifier = Modifier.fillMaxWidth().height(HeaderHeight).padding(start = SidebarWidth)) {
        days.forEachIndexed { index, name ->
            val date = monday.plusDays(index.toLong())
            val isToday = date == today
            val color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(name, style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal)
                Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun SidebarColumn(maxNodes: Int, nodeHeight: Dp) {
    Column(modifier = Modifier.width(SidebarWidth).height(nodeHeight * maxNodes).background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        for (i in 1..maxNodes) {
            Box(modifier = Modifier.fillMaxWidth().height(nodeHeight), contentAlignment = Alignment.Center) {
                Text(i.toString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CourseCard(
    item: ScheduleDisplayItem,
    viewingWeekMonday: LocalDate,
    colWidthDp: Dp,
    nodeHeight: Dp,
    maxNodes: Int,
    onCourseClick: (ScheduleDisplayItem) -> Unit
) {
    val meta = CourseEventMapper.parseMeta(item.description) ?: return
    val dayIndex = (item.startDate.toEpochDay() - viewingWeekMonday.toEpochDay()).toInt().coerceIn(0, 6)
    val startNode = meta.startNode.coerceIn(1, maxNodes)
    val endNode = meta.endNode.coerceIn(startNode, maxNodes)
    val xOffset = colWidthDp * dayIndex
    val yOffset = nodeHeight * (startNode - 1)
    val span = (endNode - startNode + 1)
    val height = nodeHeight * span - 4.dp

    AppCard(
        modifier = Modifier
            .offset(x = xOffset, y = yOffset)
            .width(colWidthDp)
            .height(height)
            .padding(1.dp)
            .clickable { onCourseClick(item) },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(item.color))
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(3.dp)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
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
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
            }
        }
    }
}
