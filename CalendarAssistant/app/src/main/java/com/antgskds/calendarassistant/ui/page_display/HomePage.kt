package com.antgskds.calendarassistant.ui.page_display

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.antgskds.calendarassistant.core.util.LunarCalendarUtils
import com.antgskds.calendarassistant.data.model.Course
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.service.accessibility.TextAccessibilityService
import com.antgskds.calendarassistant.ui.event_display.SwipeableEventItem
import com.antgskds.calendarassistant.ui.viewmodel.MainViewModel
import com.antgskds.calendarassistant.ui.dialogs.CourseSingleEditDialog
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomePage(
    viewModel: MainViewModel,
    currentTab: Int, // 外部传入 Tab 状态
    uiSize: Int = 2, // 1=小, 2=中, 3=大
    onSettingsClick: () -> Unit,
    onCourseClick: (Course, LocalDate) -> Unit = { _, _ -> },
    onAddEventClick: () -> Unit = {},
    onEditEvent: (MyEvent) -> Unit = {},
    onScheduleExpandedChange: (Boolean) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 根据 uiSize 计算 FAB 尺寸
    val fabSize = when (uiSize) {
        1 -> 56.dp  // 小
        2 -> 64.dp  // 中
        else -> 72.dp // 大
    }
    val fabIconSize = when (uiSize) {
        1 -> 24.dp  // 小
        2 -> 28.dp  // 中
        else -> 32.dp // 大
    }

    // --- 1. 手势与动画状态 ---
    val offsetY = remember { Animatable(0f) }
    val maxOffsetPx = with(LocalDensity.current) { 600.dp.toPx() }

    // 通知父组件课表展开状态
    LaunchedEffect(offsetY.value) {
        onScheduleExpandedChange(offsetY.value > 0)
    }

    // === 核心修改：NestedScrollConnection ===
    // 增加 currentTab 作为 key，确保 Tab 切换时逻辑能实时响应
    val nestedScrollConnection = remember(currentTab) {
        object : NestedScrollConnection {
            // 1. 拦截逻辑：处理【收起课表】的手指拖动阶段
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // 只要课表有一点点展开 (offsetY > 0)，且手指是向上滑 (available.y < 0)
                // 我们就拦截所有事件，专用于收起课表，绝对不把事件分发给 LazyColumn
                if (offsetY.value > 0f && available.y < 0) {
                    val newOffset = (offsetY.value + available.y).coerceAtLeast(0f)
                    scope.launch { offsetY.snapTo(newOffset) }

                    // 返回 available.y (全额消耗)，告诉系统事件已被消费
                    // 这样列表就不会因为这个上滑动作而滚动
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            // 2. 后处理逻辑：处理【下拉展开课表】的手指拖动阶段
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // available.y > 0 表示列表已经到顶了，用户还在往下拉
                // 【关键修改】：只允许在 Tab 0 (今日视图) 下拉展开
                if (available.y > 0 && currentTab == 0) {
                    // * 0.5f 增加阻尼感，手感更厚重
                    val newOffset = (offsetY.value + available.y * 0.5f).coerceAtMost(maxOffsetPx)
                    scope.launch { offsetY.snapTo(newOffset) }

                    // 全额消耗，虽然列表也滑不动，但表明这是父容器的操作
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            // 3. 惯性处理：实现“分段式停顿” + “防缓慢漂移”
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                val currentOffset = offsetY.value

                // 场景 A：课表处于展开或半展开状态 (currentOffset > 0)
                // 此时的惯性用于控制课表的“开”或“合”，必须消耗掉，不能传给列表
                // 注意：如果 currentTab != 0，但 offsetY 已经是 0，这个 if 进不来，所以不会误触
                if (currentOffset > 0f) {
                    // 1. 决定目标位置
                    val target = when {
                        available.y > 1000f -> maxOffsetPx // 猛力下滑 -> 展开
                        available.y < -1000f -> 0f      // 猛力上滑 -> 收起
                        currentOffset > maxOffsetPx / 3f -> maxOffsetPx // 慢拖过 1/3 -> 展开
                        else -> 0f // 慢拖没过线 -> 收起
                    }

                    // 2. 决定动画类型（防缓慢漂移）
                    val isHighVelocity = kotlin.math.abs(available.y) > 1000f
                    val animSpec: AnimationSpec<Float> = if (isHighVelocity) {
                        // 速度快：使用弹簧，有物理感
                        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)
                    } else {
                        // 速度慢：使用 Tween，强制在 350ms 内完成，干脆利落
                        tween(durationMillis = 350, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                    }

                    scope.launch {
                        if (target != currentOffset) {
                            offsetY.animateTo(target, animSpec)
                        }
                    }

                    // 【关键】：返回 available，消耗所有速度。
                    // 这样当课表收起到 0 时，剩下的惯性不会让列表滚动到底部。
                    return available
                }

                // 场景 B：课表完全关闭 (currentOffset == 0)
                // 此时如果列表快速滚动到顶 (available.y > 0)，我们不操作 offsetY。
                // 让系统默认处理（列表停住或边缘发光）。
                // 必须完全停稳后，再次下拉触发 onPostScroll 才能拉出课表。
                return super.onPostFling(consumed, available)
            }
        }
    }

    // 只有当课表展开时才拦截返回键，用于收起课表
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
                .padding(top = 50.dp) // 预留给标题栏的空间
                .draggable(
                    state = rememberDraggableState { delta ->
                        if (offsetY.value > 0) {
                            val newOffset = (offsetY.value + delta).coerceIn(0f, maxOffsetPx)
                            scope.launch { offsetY.snapTo(newOffset) }
                        }
                    },
                    orientation = Orientation.Vertical,
                    onDragStopped = { velocity ->
                        // 这里是背景层的直接拖拽逻辑，保持与 nestedScroll 一致
                        val target = if (velocity > 1000f) maxOffsetPx
                        else if (velocity < -500f) 0f
                        else if (offsetY.value > maxOffsetPx / 3f) maxOffsetPx else 0f

                        // 同样的防漂移逻辑
                        val isHighVelocity = kotlin.math.abs(velocity) > 1000f
                        val animSpec: AnimationSpec<Float> = if (isHighVelocity) {
                            spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                        } else {
                            tween(durationMillis = 350, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                        }

                        scope.launch {
                            offsetY.animateTo(target, animSpec)
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
                            containerColor = MaterialTheme.colorScheme.background,
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
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 80.dp),
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
                                        uiSize = uiSize
                                    )
                                }
                            }

                            if (uiState.selectedDate == LocalDate.now() && uiState.tomorrowEvents.any { it.eventType != "temp" }) {
                                item { SectionHeader("明日安排", MaterialTheme.colorScheme.tertiary) }
                                items(uiState.tomorrowEvents.filter { it.eventType != "temp" }, key = { it.id }) { event ->
                                    SwipeableEventItem(
                                        event = event,
                                        isRevealed = uiState.revealedEventId == event.id,
                                        onExpand = { viewModel.onRevealEvent(event.id) },
                                        onCollapse = { viewModel.onRevealEvent(null) },
                                        onDelete = { viewModel.deleteEvent(event) },
                                        onImportant = { viewModel.toggleImportant(event) },
                                        onEdit = { onEditEvent(event) },
                                        uiSize = uiSize
                                    )
                                }
                            }
                        }
                    } else {
                        AllEventsPage(
                            viewModel = viewModel,
                            onEditEvent = { onEditEvent(it) },
                            uiSize = uiSize
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