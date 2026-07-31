package com.antgskds.calendarassistant.feature.home.ui.render.material

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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.outlined.CalendarViewDay
import androidx.compose.material.icons.outlined.CalendarViewMonth
import androidx.compose.material.icons.outlined.CalendarViewWeek
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.antgskds.calendarassistant.feature.recognition.application.ai.AnalysisResult
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.feature.recognition.application.ai.isRecognitionConfigReady
import com.antgskds.calendarassistant.feature.recognition.application.ai.recognitionConfigMissingMessage
import com.antgskds.calendarassistant.feature.recognition.ui.feedback.RecognitionFeedbackSource
import com.antgskds.calendarassistant.shared.util.ImageImportUtils
import com.antgskds.calendarassistant.shared.util.LunarCalendarUtils
import com.antgskds.calendarassistant.feature.weather.domain.WeatherIconMapper
import com.antgskds.calendarassistant.feature.home.domain.HomeEntryKey
import com.antgskds.calendarassistant.shared.ui.material.component.AppCard
import com.antgskds.calendarassistant.shared.ui.material.component.AppGlassSettingsProvider
import com.antgskds.calendarassistant.shared.ui.material.component.AppOverlayGlassSurface
import com.antgskds.calendarassistant.shared.ui.material.component.LocalAppGlassSettings
import com.antgskds.calendarassistant.shared.ui.material.component.PredictiveFloatingActionCard
import com.antgskds.calendarassistant.app.ui.theme.material.SectionTitleTextStyle
import com.antgskds.calendarassistant.feature.schedule.presentation.model.ScheduleDisplayItem
import com.antgskds.calendarassistant.platform.accessibility.TextAccessibilityService
import com.antgskds.calendarassistant.feature.home.ui.render.material.component.IntegratedFloatingBarBottomSpacing
import com.antgskds.calendarassistant.feature.home.ui.render.material.component.IntegratedFloatingBarHeight
import com.antgskds.calendarassistant.feature.home.ui.render.material.component.IntegratedFloatingBarVisualHeight
import com.antgskds.calendarassistant.feature.home.ui.contract.HomePageUiAction
import com.antgskds.calendarassistant.feature.home.ui.contract.HomePageUiState
import com.antgskds.calendarassistant.feature.schedule.ui.render.material.component.SwipeableEventItem
import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import com.antgskds.calendarassistant.shared.ui.interaction.rememberAppHaptics
import com.antgskds.calendarassistant.shared.ui.material.dialog.DialogEdgeToEdgeEffect
import com.antgskds.calendarassistant.shared.ui.material.dialog.DisableDialogWindowDimEffect
import com.antgskds.calendarassistant.app.ui.theme.material.background.appBackgroundSurfaceAlpha
import com.antgskds.calendarassistant.app.ui.theme.material.background.rememberAppBackgroundStylePalette
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.format.TextStyle
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MaterialHomePage(
    state: HomePageUiState,
    onAction: (HomePageUiAction) -> Unit,
    scheduleContent: @Composable () -> Unit,
    allEventsContent: @Composable (String, Dp) -> Unit,
    noteListContent: @Composable (String, Dp) -> Unit,
    quickMemoContent: @Composable (String, Dp) -> Unit,
    currentPageKey: String,
    uiSize: Int = 2,
    pickupTimestamp: Long = 0L,
    openCourseRequestId: Long = 0L,
    courseFeatureEnabled: Boolean = true,
    isActionExpanded: Boolean = false,
    onActionExpandedChange: (Boolean) -> Unit = {},
    searchRequestId: Int = 0,
    imageRequestId: Int = 0,
    isSidebarOpen: Boolean = false,
    onPageChange: (String) -> Unit = {},
    onAddEventClick: () -> Unit = {},
    onEditItem: (ScheduleDisplayItem) -> Unit = {},
    onRequestDeleteItem: (ScheduleDisplayItem) -> Unit = {},
    onCreateNote: () -> Unit = {},
    onRequestClearQuickMemos: () -> Unit = {},
    quickMemoCount: Int = 0,
    onScheduleExpandedChange: (Boolean) -> Unit = {},
    onScheduleProgressChange: (Float) -> Unit = {},
    onScheduleOffsetChange: (Float) -> Unit = {},
    onOpenWeatherDetail: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptics = rememberAppHaptics(state.settings.hapticFeedbackEnabled)
    val calendarMenuBackgroundMode = state.settings.appBackgroundImagePath.isNotBlank()
    val calendarMenuBackgroundPalette = rememberAppBackgroundStylePalette(
        enabled = calendarMenuBackgroundMode,
        miuiBlurEnabled = state.settings.appBackgroundMiuiBlurTestEnabled,
        cardAlphaPercent = state.settings.appBackgroundCardAlphaPercent
    )
    val calendarMenuIsDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val calendarMenuSurfaceAlpha = MySettings.normalizeAppBackgroundCardAlphaPercent(
        state.settings.appBackgroundCardAlphaPercent
    ) / 100f
    val calendarMenuContainerColor = if (calendarMenuBackgroundMode) {
        calendarMenuBackgroundPalette.surface.copy(alpha = calendarMenuSurfaceAlpha)
    } else if (calendarMenuIsDark) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surface
    }
    val calendarMenuSelectionColor = if (calendarMenuBackgroundMode) {
        calendarMenuBackgroundPalette.accent
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val calendarMenuContentColor = if (calendarMenuBackgroundMode) {
        calendarMenuBackgroundPalette.content
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val appGlassSettings = LocalAppGlassSettings.current
    val homeSceneBackdrop = rememberLayerBackdrop()
    val homeSceneBlurActive = calendarMenuBackgroundMode &&
        state.settings.appBackgroundMiuiBlurTestEnabled &&
        appGlassSettings.active
    val homeOverlayGlassSettings = appGlassSettings.copy(
        overlayBackdrop = homeSceneBackdrop.takeIf { homeSceneBlurActive }
    )


    var todaySearchQuery by rememberSaveable { mutableStateOf("") }
    var allSearchQuery by rememberSaveable { mutableStateOf("") }
    var noteSearchQuery by rememberSaveable { mutableStateOf("") }
    var isSearchMode by rememberSaveable { mutableStateOf(false) }
    var isLegacyNoteMode by rememberSaveable { mutableStateOf(false) }
    var calendarViewName by rememberSaveable { mutableStateOf(HomeCalendarViewMode.TODAY.name) }
    var isCalendarViewMenuExpanded by remember { mutableStateOf(false) }
    var calendarViewMenuAnchorBounds by remember { mutableStateOf<Rect?>(null) }
    val calendarViewMode = HomeCalendarViewMode.valueOf(calendarViewName)

    val isTodayPage = currentPageKey == HomeEntryKey.TODAY
    val isAllPage = currentPageKey == HomeEntryKey.ALL
    val isNotePage = currentPageKey == HomeEntryKey.NOTE

    LaunchedEffect(isTodayPage) {
        if (!isTodayPage) isCalendarViewMenuExpanded = false
    }

    BackHandler(enabled = isCalendarViewMenuExpanded) {
        isCalendarViewMenuExpanded = false
    }

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
                val settings = state.settings
                if (!settings.isRecognitionConfigReady()) {
                    Toast.makeText(context, settings.recognitionConfigMissingMessage(), Toast.LENGTH_SHORT).show()
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

                val analysisResult = withContext(Dispatchers.IO) {
                    (context.applicationContext as App)
                        .recognitionCenter
                        .analyzeImage(
                            bitmap = bitmap,
                            settings = settings,
                            context = context.applicationContext,
                            sourceType = RecognitionFeedbackSource.HOME_SOURCE_TYPE,
                            sourceId = RecognitionFeedbackSource.HOME_SOURCE_ID,
                            sourceImagePath = imageFile.absolutePath,
                            ingestRequested = true
                        )
                }
                bitmap.recycle()

                when (analysisResult) {
                    is AnalysisResult.Success -> {
                        Toast.makeText(context, "识别完成，正在保存...", Toast.LENGTH_SHORT).show()
                    }
                    is AnalysisResult.Empty -> return@launch
                    is AnalysisResult.Failure -> return@launch
                }
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
    var lastHandledOpenCourseRequestId by rememberSaveable { mutableLongStateOf(0L) }
    val maxOffsetPx = with(LocalDensity.current) { 600.dp.toPx() }

    // 触发阈值：约 100dp
    val snapThresholdPx = with(LocalDensity.current) { 100.dp.toPx() }

    // 提升 listState，用于精确判断列表是否到达顶部
    val listState = rememberLazyListState()

    val progress = if (courseFeatureEnabled) (offsetY.value / maxOffsetPx).coerceIn(0f, 1f) else 0f

    LaunchedEffect(courseFeatureEnabled) {
        if (!courseFeatureEnabled && offsetY.value != 0f) {
            offsetY.snapTo(0f)
        }
    }

    LaunchedEffect(openCourseRequestId, courseFeatureEnabled) {
        if (
            openCourseRequestId > 0L &&
            openCourseRequestId != lastHandledOpenCourseRequestId
        ) {
            lastHandledOpenCourseRequestId = openCourseRequestId
            if (!courseFeatureEnabled) return@LaunchedEffect
            offsetY.animateTo(
                targetValue = maxOffsetPx,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)
            )
        }
    }

    LaunchedEffect(offsetY.value, courseFeatureEnabled) {
        onScheduleExpandedChange(courseFeatureEnabled && offsetY.value > 0)
        onScheduleProgressChange(progress)
        onScheduleOffsetChange(if (courseFeatureEnabled) offsetY.value else 0f)
    }

    // === 核心修改：NestedScrollConnection ===
    val nestedScrollConnection = remember(currentPageKey, courseFeatureEnabled) {
        object : NestedScrollConnection {

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!courseFeatureEnabled) return Offset.Zero
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
                if (!courseFeatureEnabled) return Offset.Zero
                if (!isTodayPage) return Offset.Zero

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
                if (!courseFeatureEnabled) return Velocity.Zero
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
                if (!courseFeatureEnabled) return Velocity.Zero
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

    LaunchedEffect(searchRequestId) {
        if (searchRequestId > 0) {
            isSearchMode = true
        }
    }

    LaunchedEffect(currentPageKey) {
        if (!isNotePage) {
            isLegacyNoteMode = false
        }
    }

    LaunchedEffect(imageRequestId) {
        if (imageRequestId > 0 && !isImageImporting) {
            imagePickerLauncher.launch("image/*")
        }
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
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val floatingBarOffset = IntegratedFloatingBarHeight + IntegratedFloatingBarBottomSpacing + bottomInset
    val floatingBarContentPadding = IntegratedFloatingBarVisualHeight + IntegratedFloatingBarBottomSpacing + bottomInset + 16.dp

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
        // === 背景层：课程表视图 ===
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(top = 50.dp)
                // 处理在课表区域直接触摸滑动的逻辑
                .draggable(
                    state = rememberDraggableState { delta ->
                        if (courseFeatureEnabled && offsetY.value > 0) {
                            val newOffset = (offsetY.value + delta).coerceIn(0f, maxOffsetPx)
                            scope.launch { offsetY.snapTo(newOffset) }
                        }
                    },
                    enabled = courseFeatureEnabled,
                    orientation = Orientation.Vertical,
                    onDragStopped = { velocity ->
                        if (courseFeatureEnabled) {
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
                    }
                )
                .graphicsLayer {
                    alpha = progress
                    scaleX = 0.9f + (0.1f * progress)
                    scaleY = 0.9f + (0.1f * progress)
                }
        ) {

            scheduleContent()

        }

        // === 前景层：日程列表 + Scaffold ===
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, offsetY.value.roundToInt()) }
                .graphicsLayer { alpha = 1f - progress }
                .pointerInput(isActionExpanded, isSearchMode, isCalendarViewMenuExpanded) {
                    detectTapGestures(onTap = {
                        when {
                            isCalendarViewMenuExpanded -> isCalendarViewMenuExpanded = false
                            isActionExpanded -> onActionExpandedChange(false)
                            isSearchMode -> {
                                isSearchMode = false
                                when {
                                    isAllPage -> allSearchQuery = ""
                                    isNotePage -> noteSearchQuery = ""
                                    else -> todaySearchQuery = ""
                                }
                            }
                            else -> onAction(HomePageUiAction.RevealItem(null))
                        }
                    })
                }
        ) {
            val showSearchBar = isSearchMode &&
                !isSidebarOpen &&
                (isTodayPage || isAllPage || isNotePage)
            val searchBarHeight = 64.dp
            val searchBarOffset = searchBarHeight + 12.dp

            Scaffold(
                modifier = Modifier.then(
                    if (homeSceneBlurActive) {
                        Modifier.layerBackdrop(homeSceneBackdrop)
                    } else {
                        Modifier
                    }
                ),
                containerColor = if (state.settings.appBackgroundImagePath.isNotBlank()) {
                    Color.Transparent
                } else {
                    MaterialTheme.colorScheme.background
                },
                contentWindowInsets = WindowInsets(0),
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
                            val title = when {
                                isTodayPage -> "今日日程"
                                isNotePage -> if (isLegacyNoteMode) "普通便签" else "随口记"
                                else -> "全部日程"
                            }
                            Text(title)
                        },
                        actions = {
                            if (isTodayPage) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .onGloballyPositioned { coordinates ->
                                            calendarViewMenuAnchorBounds = coordinates.boundsInRoot()
                                        }
                                        .combinedClickable(
                                            onClick = {
                                                haptics.selection()
                                                calendarViewName = calendarViewMode.next().name
                                            },
                                            onLongClick = {
                                                haptics.longPress()
                                                isCalendarViewMenuExpanded = true
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.SwapHoriz,
                                        contentDescription = calendarViewMode.nextContentDescription,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            if (isNotePage && !isLegacyNoteMode && quickMemoCount > 0) {
                                IconButton(onClick = { haptics.click(); onRequestClearQuickMemos() }) {
                                    Icon(
                                        Icons.Default.DeleteSweep,
                                        contentDescription = "清空随口记",
                                        modifier = Modifier.size(topBarIconSize)
                                    )
                                }
                            }
                        }
                    )
                },
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    val contentBottomPadding = if (showSearchBar) {
                        floatingBarContentPadding + searchBarOffset
                    } else {
                        floatingBarContentPadding
                    }

                    val pageOrder = remember { listOf(HomeEntryKey.TODAY, HomeEntryKey.ALL, HomeEntryKey.NOTE) }
                    AnimatedContent(
                        targetState = currentPageKey,
                        modifier = Modifier.fillMaxSize(),
                        transitionSpec = {
                            val from = pageOrder.indexOf(initialState).coerceAtLeast(0)
                            val to = pageOrder.indexOf(targetState).coerceAtLeast(0)
                            val direction = if (to >= from) 1 else -1
                            (slideInHorizontally(animationSpec = tween(220)) { width -> width * direction } +
                                fadeIn(animationSpec = tween(160))) togetherWith
                                (slideOutHorizontally(animationSpec = tween(220)) { width -> -width * direction } +
                                    fadeOut(animationSpec = tween(140))) using SizeTransform(clip = false)
                        },
                        label = "home_page_switch"
                    ) { animatedPageKey ->
                    val animatedIsTodayPage = animatedPageKey == HomeEntryKey.TODAY
                    val animatedIsAllPage = animatedPageKey == HomeEntryKey.ALL
                    val animatedIsNotePage = animatedPageKey == HomeEntryKey.NOTE

                    if (animatedIsTodayPage) {
                        // === 今日视图内容 ===
                        val todayEvents = remember(state.currentDateEvents, todaySearchQuery) {
                            if (todaySearchQuery.isBlank()) {
                                state.currentDateEvents
                            } else {
                                state.currentDateEvents.filter { event ->
                                    event.title.contains(todaySearchQuery, ignoreCase = true) ||
                                            event.description.contains(todaySearchQuery, ignoreCase = true) ||
                                            event.location.contains(todaySearchQuery, ignoreCase = true)
                                }
                            }
                        }
                        val tomorrowEvents = remember(state.tomorrowEvents, todaySearchQuery) {
                            if (todaySearchQuery.isBlank()) {
                                state.tomorrowEvents
                            } else {
                                state.tomorrowEvents.filter { event ->
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
                            contentPadding = PaddingValues(bottom = contentBottomPadding),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            item { Spacer(modifier = Modifier.height(0.dp)) }

                            // 日期卡片
                            item {
                                HomeCalendarCard(
                                    state = state,
                                    viewMode = calendarViewMode,
                                    onSelectDate = { date ->
                                        haptics.selection()
                                        onAction(HomePageUiAction.SelectDate(date))
                                    },
                                    onOpenWeatherDetail = {
                                        haptics.click()
                                        onOpenWeatherDetail()
                                    }
                                )
                            }

                            if (!serviceEnabled) item { PermissionWarningCard(Icons.Default.Warning, "无障碍服务未开启", { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }) }) }
                            if (!notificationEnabled) item { PermissionWarningCard(Icons.Default.NotificationsOff, "通知权限未开启", { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply { putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName); flags = Intent.FLAG_ACTIVITY_NEW_TASK }) }) }

                            item { SectionHeader(if (state.selectedDate == state.today) "今日安排" else "${state.selectedDate.monthValue}月${state.selectedDate.dayOfMonth}日 安排", MaterialTheme.colorScheme.primary) }

                            if (todayEvents.isEmpty()) {
                                val emptyText = if (todaySearchQuery.isBlank()) "今日暂无日程" else "未找到相关日程"
                                item { Text(emptyText, modifier = Modifier.padding(vertical = 40.dp), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f)) }
                            } else {
                                items(todayEvents, key = { "today_${it.stableKey}" }) { item ->
                                    SwipeableEventItem(
                                        item = item,
                                        isRevealed = state.revealedItemKey == item.stableKey,
                                        timeRefreshToken = state.timeRefreshToken,
                                        onExpand = {
                                            onAction(HomePageUiAction.RevealItem(item.stableKey))
                                        },
                                        onCollapse = {
                                            onAction(HomePageUiAction.RevealItem(null))
                                        },
                                        onDelete = { onAction(HomePageUiAction.DeleteItem(item)) },
                                        onEdit = { onEditItem(item) },
                                        onLongPress = { onRequestDeleteItem(item) },
                                        uiSize = uiSize,
                                        isArchivePage = false,
                                        onArchive = { onAction(HomePageUiAction.ArchiveItem(item)) },
                                        hapticEnabled = state.settings.hapticFeedbackEnabled
                                    )
                                }
                            }

                            if (state.selectedDate == state.today && tomorrowEvents.isNotEmpty()) {
                                item { SectionHeader("明日安排", MaterialTheme.colorScheme.tertiary) }
                                items(tomorrowEvents, key = { "tomorrow_${it.stableKey}" }) { item ->
                                    SwipeableEventItem(
                                        item = item,
                                        isRevealed = state.revealedItemKey == item.stableKey,
                                        timeRefreshToken = state.timeRefreshToken,
                                        onExpand = {
                                            onAction(HomePageUiAction.RevealItem(item.stableKey))
                                        },
                                        onCollapse = {
                                            onAction(HomePageUiAction.RevealItem(null))
                                        },
                                        onDelete = { onAction(HomePageUiAction.DeleteItem(item)) },
                                        onEdit = { onEditItem(item) },
                                        onLongPress = { onRequestDeleteItem(item) },
                                        uiSize = uiSize,
                                        isArchivePage = false,
                                        onArchive = { onAction(HomePageUiAction.ArchiveItem(item)) },
                                        hapticEnabled = state.settings.hapticFeedbackEnabled
                                    )
                                }
                            }
                        }
                    } else if (animatedIsAllPage) {
                        allEventsContent(
                            allSearchQuery,
                            if (showSearchBar) searchBarOffset else 0.dp,
                        )
                    } else if (animatedIsNotePage && isLegacyNoteMode) {
                        noteListContent(
                            noteSearchQuery,
                            if (showSearchBar) searchBarOffset else 0.dp,
                        )
                    } else {
                        quickMemoContent(
                            noteSearchQuery,
                            if (showSearchBar) searchBarOffset else 0.dp,
                        )
                    }
                    }

                }
            }

            if (showSearchBar) {
                HomeSearchBar(
                    value = when {
                        isAllPage -> allSearchQuery
                        isNotePage -> noteSearchQuery
                        else -> todaySearchQuery
                    },
                    onValueChange = { value ->
                        when {
                            isAllPage -> allSearchQuery = value
                            isNotePage -> noteSearchQuery = value
                            else -> todaySearchQuery = value
                        }
                    },
                    placeholder = when {
                        isAllPage -> "搜索标题、备注或地点..."
                        isNotePage -> if (isLegacyNoteMode) {
                            "搜索便签标题或正文..."
                        } else {
                            "搜索随口记正文..."
                        }
                        else -> "搜索标题、备注或地点..."
                    },
                    onDismiss = { isSearchMode = false },
                    floatingBarOffset = floatingBarOffset,
                    containerColor = calendarMenuContainerColor,
                    iconSize = topBarIconSize
                )
            }

            AppGlassSettingsProvider(homeOverlayGlassSettings) {

                calendarViewMenuAnchorBounds?.let { anchorBounds ->
                    val density = LocalDensity.current
                    val menuWidth = 208.dp
                    val menuWidthPx = with(density) { menuWidth.roundToPx() }
                    val menuInsetPx = with(density) { 8.dp.roundToPx() }
                    val menuGapPx = with(density) { 4.dp.roundToPx() }
                    val menuX = (anchorBounds.right.roundToInt() - menuWidthPx)
                        .coerceAtLeast(menuInsetPx)
                    val menuY = anchorBounds.bottom.roundToInt() + menuGapPx

                    AnimatedVisibility(
                        visible = isCalendarViewMenuExpanded,
                        modifier = Modifier
                            .offset { IntOffset(menuX, menuY) }
                            .zIndex(2f),
                        enter = fadeIn(tween(180)) + scaleIn(
                            animationSpec = tween(220),
                            initialScale = 0.86f,
                            transformOrigin = TransformOrigin(1f, 0f)
                        ),
                        exit = fadeOut(tween(140)) + scaleOut(
                            animationSpec = tween(180),
                            targetScale = 0.9f,
                            transformOrigin = TransformOrigin(1f, 0f)
                        )
                    ) {
                        HomeCalendarViewMenu(
                            modifier = Modifier.width(menuWidth),
                            currentMode = calendarViewMode,
                            containerColor = calendarMenuContainerColor,
                            selectionColor = calendarMenuSelectionColor,
                            contentColor = calendarMenuContentColor,
                            onSelectMode = { mode ->
                                haptics.selection()
                                calendarViewName = mode.name
                                isCalendarViewMenuExpanded = false
                            }
                        )
                    }
                }
            }

        }

        PredictiveFloatingActionCard(
            visible = isImageImporting,
            title = "正在识别",
            content = "OCR + AI 分析中...",
            confirmText = "处理中",
            dismissText = "取消",
            isDestructive = false,
            isLoading = true,
            allowDismissWhileLoading = true,
            dismissOnClickOutside = false,
            predictiveBackEnabled = state.settings.predictiveBackEnabled,
            onConfirm = {},
            onDismiss = cancelImageImport,
            modifier = Modifier
                .padding(bottom = floatingBarOffset + 16.dp)
        )
    }
}

