package com.antgskds.calendarassistant.ui.page_display

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.core.calendar.RecurringEventUtils
import com.antgskds.calendarassistant.core.course.TimeTableLayoutUtils
import com.antgskds.calendarassistant.core.event.DomainEventType
import com.antgskds.calendarassistant.core.event.events.IngestFailedEvent
import com.antgskds.calendarassistant.core.event.events.IngestSucceededEvent
import com.antgskds.calendarassistant.core.event.events.RecognitionFailedEvent
import kotlinx.coroutines.launch
import com.antgskds.calendarassistant.data.model.EventTags
import com.antgskds.calendarassistant.data.model.HomeEntryKey
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.data.model.sanitizeHomeBottomItems
import com.antgskds.calendarassistant.data.model.sanitizeHomeStartPageKey
import com.antgskds.calendarassistant.ui.components.IntegratedFloatingBar
import com.antgskds.calendarassistant.ui.components.IntegratedFloatingBarBottomSpacing
import com.antgskds.calendarassistant.ui.components.IntegratedFloatingBarToastGap
import com.antgskds.calendarassistant.ui.components.IntegratedFloatingBarVisualHeight
import com.antgskds.calendarassistant.ui.components.SettingsDestination
import com.antgskds.calendarassistant.ui.components.SettingsSidebar
import com.antgskds.calendarassistant.ui.components.ToastType
import com.antgskds.calendarassistant.ui.components.UniversalToast
import com.antgskds.calendarassistant.ui.dialogs.*
import com.antgskds.calendarassistant.ui.layout.PushSlideLayout
import com.antgskds.calendarassistant.ui.navigation.navBackwardExitTransition
import com.antgskds.calendarassistant.ui.navigation.navForwardEnterTransition
import com.antgskds.calendarassistant.ui.viewmodel.MainViewModel
import com.antgskds.calendarassistant.ui.viewmodel.SettingsViewModel
import java.time.LocalDate
import kotlin.math.roundToInt

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
    val app = LocalContext.current.applicationContext as App
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

    LaunchedEffect(app) {
        app.domainEventBus
            .eventsOfType<RecognitionFailedEvent>(DomainEventType.RECOGNITION_FAILED)
            .collect { event ->
                val payload = event.payload
                val isHomeSource =
                    payload.sourceType == RecognitionFeedbackSource.HOME_SOURCE_TYPE &&
                        payload.sourceId == RecognitionFeedbackSource.HOME_SOURCE_ID
                val isNoteSource =
                    payload.sourceType == RecognitionFeedbackSource.NOTE_SOURCE_TYPE &&
                        payload.sourceId == RecognitionFeedbackSource.NOTE_SOURCE_ID
                if (!isHomeSource && !isNoteSource) return@collect

                val message = payload.message.ifBlank { "识别失败（${payload.errorCode}）" }
                showToast(message, ToastType.ERROR)
            }
    }

    LaunchedEffect(app) {
        app.domainEventBus
            .eventsOfType<IngestSucceededEvent>(DomainEventType.INGEST_SUCCEEDED)
            .collect { event ->
                val payload = event.payload
                val isHomeSource =
                    payload.sourceType == RecognitionFeedbackSource.HOME_SOURCE_TYPE &&
                        payload.sourceId == RecognitionFeedbackSource.HOME_SOURCE_ID
                val isNoteSource =
                    payload.sourceType == RecognitionFeedbackSource.NOTE_SOURCE_TYPE &&
                        payload.sourceId == RecognitionFeedbackSource.NOTE_SOURCE_ID
                if (!isHomeSource && !isNoteSource) return@collect

                val message = when {
                    payload.createdCount <= 0 -> "已处理，无新增"
                    payload.createdCount == 1 -> "已添加 1 个事件"
                    else -> "已添加 ${payload.createdCount} 个事件"
                }
                showToast(message, ToastType.SUCCESS)
            }
    }

    LaunchedEffect(app) {
        app.domainEventBus
            .eventsOfType<IngestFailedEvent>(DomainEventType.INGEST_FAILED)
            .collect { event ->
                val payload = event.payload
                val isHomeSource =
                    payload.sourceType == RecognitionFeedbackSource.HOME_SOURCE_TYPE &&
                        payload.sourceId == RecognitionFeedbackSource.HOME_SOURCE_ID
                val isNoteSource =
                    payload.sourceType == RecognitionFeedbackSource.NOTE_SOURCE_TYPE &&
                        payload.sourceId == RecognitionFeedbackSource.NOTE_SOURCE_ID
                if (!isHomeSource && !isNoteSource) return@collect

                showToast(payload.message.ifBlank { "保存失败" }, ToastType.ERROR)
            }
    }

    // 状态管理
    var isSidebarOpen by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0=Today, 1=Note/All(无便签), 2=All(有便签)
    var isScheduleExpanded by remember { mutableStateOf(false) } // 课表是否展开
    var scheduleProgress by remember { mutableFloatStateOf(0f) }
    var scheduleOffsetPx by remember { mutableFloatStateOf(0f) }
    var isActionExpanded by remember { mutableStateOf(false) }
    var searchRequestId by remember { mutableIntStateOf(0) }
    var imageRequestId by remember { mutableIntStateOf(0) }
    var previousNoteEnabled by remember { mutableStateOf(settings.noteEnabled) }
    var hasAppliedStartPage by remember { mutableStateOf(false) }

    val homeBottomItems = remember(settings.homeBottomItems, settings.noteEnabled) {
        sanitizeHomeBottomItems(settings.homeBottomItems, settings.noteEnabled)
    }
    val homeStartPageKey = remember(settings.homeStartPageKey, homeBottomItems) {
        sanitizeHomeStartPageKey(settings.homeStartPageKey, homeBottomItems)
    }

    fun pageKeyToTab(pageKey: String): Int {
        return when (pageKey) {
            HomeEntryKey.TODAY -> 0
            HomeEntryKey.NOTE -> if (settings.noteEnabled) 1 else 0
            HomeEntryKey.ALL -> if (settings.noteEnabled) 2 else 1
            else -> 0
        }
    }

    fun tabToPageKey(tab: Int): String {
        return when {
            tab == 0 -> HomeEntryKey.TODAY
            settings.noteEnabled && tab == 1 -> HomeEntryKey.NOTE
            else -> HomeEntryKey.ALL
        }
    }

    fun selectPage(pageKey: String) {
        when (pageKey) {
            HomeEntryKey.TODAY -> selectedTab = 0
            HomeEntryKey.NOTE -> if (settings.noteEnabled) selectedTab = 1
            HomeEntryKey.ALL -> selectedTab = if (settings.noteEnabled) 2 else 1
        }
    }

    // 取件码场景保持最高优先级：强制切换到“全部”
    LaunchedEffect(pickupTimestamp) {
        if (pickupTimestamp > 0) {
            selectPage(HomeEntryKey.ALL)
        }
    }

    LaunchedEffect(settings.noteEnabled) {
        if (settings.noteEnabled != previousNoteEnabled) {
            if (settings.noteEnabled && selectedTab == 1) {
                selectedTab = 2
            } else if (!settings.noteEnabled && selectedTab == 2) {
                selectedTab = 1
            }
            previousNoteEnabled = settings.noteEnabled
        }
    }

    LaunchedEffect(settings.homeBottomItems, settings.homeStartPageKey, settings.noteEnabled) {
        if (homeBottomItems != settings.homeBottomItems || homeStartPageKey != settings.homeStartPageKey) {
            settingsViewModel.updatePreference(
                homeBottomItems = homeBottomItems,
                homeStartPageKey = homeStartPageKey
            )
        }
    }

    LaunchedEffect(homeBottomItems, homeStartPageKey, settings.noteEnabled) {
        if (!hasAppliedStartPage) {
            selectedTab = pageKeyToTab(homeStartPageKey)
            hasAppliedStartPage = true
            return@LaunchedEffect
        }

        val allowedTabs = homeBottomItems.map { pageKeyToTab(it) }.toSet()
        if (allowedTabs.isEmpty()) {
            selectedTab = pageKeyToTab(homeStartPageKey)
            return@LaunchedEffect
        }
        if (selectedTab !in allowedTabs) {
            selectedTab = pageKeyToTab(homeStartPageKey)
        }
    }

    // 弹窗状态管理
    var showAddEventDialog by remember { mutableStateOf(false) }
    var eventToEdit by remember { mutableStateOf<MyEvent?>(null) }
    var draftEventToAdd by remember { mutableStateOf<MyEvent?>(null) }
    var noteToEdit by remember { mutableStateOf<MyEvent?>(null) }
    var showNoteEditor by remember { mutableStateOf(false) }
    var noteEditorInitialNote by remember { mutableStateOf<MyEvent?>(null) }
    var editingVirtualCourse by remember { mutableStateOf<MyEvent?>(null) }
    var recurringEditSession by remember { mutableStateOf<RecurringEditSession?>(null) }
    var pendingAddDialog by remember { mutableStateOf(false) }
    var addDialogRequestId by remember { mutableIntStateOf(0) }
    val dialogDelayMs = 240L

    val noteEditorVisible = showNoteEditor || noteToEdit != null

    LaunchedEffect(noteEditorVisible, noteToEdit) {
        if (noteEditorVisible) {
            noteEditorInitialNote = noteToEdit
        }
    }

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
        draftEventToAdd = null
        noteToEdit = null
        showNoteEditor = false
        if (event.tag == EventTags.COURSE) {
            editingVirtualCourse = event
            eventToEdit = null
            recurringEditSession = null
        } else if (event.tag == EventTags.NOTE) {
            noteToEdit = event
            showNoteEditor = true
            eventToEdit = null
            editingVirtualCourse = null
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
        draftEventToAdd = null
        noteToEdit = null
        showNoteEditor = false
        eventToEdit = null
        showAddEventDialog = false
        pendingAddDialog = true
    }

    fun openPrimaryCreateDialog() {
        isActionExpanded = false
        if (settings.noteEnabled && selectedTab == 1) {
            recurringEditSession = null
            draftEventToAdd = null
            eventToEdit = null
            editingVirtualCourse = null
            noteToEdit = null
            showNoteEditor = true
            showAddEventDialog = false
            pendingAddDialog = false
        } else {
            openAddEventDialog()
        }
    }

    

    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val floatingBarOffset =
        IntegratedFloatingBarVisualHeight +
            IntegratedFloatingBarToastGap +
            IntegratedFloatingBarBottomSpacing +
            bottomInset

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
                        onAddEventClick = { openPrimaryCreateDialog() },
                        onEditEvent = { event -> beginEdit(event) },
                        onScheduleExpandedChange = { isScheduleExpanded = it },
                        onScheduleProgressChange = { scheduleProgress = it },
                        onScheduleOffsetChange = { scheduleOffsetPx = it.coerceAtLeast(0f) }
                    )
            }
        )

        val selectedPageKey = tabToPageKey(selectedTab)

        IntegratedFloatingBar(
            isExpanded = isActionExpanded,
            onExpandedChange = { isActionExpanded = it },
            isSidebarOpen = isSidebarOpen,
            navItems = homeBottomItems,
            selectedPageKey = selectedPageKey,
            onMenuClick = {
                isActionExpanded = false
                isSidebarOpen = !isSidebarOpen
            },
            onPageClick = { pageKey ->
                isActionExpanded = false
                isSidebarOpen = false
                selectPage(pageKey)
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
                openPrimaryCreateDialog()
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = IntegratedFloatingBarBottomSpacing)
                .offset { IntOffset(0, scheduleOffsetPx.roundToInt()) }
                .graphicsLayer {
                    val clamped = scheduleProgress.coerceIn(0f, 1f)
                    alpha = 1f - clamped
                }
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
    val mergedEventDraft = eventToEdit ?: draftEventToAdd
    val isDialogVisible = showAddEventDialog || mergedEventDraft != null
    val dialogKey = mergedEventDraft?.id ?: "add_$addDialogRequestId"
    key(dialogKey) {
        AddEventDialog(
            visible = isDialogVisible,
            eventToEdit = mergedEventDraft,
            currentEventsCount = uiState.allEvents.size,
            settings = settings,
            recurringNextOccurrenceText = recurringEditSession?.nextOccurrenceText,
            recurringEditHint = recurringEditSession?.editHint,
            onShowMessage = { message -> showToast(message, ToastType.INFO) },
            onDismiss = {
                pendingAddDialog = false
                showAddEventDialog = false
                eventToEdit = null
                draftEventToAdd = null
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
                draftEventToAdd = null
                recurringEditSession = null
            }
        )
    }

    AnimatedVisibility(
        visible = noteEditorVisible,
        enter = navForwardEnterTransition(),
        exit = navBackwardExitTransition()
    ) {
        NoteEditorScreen(
            initialNote = noteEditorInitialNote,
            currentEventsCount = uiState.allEvents.size,
            settings = settings,
            onDismiss = {
                showNoteEditor = false
                noteToEdit = null
            },
            onSave = { note ->
                if (noteEditorInitialNote == null) {
                    mainViewModel.addEvent(note)
                } else {
                    mainViewModel.updateEvent(note)
                }
            },
            onDelete = { note ->
                mainViewModel.deleteEvent(note)
                showNoteEditor = false
                noteToEdit = null
            },
            onShowMessage = { message, type ->
                showToast(message, type)
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
