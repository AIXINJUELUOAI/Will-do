package com.antgskds.calendarassistant.ui.page_display

import androidx.compose.foundation.layout.*
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import com.antgskds.calendarassistant.core.calendar.RecurringEventUtils
import com.antgskds.calendarassistant.core.course.TimeTableLayoutUtils
import kotlinx.coroutines.launch
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.ui.components.IntegratedFloatingBar
import com.antgskds.calendarassistant.ui.components.IntegratedFloatingBarBottomSpacing
import com.antgskds.calendarassistant.ui.components.IntegratedFloatingBarHeight
import com.antgskds.calendarassistant.ui.components.SettingsDestination
import com.antgskds.calendarassistant.ui.components.SettingsSidebar
import com.antgskds.calendarassistant.ui.components.ToastType
import com.antgskds.calendarassistant.ui.components.UniversalToast
import com.antgskds.calendarassistant.ui.dialogs.*
import com.antgskds.calendarassistant.ui.layout.PushSlideLayout
import com.antgskds.calendarassistant.ui.viewmodel.MainViewModel
import com.antgskds.calendarassistant.ui.viewmodel.SettingsViewModel
import java.time.LocalDate

private data class RecurringEditSession(
    val parentEventId: String,
    val sourceInstanceId: String,
    val sourceInstanceKey: String,
    val nextOccurrenceText: String?,
    val editHint: String
)

