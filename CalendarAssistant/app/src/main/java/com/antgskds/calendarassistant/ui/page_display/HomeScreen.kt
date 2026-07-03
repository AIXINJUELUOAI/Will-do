package com.antgskds.calendarassistant.ui.page_display

import androidx.activity.compose.BackHandler
import com.antgskds.calendarassistant.calendar.models.stubs.RecurringEventUtils
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.core.center.ClipboardCodePrompt
import com.antgskds.calendarassistant.core.model.RecurringMode
import com.antgskds.calendarassistant.core.ai.RecognitionFailureMessageMapper
import com.antgskds.calendarassistant.core.event.DomainEventType
import com.antgskds.calendarassistant.core.event.events.IngestFailedEvent
import com.antgskds.calendarassistant.core.event.events.IngestSucceededEvent
import com.antgskds.calendarassistant.core.event.events.RecognitionFailedEvent
import com.antgskds.calendarassistant.core.course.CourseEventMapper
import com.antgskds.calendarassistant.core.course.TimeTableLayoutUtils
import com.antgskds.calendarassistant.core.note.NoteEntity
import com.antgskds.calendarassistant.core.quickmemo.QuickMemoEntity
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
import com.antgskds.calendarassistant.ui.components.PredictiveFloatingActionCard
import com.antgskds.calendarassistant.ui.components.SettingsDestination
import com.antgskds.calendarassistant.ui.components.SettingsSidebar
import com.antgskds.calendarassistant.ui.components.ToastType
import com.antgskds.calendarassistant.ui.components.UniversalToast
import com.antgskds.calendarassistant.ui.dialogs.*
import com.antgskds.calendarassistant.ui.layout.PushSlideLayout
import com.antgskds.calendarassistant.ui.page_display.settings.AppBackgroundStyleTheme
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
    val patch: com.antgskds.calendarassistant.data.model.EventPatch,
    val attachments: List<EventAttachment> = emptyList()
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
    openCourseRequestId: Long = 0L,
    selectedPageKey: String = HomeEntryKey.TODAY,
    clipboardPrompt: ClipboardCodePrompt? = null,
    onConfirmClipboardPrompt: () -> Unit = {},
    onDismissClipboardPrompt: () -> Unit = {},
    onSelectedPageKeyChange: (String) -> Unit = {},
    onOpenWeatherDetail: () -> Unit = {},
    onOpenNoteEditor: (Long) -> Unit = {},
    onOpenQuickMemoDetail: (Long) -> Unit = {},
    onNavigateToSettings: (SettingsDestination) -> Unit
) {
    val app = LocalContext.current.applicationContext as App
    // 从 settings 读取主题状态
    val settings by settingsViewModel.settings.collectAsState()
    val uiState by mainViewModel.uiState.collectAsState()
    val appUpdateUiState by mainViewModel.appUpdateUiState.collectAsState()

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
                if (!isHomeSource) return@collect

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
                if (!isHomeSource) return@collect

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
                if (!isHomeSource) return@collect

                showToast(payload.message.ifBlank { "保存失败" }, ToastType.ERROR)
            }
    }

    // 状态管理
    var isSidebarOpen by remember { mutableStateOf(false) }
    var isScheduleExpanded by remember { mutableStateOf(false) } // 课表是否展开
    var scheduleProgress by remember { mutableFloatStateOf(0f) }
    var scheduleOffsetPx by remember { mutableFloatStateOf(0f) }
    var isActionExpanded by remember { mutableStateOf(false) }
    var searchRequestId by remember { mutableIntStateOf(0) }
    var imageRequestId by remember { mutableIntStateOf(0) }

    val homeBottomItems = remember(settings.homeBottomItems) {
        sanitizeHomeBottomItems(settings.homeBottomItems)
    }
    val homeStartPageKey = remember(settings.homeStartPageKey, homeBottomItems) {
        sanitizeHomeStartPageKey(settings.homeStartPageKey, homeBottomItems)
    }

    val effectiveSelectedPageKey = if (selectedPageKey in homeBottomItems) selectedPageKey else homeStartPageKey

    LaunchedEffect(settings.homeBottomItems, settings.homeStartPageKey) {
        if (homeBottomItems != settings.homeBottomItems || homeStartPageKey != settings.homeStartPageKey) {
            settingsViewModel.updatePreference(
                homeBottomItems = homeBottomItems,
                homeStartPageKey = homeStartPageKey
            )
        }
    }

    LaunchedEffect(homeBottomItems, homeStartPageKey, selectedPageKey) {
        if (selectedPageKey !in homeBottomItems) {
            onSelectedPageKeyChange(homeStartPageKey)
        }
    }

    // 弹窗状态管理
    var showAddEventDialog by remember { mutableStateOf(false) }
    var editDraft by remember { mutableStateOf<com.antgskds.calendarassistant.data.model.EditDraft?>(null) }
    var editContext by remember { mutableStateOf<EditContext?>(null) }
    var eventToEdit by remember { mutableStateOf<Event?>(null) }
    var draftEventToAdd by remember { mutableStateOf<Event?>(null) }
    var editingVirtualCourse by remember { mutableStateOf<Event?>(null) }
    var courseItemToEdit by remember { mutableStateOf<ScheduleDisplayItem?>(null) }
    var recurringEditSession by remember { mutableStateOf<RecurringEditSession?>(null) }
    var recurringEditCommitSession by remember { mutableStateOf<RecurringEditCommitSession?>(null) }
    var scheduleItemToDelete by remember { mutableStateOf<ScheduleDisplayItem?>(null) }
    var selectedNoteAction by remember { mutableStateOf<NoteEntity?>(null) }
    var selectedQuickMemoAction by remember { mutableStateOf<QuickMemoEntity?>(null) }
    var dialogAttachments by remember { mutableStateOf<List<EventAttachment>>(emptyList()) }
    var currentDialogSessionId by remember { mutableStateOf(0L) }
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

    fun beginEdit(event: Event) {
        pendingAddDialog = false
        draftEventToAdd = null
        if (event.tag == "__removed_course__") {
            editingVirtualCourse = event
            eventToEdit = null
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
        recurringEditCommitSession = null

        when (val target = item.action) {
            is ScheduleDisplayItem.ActionTarget.Single -> {
                val event = mainViewModel.getEventById(target.eventId) ?: return
                if (event.tag == EventTags.COURSE) {
                    courseItemToEdit = item
                    return
                }
                // 单次事件或已有子实例
                val draft = mainViewModel.prepareEditSingle(target.eventId)
                if (draft != null) {
                    editDraft = draft
                    dialogAttachments = emptyList()
                    val sessionId = System.nanoTime()
                    currentDialogSessionId = sessionId
                    scope.launch {
                        runCatching { mainViewModel.getEventAttachments(target.eventId) }
                            .onSuccess { if (currentDialogSessionId == sessionId) dialogAttachments = it }
                            .onFailure { showToast("附件加载失败: ${it.message}", ToastType.ERROR) }
                    }
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
                    dialogAttachments = emptyList()
                    val sessionId = System.nanoTime()
                    currentDialogSessionId = sessionId
                    scope.launch {
                        runCatching { mainViewModel.getEventAttachments(target.parentId) }
                            .onSuccess { if (currentDialogSessionId == sessionId) dialogAttachments = it }
                            .onFailure { showToast("附件加载失败: ${it.message}", ToastType.ERROR) }
                    }
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
        currentDialogSessionId = System.nanoTime()
        recurringEditSession = null
        recurringEditCommitSession = null
        draftEventToAdd = null
        eventToEdit = null
        editDraft = null
        dialogAttachments = emptyList()
        editContext = EditContext.NewEvent
        showAddEventDialog = false
        pendingAddDialog = true
    }

    fun openPrimaryCreateDialog() {
        isActionExpanded = false
        if (effectiveSelectedPageKey == HomeEntryKey.NOTE) {
            mainViewModel.createTextQuickMemo("") { id ->
                onOpenQuickMemoDetail(id)
            }
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
    val hasAppBackground = settings.appBackgroundImagePath.isNotBlank()

    Box(modifier = Modifier) {
        BackHandler(enabled = isSidebarOpen) {
            isSidebarOpen = false
        }

        // 核心布局
        PushSlideLayout(
            isOpen = isSidebarOpen,
            onOpenChange = { isSidebarOpen = it },
            enableGesture = !isScheduleExpanded, // 课表展开时禁用侧边栏手势
            contentContainerColor = if (hasAppBackground) Color.Transparent else MaterialTheme.colorScheme.background,
            sidebar = {
                AppBackgroundStyleTheme(
                    enabled = hasAppBackground,
                    miuiBlurEnabled = settings.appBackgroundMiuiBlurTestEnabled
                ) {
                    SettingsSidebar(
                        isDarkMode = settings.isDarkMode,
                        glassMode = hasAppBackground,
                        hasAppUpdate = appUpdateUiState.hasUpdate,
                        onThemeToggle = { isDark ->
                            settingsViewModel.updateDarkMode(isDark)
                        },
                        onNavigate = { destination ->
                            // 关闭侧边栏并触发导航
                            isSidebarOpen = false
                            onNavigateToSettings(destination)
                        }
                    )
                }
            },
            bottomBar = {},
            content = {
                    HomePage(
                        viewModel = mainViewModel,
                        currentPageKey = effectiveSelectedPageKey,
                        uiSize = settings.uiSize,
                        pickupTimestamp = pickupTimestamp,
                        openCourseRequestId = openCourseRequestId,
                        courseFeatureEnabled = settings.courseFeatureEnabled,
                        isActionExpanded = isActionExpanded,
                        onActionExpandedChange = { isActionExpanded = it },
                        searchRequestId = searchRequestId,
                        imageRequestId = imageRequestId,
                        isSidebarOpen = isSidebarOpen,
                        onPageChange = onSelectedPageKeyChange,
                        onAddEventClick = { openPrimaryCreateDialog() },
                        onEditItem = { item -> beginEditItem(item) },
                        onRequestDeleteItem = { item -> requestDeleteItem(item) },
                        onEditNote = { note -> note.id?.let(onOpenNoteEditor) },
                        onCreateNote = { onOpenNoteEditor(com.antgskds.calendarassistant.ui.navigation.AppRoutes.NoteEditorNewArg) },
                        onRequestDeleteNote = { note -> selectedNoteAction = note },
                        onRequestDeleteQuickMemo = { memo -> selectedQuickMemoAction = memo },
                        onOpenQuickMemoDetail = onOpenQuickMemoDetail,
                        onScheduleExpandedChange = { isScheduleExpanded = it },
                        onScheduleProgressChange = { scheduleProgress = it },
                        onScheduleOffsetChange = { scheduleOffsetPx = it.coerceAtLeast(0f) },
                        onOpenWeatherDetail = onOpenWeatherDetail
                    )
            }
        )

        IntegratedFloatingBar(
            isExpanded = isActionExpanded,
            onExpandedChange = { isActionExpanded = it },
            isSidebarOpen = isSidebarOpen,
            navItems = homeBottomItems,
            selectedPageKey = effectiveSelectedPageKey,
            onMenuClick = {
                isActionExpanded = false
                isSidebarOpen = !isSidebarOpen
            },
            onPageClick = { pageKey ->
                isActionExpanded = false
                isSidebarOpen = false
                onSelectedPageKeyChange(pageKey)
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
            backgroundMode = hasAppBackground,
            miuiBlurEnabled = settings.appBackgroundMiuiBlurTestEnabled,
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
        val clipboardPromptItem = clipboardPrompt
        PredictiveFloatingActionCard(
            visible = clipboardPromptItem != null,
            title = "识别到剪贴板中的${clipboardPromptItem?.candidate?.type?.displayLabel.orEmpty()}",
            content = "${clipboardPromptItem?.candidate?.type?.displayLabel.orEmpty()}：${clipboardPromptItem?.candidate?.code.orEmpty()}",
            confirmText = "入库",
            dismissText = "忽略",
            isDestructive = false,
            isLoading = false,
            predictiveBackEnabled = settings.predictiveBackEnabled,
            onConfirm = onConfirmClipboardPrompt,
            onDismiss = onDismissClipboardPrompt,
            modifier = Modifier
                .padding(bottom = cardFloatingBarOffset + 16.dp)
        )

        PredictiveFloatingActionCard(
            visible = selectedQuickMemoAction != null,
            title = "删除随口记",
            content = "删除后无法恢复，确认删除这条随口记吗？",
            confirmText = "删除",
            dismissText = "取消",
            isDestructive = true,
            isLoading = false,
            predictiveBackEnabled = settings.predictiveBackEnabled,
            onConfirm = {
                selectedQuickMemoAction?.id?.let { mainViewModel.deleteQuickMemo(it) }
                selectedQuickMemoAction = null
            },
            onDismiss = { selectedQuickMemoAction = null },
            modifier = Modifier
                .padding(bottom = cardFloatingBarOffset + 16.dp)
        )

        PredictiveFloatingActionCard(
            visible = selectedNoteAction != null,
            title = "删除便签",
            content = "删除后无法恢复，确认删除这条便签吗？",
            confirmText = "删除",
            dismissText = "取消",
            isDestructive = true,
            isLoading = false,
            predictiveBackEnabled = settings.predictiveBackEnabled,
            onConfirm = {
                selectedNoteAction?.id?.let { mainViewModel.deleteNote(it) }
                selectedNoteAction = null
            },
            onDismiss = { selectedNoteAction = null },
            modifier = Modifier
                .padding(bottom = cardFloatingBarOffset + 16.dp)
        )

        val singleDeleteItem = deleteItem?.takeIf { it.action is ScheduleDisplayItem.ActionTarget.Single }
        PredictiveFloatingActionCard(
            visible = singleDeleteItem != null,
            title = "删除日程",
            content = "删除后无法恢复，确认删除这条日程吗？",
            confirmText = "删除",
            dismissText = "取消",
            isDestructive = true,
            isLoading = false,
            predictiveBackEnabled = settings.predictiveBackEnabled,
            onConfirm = {
                val eventId = (singleDeleteItem?.action as? ScheduleDisplayItem.ActionTarget.Single)?.eventId
                if (eventId != null) mainViewModel.deleteEvent(eventId)
                scheduleItemToDelete = null
            },
            onDismiss = { scheduleItemToDelete = null },
            modifier = Modifier
                .padding(bottom = cardFloatingBarOffset + 16.dp)
        )

        val recurringDeleteItem = deleteItem?.takeIf { it.action is ScheduleDisplayItem.ActionTarget.RecurringOccurrence }
        PredictiveRecurringDeleteActionCard(
            visible = recurringDeleteItem != null,
            predictiveBackEnabled = settings.predictiveBackEnabled,
            onDeleteThis = {
                recurringDeleteItem?.let { mainViewModel.deleteRecurringItem(it, RecurringMode.THIS) }
                scheduleItemToDelete = null
            },
            onDeleteAll = {
                recurringDeleteItem?.let { mainViewModel.deleteRecurringItem(it, RecurringMode.ALL) }
                scheduleItemToDelete = null
            },
            modifier = Modifier
                .padding(bottom = cardFloatingBarOffset + 16.dp)
        )

        PredictiveRecurringEditActionCard(
            visible = editCommitSession != null,
            predictiveBackEnabled = settings.predictiveBackEnabled,
            onEditThis = {
                val session = editCommitSession ?: return@PredictiveRecurringEditActionCard
                recurringEditCommitSession = null
                scope.launch {
                    val editedId = mainViewModel.editRecurringFromPatchWithResult(
                        session.parentId,
                        session.occurrenceTs,
                        RecurringMode.THIS,
                        session.patch
                    )
                    editedId?.let { eventId ->
                        session.attachments.filter { it.eventId == null && it.eventKey.isNotBlank() }.forEach { attachment ->
                            mainViewModel.bindPendingAttachmentsToEvent(eventId, attachment.eventKey)
                        }
                    }
                }
            },
            onEditAll = {
                val session = editCommitSession ?: return@PredictiveRecurringEditActionCard
                recurringEditCommitSession = null
                scope.launch {
                    val editedId = mainViewModel.editRecurringFromPatchWithResult(
                        session.parentId,
                        session.occurrenceTs,
                        RecurringMode.ALL,
                        session.patch
                    )
                    editedId?.let { eventId ->
                        session.attachments.filter { it.eventId == null && it.eventKey.isNotBlank() }.forEach { attachment ->
                            mainViewModel.bindPendingAttachmentsToEvent(eventId, attachment.eventKey)
                        }
                    }
                }
            },
            modifier = Modifier
                .padding(bottom = cardFloatingBarOffset + 16.dp)
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
            attachments = dialogAttachments,
            onAddAttachment = { uri ->
                val eventId = editDraft?.eventId ?: return@AddEventDialog
                scope.launch {
                    runCatching { mainViewModel.addAttachmentToEvent(eventId, uri) }
                        .onSuccess { attachment -> dialogAttachments = dialogAttachments + attachment }
                        .onFailure { showToast("附件添加失败: ${it.message}", ToastType.ERROR) }
                }
            },
            onAddPendingAttachment = { uri, eventKey ->
                scope.launch {
                    runCatching { mainViewModel.addPendingAttachment(eventKey, uri) }
                        .onSuccess { attachment -> dialogAttachments = dialogAttachments + attachment }
                        .onFailure { showToast("附件添加失败: ${it.message}", ToastType.ERROR) }
                }
            },
            onOpenAttachment = { attachment ->
                scope.launch {
                    val opened = mainViewModel.openAttachment(attachment)
                    if (!opened) showToast("无法打开附件", ToastType.ERROR)
                }
            },
            onDeleteAttachment = { attachment ->
                scope.launch {
                    runCatching { mainViewModel.deleteAttachment(attachment) }
                        .onSuccess { dialogAttachments = dialogAttachments.filterNot { it.id == attachment.id } }
                        .onFailure { showToast("附件删除失败: ${it.message}", ToastType.ERROR) }
                }
            },
            onShowMessage = { message -> showToast(message, ToastType.INFO) },
            onDismiss = {
                pendingAddDialog = false
                showAddEventDialog = false
                editDraft = null
                if (editContext is EditContext.NewEvent || editContext == null) {
                    val pendingKey = dialogAttachments.firstOrNull { it.eventId == null }?.eventKey.orEmpty()
                    if (pendingKey.isNotBlank()) scope.launch { mainViewModel.deletePendingAttachments(pendingKey) }
                }
                dialogAttachments = emptyList()
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
                        scope.launch { mainViewModel.refreshAttachmentKey(ctx.eventId) }
                        patch.pendingAttachmentUris.forEach { uri ->
                            scope.launch {
                                runCatching { mainViewModel.addAttachmentToEvent(ctx.eventId, uri) }
                                    .onFailure { showToast("附件添加失败: ${it.message}", ToastType.ERROR) }
                            }
                        }
                    }
                    is EditContext.RecurringOccurrence -> {
                        nextRecurringCommit = RecurringEditCommitSession(
                            parentId = ctx.parentId,
                            occurrenceTs = ctx.occurrenceTs,
                            patch = patch,
                            attachments = dialogAttachments
                        )
                    }
                    is EditContext.NewEvent, null -> {
                        scope.launch {
                            val eventId = mainViewModel.addEventFromPatchWithResult(patch)
                            mainViewModel.bindPendingAttachmentsToEvent(eventId, patch.pendingAttachmentKey)
                        }
                    }
                }
                pendingAddDialog = false
                showAddEventDialog = false
                editDraft = null
                dialogAttachments = emptyList()
                editContext = null
                recurringEditCommitSession = nextRecurringCommit
                draftEventToAdd = null
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
                predictiveBackEnabled = settings.predictiveBackEnabled,
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
private fun PredictiveRecurringDeleteActionCard(
    visible: Boolean,
    predictiveBackEnabled: Boolean,
    onDeleteThis: () -> Unit,
    onDeleteAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    PredictiveFloatingActionCard(
        visible = visible,
        title = "删除重复日程",
        content = "删除本次还是全部？",
        confirmText = "全部",
        dismissText = "仅本次",
        dismissIsDestructive = true,
        isDestructive = true,
        isLoading = false,
        predictiveBackEnabled = predictiveBackEnabled,
        onConfirm = onDeleteAll,
        onDismiss = onDeleteThis,
        modifier = modifier
    )
}

@Composable
private fun PredictiveRecurringEditActionCard(
    visible: Boolean,
    predictiveBackEnabled: Boolean,
    onEditThis: () -> Unit,
    onEditAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    PredictiveFloatingActionCard(
        visible = visible,
        title = "编辑重复日程",
        content = "保存到本次还是全部？",
        confirmText = "全部",
        dismissText = "仅本次",
        isDestructive = false,
        isLoading = false,
        predictiveBackEnabled = predictiveBackEnabled,
        onConfirm = onEditAll,
        onDismiss = onEditThis,
        modifier = modifier
    )
}
