package com.antgskds.calendarassistant.ui.page_display

import android.content.Context
import android.content.Intent
import android.widget.Toast
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.antgskds.calendarassistant.core.ai.AnalysisResult
import com.antgskds.calendarassistant.core.ai.RecognitionProcessor
import com.antgskds.calendarassistant.core.ai.activeAiConfig
import com.antgskds.calendarassistant.core.ai.isConfigured
import com.antgskds.calendarassistant.core.ai.missingConfigMessage
import com.antgskds.calendarassistant.core.course.TimeTableLayoutUtils
import com.antgskds.calendarassistant.core.util.ImageImportUtils
import com.antgskds.calendarassistant.core.util.LunarCalendarUtils
import com.antgskds.calendarassistant.data.model.CalendarEventData
import com.antgskds.calendarassistant.data.model.Course
import com.antgskds.calendarassistant.data.model.EventTags
import com.antgskds.calendarassistant.ui.components.FloatingActionCard
import com.antgskds.calendarassistant.ui.theme.SectionTitleTextStyle
import com.antgskds.calendarassistant.data.model.EventType
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.service.accessibility.TextAccessibilityService
import com.antgskds.calendarassistant.ui.components.IntegratedFloatingBarBottomSpacing
import com.antgskds.calendarassistant.ui.components.IntegratedFloatingBarHeight
import com.antgskds.calendarassistant.ui.event_display.SwipeableEventItem
import com.antgskds.calendarassistant.ui.theme.EventColors
import com.antgskds.calendarassistant.ui.viewmodel.MainViewModel
import com.antgskds.calendarassistant.ui.dialogs.CourseSingleEditDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomePage(
    viewModel: MainViewModel,
    currentTab: Int,
    uiSize: Int = 2,
    pickupTimestamp: Long = 0L,
    isActionExpanded: Boolean = false,
    onActionExpandedChange: (Boolean) -> Unit = {},
    searchRequestId: Int = 0,
    imageRequestId: Int = 0,
    onTabChange: (Int) -> Unit = {},
    onCourseClick: (Course, LocalDate) -> Unit = { _, _ -> },
    onAddEventClick: () -> Unit = {},
    onEditEvent: (MyEvent) -> Unit = {},
    onScheduleExpandedChange: (Boolean) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current


    var todaySearchQuery by rememberSaveable { mutableStateOf("") }
    var allSearchQuery by rememberSaveable { mutableStateOf("") }
    var isSearchMode by rememberSaveable { mutableStateOf(false) }
    var searchTab by rememberSaveable { mutableIntStateOf(0) }

    var isImageImporting by remember { mutableStateOf(false) }
    var imageImportJob by remember { mutableStateOf<Job?>(null) }

    val cancelImageImport = {
        imageImportJob?.cancel()
        imageImportJob = null
        isImageImporting = false
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null || isImageImporting) return@rememberLauncherForActivityResult

        imageImportJob?.cancel()
        imageImportJob = scope.launch {
            isImageImporting = true
            try {
                val settings = uiState.settings
                val config = settings.activeAiConfig()
                if (!config.isConfigured()) {
                    Toast.makeText(context, config.missingConfigMessage(), Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val imageFile = ImageImportUtils.createImportedImageFile(context)
                val copied = withContext(Dispatchers.IO) {
                    ImageImportUtils.copyUriToFile(context, uri, imageFile)
                }
                if (!copied) {
                    Toast.makeText(context, "图片读取失败", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val bitmap = withContext(Dispatchers.IO) {
                    ImageImportUtils.decodeSampledBitmapFromFile(imageFile)
                }
                if (bitmap == null) {
                    Toast.makeText(context, "图片解码失败", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val ocrText = withContext(Dispatchers.IO) {
                    RecognitionProcessor.recognizeText(bitmap)
                }
                bitmap.recycle()

                if (ocrText.isBlank()) {
                    Toast.makeText(context, "OCR 结果为空", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val analysisResult = withContext(Dispatchers.IO) {
                    RecognitionProcessor.parseUserText(ocrText, settings, context.applicationContext)
                }

                val eventData = when (analysisResult) {
                    is AnalysisResult.Success -> analysisResult.data
                    is AnalysisResult.Empty -> {
                        Toast.makeText(context, analysisResult.message, Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    is AnalysisResult.Failure -> {
                        Toast.makeText(context, analysisResult.failure.fullMessage(), Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                }

                val event = convertAiEventToMyEvent(
                    eventData = eventData,
                    currentEventsCount = uiState.allEvents.size,
                    sourceImagePath = imageFile.absolutePath
                )
                viewModel.addEvent(event)
                Toast.makeText(context, "已添加: ${event.title}", Toast.LENGTH_SHORT).show()
            } catch (_: CancellationException) {
                // 取消识别时不提示错误
            } catch (e: Exception) {
                Toast.makeText(context, "分析失败：${e.message ?: "unknown"}", Toast.LENGTH_SHORT).show()
            } finally {
                isImageImporting = false
                imageImportJob = null
            }
        }
    }

    val topBarIconSize = when (uiSize) {
        1 -> 24.dp
        2 -> 28.dp
        else -> 32.dp
    }

    // --- 1. 手势与动画状态 ---
    val offsetY = remember { Animatable(0f) }
    val maxOffsetPx = with(LocalDensity.current) { 600.dp.toPx() }

    // 触发阈值：约 100dp
    val snapThresholdPx = with(LocalDensity.current) { 100.dp.toPx() }

    // 提升 listState，用于精确判断列表是否到达顶部
    val listState = rememberLazyListState()

    LaunchedEffect(offsetY.value) {
        onScheduleExpandedChange(offsetY.value > 0)
    }

    // === 核心修改：NestedScrollConnection ===
    val nestedScrollConnection = remember(currentTab) {
        object : NestedScrollConnection {

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (offsetY.value > 0f) {
                    val newOffset = (offsetY.value + available.y).coerceIn(0f, maxOffsetPx)
                    if (newOffset != offsetY.value) {
                        scope.launch { offsetY.snapTo(newOffset) }
                    }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (currentTab != 0) return Offset.Zero

                val isAtTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                val isListStationary = consumed.y == 0f

                if (available.y > 0 && isAtTop && isListStationary) {
                    val newOffset = (offsetY.value + available.y).coerceAtMost(maxOffsetPx)
                    scope.launch { offsetY.snapTo(newOffset) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            // === 关键修改：分区域判断意图 ===
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (offsetY.value > 0f) {
                    val target = when {
                        // 1. 速度优先 (降低阈值到 300f，轻轻一划就能触发)
                        available.y > 300f -> maxOffsetPx // 快速下滑 -> 展开
                        available.y < -300f -> 0f         // 快速上滑 -> 收起

                        // 2. 慢速拖动时的位置判断
                        // 分割线：屏幕中间
                        offsetY.value < (maxOffsetPx / 2) -> {
                            // 【上半区逻辑】：我们在尝试“打开”
                            // 只要向下拉过的距离超过阈值，就去全开，否则回弹关闭
                            if (offsetY.value > snapThresholdPx) maxOffsetPx else 0f
                        }
                        else -> {
                            // 【下半区逻辑】：我们在尝试“关闭”
                            // 只要向上推的距离超过阈值 (当前位置 < Max - Threshold)，就去关闭，否则回弹全开
                            if (offsetY.value < (maxOffsetPx - snapThresholdPx)) 0f else maxOffsetPx
                        }
                    }

                    scope.launch {
                        offsetY.animateTo(
                            targetValue = target,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)
                        )
                    }
                    return available
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (offsetY.value > 0f) {
                    // 同步 onPreFling 的逻辑，确保双重保险
                    val target = if (offsetY.value < (maxOffsetPx / 2)) {
                        if (offsetY.value > snapThresholdPx) maxOffsetPx else 0f
                    } else {
                        if (offsetY.value < (maxOffsetPx - snapThresholdPx)) 0f else maxOffsetPx
                    }
                    scope.launch { offsetY.animateTo(target) }
                    return available
                }
                return super.onPostFling(consumed, available)
            }
        }
    }

    LaunchedEffect(currentTab) {
        if (isSearchMode && currentTab != searchTab) {
            isSearchMode = false
            when (searchTab) {
                0 -> todaySearchQuery = ""
                1 -> allSearchQuery = ""
            }
        }
    }

    BackHandler(enabled = offsetY.value > 0f) {
        scope.launch { offsetY.animateTo(0f) }
    }

    BackHandler(enabled = isSearchMode) {
        isSearchMode = false
        when (searchTab) {
            0 -> todaySearchQuery = ""
            1 -> allSearchQuery = ""
        }
    }

    LaunchedEffect(searchRequestId) {
        if (searchRequestId > 0) {
            isSearchMode = true
            searchTab = currentTab
        }
    }

    LaunchedEffect(imageRequestId) {
        if (imageRequestId > 0 && !isImageImporting) {
            imagePickerLauncher.launch("image/*")
        }
    }

    BackHandler(enabled = isActionExpanded) {
        onActionExpandedChange(false)
    }

    var serviceEnabled by remember {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        mutableStateOf(enabledServices.any {
            it.resolveInfo.serviceInfo.packageName == context.packageName &&
                    it.resolveInfo.serviceInfo.name == TextAccessibilityService::class.java.name
        })
    }
    var notificationEnabled by remember {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        mutableStateOf(notificationManager.areNotificationsEnabled())
    }
    var editingCourse by remember { mutableStateOf<Pair<Course, LocalDate>?>(null) }

    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val floatingBarOffset = IntegratedFloatingBarHeight + IntegratedFloatingBarBottomSpacing + bottomInset

    LifecycleResumeEffect(context) {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        serviceEnabled = enabledServices.any {
            it.resolveInfo.serviceInfo.packageName == context.packageName &&
                    it.resolveInfo.serviceInfo.name == TextAccessibilityService::class.java.name
        }
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationEnabled = notificationManager.areNotificationsEnabled()
        onPauseOrDispose { }
    }

    // --- 3. 根布局 ---
    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
    ) {
        val progress = (offsetY.value / maxOffsetPx).coerceIn(0f, 1f)

        // === 背景层：课程表视图 ===
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(top = 50.dp)
                // 处理在课表区域直接触摸滑动的逻辑
                .draggable(
                    state = rememberDraggableState { delta ->
                        if (offsetY.value > 0) {
                            val newOffset = (offsetY.value + delta).coerceIn(0f, maxOffsetPx)
                            scope.launch { offsetY.snapTo(newOffset) }
                        }
                    },
                    orientation = Orientation.Vertical,
                    onDragStopped = { velocity ->
                        // === 关键修改：Draggable 的松手逻辑同步 ===
                        val target = when {
                            velocity > 300f -> maxOffsetPx
                            velocity < -300f -> 0f
                            // 慢速松手判断：
                            offsetY.value < (maxOffsetPx / 2) -> {
                                if (offsetY.value > snapThresholdPx) maxOffsetPx else 0f
                            }
                            else -> {
                                if (offsetY.value < (maxOffsetPx - snapThresholdPx)) 0f else maxOffsetPx
                            }
                        }

                        scope.launch {
                            offsetY.animateTo(target, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                        }
                    }
                )
                .graphicsLayer {
                    alpha = progress
                    scaleX = 0.9f + (0.1f * progress)
                    scaleY = 0.9f + (0.1f * progress)
                }
        ) {
            val maxNodes = remember(uiState.settings.timeTableJson) {
                TimeTableLayoutUtils.nodeCountFromJson(uiState.settings.timeTableJson)
            }

            ScheduleView(
                courses = uiState.courses,
                semesterStartDateStr = uiState.settings.semesterStartDate,
                totalWeeks = uiState.settings.totalWeeks,
                maxNodes = maxNodes,
                selectedDate = uiState.selectedDate,
                onCourseClick = { course, date -> editingCourse = course to date }
            )

            // 编辑弹窗
            editingCourse?.let { (course, date) ->
                CourseSingleEditDialog(
                    initialName = course.name,
                    initialLocation = course.location,
                    initialStartNode = course.startNode,
                    initialEndNode = course.endNode,
                    initialDate = date,
                    maxNodes = maxNodes,
                    onDismiss = { editingCourse = null },
                    onDelete = {
                        viewModel.deleteSingleCourseInstance(course, date)
                        editingCourse = null
                    },
                    onConfirm = { name, location, start, end, newDate ->
                        val virtualEventId = "course_${course.id}_${date}"
                        viewModel.updateSingleCourseInstance(
                            virtualEventId = virtualEventId,
                            newName = name,
                            newLoc = location,
                            newStartNode = start,
                            newEndNode = end,
                            newDate = newDate
                        )
                        editingCourse = null
                    }
                )
            }
        }

        // === 前景层：日程列表 + Scaffold ===
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, offsetY.value.roundToInt()) }
                .graphicsLayer { alpha = 1f - progress }
                .pointerInput(isActionExpanded) {
                    detectTapGestures(onTap = {
                        if (isActionExpanded) {
                            onActionExpandedChange(false)
                        } else {
                            viewModel.onRevealEvent(null)
                        }
                    })
                }
        ) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    CenterAlignedTopAppBar(
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            /*设置为background后会导致深浅模式切换时topbar不同步;此注释严禁删除
                            containerColor = MaterialTheme.colorScheme.background,*/
                            containerColor = Color.Transparent,
                            titleContentColor = MaterialTheme.colorScheme.onBackground,
                            navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                        ),
                        title = {
                            if ((currentTab == 0 || currentTab == 1) && isSearchMode) {
                                OutlinedTextField(
                                    value = if (currentTab == 1) allSearchQuery else todaySearchQuery,
                                    onValueChange = {
                                        if (currentTab == 1) {
                                            allSearchQuery = it
                                        } else {
                                            todaySearchQuery = it
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text("搜索标题、备注或地点...") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Search,
                                            contentDescription = "搜索",
                                            modifier = Modifier.size(topBarIconSize)
                                        )
                                    },
                                    trailingIcon = if ((if (currentTab == 1) allSearchQuery else todaySearchQuery).isNotEmpty()) {
                                        {
                                            IconButton(onClick = {
                                                if (currentTab == 1) {
                                                    allSearchQuery = ""
                                                } else {
                                                    todaySearchQuery = ""
                                                }
                                            }) {
                                                Icon(
                                                    Icons.Default.Clear,
                                                    contentDescription = "清除",
                                                    modifier = Modifier.size(topBarIconSize)
                                                )
                                            }
                                        }
                                    } else null,
                                    singleLine = true,
                                    shape = RoundedCornerShape(24.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                    )
                                )
                            } else {
                                Text(if (currentTab == 0) "今日日程" else "全部日程")
                            }
                        },
                        actions = {}
                    )
                },
                bottomBar = {}
            ) { innerPadding ->
                Box(modifier = Modifier.padding(innerPadding)) {
                    if (currentTab == 0) {
                        // === 今日视图内容 ===
                        val todayEvents = remember(uiState.currentDateEvents, todaySearchQuery) {
                            if (todaySearchQuery.isBlank()) {
                                uiState.currentDateEvents
                            } else {
                                uiState.currentDateEvents.filter { event ->
                                    event.title.contains(todaySearchQuery, ignoreCase = true) ||
                                            event.description.contains(todaySearchQuery, ignoreCase = true) ||
                                            event.location.contains(todaySearchQuery, ignoreCase = true)
                                }
                            }
                        }
                        val tomorrowEvents = remember(uiState.tomorrowEvents, todaySearchQuery) {
                            if (todaySearchQuery.isBlank()) {
                                uiState.tomorrowEvents
                            } else {
                                uiState.tomorrowEvents.filter { event ->
                                    event.title.contains(todaySearchQuery, ignoreCase = true) ||
                                            event.description.contains(todaySearchQuery, ignoreCase = true) ||
                                            event.location.contains(todaySearchQuery, ignoreCase = true)
                                }
                            }
                        }
                        LazyColumn(
                            // 绑定 listState
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = floatingBarOffset),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            item { Spacer(modifier = Modifier.height(0.dp)) }

                            // 日期卡片
                            item {
                                val isToday = uiState.selectedDate == LocalDate.now()
                                Card(
                                    modifier = Modifier
                                        .padding(horizontal = 24.dp)
                                        .fillMaxWidth()
                                        .aspectRatio(0.95f)
                                        .pointerInput(Unit) {
                                            var totalDrag = 0f
                                            detectHorizontalDragGestures(
                                                onDragEnd = {
                                                    if (totalDrag < -50) viewModel.updateSelectedDate(uiState.selectedDate.plusDays(1))
                                                    else if (totalDrag > 50) viewModel.updateSelectedDate(uiState.selectedDate.minusDays(1))
                                                    totalDrag = 0f
                                                },
                                                onHorizontalDrag = { change, dragAmount ->
                                                    change.consume()
                                                    totalDrag += dragAmount
                                                }
                                            )
                                        },
                                    shape = RoundedCornerShape(4.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        Box(
                                            modifier = Modifier.weight(0.2f).fillMaxWidth()
                                                .background(if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                                .clickable { viewModel.updateSelectedDate(LocalDate.now()) }
                                        )
                                        Column(
                                            modifier = Modifier.weight(0.8f).fillMaxWidth(),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                                                Text(uiState.selectedDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.CHINESE), style = MaterialTheme.typography.titleLarge)
                                                Spacer(Modifier.width(8.dp))
                                                Text(LunarCalendarUtils.getLunarDate(uiState.selectedDate), style = MaterialTheme.typography.titleLarge)
                                            }
                                            Text(
                                                text = uiState.selectedDate.dayOfMonth.toString(),
                                                fontSize = 140.sp, fontWeight = FontWeight.Black, lineHeight = 140.sp,
                                                modifier = Modifier.clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null
                                                ) { viewModel.updateSelectedDate(LocalDate.now()) }
                                            )
                                            Text("${uiState.selectedDate.year}年${uiState.selectedDate.monthValue}月", style = MaterialTheme.typography.bodyLarge, color = Color.Gray)
                                        }
                                    }
                                }
                            }

                            if (!serviceEnabled) item { PermissionWarningCard(Icons.Default.Warning, "无障碍服务未开启", { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }) }) }
                            if (!notificationEnabled) item { PermissionWarningCard(Icons.Default.NotificationsOff, "通知权限未开启", { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply { putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName); flags = Intent.FLAG_ACTIVITY_NEW_TASK }) }) }

                            item { SectionHeader(if (uiState.selectedDate == LocalDate.now()) "今日安排" else "${uiState.selectedDate.monthValue}月${uiState.selectedDate.dayOfMonth}日 安排", MaterialTheme.colorScheme.primary) }

                            if (todayEvents.isEmpty()) {
                                val emptyText = if (todaySearchQuery.isBlank()) "下滑以打开课表" else "未找到相关日程"
                                item { Text(emptyText, modifier = Modifier.padding(vertical = 40.dp), color = Color.LightGray) }
                            } else {
                                items(todayEvents, key = { "today_${it.id}" }) { event ->
                                    SwipeableEventItem(
                                        event = event,
                                        isRevealed = uiState.revealedEventId == event.id,
                                        onExpand = { viewModel.onRevealEvent(event.id) },
                                        onCollapse = { viewModel.onRevealEvent(null) },
                                        onDelete = { viewModel.deleteEvent(event) },
                                        onImportant = { viewModel.toggleImportant(event) },
                                        onEdit = { onEditEvent(event) },
                                        uiSize = uiSize,
                                        isArchivePage = false,
                                        onArchive = { viewModel.archiveEvent(it.id) } // 归档回调
                                    )
                                }
                            }

                            if (uiState.selectedDate == LocalDate.now() && tomorrowEvents.isNotEmpty()) {
                                item { SectionHeader("明日安排", MaterialTheme.colorScheme.tertiary) }
                                items(tomorrowEvents, key = { "tomorrow_${it.id}" }) { event ->
                                    SwipeableEventItem(
                                        event = event,
                                        isRevealed = uiState.revealedEventId == event.id,
                                        onExpand = { viewModel.onRevealEvent(event.id) },
                                        onCollapse = { viewModel.onRevealEvent(null) },
                                        onDelete = { viewModel.deleteEvent(event) },
                                        onImportant = { viewModel.toggleImportant(event) },
                                        onEdit = { onEditEvent(event) },
                                        uiSize = uiSize,
                                        isArchivePage = false,
                                        onArchive = { viewModel.archiveEvent(it.id) } // 归档回调
                                    )
                                }
                            }
                        }
                    } else {
                        AllEventsPage(
                            viewModel = viewModel,
                            onEditEvent = { onEditEvent(it) },
                            uiSize = uiSize,
                            // 【修改 2】透传给 AllEventsPage
                            pickupTimestamp = pickupTimestamp,
                            searchQuery = allSearchQuery
                        )
                    }
                }
            }

        }

        AnimatedVisibility(
            visible = isImageImporting,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
            )
        }

        FloatingActionCard(
            visible = isImageImporting,
            title = "正在识别",
            content = "OCR + AI 分析中...",
            confirmText = "处理中",
            dismissText = "取消",
            isDestructive = false,
            isLoading = true,
            allowDismissWhileLoading = true,
            onConfirm = {},
            onDismiss = cancelImageImport,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

private fun convertAiEventToMyEvent(
    eventData: CalendarEventData,
    currentEventsCount: Int,
    sourceImagePath: String?
): MyEvent {
    val now = LocalDateTime.now()
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    val startDateTime = try {
        if (eventData.startTime.isNotBlank()) LocalDateTime.parse(eventData.startTime, formatter) else now
    } catch (e: Exception) { now }

    val endDateTime = try {
        if (eventData.endTime.isNotBlank()) LocalDateTime.parse(eventData.endTime, formatter) else startDateTime.plusHours(1)
    } catch (e: Exception) { startDateTime.plusHours(1) }

    val resolvedTag = when {
        eventData.tag.isNotBlank() && eventData.tag != EventTags.GENERAL -> eventData.tag
        eventData.type == "pickup" -> EventTags.PICKUP
        else -> eventData.tag
    }.ifBlank { EventTags.GENERAL }

    val color = if (EventColors.isNotEmpty()) {
        EventColors[currentEventsCount % EventColors.size]
    } else {
        Color.Gray
    }

    return MyEvent(
        id = UUID.randomUUID().toString(),
        title = eventData.title.trim(),
        startDate = startDateTime.toLocalDate(),
        endDate = endDateTime.toLocalDate(),
        startTime = startDateTime.format(timeFormatter),
        endTime = endDateTime.format(timeFormatter),
        location = eventData.location,
        description = eventData.description,
        color = color,
        sourceImagePath = sourceImagePath,
        eventType = EventType.EVENT,
        tag = resolvedTag
    )
}

@Composable
private fun PermissionWarningCard(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)), onClick = onClick) {
        Row(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun SectionHeader(title: String, color: Color) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(6.dp).background(color, CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(text = title, style = SectionTitleTextStyle.copy(color = MaterialTheme.colorScheme.onSurface))
    }
}
