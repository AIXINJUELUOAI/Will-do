package com.antgskds.calendarassistant.ui.page_display

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.antgskds.calendarassistant.core.util.LunarCalendarUtils
import com.antgskds.calendarassistant.data.model.Course
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.service.accessibility.TextAccessibilityService
import com.antgskds.calendarassistant.ui.analyzer.ScheduleRecommendationAnalyzer
import com.antgskds.calendarassistant.ui.event_display.SwipeableEventItem
import com.antgskds.calendarassistant.ui.viewmodel.MainViewModel
import com.antgskds.calendarassistant.ui.dialogs.CourseSingleEditDialog
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomePage(
    viewModel: MainViewModel,
    currentTab: Int,
    uiSize: Int = 2,
    pickupTimestamp: Long = 0L,
    onSettingsClick: () -> Unit,
    onCourseClick: (Course, LocalDate) -> Unit = { _, _ -> },
    onAddEventClick: () -> Unit = {},
    onEditEvent: (MyEvent) -> Unit = {},
    onScheduleExpandedChange: (Boolean) -> Unit = {},
    onRecommendedClick: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val recommendationAnalyzer = remember { ScheduleRecommendationAnalyzer() }
    var recommendedTitle by remember { mutableStateOf<String?>(null) }

    val fabSize = when (uiSize) {
        1 -> 56.dp
        2 -> 64.dp
        else -> 72.dp
    }
    val fabIconSize = when (uiSize) {
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

    BackHandler(enabled = offsetY.value > 0f) {
        scope.launch { offsetY.animateTo(0f) }
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
    var isFabExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(isFabExpanded) {
        if (isFabExpanded && uiState.settings.enableSmartRecommendation) {
            recommendedTitle = recommendationAnalyzer.getRecommendation(
                events = uiState.allEvents,
                currentDateTime = LocalDateTime.now()
            )
        } else {
            recommendedTitle = null
        }
    }

    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

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
                try {
                    Json.parseToJsonElement(uiState.settings.timeTableJson).jsonArray.size
                } catch (e: Exception) { 12 }
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
                .pointerInput(isFabExpanded) {
                    detectTapGestures(onTap = {
                        if (isFabExpanded) {
                            isFabExpanded = false
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
                            Text(if (currentTab == 0) "今日日程" else "全部日程")
                        }
                    )
                },
                floatingActionButton = {
                    val fabRotation by animateFloatAsState(
                        targetValue = if (isFabExpanded) 45f else 0f,
                        animationSpec = tween(durationMillis = 300),
                        label = "fabRotation"
                    )

                    Row(
                        modifier = Modifier.fillMaxHeight(),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End)
                    ) {
                        // 推荐按钮 - 左侧展开
                        AnimatedVisibility(
                            visible = isFabExpanded && recommendedTitle != null,
                            enter = fadeIn(tween(300)) + slideInHorizontally(tween(300)) { it } + expandHorizontally(tween(300)),
                            exit = fadeOut(tween(300)) + slideOutHorizontally(tween(300)) { it } + shrinkHorizontally(tween(300))
                        ) {
                            Surface(
                                onClick = {
                                    isFabExpanded = false
                                    recommendedTitle?.let { onRecommendedClick(it) }
                                },
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shadowElevation = 6.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .height(fabSize)
                                        .padding(horizontal = 20.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(fabIconSize),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = recommendedTitle ?: "",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }

                        // 添加日程按钮
                        AnimatedVisibility(
                            visible = isFabExpanded,
                            enter = fadeIn(tween(300)) + scaleIn(tween(300), initialScale = 0f),
                            exit = fadeOut(tween(300)) + scaleOut(tween(300), targetScale = 0f)
                        ) {
                            androidx.compose.material3.FloatingActionButton(
                                onClick = {
                                    isFabExpanded = false
                                    onAddEventClick()
                                },
                                shape = CircleShape,
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(fabSize)
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "添加日程",
                                    modifier = Modifier.size(fabIconSize)
                                )
                            }
                        }

                        // 主FAB按钮
                        androidx.compose.material3.FloatingActionButton(
                            onClick = { isFabExpanded = !isFabExpanded },
                            shape = CircleShape,
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(fabSize)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "展开菜单",
                                modifier = Modifier
                                    .size(fabIconSize)
                                    .graphicsLayer {
                                        rotationZ = fabRotation
                                    }
                            )
                        }
                    }
                },
                bottomBar = {}
            ) { innerPadding ->
                Box(modifier = Modifier.padding(innerPadding)) {
                    if (currentTab == 0) {
                        // === 今日视图内容 ===
                        LazyColumn(
                            // 绑定 listState
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 80.dp + bottomInset),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            item { Spacer(modifier = Modifier.height(8.dp)) }

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
                                            Text("${uiState.selectedDate.year}年${uiState.selectedDate.monthValue}月", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                                        }
                                    }
                                }
                            }

                            if (!serviceEnabled) item { PermissionWarningCard(Icons.Default.Warning, "无障碍服务未开启", { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }) }) }
                            if (!notificationEnabled) item { PermissionWarningCard(Icons.Default.NotificationsOff, "通知权限未开启", { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply { putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName); flags = Intent.FLAG_ACTIVITY_NEW_TASK }) }) }

                            item { SectionHeader(if (uiState.selectedDate == LocalDate.now()) "今日安排" else "${uiState.selectedDate.monthValue}月${uiState.selectedDate.dayOfMonth}日 安排", MaterialTheme.colorScheme.primary) }

                            val events = uiState.currentDateEvents.filter { it.eventType != "temp" }
                            if (events.isEmpty()) {
                                item { Text("下滑以打开课表", modifier = Modifier.padding(vertical = 40.dp), color = Color.LightGray) }
                            } else {
                                items(events, key = { it.id }) { event ->
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

                            if (uiState.selectedDate == LocalDate.now() && uiState.tomorrowEvents.any { it.eventType != "temp" }) {
                                item { SectionHeader("明日安排", MaterialTheme.colorScheme.tertiary) }
                                val todayEventIds = uiState.currentDateEvents.map { it.id }.toSet()
                                items(uiState.tomorrowEvents.filter { it.eventType != "temp" && it.id !in todayEventIds }, key = { it.id }) { event ->
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
                            pickupTimestamp = pickupTimestamp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionWarningCard(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)), onClick = onClick) {
        Row(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun SectionHeader(title: String, color: Color) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(6.dp).background(color, CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
    }
}