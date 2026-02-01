package com.antgskds.calendarassistant.ui.page_display

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.antgskds.calendarassistant.data.model.Course
import java.time.LocalDate
import java.time.temporal.ChronoUnit


// === 依据技术文档定义的常量 ===
private val HeaderHeight = 50.dp
private val SidebarWidth = 35.dp

@Composable
fun ScheduleView(
    courses: List<Course>,
    semesterStartDateStr: String?,
    totalWeeks: Int,
    maxNodes: Int,
    selectedDate: LocalDate,  // 保留参数以兼容调用
    modifier: Modifier = Modifier,
    onCourseClick: (Course, LocalDate) -> Unit = { _, _ -> }  // 修改：传入课程和具体日期
) {
    // 1. 解析开学日期
    // 修复：增加了对 null 的处理 (!semesterStartDateStr.isNullOrBlank())
    val semesterStart = remember(semesterStartDateStr) {
        try {
            // 如果字符串不为 null 且不为空白，则尝试解析
            if (!semesterStartDateStr.isNullOrBlank()) {
                LocalDate.parse(semesterStartDateStr)
            } else {
                LocalDate.now() // 没设置就默认今天
            }
        } catch (e: Exception) {
            LocalDate.now()
        }
    }

    // 2. 计算"系统今天"是第几周
    val systemCurrentWeek = remember(semesterStart) {
        // 修复：使用 isNullOrBlank() 安全判断
        if (semesterStartDateStr.isNullOrBlank()) {
            1 // 没设置开学日期 -> 显示第1周
        } else {
            val today = LocalDate.now() // 强制获取系统今天
            val daysDiff = ChronoUnit.DAYS.between(semesterStart, today)
            // 计算公式：相差天数 / 7 + 1
            (daysDiff / 7).toInt() + 1
        }
    }

    // 3. 初始化显示周次
    // 使用 semesterStart 作为 key：只有当开学日期改变时，才重置 viewingWeek
    var viewingWeek by remember(semesterStart) {
        mutableIntStateOf(systemCurrentWeek)
    }

    // 计算查看周的周一
    val viewingWeekMonday = remember(semesterStart, viewingWeek) {
        semesterStart.plusWeeks((viewingWeek - 1).toLong())
    }

    // 今天（用于星期表头高亮）
    val today = remember { LocalDate.now() }

    // 2. 核心筛选逻辑 (复刻自文档)
    val displayCourses = remember(courses, viewingWeek, viewingWeekMonday) {
        courses.filter { course ->
            // (a) 周次范围
            val weekRangeMatch = viewingWeek in course.startWeek..course.endWeek

            // (b) 单双周 (0=All, 1=Odd, 2=Even)
            val isOdd = viewingWeek % 2 != 0
            val targetType = if (isOdd) 1 else 2
            val typeMatch = course.weekType == 0 || course.weekType == targetType

            // (c) 排除日期逻辑
            // 计算该课程在本周的具体物理日期
            val courseDate = viewingWeekMonday.plusDays((course.dayOfWeek - 1).toLong())
            val notExcluded = !course.excludedDates.contains(courseDate.toString())

            weekRangeMatch && typeMatch && notExcluded
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // 顶部控制栏
        WeekControllerBar(
            currentWeek = systemCurrentWeek,
            viewingWeek = viewingWeek,
            viewingMonth = viewingWeekMonday.monthValue,
            totalWeeks = totalWeeks,
            onWeekChange = { viewingWeek = it },
            onReset = { viewingWeek = systemCurrentWeek }
        )

        // 星期表头
        WeekHeaderRow(viewingWeekMonday, today)

        // 滚动网格区域
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val availableHeight = maxHeight
            val dynamicNodeHeight = availableHeight / maxNodes

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dynamicNodeHeight * maxNodes + 32.dp)  // 增加底部缓冲空间
                    .verticalScroll(rememberScrollState())
            ) {
                // 左侧节次
                SidebarColumn(maxNodes, dynamicNodeHeight)

                // 右侧绝对定位网格
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(dynamicNodeHeight * maxNodes + 32.dp)  // 明确设置高度
                ) {
                    // 修正：先减去 SidebarWidth (35.dp)，再除以7
                    val colWidth = (this@BoxWithConstraints.maxWidth - SidebarWidth) / 7

                    // 绘制背景参考线 (可选)
                    // ...

                    // 渲染课程卡片
                    displayCourses.forEach { course ->
                        CourseCard(
                            course = course,
                            viewingWeekMonday = viewingWeekMonday,
                            colWidthDp = colWidth,
                            nodeHeight = dynamicNodeHeight,
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
        modifier = Modifier
            .fillMaxWidth()
            .height(HeaderHeight)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = { if (viewingWeek > 1) onWeekChange(viewingWeek - 1) }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Prev", tint = MaterialTheme.colorScheme.onSurface)
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable { onReset() }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "第 $viewingWeek 周",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (viewingWeek == currentWeek) {
                    Text(
                        text = " (本周)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                text = "$viewingMonth 月",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(onClick = { if (viewingWeek < totalWeeks) onWeekChange(viewingWeek + 1) }) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next", tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun WeekHeaderRow(monday: LocalDate, today: LocalDate) {
    val days = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(HeaderHeight)
            .padding(start = SidebarWidth) // 让出左侧空间
    ) {
        days.forEachIndexed { index, name ->
            val date = monday.plusDays(index.toLong())
            val isToday = date == today
            val color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface

            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = color,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun SidebarColumn(maxNodes: Int, nodeHeight: Dp) {
    Column(
        modifier = Modifier
            .width(SidebarWidth)
            .height(nodeHeight * maxNodes)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        for (i in 1..maxNodes) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(nodeHeight),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = i.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CourseCard(
    course: Course,
    viewingWeekMonday: LocalDate,  // 新增：当前查看周的周一
    colWidthDp: Dp,
    nodeHeight: Dp,
    onCourseClick: (Course, LocalDate) -> Unit
) {
    // 计算该课程在本周的具体日期
    val courseDate = viewingWeekMonday.plusDays((course.dayOfWeek - 1).toLong())

    // 依据文档实现的定位逻辑
    val xOffset = colWidthDp * (course.dayOfWeek - 1)
    val yOffset = nodeHeight * (course.startNode - 1)
    val span = (course.endNode - course.startNode + 1)
    val height = nodeHeight * span - 4.dp // 留出视觉间隙

    Card(
        modifier = Modifier
            .offset(x = xOffset, y = yOffset)
            .width(colWidthDp)
            .height(height)
            .padding(1.dp)
            .clickable { onCourseClick(course, courseDate) },  // 修改：传入课程和具体日期
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = course.color)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(3.dp)
        ) {
            Text(
                text = course.name,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (course.location.isNotBlank()) {
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "@${course.location}",
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
