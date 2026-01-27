package com.antgskds.calendarassistant.ui.page_display

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.ui.components.SettingsDestination
import com.antgskds.calendarassistant.ui.page_display.settings.*
import com.antgskds.calendarassistant.ui.viewmodel.MainViewModel
import com.antgskds.calendarassistant.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDetailScreen(
    destinationStr: String,
    mainViewModel: MainViewModel,
    settingsViewModel: SettingsViewModel,
    onBack: () -> Unit,
    onNavigateTo: (String) -> Unit,
    uiSize: Int = 2
) {
    val destination = try {
        SettingsDestination.valueOf(destinationStr)
    } catch (e: Exception) {
        null
    }

    val titleText = when {
        destinationStr == "course_manager" -> "课程管理"
        destinationStr == "timetable_editor" -> "作息时间"
        destination == SettingsDestination.AI -> "AI 智能配置"
        destination == SettingsDestination.Schedule -> "课表设置"
        destination == SettingsDestination.CourseManage -> "课表管理"
        destination == SettingsDestination.TimeTableManage -> "作息表管理"
        destination == SettingsDestination.SemesterConfig -> "学期配置"
        destination == SettingsDestination.Preference -> "偏好设置"
        destination == SettingsDestination.Backup -> "数据备份"
        destination == SettingsDestination.About -> "关于应用"
        else -> "设置"
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(titleText) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            modifier = Modifier.size(
                                when (uiSize) {
                                    1 -> 24.dp  // 小
                                    2 -> 28.dp  // 中
                                    else -> 32.dp // 大
                                }
                            )
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            when {
                destinationStr == "course_manager" -> CourseManagerScreen(mainViewModel, uiSize)
                destinationStr == "timetable_editor" -> TimeTableEditorScreen(settingsViewModel)
                destination == SettingsDestination.AI -> AiSettingsPage(settingsViewModel)
                destination == SettingsDestination.Schedule -> ScheduleSettingsPage(
                    viewModel = settingsViewModel,
                    onNavigateTo = onNavigateTo
                )
                destination == SettingsDestination.CourseManage -> CourseManagerScreen(mainViewModel, uiSize)
                destination == SettingsDestination.TimeTableManage -> TimeTableEditorScreen(settingsViewModel)
                destination == SettingsDestination.SemesterConfig -> ScheduleSettingsPage(
                    viewModel = settingsViewModel,
                    onNavigateTo = onNavigateTo
                )
                destination == SettingsDestination.Preference -> PreferenceSettingsPage(settingsViewModel)
                destination == SettingsDestination.Backup -> BackupSettingsPage(settingsViewModel)
                destination == SettingsDestination.About -> AboutPage()
            }
        }
    }
}
