package com.antgskds.calendarassistant.ui.page_display.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.ui.components.WheelDatePickerDialog
import com.antgskds.calendarassistant.ui.components.WheelPicker
import com.antgskds.calendarassistant.ui.viewmodel.SettingsViewModel
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun ScheduleSettingsPage(
    viewModel: SettingsViewModel,
    onNavigateTo: (String) -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val scrollState = rememberScrollState()

    // 本地弹窗状态
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("学期配置", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)

        SettingItem(
            title = "第一周第一天",
            value = semesterStartDate?.toString() ?: "未设置",
            onClick = { showDatePicker = true }
        )

        SettingItem(
            title = "当前周次",
            value = "第 $currentWeek 周",
            onClick = { showWeekPicker = true }
        )

        SettingItem(
            title = "学期总周数",
            value = "${settings.totalWeeks} 周",
            onClick = { showTotalWeeksPicker = true }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text("课程管理", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)

        SettingItem(
            title = "管理所有课程",
            value = "",
            icon = Icons.Default.ChevronRight,
            onClick = { onNavigateTo("settings/course_manager") }
        )

        SettingItem(
            title = "作息时间设置",
            value = "设置每日节次时间段",
            icon = Icons.Default.AccessTime,
            onClick = { onNavigateTo("settings/timetable_editor") }
        )

        // 修改：增加底部Spacer，确保最后一行不贴底，适配小白条
        Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }

    // --- 弹窗逻辑 ---
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

// 🔥 之前丢失的辅助组件定义
@Composable
fun SettingItem(
    title: String,
    value: String,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (value.isNotBlank() && icon == null) {
                Text(value, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
            } else if (value.isNotBlank()) {
                Text(value, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (icon != null) {
            Icon(icon, null, tint = Color.Gray)
        }
    }
}