@Composable
fun HomeScreen(
    mainViewModel: MainViewModel,
    settingsViewModel: SettingsViewModel,
    pickupTimestamp: Long = 0L, // 【修改 1】参数改为 Long
    onNavigateToSettings: (SettingsDestination) -> Unit
) {
    // 从 settings 读取主题状态
    val settings by settingsViewModel.settings.collectAsState()
    val uiState by mainViewModel.uiState.collectAsState()

    // Snackbar 状态
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var currentToastType by remember { mutableStateOf(ToastType.INFO) }
    val maxNodes = remember(uiState.settings.timeTableJson) {
        TimeTableLayoutUtils.nodeCountFromJson(uiState.settings.timeTableJson)
    }

    fun showToast(message: String, type: ToastType = ToastType.INFO) {
        currentToastType = type
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    // 状态管理
    var isSidebarOpen by remember { mutableStateOf(false) }
    // 【修改 2】初始 Tab 状态不需要依赖参数了，默认为 0 即可，依靠 LaunchedEffect 来跳转
    var selectedTab by remember { mutableIntStateOf(0) } // 0=Today, 1=All
    var isScheduleExpanded by remember { mutableStateOf(false) } // 课表是否展开
    var isActionExpanded by remember { mutableStateOf(false) }
    var searchRequestId by remember { mutableIntStateOf(0) }
    var imageRequestId by remember { mutableIntStateOf(0) }

    // 【修改 3】新增：监听时间戳变化
    // 只要 timestamp 变化且大于 0，就强制切到"全部"Tab
    LaunchedEffect(pickupTimestamp) {
        if (pickupTimestamp > 0) {
            selectedTab = 1
        }
    }

    // 弹窗状态管理
    var showAddEventDialog by remember { mutableStateOf(false) }
    var eventToEdit by remember { mutableStateOf<MyEvent?>(null) }
    var editingVirtualCourse by remember { mutableStateOf<MyEvent?>(null) }
    var recurringEditSession by remember { mutableStateOf<RecurringEditSession?>(null) }
    var pendingAddDialog by remember { mutableStateOf(false) }
    var addDialogRequestId by remember { mutableIntStateOf(0) }
    val dialogDelayMs = 240L

    LaunchedEffect(pendingAddDialog) {
        if (!pendingAddDialog) return@LaunchedEffect
        kotlinx.coroutines.delay(dialogDelayMs)
        if (!showAddEventDialog && eventToEdit == null) {
            showAddEventDialog = true
        }
        pendingAddDialog = false
    }

    fun beginEdit(event: MyEvent) {
        pendingAddDialog = false
        if (event.eventType == "course") {
            editingVirtualCourse = event
            eventToEdit = null
            recurringEditSession = null
        } else if (event.isRecurringParent) {
            val nextInstance = mainViewModel.findNextRecurringInstance(event)
            val previewEvent = if (nextInstance != null) {
                nextInstance
            } else if (!event.recurringInstanceKey.isNullOrBlank()) {
                event.copy(
                    id = "preview_${event.recurringInstanceKey}",
                    isRecurringParent = false,
                    parentRecurringId = event.id
                )
            } else {
                null
            }
            val instanceKey = previewEvent?.recurringInstanceKey
            if (previewEvent == null || instanceKey.isNullOrBlank()) {
                eventToEdit = null
                recurringEditSession = null
                showToast("未找到可编辑的下次实例")
            } else {
                eventToEdit = previewEvent
                editingVirtualCourse = null
                recurringEditSession = RecurringEditSession(
                    parentEventId = event.id,
                    sourceInstanceId = nextInstance?.id ?: "",
                    sourceInstanceKey = instanceKey,
                    nextOccurrenceText = RecurringEventUtils.formatMillis(event.nextOccurrenceStartMillis),
                    editHint = "本次修改将应用到下次实例，并脱离重复系列"
                )
            }
        } else if (event.isRecurring) {
            val parentEvent = mainViewModel.findRecurringParent(event)
            val instanceKey = event.recurringInstanceKey
            if (parentEvent == null || instanceKey.isNullOrBlank()) {
                eventToEdit = null
                recurringEditSession = null
                showToast("未找到对应的重复系列信息")
            } else {
                eventToEdit = event
                editingVirtualCourse = null
                recurringEditSession = RecurringEditSession(
                    parentEventId = parentEvent.id,
                    sourceInstanceId = event.id,
                    sourceInstanceKey = instanceKey,
                    nextOccurrenceText = RecurringEventUtils.formatMillis(parentEvent.nextOccurrenceStartMillis),
                    editHint = "本次修改将应用到当前实例，并脱离重复系列"
                )
            }
        } else {
            eventToEdit = event
            editingVirtualCourse = null
            recurringEditSession = null
        }
    }

    fun openAddEventDialog() {
        isActionExpanded = false
        addDialogRequestId += 1
        recurringEditSession = null
        eventToEdit = null
        showAddEventDialog = false
        pendingAddDialog = true
    }

    

    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val floatingBarOffset = IntegratedFloatingBarHeight + IntegratedFloatingBarBottomSpacing + bottomInset

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
            bottomBar = {},
            content = {
                    HomePage(
                        viewModel = mainViewModel,
                        currentTab = selectedTab,
                        uiSize = settings.uiSize,
                        pickupTimestamp = pickupTimestamp,
                        isActionExpanded = isActionExpanded,
                        onActionExpandedChange = { isActionExpanded = it },
                        searchRequestId = searchRequestId,
                        imageRequestId = imageRequestId,
                        isSidebarOpen = isSidebarOpen,
                        onTabChange = { selectedTab = it },
                        onCourseClick = { _, _ -> },
                        onAddEventClick = { openAddEventDialog() },
                        onEditEvent = { event -> beginEdit(event) },
                        onScheduleExpandedChange = { isScheduleExpanded = it }
                    )
            }
        )

        IntegratedFloatingBar(
            isExpanded = isActionExpanded,
            onExpandedChange = { isActionExpanded = it },
            isSidebarOpen = isSidebarOpen,
            selectedTab = selectedTab,
            onMenuClick = {
                isActionExpanded = false
                isSidebarOpen = !isSidebarOpen
            },
            onHomeClick = {
                isActionExpanded = false
                isSidebarOpen = false
                selectedTab = 0
            },
            onListClick = {
                isActionExpanded = false
                isSidebarOpen = false
                selectedTab = 1
            },
            onSearchClick = {
                isActionExpanded = false
                isSidebarOpen = false
                searchRequestId += 1
            },
            onImageClick = {
                isActionExpanded = false
                isSidebarOpen = false
                imageRequestId += 1
            },
            onEditClick = {
                isActionExpanded = false
                isSidebarOpen = false
                openAddEventDialog()
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = IntegratedFloatingBarBottomSpacing + bottomInset)
                .zIndex(3f)
        )

        // SnackbarHost 放在屏幕底部
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = floatingBarOffset),
            snackbar = { snackbarData ->
                UniversalToast(message = snackbarData.visuals.message, type = currentToastType)
            }
        )
    }

    // --- 全局弹窗处理 (仅保留日常操作) ---

    // 1. 普通日程编辑/添加
    val isDialogVisible = showAddEventDialog || eventToEdit != null
    val dialogKey = eventToEdit?.id ?: "add_$addDialogRequestId"
    key(dialogKey) {
        AddEventDialog(
            visible = isDialogVisible,
            eventToEdit = eventToEdit,
            currentEventsCount = uiState.allEvents.size,
            settings = settings,
            recurringNextOccurrenceText = recurringEditSession?.nextOccurrenceText,
            recurringEditHint = recurringEditSession?.editHint,
            onShowMessage = { message -> showToast(message, ToastType.INFO) },
            onDismiss = {
                pendingAddDialog = false
                showAddEventDialog = false
                eventToEdit = null
                recurringEditSession = null
            },
            onConfirm = { newEvent ->
                val recurringSession = recurringEditSession
                val editingEvent = eventToEdit
                if (recurringSession != null && editingEvent != null) {
                    mainViewModel.detachRecurringInstance(
                        parentEventId = recurringSession.parentEventId,
                        sourceInstanceId = recurringSession.sourceInstanceId,
                        sourceInstanceKey = recurringSession.sourceInstanceKey,
                        detachedEvent = newEvent
                    )
                } else if (editingEvent == null) {
                    mainViewModel.addEvent(newEvent)
                } else {
                    mainViewModel.updateEvent(newEvent)
                }
                pendingAddDialog = false
                showAddEventDialog = false
                eventToEdit = null
                recurringEditSession = null
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
            maxNodes = maxNodes,
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
