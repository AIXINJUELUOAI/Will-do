package com.antgskds.calendarassistant.feature.home.ui.render.material

import com.antgskds.calendarassistant.feature.accounting.ui.AccountingSummaryHeader
import com.antgskds.calendarassistant.shared.ui.material.component.AppPageScaffold
import com.antgskds.calendarassistant.shared.ui.material.component.LocalAppPageBottomPadding
import com.antgskds.calendarassistant.shared.ui.material.component.AppTopBar

import android.content.Context
import android.content.Intent
import com.antgskds.calendarassistant.shared.util.AppLogger as Log
import android.widget.Toast
import android.provider.Settings
import com.antgskds.calendarassistant.shared.ui.material.component.DetailWorkspaceOverlay
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CalendarViewDay
import androidx.compose.material.icons.outlined.CalendarViewMonth
import androidx.compose.material.icons.outlined.CalendarViewWeek
import androidx.compose.material.icons.outlined.ViewTimeline
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.antgskds.calendarassistant.feature.schedule.ui.render.material.calendarGridBoundary
import com.antgskds.calendarassistant.feature.schedule.ui.render.material.calendarMinuteOffset
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
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
import com.antgskds.calendarassistant.shared.ui.material.component.AppDropdownMenu
import com.antgskds.calendarassistant.shared.ui.material.component.AppMenuItem
import com.antgskds.calendarassistant.shared.ui.material.component.AppOverlayGlassSurface
import com.antgskds.calendarassistant.shared.ui.material.component.AppSegmentedControl
import com.antgskds.calendarassistant.shared.ui.material.component.LocalAppGlassSettings
import com.antgskds.calendarassistant.shared.ui.material.component.PredictiveFloatingActionCard
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog
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
import com.antgskds.calendarassistant.shared.ui.adaptive.AdaptiveTwoPaneLayout
import com.antgskds.calendarassistant.shared.ui.adaptive.AdaptiveTwoPaneTopBar
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
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt
import com.antgskds.calendarassistant.shared.ui.material.component.appWindowBackdrop
import com.antgskds.calendarassistant.shared.ui.material.component.rememberAppWindowBackdrop

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MaterialHomePage(
    state: HomePageUiState,
    onAction: (HomePageUiAction) -> Unit,
    scheduleContent: @Composable () -> Unit,
    allEventsContent: @Composable (String, Dp) -> Unit,
    quickMemoContent: @Composable (String, Dp) -> Unit,
    currentPageKey: String,
    pageOrder: List<String>,
    uiSize: Int = 2,
    pickupTimestamp: Long = 0L,
    openCourseRequestId: Long = 0L,
    courseFeatureEnabled: Boolean = true,
    isActionExpanded: Boolean = false,
    onActionExpandedChange: (Boolean) -> Unit = {},
    searchRequestId: Int = 0,
    imageRequestId: Int = 0,
    isSidebarOpen: Boolean = false,
    isWideNavigation: Boolean = false,
    isTwoPane: Boolean = false,
    onPageChange: (String) -> Unit = {},
    onAddEventClick: () -> Unit = {},
    onAddCourseClick: () -> Unit = {},
    onEditItem: (ScheduleDisplayItem) -> Unit = {},
    onRequestDeleteItem: (ScheduleDisplayItem) -> Unit = {},
    onRequestClearQuickMemos: () -> Unit = {},
    quickMemoCount: Int = 0,
    onScheduleExpandedChange: (Boolean) -> Unit = {},
    onScheduleProgressChange: (Float) -> Unit = {},
    onScheduleOffsetChange: (Float) -> Unit = {},
    onOpenWeatherDetail: () -> Unit = {},
    onOpenAccounting: (java.time.LocalDate, Boolean) -> Unit = { _, _ -> },
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
    val homeSceneBackdrop = rememberAppWindowBackdrop(parent = LocalAppGlassSettings.current.overlayBackdrop)
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
    var calendarViewName by rememberSaveable { mutableStateOf(HomeCalendarViewMode.TODAY.name) }
    var isCalendarViewMenuExpanded by remember { mutableStateOf(false) }
    var isWideActionMenuExpanded by remember { mutableStateOf(false) }
    val calendarViewMode = HomeCalendarViewMode.valueOf(calendarViewName)

    val isTodayPage = currentPageKey == HomeEntryKey.TODAY
    val isAllPage = currentPageKey == HomeEntryKey.ALL
    val isNotePage = currentPageKey == HomeEntryKey.NOTE
    val homePageTitle = when {
        isTodayPage -> if (isWideNavigation) "今日" else "今日日程"
        isNotePage -> "随口记"
        else -> if (isWideNavigation) "全部" else "全部日程"
    }

    LaunchedEffect(isTodayPage) {
        if (!isTodayPage) isCalendarViewMenuExpanded = false
    }

    LaunchedEffect(isTwoPane) {
        if (isTwoPane && calendarViewMode !in HomeCalendarViewMode.wideModes) {
            calendarViewName = HomeCalendarViewMode.WEEK.name
        } else if (!isTwoPane && calendarViewMode !in HomeCalendarViewMode.phoneModes) {
            calendarViewName = HomeCalendarViewMode.TODAY.name
        }
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

        Log.i("WillDoRecognition", "home image selected authority=${uri.authority.orEmpty()}")
        imageImportJob?.cancel()
        imageImportJob = scope.launch {
            isImageImporting = true
            try {
                val settings = state.settings
                if (!settings.isRecognitionConfigReady()) {
                    Log.w(
                        "WillDoRecognition",
                        "home image rejected configReady=false multimodal=true"
                    )
                    Toast.makeText(context, settings.recognitionConfigMissingMessage(), Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val imageFile = ImageImportUtils.createImportedImageFile(context)
                val copied = withContext(Dispatchers.IO) {
                    ImageImportUtils.copyUriToFile(context, uri, imageFile)
                }
                Log.i("WillDoRecognition", "home image copy completed success=$copied")
                if (!copied) {
                    Toast.makeText(context, "图片读取失败", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val bitmap = withContext(Dispatchers.IO) {
                    ImageImportUtils.decodeSampledBitmapFromFile(imageFile)
                }
                Log.i(
                    "WillDoRecognition",
                    "home image decode completed success=${bitmap != null} " +
                        "size=${bitmap?.width ?: 0}x${bitmap?.height ?: 0}"
                )
                if (bitmap == null) {
                    Toast.makeText(context, "图片解码失败", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                Log.i("WillDoRecognition", "home image dispatching recognition")
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
                        Toast.makeText(context, analysisResult.feedback(), Toast.LENGTH_LONG).show()
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

    val courseModuleEnabled = com.antgskds.calendarassistant.feature.schedule.domain.course.CourseFeaturePolicy.enabled(state.settings)
    val progress = if (courseModuleEnabled) (offsetY.value / maxOffsetPx).coerceIn(0f, 1f) else 0f
    LaunchedEffect(courseModuleEnabled) {
        if (!courseModuleEnabled && calendarViewMode == HomeCalendarViewMode.COURSE) {
            calendarViewName = HomeCalendarViewMode.WEEK.name
        }
    }
    LaunchedEffect(courseModuleEnabled) {
        if (!courseModuleEnabled && offsetY.value != 0f) {
            offsetY.snapTo(0f)
        }
    }

    LaunchedEffect(openCourseRequestId, courseModuleEnabled) {
        if (
            openCourseRequestId > 0L &&
            openCourseRequestId != lastHandledOpenCourseRequestId
        ) {
            lastHandledOpenCourseRequestId = openCourseRequestId
            if (!courseModuleEnabled) return@LaunchedEffect
            if (isTwoPane) {
                calendarViewName = HomeCalendarViewMode.COURSE.name
                return@LaunchedEffect
            }
            offsetY.animateTo(
                targetValue = maxOffsetPx,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)
            )
        }
    }

    LaunchedEffect(offsetY.value, courseModuleEnabled) {
        onScheduleExpandedChange(courseModuleEnabled && offsetY.value > 0)
        onScheduleProgressChange(progress)
        onScheduleOffsetChange(if (courseModuleEnabled) offsetY.value else 0f)
    }

    // === 核心修改：NestedScrollConnection ===
    val nestedScrollConnection = remember(currentPageKey, courseFeatureEnabled, courseModuleEnabled, calendarViewMode, todaySearchQuery.isNotBlank()) {
        object : NestedScrollConnection {

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (isTwoPane) return Offset.Zero
                if (!courseModuleEnabled || (!courseFeatureEnabled && offsetY.value == 0f)) return Offset.Zero
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
                if (isTwoPane) return Offset.Zero
                if (!courseModuleEnabled || (!courseFeatureEnabled && offsetY.value == 0f)) return Offset.Zero
                if (!isTodayPage) return Offset.Zero
                if (calendarViewMode == HomeCalendarViewMode.AGENDA || todaySearchQuery.isNotBlank()) return Offset.Zero

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
                if (!courseModuleEnabled || (!courseFeatureEnabled && offsetY.value == 0f)) return Velocity.Zero
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
                if (!courseModuleEnabled || (!courseFeatureEnabled && offsetY.value == 0f)) return Velocity.Zero
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
    val floatingBarOffset = if (isWideNavigation) {
        bottomInset
    } else {
        IntegratedFloatingBarHeight + IntegratedFloatingBarBottomSpacing + bottomInset
    }
    val floatingBarContentPadding = if (isWideNavigation) {
        bottomInset + 24.dp
    } else {
        IntegratedFloatingBarVisualHeight + IntegratedFloatingBarBottomSpacing + bottomInset + 16.dp
    }

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
        if (!isTwoPane) Box(
            modifier = Modifier
                .matchParentSize()
                .padding(top = 50.dp)
                // 处理在课表区域直接触摸滑动的逻辑
                .draggable(
                    state = rememberDraggableState { delta ->
                        if (courseModuleEnabled && offsetY.value > 0) {
                            val newOffset = (offsetY.value + delta).coerceIn(0f, maxOffsetPx)
                            scope.launch { offsetY.snapTo(newOffset) }
                        }
                    },
                    enabled = courseModuleEnabled,
                    orientation = Orientation.Vertical,
                    onDragStopped = { velocity ->
                        if (courseModuleEnabled) {
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
                .offset { IntOffset(0, if (isTwoPane) 0 else offsetY.value.roundToInt()) }
                .graphicsLayer {
                    alpha = if (isTwoPane) 1f else 1f - progress
                }
                .pointerInput(isActionExpanded, isSearchMode) {
                    detectTapGestures(onTap = {
                        when {
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

            AppPageScaffold(
                modifier = Modifier.then(
                    if (homeSceneBlurActive) {
                        Modifier.appWindowBackdrop(homeSceneBackdrop)
                    } else {
                        Modifier
                    }
                ),
                containerColor = if (state.settings.appBackgroundImagePath.isNotBlank()) {
                    Color.Transparent
                } else {
                    MaterialTheme.colorScheme.background
                },
                edgeToEdgeContent = true,
                avoidKeyboard = false,
                contentMaxWidth = androidx.compose.ui.unit.Dp.Unspecified,
                topBar = {
                    val actions: @Composable RowScope.() -> Unit = {
                        AppGlassSettingsProvider(homeOverlayGlassSettings) {
                            HomeTopBarActions(
                                isTodayPage = isTodayPage,
                                isNotePage = isNotePage,
                                isWideNavigation = isWideNavigation,
                                isTwoPane = isTwoPane,
                                quickMemoCount = quickMemoCount,
                                topBarIconSize = topBarIconSize,
                                calendarViewMode = calendarViewMode,
                                isWideActionMenuExpanded = isWideActionMenuExpanded,
                                onWideActionMenuExpandedChange = { isWideActionMenuExpanded = it },
                                onCycleCalendarMode = {
                                    haptics.selection()
                                    calendarViewName = calendarViewMode.next(isTwoPane).name
                                },
                                onOpenCalendarModeMenu = {
                                    haptics.longPress()
                                    isCalendarViewMenuExpanded = true
                                },
                                isCalendarViewMenuExpanded = isCalendarViewMenuExpanded,
                                onDismissCalendarModeMenu = { isCalendarViewMenuExpanded = false },
                                onSelectCalendarMode = { mode ->
                                    haptics.selection()
                                    calendarViewName = mode.name
                                },
                                menuContainerColor = calendarMenuContainerColor,
                                menuSelectionColor = calendarMenuSelectionColor,
                                menuContentColor = calendarMenuContentColor,
                                onCreate = {
                                    haptics.click()
                                    if (isTwoPane && calendarViewMode == HomeCalendarViewMode.COURSE) onAddCourseClick() else onAddEventClick()
                                },
                                onSearch = {
                                    haptics.click()
                                    isWideActionMenuExpanded = false
                                    isSearchMode = true
                                },
                                onImage = {
                                    haptics.click()
                                    isWideActionMenuExpanded = false
                                    if (!isImageImporting) imagePickerLauncher.launch("image/*")
                                },
                                onClearQuickMemos = {
                                    haptics.click()
                                    isWideActionMenuExpanded = false
                                    onRequestClearQuickMemos()
                                },
                            )
                        }
                    }
                    /*设置为background后会导致深浅模式切换时topbar不同步;此注释严禁删除
                    containerColor = MaterialTheme.colorScheme.background,*/
                    if (isTwoPane) {
                        AdaptiveTwoPaneTopBar(
                            primaryTitle = homePageTitle,
                            secondaryTitle = if (isNotePage) "随口记详情" else "日历",
                            primaryWidth = ConfigCatalog.ADAPTIVE_COMPACT_PANE_WIDTH_DP.dp,
                            primaryActions = actions,
                        )
                    } else {
                        AppTopBar(
                            title = homePageTitle,
                            containerColor = Color.Transparent,
                            actions = actions,
                        )
                    }
                },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    val contentBottomPadding = if (showSearchBar) {
                        floatingBarContentPadding + searchBarOffset
                    } else {
                        floatingBarContentPadding
                    }

                    AnimatedContent(
                        targetState = currentPageKey,
                        modifier = Modifier.fillMaxSize(),
                        transitionSpec = {
                            val from = pageOrder.indexOf(initialState)
                            val to = pageOrder.indexOf(targetState)
                            if (from < 0 || to < 0) {
                                (fadeIn(animationSpec = tween(160)) togetherWith
                                    fadeOut(animationSpec = tween(140))) using SizeTransform(clip = false)
                            } else {
                                val direction = if (to >= from) 1 else -1
                                (slideInHorizontally(animationSpec = tween(220)) { width -> width * direction } +
                                    fadeIn(animationSpec = tween(160))) togetherWith
                                    (slideOutHorizontally(animationSpec = tween(220)) { width -> -width * direction } +
                                        fadeOut(animationSpec = tween(140))) using SizeTransform(clip = false)
                            }
                        },
                        label = "home_page_switch"
                    ) { animatedPageKey ->
                    val animatedIsTodayPage = animatedPageKey == HomeEntryKey.TODAY
                    val animatedIsAllPage = animatedPageKey == HomeEntryKey.ALL

                    if (animatedIsTodayPage) {
                        // === 今日视图内容 ===
                        HomeTodayContent(
                            state = state,
                            todayEvents = state.currentDateEvents,
                            tomorrowEvents = state.tomorrowEvents,
                            searchQuery = todaySearchQuery,
                            calendarViewMode = calendarViewMode,
                            listState = listState,
                            contentBottomPadding = contentBottomPadding,
                            uiSize = uiSize,
                            isTwoPane = isTwoPane,
                            serviceEnabled = serviceEnabled,
                            notificationEnabled = notificationEnabled,
                            onSelectDate = { date ->
                                haptics.selection()
                                onAction(HomePageUiAction.SelectDate(date))
                            },
                            onOpenWeatherDetail = {
                                haptics.click()
                                onOpenWeatherDetail()
                            },
                            onOpenAccounting = onOpenAccounting,
                            onOpenAccessibilitySettings = {
                                context.startActivity(
                                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    },
                                )
                            },
                            onOpenNotificationSettings = {
                                context.startActivity(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    },
                                )
                            },
                            onAction = onAction,
                            onEditItem = onEditItem,
                            onRequestDeleteItem = onRequestDeleteItem,
                            scheduleContent = scheduleContent,
                            onCalendarViewModeChange = { mode -> calendarViewName = mode.name },
                        )
                    } else if (animatedIsAllPage) {
                        if (isTwoPane) {
                            AdaptiveTwoPaneLayout(
                                primaryWidth = ConfigCatalog.ADAPTIVE_COMPACT_PANE_WIDTH_DP.dp,
                                primary = {
                                    allEventsContent(
                                        allSearchQuery,
                                        if (showSearchBar) searchBarOffset else 0.dp,
                                    )
                                },
                                secondary = {
                                    HomeWideCalendarWorkspace(
                                        state = state,
                                        viewMode = calendarViewMode,
                                        onViewModeChange = { mode -> calendarViewName = mode.name },
                                        onSelectDate = { date ->
                                            haptics.selection()
                                            onAction(HomePageUiAction.SelectDate(date))
                                        },
                                        onEditItem = onEditItem,
                                        onOpenWeatherDetail = {},
                                        scheduleContent = scheduleContent,
                                    )
                                },
                            )
                        } else {
                            AdaptiveListPageContainer(
                                enabled = isWideNavigation,
                                maxContentWidth = 960.dp,
                            ) {
                                allEventsContent(
                                    allSearchQuery,
                                    if (showSearchBar) searchBarOffset else 0.dp,
                                )
                            }
                        }
                    } else {
                        if (isTwoPane) {
                            quickMemoContent(
                                noteSearchQuery,
                                if (showSearchBar) searchBarOffset else 0.dp,
                            )
                        } else AdaptiveListPageContainer(
                            enabled = isWideNavigation,
                            maxContentWidth = 960.dp,
                        ) {
                            quickMemoContent(
                                noteSearchQuery,
                                if (showSearchBar) searchBarOffset else 0.dp,
                            )
                        }
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
                        isNotePage -> "搜索随口记正文..."
                        else -> "搜索标题、备注或地点..."
                    },
                    onDismiss = { isSearchMode = false },
                    floatingBarOffset = floatingBarOffset,
                    containerColor = calendarMenuContainerColor,
                    iconSize = topBarIconSize
                )
            }

            if (isTwoPane && (isTodayPage || isAllPage) && com.antgskds.calendarassistant.shared.ui.material.component.LocalDetailWorkspaceContent.current == null) {
                HomeWideQuickActions(
                    expanded = isWideActionMenuExpanded,
                    onExpandedChange = { isWideActionMenuExpanded = it },
                    onSearch = {
                        isWideActionMenuExpanded = false
                        isSearchMode = true
                    },
                    onImage = {
                        isWideActionMenuExpanded = false
                        if (!isImageImporting) imagePickerLauncher.launch("image/*")
                    },
                    onCreate = {
                        isWideActionMenuExpanded = false
                        if (isTwoPane && calendarViewMode == HomeCalendarViewMode.COURSE) onAddCourseClick() else onAddEventClick()
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 24.dp, bottom = bottomInset + 24.dp),
                )
            }

        }

        PredictiveFloatingActionCard(
            visible = isImageImporting,
            title = "正在识别",
            content = "多模态 AI 分析中...",
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

@Composable
private fun AdaptiveListPageContainer(
    enabled: Boolean,
    maxContentWidth: Dp = 960.dp,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        content()
        return
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = maxContentWidth)
                .fillMaxWidth()
                .fillMaxHeight(),
        ) {
            content()
        }
    }
}

@Composable
internal fun HomeCalendarContent(
    state: HomePageUiState,
    todayEvents: List<ScheduleDisplayItem>,
    tomorrowEvents: List<ScheduleDisplayItem>,
    searchQuery: String,
    calendarViewMode: HomeCalendarViewMode,
    listState: LazyListState,
    contentBottomPadding: Dp,
    uiSize: Int,
    isTwoPane: Boolean,
    serviceEnabled: Boolean,
    notificationEnabled: Boolean,
    onSelectDate: (LocalDate) -> Unit,
    onOpenWeatherDetail: () -> Unit,
    onOpenAccounting: (LocalDate, Boolean) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onAction: (HomePageUiAction) -> Unit,
    onEditItem: (ScheduleDisplayItem) -> Unit,
    onRequestDeleteItem: (ScheduleDisplayItem) -> Unit,
    scheduleContent: @Composable () -> Unit,
    onCalendarViewModeChange: (HomeCalendarViewMode) -> Unit,
) {
    if (!isTwoPane) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = contentBottomPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { Spacer(modifier = Modifier.height(0.dp)) }
            homeCalendarPaneItems(
                state = state,
                calendarViewMode = calendarViewMode,
                serviceEnabled = serviceEnabled,
                notificationEnabled = notificationEnabled,
                onSelectDate = onSelectDate,
                onOpenWeatherDetail = onOpenWeatherDetail,
                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                onOpenNotificationSettings = onOpenNotificationSettings,
            )
            homeAgendaItems(
                showAccounting = true,
                onOpenAccounting = onOpenAccounting,
                state = state,
                todayEvents = todayEvents,
                tomorrowEvents = tomorrowEvents,
                searchQuery = searchQuery,
                uiSize = uiSize,
                onAction = onAction,
                onEditItem = onEditItem,
                onRequestDeleteItem = onRequestDeleteItem,
            )
        }
        return
    }

    AdaptiveTwoPaneLayout(
        primaryWidth = ConfigCatalog.ADAPTIVE_COMPACT_PANE_WIDTH_DP.dp,
        primary = {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = contentBottomPadding),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                homePermissionItems(
                    serviceEnabled = serviceEnabled,
                    notificationEnabled = notificationEnabled,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                    onOpenNotificationSettings = onOpenNotificationSettings,
                )
                homeAgendaItems(
                    showAccounting = false,
                    onOpenAccounting = onOpenAccounting,
                    state = state,
                    todayEvents = todayEvents,
                    tomorrowEvents = tomorrowEvents,
                    searchQuery = searchQuery,
                    uiSize = uiSize,
                    onAction = onAction,
                    onEditItem = onEditItem,
                    onRequestDeleteItem = onRequestDeleteItem,
                )
            }
        },
        secondary = {
            HomeWideCalendarWorkspace(
                state = state,
                viewMode = calendarViewMode,
                onViewModeChange = onCalendarViewModeChange,
                onSelectDate = onSelectDate,
                onEditItem = onEditItem,
                onOpenWeatherDetail = onOpenWeatherDetail,
                scheduleContent = scheduleContent,
            )
        },
    )
}

private fun LazyListScope.homeCalendarPaneItems(
    state: HomePageUiState,
    calendarViewMode: HomeCalendarViewMode,
    serviceEnabled: Boolean,
    notificationEnabled: Boolean,
    onSelectDate: (LocalDate) -> Unit,
    onOpenWeatherDetail: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
) {
    homePermissionItems(
        serviceEnabled = serviceEnabled,
        notificationEnabled = notificationEnabled,
        onOpenAccessibilitySettings = onOpenAccessibilitySettings,
        onOpenNotificationSettings = onOpenNotificationSettings,
    )
    item {
        HomeCalendarCard(
            state = state,
            viewMode = calendarViewMode,
            onSelectDate = onSelectDate,
            onOpenWeatherDetail = onOpenWeatherDetail,
        )
    }
}

private fun LazyListScope.homePermissionItems(
    serviceEnabled: Boolean,
    notificationEnabled: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
) {
    if (!serviceEnabled) {
        item {
            PermissionWarningCard(
                icon = Icons.Default.Warning,
                text = "无障碍服务未开启",
                onClick = onOpenAccessibilitySettings,
            )
        }
    }
    if (!notificationEnabled) {
        item {
            PermissionWarningCard(
                icon = Icons.Default.NotificationsOff,
                text = "通知权限未开启",
                onClick = onOpenNotificationSettings,
            )
        }
    }
}

private fun LazyListScope.homeAgendaItems(
    state: HomePageUiState,
    showAccounting: Boolean,
    onOpenAccounting: (LocalDate, Boolean) -> Unit,
    todayEvents: List<ScheduleDisplayItem>,
    tomorrowEvents: List<ScheduleDisplayItem>,
    searchQuery: String,
    uiSize: Int,
    onAction: (HomePageUiAction) -> Unit,
    onEditItem: (ScheduleDisplayItem) -> Unit,
    onRequestDeleteItem: (ScheduleDisplayItem) -> Unit,
) {
    if (showAccounting) item {
        AccountingSummaryHeader(
            date = state.selectedDate,
            onOpen = onOpenAccounting,
            title = if (state.selectedDate == state.today) {
                "今日安排"
            } else {
                "${state.selectedDate.monthValue}月${state.selectedDate.dayOfMonth}日 安排"
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            titleFontWeight = FontWeight.ExtraBold,
            summaryFontWeight = FontWeight.ExtraBold,
            hapticEnabled = state.settings.hapticFeedbackEnabled,
        )
    }

    if (todayEvents.isEmpty()) {
        item {
            Text(
                text = if (searchQuery.isBlank()) "今日暂无日程" else "未找到相关日程",
                modifier = Modifier.padding(vertical = 40.dp),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
            )
        }
    } else {
        items(todayEvents, key = { "today_${it.stableKey}" }) { item ->
            HomeAgendaEventItem(
                item = item,
                state = state,
                uiSize = uiSize,
                onAction = onAction,
                onEditItem = onEditItem,
                onRequestDeleteItem = onRequestDeleteItem,
            )
        }
    }

    if (state.selectedDate == state.today && tomorrowEvents.isNotEmpty()) {
        item { SectionHeader("明日安排", MaterialTheme.colorScheme.tertiary) }
        items(tomorrowEvents, key = { "tomorrow_${it.stableKey}" }) { item ->
            HomeAgendaEventItem(
                item = item,
                state = state,
                uiSize = uiSize,
                onAction = onAction,
                onEditItem = onEditItem,
                onRequestDeleteItem = onRequestDeleteItem,
            )
        }
    }
}

@Composable
internal fun HomeAgendaEventItem(
    item: ScheduleDisplayItem,
    state: HomePageUiState,
    uiSize: Int,
    onAction: (HomePageUiAction) -> Unit,
    onEditItem: (ScheduleDisplayItem) -> Unit,
    onRequestDeleteItem: (ScheduleDisplayItem) -> Unit,
) {
    SwipeableEventItem(
        item = item,
        isRevealed = state.revealedItemKey == item.stableKey,
        timeRefreshToken = state.timeRefreshToken,
        onExpand = { onAction(HomePageUiAction.RevealItem(item.stableKey)) },
        onCollapse = { onAction(HomePageUiAction.RevealItem(null)) },
        onDelete = { onAction(HomePageUiAction.DeleteItem(item)) },
        onEdit = { onEditItem(item) },
        onLongPress = { onRequestDeleteItem(item) },
        uiSize = uiSize,
        isArchivePage = false,
        onArchive = { onAction(HomePageUiAction.ArchiveItem(item)) },
        hapticEnabled = state.settings.hapticFeedbackEnabled,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RowScope.HomeTopBarActions(
    isTodayPage: Boolean,
    isNotePage: Boolean,
    isWideNavigation: Boolean,
    isTwoPane: Boolean,
    quickMemoCount: Int,
    topBarIconSize: Dp,
    calendarViewMode: HomeCalendarViewMode,
    isWideActionMenuExpanded: Boolean,
    onWideActionMenuExpandedChange: (Boolean) -> Unit,
    onCycleCalendarMode: () -> Unit,
    onOpenCalendarModeMenu: () -> Unit,
    isCalendarViewMenuExpanded: Boolean,
    onDismissCalendarModeMenu: () -> Unit,
    onSelectCalendarMode: (HomeCalendarViewMode) -> Unit,
    menuContainerColor: Color,
    menuSelectionColor: Color,
    menuContentColor: Color,
    onCreate: () -> Unit,
    onSearch: () -> Unit,
    onImage: () -> Unit,
    onClearQuickMemos: () -> Unit,
) {
    if (isTodayPage && !isTwoPane) {
        Box {
            Box(
                modifier = Modifier.size(48.dp).combinedClickable(
                    onClick = onCycleCalendarMode,
                    onLongClick = onOpenCalendarModeMenu,
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.SwapHoriz,
                    contentDescription = calendarViewMode.nextContentDescription,
                    modifier = Modifier.size(28.dp),
                )
            }
            AppDropdownMenu(
                expanded = isCalendarViewMenuExpanded,
                onDismissRequest = onDismissCalendarModeMenu,
                containerColor = menuContainerColor,
                selectionColor = menuSelectionColor,
                contentColor = menuContentColor,
                items = (if (isTwoPane) HomeCalendarViewMode.wideModes else HomeCalendarViewMode.phoneModes).map { mode ->
                    AppMenuItem(
                        text = mode.menuLabel,
                        selected = mode == calendarViewMode,
                        icon = when (mode) {
                            HomeCalendarViewMode.TODAY -> Icons.Outlined.CalendarViewDay
                            HomeCalendarViewMode.WEEK -> Icons.Outlined.CalendarViewWeek
                            HomeCalendarViewMode.MONTH -> Icons.Outlined.CalendarViewMonth
                            HomeCalendarViewMode.AGENDA -> Icons.Outlined.ViewTimeline
                            HomeCalendarViewMode.COURSE -> Icons.Default.TableChart
                        },
                        onClick = { onSelectCalendarMode(mode) },
                    )
                },
            )
        }
    }

    if (isWideNavigation && (isNotePage || !isTwoPane)) {
        IconButton(onClick = onCreate) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = if (isNotePage) "新建随口记" else "新建日程",
                modifier = Modifier.size(topBarIconSize),
            )
        }
        Box {
            IconButton(onClick = { onWideActionMenuExpandedChange(true) }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "更多操作",
                    modifier = Modifier.size(topBarIconSize),
                )
            }
            AppDropdownMenu(
                expanded = isWideActionMenuExpanded,
                onDismissRequest = { onWideActionMenuExpandedChange(false) },
                containerColor = menuContainerColor,
                selectionColor = menuSelectionColor,
                contentColor = menuContentColor,
                items = buildList {
                    add(AppMenuItem("搜索", onSearch, Icons.Default.Search))
                    add(AppMenuItem("上传图片识别", onImage, Icons.Default.Image))
                    if (isNotePage && quickMemoCount > 0) {
                        add(AppMenuItem("清空随口记", onClearQuickMemos, Icons.Default.DeleteSweep, destructive = true))
                    }
                },
            )
        }
    } else if (isNotePage && quickMemoCount > 0) {
        IconButton(onClick = onClearQuickMemos) {
            Icon(
                imageVector = Icons.Default.DeleteSweep,
                contentDescription = "清空随口记",
                modifier = Modifier.size(topBarIconSize),
            )
        }
    }
}

@Composable
private fun HomeWideQuickActions(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSearch: () -> Unit,
    onImage: () -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AnimatedVisibility(visible = expanded) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ExtendedFloatingActionButton(
                    text = { Text("搜索") },
                    icon = { Icon(Icons.Default.Search, contentDescription = null) },
                    onClick = onSearch,
                )
                ExtendedFloatingActionButton(
                    text = { Text("上传图片") },
                    icon = { Icon(Icons.Default.Image, contentDescription = null) },
                    onClick = onImage,
                )
                ExtendedFloatingActionButton(
                    text = { Text("新建日程") },
                    icon = { Icon(Icons.Default.Event, contentDescription = null) },
                    onClick = onCreate,
                )
            }
        }
        FloatingActionButton(onClick = { onExpandedChange(!expanded) }) {
            Icon(
                imageVector = if (expanded) Icons.Default.Close else Icons.Default.Add,
                contentDescription = if (expanded) "收起快捷操作" else "展开快捷操作",
            )
        }
    }
}

@Composable
private fun HomeWideCalendarWorkspace(
    state: HomePageUiState,
    viewMode: HomeCalendarViewMode,
    onViewModeChange: (HomeCalendarViewMode) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onEditItem: (ScheduleDisplayItem) -> Unit,
    onOpenWeatherDetail: () -> Unit,
    scheduleContent: @Composable () -> Unit,
) {
    fun movePeriod(direction: Long) {
        val nextDate = when (viewMode) {
            HomeCalendarViewMode.MONTH -> state.selectedDate.plusMonths(direction)
            HomeCalendarViewMode.WEEK, HomeCalendarViewMode.COURSE -> state.selectedDate.plusWeeks(direction)
            HomeCalendarViewMode.TODAY, HomeCalendarViewMode.AGENDA -> state.selectedDate.plusDays(direction)
        }
        onSelectDate(nextDate)
    }

    DetailWorkspaceOverlay {
    Column(
        modifier = Modifier
            .fillMaxSize()
            // 安全留白由网格的滚动内容末尾承担，不缩短可见视口。
            .padding(top = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = calendarWorkspacePeriodText(viewMode, state.selectedDate),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            TextButton(onClick = { onSelectDate(state.today) }) {
                Text("今天")
            }
            IconButton(onClick = { movePeriod(-1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上一时段")
            }
            IconButton(onClick = { movePeriod(1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下一时段")
            }
        }
        HomeWideTimeModeSelector(
            courseEnabled = com.antgskds.calendarassistant.feature.schedule.domain.course.CourseFeaturePolicy.enabled(state.settings),
            selected = viewMode,
            onSelected = onViewModeChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        )
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(viewMode, state.selectedDate) {
                    var totalDrag = 0f
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            when {
                                totalDrag < -50f -> movePeriod(1)
                                totalDrag > 50f -> movePeriod(-1)
                            }
                            totalDrag = 0f
                        },
                        onDragCancel = { totalDrag = 0f },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            totalDrag += amount
                        },
                    )
                },
        ) {
            if (viewMode == HomeCalendarViewMode.COURSE) {
                scheduleContent()
            } else {
                HomeCalendarCard(
                    state = state,
                    viewMode = viewMode,
                    wide = true,
                    modifier = Modifier.fillMaxSize(),
                    onSelectDate = onSelectDate,
                    onEditItem = onEditItem,
                    onOpenWeatherDetail = onOpenWeatherDetail,
                )
            }
        }
    }    }

}

private fun calendarWorkspacePeriodText(
    viewMode: HomeCalendarViewMode,
    selectedDate: LocalDate,
): String {
    if (viewMode == HomeCalendarViewMode.MONTH) {
        return "${selectedDate.year}年${selectedDate.monthValue}月"
    }
    val weekStart = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val weekEnd = weekStart.plusDays(6)
    return when {
        weekStart.year != weekEnd.year -> {
            "${weekStart.year}年${weekStart.monthValue}月${weekStart.dayOfMonth}日－${weekEnd.year}年${weekEnd.monthValue}月${weekEnd.dayOfMonth}日"
        }
        weekStart.monthValue != weekEnd.monthValue -> {
            "${weekStart.year}年${weekStart.monthValue}月${weekStart.dayOfMonth}日－${weekEnd.monthValue}月${weekEnd.dayOfMonth}日"
        }
        else -> "${weekStart.year}年${weekStart.monthValue}月${weekStart.dayOfMonth}日－${weekEnd.dayOfMonth}日"
    }
}

@Composable
private fun HomeWideTimeModeSelector(
    courseEnabled: Boolean = true,
    selected: HomeCalendarViewMode,
    onSelected: (HomeCalendarViewMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppSegmentedControl(
        options = HomeCalendarViewMode.wideModes.filter { courseEnabled || it != HomeCalendarViewMode.COURSE },
        selectedOption = selected,
        onSelected = onSelected,
        label = { mode ->
            when (mode) {
                HomeCalendarViewMode.WEEK -> "周"
                HomeCalendarViewMode.MONTH -> "月"
                HomeCalendarViewMode.COURSE -> "课表"
                else -> mode.menuLabel
            }
        },
        modifier = modifier,
    )
}

internal enum class HomeCalendarViewMode {
    TODAY,
    WEEK,
    MONTH,
    AGENDA,
    COURSE;

    fun next(twoPane: Boolean): HomeCalendarViewMode {
        val modes = if (twoPane) wideModes else phoneModes
        return modes[(modes.indexOf(this).takeIf { it >= 0 } ?: 0).let { (it + 1) % modes.size }]
    }

    val nextContentDescription: String
        get() = when (this) {
            TODAY -> "切换到周视图"
            WEEK -> "切换到月视图"
            MONTH -> "切换到日程视图"
            AGENDA -> "返回日期视图"
            COURSE -> "切换到周视图"
        }

    val menuLabel: String
        get() = when (this) {
            TODAY -> "今日视图"
            WEEK -> "周视图"
            MONTH -> "月视图"
            AGENDA -> "日程视图"
            COURSE -> "课表视图"
    }

    companion object {
        val phoneModes = listOf(TODAY, WEEK, MONTH, AGENDA)
        val wideModes = listOf(WEEK, MONTH, COURSE)
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
                        .padding(horizontal = 24.dp)
                        .widthIn(max = 720.dp)
                        .fillMaxWidth()
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
private fun HomeCalendarCard(
    state: HomePageUiState,
    viewMode: HomeCalendarViewMode,
    wide: Boolean = false,
    modifier: Modifier = Modifier,
    onSelectDate: (LocalDate) -> Unit,
    onEditItem: (ScheduleDisplayItem) -> Unit = {},
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
        modifier = modifier
            .padding(horizontal = if (wide) 0.dp else 24.dp)
            .fillMaxWidth(),
    ) {
        val cardWidth = maxWidth
        val todayCardHeight = if (wide) minOf(cardWidth / 1.45f, 560.dp) else cardWidth / 0.95f
        val calendarHeaderHeight = if (wide) 56.dp else todayCardHeight * 0.2f
        val weekdayHeaderHeight = if (wide) 36.dp else 18.dp
        val monthRowHeight = (todayCardHeight - calendarHeaderHeight - weekdayHeaderHeight - 10.dp) / 6f
        val targetHeight = when (viewMode) {
            HomeCalendarViewMode.TODAY, HomeCalendarViewMode.AGENDA -> todayCardHeight
            HomeCalendarViewMode.WEEK, HomeCalendarViewMode.COURSE -> calendarHeaderHeight + 114.dp
            HomeCalendarViewMode.MONTH -> todayCardHeight
        }
        val animatedCardHeight by animateDpAsState(
            targetValue = targetHeight,
            animationSpec = tween(durationMillis = 420),
            label = "home_calendar_height"
        )
        val cardHeight = if (wide) maxHeight else animatedCardHeight

        AppCard(
            modifier = Modifier
                .fillMaxWidth()
                .height(cardHeight)
                .then(
                    if (!wide && hasAppBackground && !state.settings.appBackgroundMiuiBlurTestEnabled) {
                        Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, dateCardShape)
                    } else {
                        Modifier
                    }
                )
                .then(
                    if (wide) Modifier else Modifier.pointerInput(viewMode, state.selectedDate) {
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
                                        HomeCalendarViewMode.TODAY, HomeCalendarViewMode.AGENDA -> state.selectedDate.plusDays(direction)
                                        HomeCalendarViewMode.WEEK, HomeCalendarViewMode.COURSE -> state.selectedDate.plusWeeks(direction)
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
                            },
                        )
                    }
                ),
            shape = if (wide) RectangleShape else dateCardShape,
            elevation = CardDefaults.cardElevation(defaultElevation = if (wide || hasAppBackground) 0.dp else 6.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (wide) {
                    Color.Transparent
                } else if (hasAppBackground || MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
                    MaterialTheme.colorScheme.surfaceContainerLow
                } else {
                    MaterialTheme.colorScheme.surface
                },
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (!wide) {
                    HomeCalendarSelectionIndicator(
                        viewMode = viewMode,
                        selectedDate = state.selectedDate,
                        headerHeight = calendarHeaderHeight,
                        monthRowHeight = monthRowHeight,
                        selectionColor = topBarColor,
                    )
                }
                AnimatedContent(
                    targetState = viewMode,
                    modifier = Modifier.fillMaxSize().homeDateSelectionForeground(),
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
                        HomeCalendarViewMode.TODAY, HomeCalendarViewMode.AGENDA -> HomeTodayDateContent(
                            selectedDate = state.selectedDate,
                            today = state.today,
                            weatherData = state.weatherData,
                            hasAppBackground = hasAppBackground,
                            topContentColor = topContentColor,
                            onSelectDate = onSelectDate,
                            onOpenWeatherDetail = onOpenWeatherDetail,
                        )
                        HomeCalendarViewMode.WEEK, HomeCalendarViewMode.COURSE -> HomeWeekContent(
                            selectedDate = state.selectedDate,
                            today = state.today,
                            headerHeight = calendarHeaderHeight,
                            datesWithEvents = state.datesWithEvents,
                            selectionContentColor = if (wide) MaterialTheme.colorScheme.onPrimaryContainer else topContentColor,
                            grid = wide,
                            calendarItems = state.calendarItems,
                            onEditItem = onEditItem,
                            onSelectDate = onSelectDate,
                        )
                        HomeCalendarViewMode.MONTH -> HomeMonthContent(
                            selectedDate = state.selectedDate,
                            today = state.today,
                            headerHeight = calendarHeaderHeight,
                            weekdayHeaderHeight = weekdayHeaderHeight,
                            monthRowHeight = monthRowHeight,
                            datesWithEvents = state.datesWithEvents,
                            selectionContentColor = if (wide) MaterialTheme.colorScheme.onPrimaryContainer else topContentColor,
                            grid = wide,
                            calendarItems = state.calendarItems,
                            onEditItem = onEditItem,
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
            HomeCalendarViewMode.TODAY, HomeCalendarViewMode.AGENDA -> 0.dp
            HomeCalendarViewMode.WEEK, HomeCalendarViewMode.COURSE -> horizontalPadding + (cellWidth + horizontalSpacing) * weekdayIndex
            HomeCalendarViewMode.MONTH -> {
                horizontalPadding + (cellWidth + horizontalSpacing) * weekdayIndex + columnInset
            }
        }
        val targetY = when (viewMode) {
            HomeCalendarViewMode.TODAY, HomeCalendarViewMode.AGENDA -> 0.dp
            HomeCalendarViewMode.WEEK, HomeCalendarViewMode.COURSE -> headerHeight
            HomeCalendarViewMode.MONTH -> {
                headerHeight + 18.dp + monthRowHeight * monthRow + monthCellInset
            }
        }
        val targetWidth = when (viewMode) {
            HomeCalendarViewMode.TODAY, HomeCalendarViewMode.AGENDA -> maxWidth
            HomeCalendarViewMode.WEEK, HomeCalendarViewMode.COURSE -> cellWidth
            HomeCalendarViewMode.MONTH -> monthCellSize
        }
        val targetHeight = when (viewMode) {
            HomeCalendarViewMode.TODAY, HomeCalendarViewMode.AGENDA -> headerHeight
            HomeCalendarViewMode.WEEK, HomeCalendarViewMode.COURSE -> 104.dp
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
                .homeDateSelectionBackground(radius, color)
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
    grid: Boolean,
    calendarItems: List<ScheduleDisplayItem>,
    onEditItem: (ScheduleDisplayItem) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
) {
    val weekStart = remember(selectedDate) {
        selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }
    if (grid) {
        HomeWeekTimeline(
            weekStart = weekStart,
            selectedDate = selectedDate,
            today = today,
            calendarItems = calendarItems,
            onSelectDate = onSelectDate,
            onEditItem = onEditItem,
        )
        return
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

internal data class WeekTimelineBlock(
    val item: ScheduleDisplayItem,
    val dayIndex: Int,
    val startMinute: Int,
    val endMinute: Int,
    val column: Int,
    val columnCount: Int,
)

private data class WeekTimelineSegment(
    val item: ScheduleDisplayItem,
    val dayIndex: Int,
    val startMinute: Int,
    val endMinute: Int,
)

internal fun buildWeekTimelineBlocks(
    items: List<ScheduleDisplayItem>,
    weekStart: LocalDate,
): List<WeekTimelineBlock> {
    val weekEnd = weekStart.plusDays(6)
    val segments = items.asSequence()
        .filter { !it.isAllDay && it.startDate == it.endDate && it.startDate in weekStart..weekEnd }
        .distinctBy { it.stableKey }
        .map { item ->
            val start = item.startLocalTime.hour * 60 + item.startLocalTime.minute
            val rawEnd = item.endLocalTime.hour * 60 + item.endLocalTime.minute
            WeekTimelineSegment(
                item = item,
                dayIndex = ChronoUnit.DAYS.between(weekStart, item.startDate).toInt(),
                startMinute = start,
            endMinute = rawEnd.coerceAtLeast(start + 1).coerceAtMost(24 * 60),
            )
        }
        .groupBy { it.dayIndex }

    return segments.flatMap { (_, daySegments) ->
        val sorted = daySegments.sortedWith(compareBy<WeekTimelineSegment> { it.startMinute }.thenBy { it.endMinute })
        val result = mutableListOf<WeekTimelineBlock>()
        val group = mutableListOf<WeekTimelineSegment>()
        var groupEnd = -1

        fun flushGroup() {
            if (group.isEmpty()) return
            val columnEnds = mutableListOf<Int>()
            val assigned = group.map { segment ->
                val reusable = columnEnds.indexOfFirst { it <= segment.startMinute }
                val column = if (reusable >= 0) reusable else columnEnds.size.also { columnEnds += segment.endMinute }
                columnEnds[column] = segment.endMinute
                segment to column
            }
            val columnCount = columnEnds.size.coerceAtLeast(1)
            assigned.forEach { (segment, column) ->
                result += WeekTimelineBlock(
                    item = segment.item,
                    dayIndex = segment.dayIndex,
                    startMinute = segment.startMinute,
                    endMinute = segment.endMinute,
                    column = column,
                    columnCount = columnCount,
                )
            }
            group.clear()
            groupEnd = -1
        }

        sorted.forEach { segment ->
            if (group.isNotEmpty() && segment.startMinute >= groupEnd) flushGroup()
            group += segment
            groupEnd = maxOf(groupEnd, segment.endMinute)
        }
        flushGroup()
        result
    }
}

@Composable
private fun HomeWeekTimeline(
    weekStart: LocalDate,
    selectedDate: LocalDate,
    today: LocalDate,
    calendarItems: List<ScheduleDisplayItem>,
    onSelectDate: (LocalDate) -> Unit,
    onEditItem: (ScheduleDisplayItem) -> Unit,
) {
    val weekDates = remember(weekStart) { List(7) { weekStart.plusDays(it.toLong()) } }
    val weekEnd = weekDates.last()
    val blocks = remember(calendarItems, weekStart) { buildWeekTimelineBlocks(calendarItems, weekStart) }
    val allDayByDate = remember(calendarItems, weekStart) {
        weekDates.associateWith { date ->
            calendarItems.distinctBy { it.stableKey }.filter { item ->
                (item.isAllDay || item.startDate != item.endDate) && itemOverlapsCalendarDate(item, date)
            }
        }
    }
    val hasAllDay = allDayByDate.values.any { it.isNotEmpty() }
    val hourHeight = 64.dp
    val timeAxisWidth = 58.dp
    val density = LocalDensity.current
    val initialScroll = with(density) { (hourHeight * 7).roundToPx() }
    val scrollState = rememberScrollState(initial = initialScroll)
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val now = LocalTime.now()

    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 12.dp).padding(top = 4.dp)) {
        val axisPx = with(density) { timeAxisWidth.roundToPx() }
        val hourPx = with(density) { hourHeight.roundToPx() }
        val daysPx = (constraints.maxWidth - axisPx).coerceAtLeast(0)
        val boundaries = List(8) { axisPx + calendarGridBoundary(daysPx, it, 7) }
        fun dayWidth(index: Int) = with(density) { (boundaries[index + 1] - boundaries[index]).toDp() }
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(62.dp)) {
                Spacer(Modifier.width(timeAxisWidth))
                weekDates.forEachIndexed { dayIndex, date ->
                    val selected = date == selectedDate
                    Column(
                        modifier = Modifier
                            .width(dayWidth(dayIndex))
                            .fillMaxHeight()
                            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .border(0.5.dp, lineColor, RectangleShape)
                            .clickable { onSelectDate(date) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.CHINESE),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            date.dayOfMonth.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (date == today || selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (date == today) MaterialTheme.colorScheme.primary else if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            if (hasAllDay) {
                Row(Modifier.fillMaxWidth().height(44.dp)) {
                    Box(Modifier.width(timeAxisWidth).fillMaxHeight(), contentAlignment = Alignment.CenterEnd) {
                        Text("全天", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    weekDates.forEachIndexed { dayIndex, date ->
                        val items = allDayByDate[date].orEmpty()
                        Box(
                            Modifier.width(dayWidth(dayIndex)).fillMaxHeight()
                                .border(0.5.dp, lineColor, RectangleShape),
                        ) {
                            items.firstOrNull()?.let { item ->
                                Text(
                                    text = if (items.size > 1) "${item.title} +${items.size - 1}" else item.title,
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
                                        .background(calendarEventColor(item).copy(alpha = 0.18f))
                                        .clickable { onSelectDate(date); onEditItem(item) }
                                        .padding(horizontal = 5.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
            HorizontalDivider(color = lineColor)
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val timelineHeight = with(density) { (hourPx * 24).toDp() }
                Box(
                    modifier = Modifier.fillMaxWidth().verticalScroll(scrollState)
                        .padding(bottom = LocalAppPageBottomPadding.current).height(timelineHeight),
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        for (hour in 0..24) {
                            val y = (hourPx * hour).toFloat()
                            drawLine(lineColor, Offset(axisPx.toFloat(), y), Offset(size.width, y), strokeWidth = 1f)
                            if (hour < 24) {
                                val halfHourY = calendarMinuteOffset(hourPx, hour * 60 + 30).toFloat()
                                drawLine(
                                    lineColor.copy(alpha = 0.35f),
                                    Offset(axisPx.toFloat(), halfHourY),
                                    Offset(size.width, halfHourY),
                                    strokeWidth = 1f,
                                )
                            }
                        }
                        for (day in 0..7) {
                            val x = boundaries[day].toFloat()
                            drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                        }
                    }
                    for (hour in 0..23) {
                        Text(
                            text = "%02d:00".format(hour),
                            modifier = Modifier.width(timeAxisWidth - 6.dp).offset(y = with(density) { (hourPx * hour).toDp() } - 8.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    blocks.forEach { block ->
                        val dayStart = boundaries[block.dayIndex]
                        val daySize = boundaries[block.dayIndex + 1] - dayStart
                        val gapPx = with(density) { 2.dp.roundToPx() }
                        val left = dayStart + calendarGridBoundary(daySize, block.column, block.columnCount) +
                            if (block.column > 0) gapPx / 2 else 0
                        val right = dayStart + calendarGridBoundary(daySize, block.column + 1, block.columnCount) -
                            if (block.column < block.columnCount - 1) gapPx - gapPx / 2 else 0
                        val top = calendarMinuteOffset(hourPx, block.startMinute)
                        val bottom = calendarMinuteOffset(hourPx, block.endMinute)
                        val height = with(density) { (bottom - top).coerceAtLeast(1).toDp() }
                        val eventColor = calendarEventColor(block.item)
                        Column(
                            modifier = Modifier
                                .offset { IntOffset(left, top) }
                                .width(with(density) { (right - left).coerceAtLeast(1).toDp() })
                                .height(height)
                                .clip(RoundedCornerShape(6.dp))
                                .background(eventColor.copy(alpha = if (block.item.isCompleted) 0.1f else 0.2f))
                                .border(1.dp, eventColor.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                                .semantics { contentDescription = "${block.item.title}，${block.item.startTime}至${block.item.endTime}" }
                                .clickable {
                                    onSelectDate(weekStart.plusDays(block.dayIndex.toLong()))
                                    onEditItem(block.item)
                                }
                                .padding(horizontal = 5.dp, vertical = if (height >= 24.dp) 3.dp else 0.dp),
                        ) {
                            if (height >= 16.dp) Text(
                                block.item.title,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = if (height >= 42.dp) 2 else 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                            if (height >= 42.dp) {
                                Text(
                                    "${block.item.startTime}–${block.item.endTime}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                    if (today in weekStart..weekEnd) {
                        val todayIndex = ChronoUnit.DAYS.between(weekStart, today).toInt()
                        val y = with(density) { calendarMinuteOffset(hourPx, now.hour * 60 + now.minute).toDp() }
                        val x = with(density) { boundaries[todayIndex].toDp() }
                        Box(
                            Modifier.offset(x = x, y = y)
                                .width(dayWidth(todayIndex)).height(2.dp).background(MaterialTheme.colorScheme.error),
                        )
                        Box(
                            Modifier.offset(x = x - 3.dp, y = y - 3.dp)
                                .size(8.dp).background(MaterialTheme.colorScheme.error, CircleShape),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeMonthWorkspace(
    month: YearMonth,
    selectedDate: LocalDate,
    today: LocalDate,
    calendarItems: List<ScheduleDisplayItem>,
    onSelectDate: (LocalDate) -> Unit,
    onEditItem: (ScheduleDisplayItem) -> Unit,
) {
    val weekdays = remember { listOf("一", "二", "三", "四", "五", "六", "日") }
    val gridStart = remember(month) { month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
    val dates = remember(gridStart) { List(42) { gridStart.plusDays(it.toLong()) } }
    val itemsByDate = remember(calendarItems, gridStart) {
        dates.associateWith { date ->
            calendarItems.distinctBy { it.stableKey }.filter { itemOverlapsCalendarDate(it, date) }
                .sortedWith(compareBy<ScheduleDisplayItem> { !it.isAllDay }.thenBy { it.startTS })
        }
    }
    val lineColor = MaterialTheme.colorScheme.outlineVariant

    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 12.dp).padding(top = 4.dp)) {
        val density = LocalDensity.current
        val boundaries = List(8) { calendarGridBoundary(constraints.maxWidth, it, 7) }
        fun dayWidth(index: Int) = with(density) { (boundaries[index + 1] - boundaries[index]).toDp() }
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(34.dp)) {
                weekdays.forEachIndexed { index, weekday ->
                    Box(
                        Modifier.width(dayWidth(index)).fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .border(0.5.dp, lineColor, RectangleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(weekday, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val gridHeight = maxHeight
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(bottom = LocalAppPageBottomPadding.current).height(gridHeight),
                ) {
                    dates.chunked(7).forEach { week ->
                        Row(Modifier.fillMaxWidth().weight(1f)) {
                            week.forEachIndexed { index, date ->
                                val inMonth = YearMonth.from(date) == month
                                val selected = date == selectedDate
                                val items = itemsByDate[date].orEmpty()
                                Column(
                                    modifier = Modifier.width(dayWidth(index)).fillMaxHeight()
                                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                        .border(0.5.dp, lineColor, RectangleShape)
                                        .clickable { onSelectDate(date) },
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            date.dayOfMonth.toString(),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = if (date == today || selected) FontWeight.Bold else FontWeight.Medium,
                                            color = when {
                                                date == today -> MaterialTheme.colorScheme.primary
                                                selected -> MaterialTheme.colorScheme.onPrimaryContainer
                                                !inMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                                else -> MaterialTheme.colorScheme.onSurface
                                            },
                                        )
                                        Spacer(Modifier.weight(1f))
                                        Text(
                                            LunarCalendarUtils.getLunarDate(date).takeLast(2),
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (inMonth) 0.7f else 0.3f),
                                        )
                                    }
                                    items.take(3).forEach { item ->
                                        val eventColor = calendarEventColor(item)
                                        Text(
                                            text = if (item.isAllDay) item.title else "${item.startTime} ${item.title}",
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 1.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(eventColor.copy(alpha = if (item.isCompleted) 0.08f else 0.16f))
                                                .clickable { onSelectDate(date); onEditItem(item) }
                                                .padding(horizontal = 4.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                            color = if (inMonth) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        )
                                    }
                                    if (items.size > 3) {
                                        Text(
                                            "还有 ${items.size - 3} 项",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun calendarEventColor(item: ScheduleDisplayItem): Color {
    return if (item.color != 0) item.composeColor else MaterialTheme.colorScheme.primary
}

private fun itemOverlapsCalendarDate(item: ScheduleDisplayItem, date: LocalDate): Boolean {
    if (date !in item.startDate..item.endDate) return false
    return item.startDate == item.endDate || date != item.endDate || item.endLocalTime != LocalTime.MIDNIGHT
}

@Composable
private fun HomeMonthContent(
    selectedDate: LocalDate,
    today: LocalDate,
    headerHeight: Dp,
    weekdayHeaderHeight: Dp,
    monthRowHeight: Dp,
    datesWithEvents: Set<LocalDate>,
    selectionContentColor: Color,
    grid: Boolean,
    calendarItems: List<ScheduleDisplayItem>,
    onEditItem: (ScheduleDisplayItem) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
) {
    val month = remember(selectedDate) { YearMonth.from(selectedDate) }
    val weekdays = remember { listOf("一", "二", "三", "四", "五", "六", "日") }
    if (grid) {
        HomeMonthWorkspace(
            month = month,
            selectedDate = selectedDate,
            today = today,
            calendarItems = calendarItems,
            onSelectDate = onSelectDate,
            onEditItem = onEditItem,
        )
        return
    }

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
                    .height(weekdayHeaderHeight),
                verticalAlignment = Alignment.CenterVertically
            ) {
                weekdays.forEach { weekday ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = weekday,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
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
        Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), shape)
    } else Modifier
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
