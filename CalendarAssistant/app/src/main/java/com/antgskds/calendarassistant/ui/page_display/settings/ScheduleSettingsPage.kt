package com.antgskds.calendarassistant.ui.page_display.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.ui.components.WheelDatePickerDialog
import com.antgskds.calendarassistant.ui.components.WheelPicker
import com.antgskds.calendarassistant.ui.viewmodel.SettingsViewModel
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun ScheduleSettingsPage(
    viewModel: SettingsViewModel,
    onNavigateTo: (String) -> Unit,
    uiSize: Int = 2
) {
    val settings by viewModel.settings.collectAsState()
    val scrollState = rememberScrollState()

    var showDatePicker by remember { mutableStateOf(false) }
    var showWeekPicker by remember { mutableStateOf(false) }
    var showTotalWeeksPicker by remember { mutableStateOf(false) }

    val semesterStartDate = try {
        if(settings.semesterStartDate.isNotBlank()) LocalDate.parse(settings.semesterStartDate) else null
    } catch(e: Exception) { null }

    val currentWeek = if (semesterStartDate != null) {
        val daysDiff = ChronoUnit.DAYS.between(semesterStartDate, LocalDate.now())
        (daysDiff / 7).toInt() + 1
    } else { 1 }

    // --- 字体样式优化 ---
    // 板块标题：Primary + ExtraBold
    val sectionTitleStyle = MaterialTheme.typography.titleMedium.copy(
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary
    )
    // 列表项标题：OnSurface + Medium
    val cardTitleStyle = MaterialTheme.typography.bodyLarge.copy(
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface
    )
    // 右侧数值：Grey + Normal (关键修改)
    val cardValueStyle = MaterialTheme.typography.bodyMedium.copy(
        fontWeight = FontWeight.Normal,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    // 副标题/提示：Grey + Transparent
    val cardSubtitleStyle = MaterialTheme.typography.bodySmall.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 学期配置板块
        Text("学期配置", style = sectionTitleStyle)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                SettingItem(
                    title = "第一周第一天",
                    value = semesterStartDate?.toString() ?: "未设置",
                    onClick = { showDatePicker = true },
                    cardTitleStyle = cardTitleStyle,
                    cardValueStyle = cardValueStyle,
                    cardSubtitleStyle = cardSubtitleStyle
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 16.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                SettingItem(
                    title = "当前周次",
                    value = "第 $currentWeek 周",
                    onClick = { showWeekPicker = true },
                    cardTitleStyle = cardTitleStyle,
                    cardValueStyle = cardValueStyle,
                    cardSubtitleStyle = cardSubtitleStyle
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 16.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                SettingItem(
                    title = "学期总周数",
                    value = "${settings.totalWeeks} 周",
                    onClick = { showTotalWeeksPicker = true },
                    cardTitleStyle = cardTitleStyle,
                    cardValueStyle = cardValueStyle,
                    cardSubtitleStyle = cardSubtitleStyle
                )
            }
        }

        // 课程管理板块
        Text("课程管理", style = sectionTitleStyle)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                SettingItem(
                    title = "管理所有课程",
                    value = "添加、修改或删除课程",
                    icon = Icons.Default.ChevronRight,
                    onClick = { onNavigateTo("settings/course_manager") },
                    cardTitleStyle = cardTitleStyle,
                    cardValueStyle = cardValueStyle,
                    cardSubtitleStyle = cardSubtitleStyle
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 16.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                SettingItem(
                    title = "作息时间设置",
                    value = "设置每日节次时间段",
                    icon = Icons.Default.ChevronRight,
                    onClick = { onNavigateTo("settings/timetable_editor") },
                    cardTitleStyle = cardTitleStyle,
                    cardValueStyle = cardValueStyle,
                    cardSubtitleStyle = cardSubtitleStyle
                )
            }
        }

        Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }

    if (showDatePicker) {
        WheelDatePickerDialog(
            initialDate = semesterStartDate ?: LocalDate.now(),
            onDismiss = { showDatePicker = false },
            onConfirm = {
                viewModel.updateSemesterStartDate(it.toString())
                showDatePicker = false
            }
        )
    }

    if (showWeekPicker) {
        val weekOptions = (1..30).toList()
        var selectedWeek by remember { mutableIntStateOf(currentWeek) }
        AlertDialog(
            onDismissRequest = { showWeekPicker = false },
            title = { Text("设置当前是第几周") },
            text = {
                WheelPicker(items = weekOptions.map { "第 $it 周" }, initialIndex = (currentWeek - 1).coerceAtLeast(0), onSelectionChanged = { selectedWeek = weekOptions[it] })
            },
            confirmButton = {
                TextButton(onClick = {
                    val today = LocalDate.now()
                    val daysToSubtract = (selectedWeek - 1) * 7L
                    val newStartDate = today.minusDays(daysToSubtract)
                    viewModel.updateSemesterStartDate(newStartDate.toString())
                    showWeekPicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showWeekPicker = false }) { Text("取消") } }
        )
    }

    if (showTotalWeeksPicker) {
        val totalOptions = (10..30).toList()
        var selectedTotal by remember { mutableIntStateOf(settings.totalWeeks) }
        AlertDialog(
            onDismissRequest = { showTotalWeeksPicker = false },
            title = { Text("设置学期总周数") },
            text = {
                WheelPicker(items = totalOptions.map { "$it 周" }, initialIndex = totalOptions.indexOf(settings.totalWeeks).coerceAtLeast(0), onSelectionChanged = { selectedTotal = totalOptions[it] })
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateTotalWeeks(selectedTotal)
                    showTotalWeeksPicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showTotalWeeksPicker = false }) { Text("取消") } }
        )
    }
}

@Composable
fun SettingItem(
    title: String,
    value: String,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    cardTitleStyle: TextStyle,
    cardValueStyle: TextStyle,
    cardSubtitleStyle: TextStyle
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(title, style = cardTitleStyle)
            if (value.isNotBlank() && icon == null) {
                // 右侧纯数值，使用灰色的 cardValueStyle
                Text(value, style = cardValueStyle)
            } else if (value.isNotBlank()) {
                // 如果有图标（比如是“说明文字”而非状态），使用 Subtitle 样式，其实也是灰色
                Text(value, style = cardSubtitleStyle)
            }
        }
        if (icon != null) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}