private enum class HomeCalendarViewMode {
    TODAY,
    WEEK,
    MONTH;

    fun next(): HomeCalendarViewMode = when (this) {
        TODAY -> WEEK
        WEEK -> MONTH
        MONTH -> TODAY
    }

    val nextContentDescription: String
        get() = when (this) {
            TODAY -> "切换到周视图"
            WEEK -> "切换到月视图"
            MONTH -> "返回日期视图"
        }

    val menuLabel: String
        get() = when (this) {
            TODAY -> "今日视图"
            WEEK -> "周视图"
            MONTH -> "月视图"
    }
}

@Composable
private fun HomeSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onDismiss: () -> Unit,
    floatingBarOffset: Dp,
    containerColor: Color,
    iconSize: Dp,
) {
    val glassSettings = LocalAppGlassSettings.current
    val searchFocusRequester = remember { FocusRequester() }
    val searchBarHeight = 64.dp
    val searchBarShape = RoundedCornerShape(24.dp)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        val keyboardController = LocalSoftwareKeyboardController.current
        val imePadding = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
        val bottomPadding = (imePadding + 12.dp).coerceAtLeast(floatingBarOffset + 36.dp)

        DialogEdgeToEdgeEffect(isDarkTheme = glassSettings.darkTheme)
        DisableDialogWindowDimEffect()

        LaunchedEffect(Unit) {
            searchFocusRequester.requestFocus()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = bottomPadding)
                    .height(searchBarHeight + 36.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                contentAlignment = Alignment.BottomCenter
            ) {
                AppOverlayGlassSurface(
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .height(searchBarHeight),
                    shape = searchBarShape,
                    fallbackColor = containerColor
                ) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier
                            .fillMaxSize()
                            .focusRequester(searchFocusRequester)
                            .pointerInput(Unit) {
                                detectTapGestures {
                                    searchFocusRequester.requestFocus()
                                    keyboardController?.show()
                                }
                            },
                        placeholder = { Text(placeholder) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "搜索",
                                modifier = Modifier.size(iconSize)
                            )
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "关闭",
                                    modifier = Modifier.size(iconSize)
                                )
                            }
                        },
                        singleLine = true,
                        shape = searchBarShape,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (glassSettings.overlayActive) {
                                Color.Transparent
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            unfocusedBorderColor = if (glassSettings.overlayActive) {
                                Color.Transparent
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            },
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            errorContainerColor = Color.Transparent
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeCalendarViewMenu(
    currentMode: HomeCalendarViewMode,
    containerColor: Color,
    selectionColor: Color,
    contentColor: Color,
    onSelectMode: (HomeCalendarViewMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    AppOverlayGlassSurface(
        modifier = modifier
            .shadow(elevation = 8.dp, shape = shape, clip = false)
            .clip(shape),
        shape = shape,
        fallbackColor = containerColor
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            HomeCalendarViewMode.entries.forEach { mode ->
                val selected = mode == currentMode
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = when (mode) {
                                    HomeCalendarViewMode.TODAY -> Icons.Outlined.CalendarViewDay
                                    HomeCalendarViewMode.WEEK -> Icons.Outlined.CalendarViewWeek
                                    HomeCalendarViewMode.MONTH -> Icons.Outlined.CalendarViewMonth
                                },
                                contentDescription = null,
                                tint = contentColor,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = mode.menuLabel,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 0.sp
                                ),
                                maxLines = 1
                            )
                        }
                    },
                    onClick = { onSelectMode(mode) },
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) selectionColor else Color.Transparent),
                    trailingIcon = {
                        if (selected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "当前视图",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    colors = MenuDefaults.itemColors(
                        textColor = contentColor,
                        trailingIconColor = contentColor
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun HomeCalendarCard(
    state: HomePageUiState,
    viewMode: HomeCalendarViewMode,
    onSelectDate: (LocalDate) -> Unit,
    onOpenWeatherDetail: () -> Unit,
) {
    val hasAppBackground = state.settings.appBackgroundImagePath.isNotBlank()
    val isToday = state.selectedDate == state.today
    val topBaseColor = if (isToday) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val topContentColor = if (isToday) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val topBarColor = if (hasAppBackground) {
        val glassAlpha = appBackgroundSurfaceAlpha(
            cardAlphaPercent = state.settings.appBackgroundCardAlphaPercent,
            dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f,
            miuiBlurEnabled = state.settings.appBackgroundMiuiBlurTestEnabled
        )
        topBaseColor.copy(alpha = glassAlpha)
    } else {
        topBaseColor
    }
    val dateCardShape = RoundedCornerShape(16.dp)

    BoxWithConstraints(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
    ) {
        val cardWidth = maxWidth
        val todayCardHeight = cardWidth / 0.95f
        val calendarHeaderHeight = todayCardHeight * 0.2f
        val monthRowHeight = (todayCardHeight - calendarHeaderHeight - 28.dp) / 6f
        val targetHeight = when (viewMode) {
            HomeCalendarViewMode.TODAY -> todayCardHeight
            HomeCalendarViewMode.WEEK -> calendarHeaderHeight + 114.dp
            HomeCalendarViewMode.MONTH -> todayCardHeight
        }
        val cardHeight by animateDpAsState(
            targetValue = targetHeight,
            animationSpec = tween(durationMillis = 420),
            label = "home_calendar_height"
        )

        AppCard(
            modifier = Modifier
                .fillMaxWidth()
                .height(cardHeight)
                .then(
                    if (hasAppBackground && !state.settings.appBackgroundMiuiBlurTestEnabled) {
                        Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, dateCardShape)
                    } else {
                        Modifier
                    }
                )
                .pointerInput(viewMode, state.selectedDate) {
                    var totalDrag = 0f
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val direction = when {
                                totalDrag < -50f -> 1L
                                totalDrag > 50f -> -1L
                                else -> 0L
                            }
                            if (direction != 0L) {
                                val nextDate = when (viewMode) {
                                    HomeCalendarViewMode.TODAY -> state.selectedDate.plusDays(direction)
                                    HomeCalendarViewMode.WEEK -> state.selectedDate.plusWeeks(direction)
                                    HomeCalendarViewMode.MONTH -> state.selectedDate.plusMonths(direction)
                                }
                                onSelectDate(nextDate)
                            }
                            totalDrag = 0f
                        },
                        onDragCancel = { totalDrag = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            totalDrag += dragAmount
                        }
                    )
                },
            shape = dateCardShape,
            elevation = CardDefaults.cardElevation(defaultElevation = if (hasAppBackground) 0.dp else 6.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (hasAppBackground || MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
                    MaterialTheme.colorScheme.surfaceContainerLow
                } else {
                    MaterialTheme.colorScheme.surface
                },
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                HomeCalendarSelectionIndicator(
                    viewMode = viewMode,
                    selectedDate = state.selectedDate,
                    headerHeight = calendarHeaderHeight,
                    monthRowHeight = monthRowHeight,
                    selectionColor = topBarColor,
                )
                AnimatedContent(
                    targetState = viewMode,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        when {
                            initialState == HomeCalendarViewMode.MONTH && targetState == HomeCalendarViewMode.TODAY -> {
                                (fadeIn(tween(280)) + scaleIn(tween(360), initialScale = 0.9f)) togetherWith
                                    (fadeOut(tween(160)) + shrinkVertically(tween(320), shrinkTowards = Alignment.Top))
                            }
                            targetState.ordinal > initialState.ordinal -> {
                                (fadeIn(tween(260)) + expandVertically(tween(360), expandFrom = Alignment.Top)) togetherWith
                                    fadeOut(tween(160))
                            }
                            else -> fadeIn(tween(260)) togetherWith fadeOut(tween(160))
                        }
                    },
                    label = "home_calendar_mode"
                ) { mode ->
                    when (mode) {
                        HomeCalendarViewMode.TODAY -> HomeTodayDateContent(
                            selectedDate = state.selectedDate,
                            today = state.today,
                            weatherData = state.weatherData,
                            hasAppBackground = hasAppBackground,
                            topContentColor = topContentColor,
                            onSelectDate = onSelectDate,
                            onOpenWeatherDetail = onOpenWeatherDetail,
                        )
                        HomeCalendarViewMode.WEEK -> HomeWeekContent(
                            selectedDate = state.selectedDate,
                            today = state.today,
                            headerHeight = calendarHeaderHeight,
                            datesWithEvents = state.datesWithEvents,
                            selectionContentColor = topContentColor,
                            onSelectDate = onSelectDate,
                        )
                        HomeCalendarViewMode.MONTH -> HomeMonthContent(
                            selectedDate = state.selectedDate,
                            today = state.today,
                            headerHeight = calendarHeaderHeight,
                            monthRowHeight = monthRowHeight,
                            datesWithEvents = state.datesWithEvents,
                            selectionContentColor = topContentColor,
                            onSelectDate = onSelectDate,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.HomeCalendarSelectionIndicator(
    viewMode: HomeCalendarViewMode,
    selectedDate: LocalDate,
    headerHeight: Dp,
    monthRowHeight: Dp,
    selectionColor: Color,
) {
    BoxWithConstraints(modifier = Modifier.matchParentSize()) {
        val horizontalPadding = 12.dp
        val horizontalSpacing = 2.dp
        val cellWidth = (maxWidth - horizontalPadding * 2 - horizontalSpacing * 6) / 7f
        val weekdayIndex = selectedDate.dayOfWeek.value - 1
        val selectedMonth = YearMonth.from(selectedDate)
        val monthGridStart = selectedMonth
            .atDay(1)
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val monthCellIndex = ChronoUnit.DAYS.between(monthGridStart, selectedDate).toInt()
        val monthRow = monthCellIndex / 7
        val monthCellSize = if (cellWidth < monthRowHeight) cellWidth else monthRowHeight
        val monthCellInset = (monthRowHeight - monthCellSize) / 2f
        val columnInset = (cellWidth - monthCellSize) / 2f

        val targetX = when (viewMode) {
            HomeCalendarViewMode.TODAY -> 0.dp
            HomeCalendarViewMode.WEEK -> horizontalPadding + (cellWidth + horizontalSpacing) * weekdayIndex
            HomeCalendarViewMode.MONTH -> {
                horizontalPadding + (cellWidth + horizontalSpacing) * weekdayIndex + columnInset
            }
        }
        val targetY = when (viewMode) {
            HomeCalendarViewMode.TODAY -> 0.dp
            HomeCalendarViewMode.WEEK -> headerHeight
            HomeCalendarViewMode.MONTH -> {
                headerHeight + 18.dp + monthRowHeight * monthRow + monthCellInset
            }
        }
        val targetWidth = when (viewMode) {
            HomeCalendarViewMode.TODAY -> maxWidth
            HomeCalendarViewMode.WEEK -> cellWidth
            HomeCalendarViewMode.MONTH -> monthCellSize
        }
        val targetHeight = when (viewMode) {
            HomeCalendarViewMode.TODAY -> headerHeight
            HomeCalendarViewMode.WEEK -> 104.dp
            HomeCalendarViewMode.MONTH -> monthCellSize
        }
        val targetRadius = if (viewMode == HomeCalendarViewMode.TODAY) 0.dp else 12.dp
        val animationSpec = tween<Dp>(durationMillis = 420)
        val x by animateDpAsState(targetX, animationSpec, label = "home_calendar_indicator_x")
        val y by animateDpAsState(targetY, animationSpec, label = "home_calendar_indicator_y")
        val width by animateDpAsState(targetWidth, animationSpec, label = "home_calendar_indicator_width")
        val height by animateDpAsState(targetHeight, animationSpec, label = "home_calendar_indicator_height")
        val radius by animateDpAsState(targetRadius, animationSpec, label = "home_calendar_indicator_radius")
        val color by animateColorAsState(
            targetValue = selectionColor,
            animationSpec = tween(durationMillis = 240),
            label = "home_calendar_indicator_color"
        )

        Box(
            modifier = Modifier
                .offset(x = x, y = y)
                .size(width = width, height = height)
                .clip(RoundedCornerShape(radius))
                .background(color)
        )
    }
}

@Composable
private fun HomeTodayDateContent(
    selectedDate: LocalDate,
    today: LocalDate,
    weatherData: com.antgskds.calendarassistant.feature.weather.domain.model.WeatherData?,
    hasAppBackground: Boolean,
    topContentColor: Color,
    onSelectDate: (LocalDate) -> Unit,
    onOpenWeatherDetail: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(0.2f)
                .fillMaxWidth()
                .clickable { onSelectDate(today) }
        ) {
            if (weatherData != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onOpenWeatherDetail)
                        .padding(horizontal = 22.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(WeatherIconMapper.iconRes(weatherData)),
                        contentDescription = weatherData.text.ifBlank { "天气" },
                        modifier = Modifier.size(30.dp),
                        tint = topContentColor
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = buildString {
                            append(weatherData.temperature.ifBlank { "--" })
                            append("°C")
                            if (weatherData.text.isNotBlank()) {
                                append(" · ")
                                append(weatherData.text)
                            }
                        },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = topContentColor,
                        maxLines = 1
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .weight(0.8f)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Text(
                    selectedDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.CHINESE),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    LunarCalendarUtils.getLunarDate(selectedDate),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = selectedDate.dayOfMonth.toString(),
                fontSize = 140.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 140.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onSelectDate(today) }
            )
            Text(
                "${selectedDate.year}年${selectedDate.monthValue}月",
                style = MaterialTheme.typography.bodyLarge,
                color = if (hasAppBackground) MaterialTheme.colorScheme.onSurfaceVariant else Color.Gray
            )
        }
    }
}

@Composable
private fun HomeWeekContent(
    selectedDate: LocalDate,
    today: LocalDate,
    headerHeight: Dp,
    datesWithEvents: Set<LocalDate>,
    selectionContentColor: Color,
    onSelectDate: (LocalDate) -> Unit,
) {
    val weekStart = remember(selectedDate) {
        selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }
    AnimatedContent(
        targetState = weekStart,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            val movingForward = targetState.isAfter(initialState)
            val enterDirection = if (movingForward) 1 else -1
            (slideInHorizontally(tween(320)) { width -> width * enterDirection } +
                fadeIn(tween(220))) togetherWith
                (slideOutHorizontally(tween(320)) { width -> -width * enterDirection } +
                    fadeOut(tween(180)))
        },
        label = "home_week_switch"
    ) { displayedWeekStart ->
        val weekDates = remember(displayedWeekStart) {
            List(7) { displayedWeekStart.plusDays(it.toLong()) }
        }
        val weekEnd = weekDates.last()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
                .padding(bottom = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(headerHeight),
                contentAlignment = Alignment.Center
            ) {
                HomeCalendarPeriodTitle(
                    startMonth = YearMonth.from(displayedWeekStart),
                    endMonth = YearMonth.from(weekEnd),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                weekDates.forEach { date ->
                    HomeCalendarDayCell(
                        date = date,
                        selected = date == selectedDate,
                        today = date == today,
                        inDisplayedMonth = true,
                        showWeekday = true,
                        hasEvents = date in datesWithEvents,
                        selectionContentColor = selectionContentColor,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onClick = { onSelectDate(date) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeMonthContent(
    selectedDate: LocalDate,
    today: LocalDate,
    headerHeight: Dp,
    monthRowHeight: Dp,
    datesWithEvents: Set<LocalDate>,
    selectionContentColor: Color,
    onSelectDate: (LocalDate) -> Unit,
) {
    val month = remember(selectedDate) { YearMonth.from(selectedDate) }
    val weekdays = remember { listOf("一", "二", "三", "四", "五", "六", "日") }

    AnimatedContent(
        targetState = month,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            val movingForward = targetState.isAfter(initialState)
            val enterDirection = if (movingForward) 1 else -1
            (slideInHorizontally(tween(340)) { width -> width * enterDirection } +
                fadeIn(tween(220))) togetherWith
                (slideOutHorizontally(tween(340)) { width -> -width * enterDirection } +
                    fadeOut(tween(180)))
        },
        label = "home_month_switch"
    ) { displayedMonth ->
        val gridStart = remember(displayedMonth) {
            displayedMonth.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        }
        val dates = remember(gridStart) { List(42) { gridStart.plusDays(it.toLong()) } }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
                .padding(bottom = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(headerHeight),
                contentAlignment = Alignment.Center
            ) {
                HomeCalendarPeriodTitle(startMonth = displayedMonth)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                weekdays.forEach { weekday ->
                    Text(
                        text = weekday,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            dates.chunked(7).forEach { week ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(monthRowHeight),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    week.forEach { date ->
                        HomeCalendarDayCell(
                            date = date,
                            selected = date == selectedDate,
                            today = date == today,
                            inDisplayedMonth = YearMonth.from(date) == displayedMonth,
                            showWeekday = false,
                            hasEvents = date in datesWithEvents,
                            selectionContentColor = selectionContentColor,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            onClick = { onSelectDate(date) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeCalendarPeriodTitle(
    startMonth: YearMonth,
    endMonth: YearMonth = startMonth,
) {
    val text = when {
        startMonth == endMonth -> "${startMonth.year}年${startMonth.monthValue}月"
        startMonth.year == endMonth.year -> {
            "${startMonth.year}年${startMonth.monthValue}月 - ${endMonth.monthValue}月"
        }
        else -> {
            "${startMonth.year}年${startMonth.monthValue}月 - ${endMonth.year}年${endMonth.monthValue}月"
        }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall.copy(
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.sp
        ),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1
    )
}

@Composable
private fun HomeCalendarDayCell(
    date: LocalDate,
    selected: Boolean,
    today: Boolean,
    inDisplayedMonth: Boolean,
    showWeekday: Boolean,
    hasEvents: Boolean,
    selectionContentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val contentColor = when {
        selected -> selectionContentColor
        !inDisplayedMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    val outlineModifier = if (today && !selected) {
        Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), shape)
    } else {
        Modifier
    }
    val lunarDateText = LunarCalendarUtils.getLunarDate(date).let { lunarDate ->
        if (lunarDate.length >= 2) lunarDate.takeLast(2) else lunarDate
    }
    val eventMarkerColor = when {
        selected -> selectionContentColor
        inDisplayedMonth -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    }

    Box(
        modifier = modifier
            .clip(shape)
            .then(outlineModifier)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = if (showWeekday) 2.dp else 1.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (showWeekday) {
                Text(
                    text = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.CHINESE),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = contentColor.copy(alpha = 0.7f),
                    maxLines = 1
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(
                text = date.dayOfMonth.toString(),
                style = if (showWeekday) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected || today) FontWeight.Black else FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1
            )
            Text(
                text = lunarDateText,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Normal
                ),
                color = if (selected) {
                    contentColor.copy(alpha = 0.85f)
                } else {
                    contentColor.copy(alpha = 0.5f)
                },
                maxLines = 1,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        if (hasEvents) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 6.dp)
                    .size(4.dp)
                    .background(eventMarkerColor, CircleShape)
            )
        }
    }
}

@Composable
private fun PermissionWarningCard(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)), onClick = onClick) {
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
        Text(text = title, style = SectionTitleTextStyle.copy(color = MaterialTheme.colorScheme.onBackground))
    }
}
