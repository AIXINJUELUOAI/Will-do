package com.antgskds.calendarassistant.ui.page_display

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.ui.components.SettingsDestination
import com.antgskds.calendarassistant.ui.components.SettingsSidebar
import com.antgskds.calendarassistant.ui.components.ToastType
import com.antgskds.calendarassistant.ui.components.UniversalToast
import com.antgskds.calendarassistant.ui.dialogs.*
import com.antgskds.calendarassistant.ui.layout.PushSlideLayout
import com.antgskds.calendarassistant.ui.viewmodel.MainViewModel
import com.antgskds.calendarassistant.ui.viewmodel.SettingsViewModel
import java.time.LocalDate

@Composable
fun HomeScreen(
    mainViewModel: MainViewModel,
    settingsViewModel: SettingsViewModel,
    onNavigateToSettings: (SettingsDestination) -> Unit
) {
    // 从 settings 读取主题状态
    val settings by settingsViewModel.settings.collectAsState()

    // Snackbar 状态
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var currentToastType by remember { mutableStateOf(ToastType.INFO) }

    fun showToast(message: String, type: ToastType = ToastType.INFO) {
        currentToastType = type
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    // 状态管理
    var isSidebarOpen by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0=Today, 1=All
    var isScheduleExpanded by remember { mutableStateOf(false) } // 课表是否展开

    // 弹窗状态管理 (❌ 已移除 showCourseManager、showTimeTableEditor 和 AI 输入)
    var showAddEventDialog by remember { mutableStateOf(false) }
    var eventToEdit by remember { mutableStateOf<MyEvent?>(null) }
    var editingVirtualCourse by remember { mutableStateOf<MyEvent?>(null) }

    Box(modifier = Modifier) {
        // 核心布局
        PushSlideLayout(
            isOpen = isSidebarOpen,
            onOpenChange = { isSidebarOpen = it },
            enableGesture = !isScheduleExpanded, // 课表展开时禁用侧边栏手势
            sidebar = {
                SettingsSidebar(
                    isDarkMode = settings.isDarkMode,
                    onThemeToggle = { isDark ->
                        settingsViewModel.updateDarkMode(isDark)
                    },
                    onNavigate = { destination ->
                        // 关闭侧边栏并触发导航
                        isSidebarOpen = false
                        onNavigateToSettings(destination)
                    }
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Today, null) },
                        label = { Text("今日") },
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.List, null) },
                        label = { Text("全部") },
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 }
                    )
                }
            },
            content = {
                HomePage(
                    viewModel = mainViewModel,
                    currentTab = selectedTab,
                    uiSize = settings.uiSize,
                    onSettingsClick = { isSidebarOpen = !isSidebarOpen },
                    onCourseClick = { _, _ -> },
                    onAddEventClick = { showAddEventDialog = true },
                    onEditEvent = { event ->
                        if (event.eventType == "course") {
                            editingVirtualCourse = event
                            eventToEdit = null
                        } else {
                            eventToEdit = event
                            editingVirtualCourse = null
                        }
                    },
                    onScheduleExpandedChange = { isScheduleExpanded = it }
                )
            }
        )

        // SnackbarHost 放在屏幕底部
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            snackbar = { data ->
                UniversalToast(message = data.visuals.message, type = currentToastType)
            }
        )
    }

    // --- 全局弹窗处理 (仅保留日常操作) ---

    // 1. 普通日程编辑/添加
    if (showAddEventDialog || eventToEdit != null) {
        val uiState by mainViewModel.uiState.collectAsState()
        AddEventDialog(
            eventToEdit = eventToEdit,
            currentEventsCount = uiState.allEvents.size,
            settings = settings,  // 传入 settings 参数
            onShowMessage = { message -> showToast(message, ToastType.INFO) },
            onDismiss = {
                showAddEventDialog = false
                eventToEdit = null
            },
            onConfirm = { newEvent ->
                if (eventToEdit == null) mainViewModel.addEvent(newEvent) else mainViewModel.updateEvent(newEvent)
                showAddEventDialog = false
                eventToEdit = null
            }
        )
    }

    // 2. 单次课程编辑
    if (editingVirtualCourse != null) {
        val event = editingVirtualCourse!!
        val nodePattern = Regex("第(\\d+)-(\\d+)节")
        val nodeMatch = nodePattern.find(event.description)
        val sNode = nodeMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
        val eNode = nodeMatch?.groupValues?.get(2)?.toIntOrNull() ?: 1
        val cleanLocation = event.location.split(" | ").firstOrNull() ?: ""
        val parts = event.id.split("_")
        val originalDate = if (parts.size >= 3) {
            try { LocalDate.parse(parts[2]) } catch (e: Exception) { event.startDate }
        } else { event.startDate }

        CourseSingleEditDialog(
            initialName = event.title,
            initialLocation = cleanLocation,
            initialStartNode = sNode,
            initialEndNode = eNode,
            initialDate = originalDate,
            onDismiss = { editingVirtualCourse = null },
            onDelete = {
                mainViewModel.deleteEvent(event)
                editingVirtualCourse = null
            },
            onConfirm = { name, loc, start, end, date ->
                mainViewModel.updateSingleCourseInstance(
                    virtualEventId = event.id,
                    newName = name,
                    newLoc = loc,
                    newStartNode = start,
                    newEndNode = end,
                    newDate = date
                )
                editingVirtualCourse = null
            }
        )
    }
}
