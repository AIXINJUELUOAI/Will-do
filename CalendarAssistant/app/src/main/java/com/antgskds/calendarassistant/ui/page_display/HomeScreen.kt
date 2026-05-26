package com.antgskds.calendarassistant.ui.page_display

import androidx.compose.animation.AnimatedVisibility
import com.antgskds.calendarassistant.calendar.models.stubs.RecurringEventUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.core.model.RecurringMode
import com.antgskds.calendarassistant.core.ai.RecognitionFailureMessageMapper
import com.antgskds.calendarassistant.core.event.DomainEventType
import com.antgskds.calendarassistant.core.event.events.IngestFailedEvent
import com.antgskds.calendarassistant.core.event.events.IngestSucceededEvent
import com.antgskds.calendarassistant.core.event.events.RecognitionFailedEvent
import com.antgskds.calendarassistant.core.course.CourseEventMapper
import com.antgskds.calendarassistant.core.course.TimeTableLayoutUtils
import kotlinx.coroutines.launch
import com.antgskds.calendarassistant.calendar.models.EventTags
import com.antgskds.calendarassistant.data.model.ScheduleDisplayItem
import com.antgskds.calendarassistant.data.model.HomeEntryKey
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.*
import com.antgskds.calendarassistant.data.model.sanitizeHomeBottomItems
import com.antgskds.calendarassistant.data.model.sanitizeHomeStartPageKey
import com.antgskds.calendarassistant.ui.components.IntegratedFloatingBar
import com.antgskds.calendarassistant.ui.components.IntegratedFloatingBarBottomSpacing
import com.antgskds.calendarassistant.ui.components.IntegratedFloatingBarHeight
import com.antgskds.calendarassistant.ui.components.IntegratedFloatingBarToastGap
import com.antgskds.calendarassistant.ui.components.IntegratedFloatingBarVisualHeight
import com.antgskds.calendarassistant.ui.components.FloatingActionCard
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
    val parentEventId: Long?,
    val occurrenceTs: Long,
    val nextOccurrenceText: String?,
    val editHint: String
)

private data class RecurringEditCommitSession(
    val parentId: Long,
    val occurrenceTs: Long,
    val patch: com.antgskds.calendarassistant.data.model.EventPatch
)

/** 编辑上下文：记录当前编辑弹窗要操作的目标类型 */
private sealed class EditContext {
    object NewEvent : EditContext()
    data class SingleEvent(val eventId: Long) : EditContext()
    data class RecurringOccurrence(val parentId: Long, val occurrenceTs: Long) : EditContext()
}

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

                val message = RecognitionFailureMessageMapper.userMessage(payload)
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
    var editDraft by remember { mutableStateOf<com.antgskds.calendarassistant.data.model.EditDraft?>(null) }
    var editContext by remember { mutableStateOf<EditContext?>(null) }
    var eventToEdit by remember { mutableStateOf<Event?>(null) }  // 仅用于 Note 编辑和旧 beginEdit 桥接
    var draftEventToAdd by remember { mutableStateOf<Event?>(null) }
    var noteToEdit by remember { mutableStateOf<Event?>(null) }
    var showNoteEditor by remember { mutableStateOf(false) }
    var noteEditorInitialNote by remember { mutableStateOf<Event?>(null) }
    var noteEditorSessionKey by remember { mutableIntStateOf(0) }
    var editingVirtualCourse by remember { mutableStateOf<Event?>(null) }
    var courseItemToEdit by remember { mutableStateOf<ScheduleDisplayItem?>(null) }
    var recurringEditSession by remember { mutableStateOf<RecurringEditSession?>(null) }
    var recurringEditCommitSession by remember { mutableStateOf<RecurringEditCommitSession?>(null) }
    var scheduleItemToDelete by remember { mutableStateOf<ScheduleDisplayItem?>(null) }
    var pendingAddDialog by remember { mutableStateOf(false) }
    var addDialogRequestId by remember { mutableIntStateOf(0) }
    val dialogDelayMs = 240L

    val noteEditorVisible = showNoteEditor || noteToEdit != null

    fun openNoteEditor(note: Event?) {
        noteEditorInitialNote = note
        noteToEdit = note
        noteEditorSessionKey += 1
        showNoteEditor = true
    }

    LaunchedEffect(pendingAddDialog) {
        if (!pendingAddDialog) return@LaunchedEffect
        kotlinx.coroutines.delay(dialogDelayMs)
        if (!showAddEventDialog && eventToEdit == null) {
            showAddEventDialog = true
        }
        pendingAddDialog = false
    }

    fun beginEdit(event: Event) {
        pendingAddDialog = false
        draftEventToAdd = null
        noteToEdit = null
        showNoteEditor = false
        if (event.tag == "__removed_course__") {
            editingVirtualCourse = event
            eventToEdit = null
            recurringEditSession = null
        } else if (event.tag == EventTags.NOTE) {
            openNoteEditor(event)
            eventToEdit = null
            editingVirtualCourse = null
            recurringEditSession = null
        } else if (event.isRecurring) {
            val nextInstance = mainViewModel.findNextRecurringInstance(event)
            val previewEvent = if (nextInstance != null) {
                nextInstance
            } else if (!event.idString.isNullOrBlank()) {
                event.copy(
                    id = null,
                    rrule = "",
                    parentId = event.id ?: 0L
                )
            } else {
                null
            }
            val instanceKey = previewEvent?.idString
            if (previewEvent == null || instanceKey.isNullOrBlank()) {
                eventToEdit = null
                recurringEditSession = null
                showToast("未找到可编辑的下次实例")
            } else {
                eventToEdit = previewEvent
                editingVirtualCourse = null
                recurringEditSession = RecurringEditSession(
                    parentEventId = event.id,
                    occurrenceTs = previewEvent.startTS,
                    nextOccurrenceText = RecurringEventUtils.formatMillis(event.startMillis),
                    editHint = "本次修改将应用到下次实例，并脱离重复系列"
                )
            }
        } else if (event.parentId != 0L) {
            // 虚拟展开实例或已有的子实例（exception instance）
            val parentEvent = mainViewModel.findRecurringParent(event)
            if (parentEvent == null) {
                eventToEdit = null
                recurringEditSession = null
                showToast("未找到对应的重复系列信息")
            } else {
                // 用父事件的信息构造预览，但保留当前实例的时间
                eventToEdit = event.copy(
                    id = null,  // 清掉负数合成 id，确保不会被当成真实记录更新
                    importId = "",
                    rrule = "",
                    exdates = emptyList()
                )
                editingVirtualCourse = null
                recurringEditSession = RecurringEditSession(
                    parentEventId = parentEvent.id,
                    occurrenceTs = event.startTS,
                    nextOccurrenceText = RecurringEventUtils.formatMillis(parentEvent.startMillis),
                    editHint = "本次修改将应用到当前实例，并脱离重复系列"
                )
            }
        } else {
            eventToEdit = event
            editingVirtualCourse = null
            recurringEditSession = null
        }
    }

    /**
     * 新版编辑入口：从 ScheduleDisplayItem 路由到正确的编辑流程。
     * Single → 加载真实 Event 走旧流程
     * RecurringOccurrence → 加载母事件，设置 recurringEditSession
     */
    fun beginEditItem(item: ScheduleDisplayItem) {
        pendingAddDialog = false
        draftEventToAdd = null
        noteToEdit = null
        showNoteEditor = false
        recurringEditCommitSession = null

        when (val target = item.action) {
            is ScheduleDisplayItem.ActionTarget.Single -> {
                val event = mainViewModel.getEventById(target.eventId) ?: return
                if (event.tag == EventTags.COURSE) {
                    courseItemToEdit = item
                    return
                }
                if (event.tag == EventTags.NOTE) {
                    openNoteEditor(event)
                    return
                }
                // 单次事件或已有子实例
                val draft = mainViewModel.prepareEditSingle(target.eventId)
                if (draft != null) {
                    editDraft = draft
                    editContext = if (event.parentId != 0L) {
                        // 已存在的异常子实例 → 按单次事件更新
                        EditContext.SingleEvent(target.eventId)
                    } else {
                        EditContext.SingleEvent(target.eventId)
                    }
                    showAddEventDialog = true
                }
            }
            is ScheduleDisplayItem.ActionTarget.RecurringOccurrence -> {
                if (item.tag == EventTags.COURSE) {
                    courseItemToEdit = item
                    return
                }
                val draft = mainViewModel.prepareEditRecurringOccurrence(target.parentId, target.occurrenceTs)
                if (draft != null) {
                    editDraft = draft
                    editContext = EditContext.RecurringOccurrence(target.parentId, target.occurrenceTs)
                    showAddEventDialog = true
                }
            }
        }
    }

    fun requestDeleteItem(item: ScheduleDisplayItem) {
        mainViewModel.onRevealItem(null)
        scheduleItemToDelete = item
    }

    fun openAddEventDialog() {
        isActionExpanded = false
        addDialogRequestId += 1
        recurringEditSession = null
        recurringEditCommitSession = null
        draftEventToAdd = null
        noteToEdit = null
        showNoteEditor = false
        eventToEdit = null
        editDraft = null
        editContext = EditContext.NewEvent
        showAddEventDialog = false
        pendingAddDialog = true
    }

    fun openPrimaryCreateDialog() {
        isActionExpanded = false
        if (settings.noteEnabled && selectedTab == 1) {
            recurringEditSession = null
            recurringEditCommitSession = null
            draftEventToAdd = null
            eventToEdit = null
            editingVirtualCourse = null
            openNoteEditor(null)
            showAddEventDialog = false
            pendingAddDialog = false
        } else {
            openAddEventDialog()
        }
    }

    

    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val cardFloatingBarOffset =
        IntegratedFloatingBarHeight +
            IntegratedFloatingBarBottomSpacing +
            bottomInset
    val toastFloatingBarOffset =
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
                        onAddEventClick = { openPrimaryCreateDialog() },
                        onEditItem = { item -> beginEditItem(item) },
                        onRequestDeleteItem = { item -> requestDeleteItem(item) },
                        onEditNote = { note -> beginEdit(note) },
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

        val deleteItem = scheduleItemToDelete
        val editCommitSession = recurringEditCommitSession
        AnimatedVisibility(
            visible = deleteItem != null || editCommitSession != null,
            modifier = Modifier
                .matchParentSize()
                .zIndex(2f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            scheduleItemToDelete = null
                            recurringEditCommitSession = null
                        }
                    )
            )
        }

        val singleDeleteItem = deleteItem?.takeIf { it.action is ScheduleDisplayItem.ActionTarget.Single }
        FloatingActionCard(
            visible = singleDeleteItem != null,
            title = "删除日程",
            content = "删除后无法恢复，确认删除这条日程吗？",
            confirmText = "删除",
            dismissText = "取消",
            isDestructive = true,
            isLoading = false,
            onConfirm = {
                val eventId = (singleDeleteItem?.action as? ScheduleDisplayItem.ActionTarget.Single)?.eventId
                if (eventId != null) mainViewModel.deleteEvent(eventId)
                scheduleItemToDelete = null
            },
            onDismiss = { scheduleItemToDelete = null },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = cardFloatingBarOffset + 16.dp)
                .zIndex(5f)
        )

        val recurringDeleteItem = deleteItem?.takeIf { it.action is ScheduleDisplayItem.ActionTarget.RecurringOccurrence }
        RecurringDeleteActionCard(
            visible = recurringDeleteItem != null,
            onDeleteThis = {
                recurringDeleteItem?.let { mainViewModel.deleteRecurringItem(it, RecurringMode.THIS) }
                scheduleItemToDelete = null
            },
            onDeleteAll = {
                recurringDeleteItem?.let { mainViewModel.deleteRecurringItem(it, RecurringMode.ALL) }
                scheduleItemToDelete = null
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = cardFloatingBarOffset + 16.dp)
                .zIndex(5f)
        )

        RecurringEditActionCard(
            visible = editCommitSession != null,
            onEditThis = {
                val session = editCommitSession ?: return@RecurringEditActionCard
                recurringEditCommitSession = null
                mainViewModel.editRecurringFromPatch(
                    session.parentId,
                    session.occurrenceTs,
                    RecurringMode.THIS,
                    session.patch
                )
            },
            onEditAll = {
                val session = editCommitSession ?: return@RecurringEditActionCard
                recurringEditCommitSession = null
                mainViewModel.editRecurringFromPatch(
                    session.parentId,
                    session.occurrenceTs,
                    RecurringMode.ALL,
                    session.patch
                )
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = cardFloatingBarOffset + 16.dp)
                .zIndex(5f)
        )

        // SnackbarHost 放在屏幕底部
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = toastFloatingBarOffset),
            snackbar = { snackbarData ->
                UniversalToast(message = snackbarData.visuals.message, type = currentToastType)
            }
        )
    }

    // --- 全局弹窗处理 (仅保留日常操作) ---

    // 1. 普通日程编辑/添加
    val isDialogVisible = showAddEventDialog || editDraft != null
    val dialogKey = editDraft?.hashCode() ?: "add_$addDialogRequestId"
    key(dialogKey) {
        AddEventDialog(
            visible = isDialogVisible,
            editDraft = editDraft,
            currentEventsCount = uiState.rawEventCount,
            settings = settings,
            onShowMessage = { message -> showToast(message, ToastType.INFO) },
            onDismiss = {
                pendingAddDialog = false
                showAddEventDialog = false
                editDraft = null
                editContext = null
                recurringEditCommitSession = null
                draftEventToAdd = null
            },
            onConfirm = { patch ->
                val ctx = editContext
                var nextRecurringCommit: RecurringEditCommitSession? = null
                when (ctx) {
                    is EditContext.SingleEvent -> {
                        mainViewModel.updateSingleFromPatch(ctx.eventId, patch)
                    }
                    is EditContext.RecurringOccurrence -> {
                        nextRecurringCommit = RecurringEditCommitSession(
                            parentId = ctx.parentId,
                            occurrenceTs = ctx.occurrenceTs,
                            patch = patch
                        )
                    }
                    is EditContext.NewEvent, null -> {
                        mainViewModel.addEventFromPatch(patch)
                    }
                }
                pendingAddDialog = false
                showAddEventDialog = false
                editDraft = null
                editContext = null
                recurringEditCommitSession = nextRecurringCommit
                draftEventToAdd = null
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
            editorSessionKey = noteEditorSessionKey,
            currentEventsCount = uiState.rawEventCount,
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

    courseItemToEdit?.let { item ->
        val meta = CourseEventMapper.parseMeta(item.description)
        val maxNodes = TimeTableLayoutUtils.nodeCountFromJson(settings.timeTableJson)
        if (meta != null) {
            CourseSingleEditDialog(
                initialName = item.title,
                initialLocation = item.location,
                initialStartNode = meta.startNode,
                initialEndNode = meta.endNode,
                initialDate = item.startDate,
                maxNodes = maxNodes,
                onDismiss = { courseItemToEdit = null },
                onDelete = {
                    mainViewModel.deleteCourseOccurrence(item)
                    courseItemToEdit = null
                },
                onConfirm = { name, location, startNode, endNode, date ->
                    mainViewModel.updateCourseOccurrence(item, name, location, startNode, endNode, date)
                    courseItemToEdit = null
                }
            )
        } else {
            LaunchedEffect(item.stableKey) { courseItemToEdit = null }
        }
    }
}

@Composable
private fun RecurringDeleteActionCard(
    visible: Boolean,
    onDeleteThis: () -> Unit,
    onDeleteAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    FloatingActionCard(
        visible = visible,
        title = "删除重复日程",
        content = "删除本次还是全部？",
        confirmText = "全部",
        dismissText = "仅本次",
        dismissIsDestructive = true,
        isDestructive = true,
        isLoading = false,
        onConfirm = onDeleteAll,
        onDismiss = onDeleteThis,
        modifier = modifier
    )
}

@Composable
private fun RecurringEditActionCard(
    visible: Boolean,
    onEditThis: () -> Unit,
    onEditAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    FloatingActionCard(
        visible = visible,
        title = "编辑重复日程",
        content = "保存到本次还是全部？",
        confirmText = "全部",
        dismissText = "仅本次",
        isDestructive = false,
        isLoading = false,
        onConfirm = onEditAll,
        onDismiss = onEditThis,
        modifier = modifier
    )
}
