package com.antgskds.calendarassistant.feature.home.ui.render

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.app.ui.navigation.SettingsDestination
import com.antgskds.calendarassistant.feature.home.domain.HomeEntryKey
import com.antgskds.calendarassistant.feature.home.domain.sanitizeHomeBottomItems
import com.antgskds.calendarassistant.feature.home.domain.visibleHomeBottomItems
import com.antgskds.calendarassistant.feature.home.ui.contract.HomePageUiAction
import com.antgskds.calendarassistant.feature.home.ui.contract.HomePageUiState
import com.antgskds.calendarassistant.feature.recognition.application.ai.AnalysisResult
import com.antgskds.calendarassistant.feature.recognition.application.ai.isRecognitionConfigReady
import com.antgskds.calendarassistant.feature.recognition.application.ai.recognitionConfigMissingMessage
import com.antgskds.calendarassistant.feature.recognition.ui.feedback.RecognitionFeedbackSource
import com.antgskds.calendarassistant.feature.schedule.presentation.model.ScheduleDisplayItem
import com.antgskds.calendarassistant.feature.schedule.ui.render.HyperEventItem
import com.antgskds.calendarassistant.feature.weather.domain.WeatherIconMapper
import com.antgskds.calendarassistant.shared.ui.interaction.rememberAppHaptics
import com.antgskds.calendarassistant.shared.util.ImageImportUtils
import com.antgskds.calendarassistant.shared.util.LunarCalendarUtils
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.CardDefaults as MiuixCardDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarDisplayMode
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun HomePageScreen(
    state: HomePageUiState,
    onAction: (HomePageUiAction) -> Unit,
    scheduleContent: @Composable () -> Unit,
    allEventsContent: @Composable (String, Dp) -> Unit,
    noteListContent: @Composable (String, Dp) -> Unit,
    quickMemoContent: @Composable (String, Dp) -> Unit,
    currentPageKey: String,
    uiSize: Int,
    pickupTimestamp: Long,
    openCourseRequestId: Long,
    courseFeatureEnabled: Boolean,
    isActionExpanded: Boolean,
    onActionExpandedChange: (Boolean) -> Unit,
    searchRequestId: Int,
    imageRequestId: Int,
    isSidebarOpen: Boolean,
    onPageChange: (String) -> Unit,
    onAddEventClick: () -> Unit,
    onEditItem: (ScheduleDisplayItem) -> Unit,
    onRequestDeleteItem: (ScheduleDisplayItem) -> Unit,
    onCreateNote: () -> Unit,
    onRequestClearQuickMemos: () -> Unit,
    quickMemoCount: Int,
    onScheduleExpandedChange: (Boolean) -> Unit,
    onScheduleProgressChange: (Float) -> Unit,
    onScheduleOffsetChange: (Float) -> Unit,
    onOpenWeatherDetail: () -> Unit,
    onNavigateToSettings: (SettingsDestination) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var todayQuery by rememberSaveable { mutableStateOf("") }
    var allQuery by rememberSaveable { mutableStateOf("") }
    var memoQuery by rememberSaveable { mutableStateOf("") }
    var settingsQuery by rememberSaveable { mutableStateOf("") }
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var imageImportJob by remember { mutableStateOf<Job?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    fun showMessage(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null || imageImportJob != null) return@rememberLauncherForActivityResult
        imageImportJob = scope.launch {
            try {
                if (!state.settings.isRecognitionConfigReady()) {
                    showMessage(state.settings.recognitionConfigMissingMessage())
                    return@launch
                }
                val imageFile = ImageImportUtils.createImportedImageFile(context)
                val copied = withContext(Dispatchers.IO) {
                    ImageImportUtils.copyUriToFile(context, uri, imageFile)
                }
                if (!copied) {
                    showMessage("图片读取失败")
                    return@launch
                }
                val bitmap = withContext(Dispatchers.IO) {
                    ImageImportUtils.decodeSampledBitmapFromFile(imageFile)
                } ?: run {
                    showMessage("图片解码失败")
                    return@launch
                }
                val result = withContext(Dispatchers.IO) {
                    (context.applicationContext as App).recognitionCenter.analyzeImage(
                        bitmap = bitmap,
                        settings = state.settings,
                        context = context.applicationContext,
                        sourceType = RecognitionFeedbackSource.HOME_SOURCE_TYPE,
                        sourceId = RecognitionFeedbackSource.HOME_SOURCE_ID,
                        sourceImagePath = imageFile.absolutePath,
                        ingestRequested = true,
                    )
                }
                bitmap.recycle()
                if (result is AnalysisResult.Success) {
                    showMessage("识别完成，正在保存")
                }
            } catch (_: CancellationException) {
            } catch (error: Exception) {
                showMessage("分析失败：${error.message ?: "unknown"}")
            } finally {
                imageImportJob = null
            }
        }
    }

    val navItems = remember(state.settings.homeBottomItems, state.settings.voiceInputEnabled) {
        editionHomeEntries(
            visibleHomeBottomItems(
                sanitizeHomeBottomItems(state.settings.homeBottomItems),
                quickMemoEnabled = state.settings.voiceInputEnabled,
            ),
        )
    }

    fun queryForPage(): String = when (currentPageKey) {
        HomeEntryKey.TODAY -> todayQuery
        HomeEntryKey.ALL -> allQuery
        HomeEntryKey.NOTE -> memoQuery
        else -> settingsQuery
    }

    fun updateQuery(value: String) {
        when (currentPageKey) {
            HomeEntryKey.TODAY -> todayQuery = value
            HomeEntryKey.ALL -> allQuery = value
            HomeEntryKey.NOTE -> memoQuery = value
            else -> settingsQuery = value
        }
    }

    LaunchedEffect(currentPageKey) {
        searchVisible = false
    }
    LaunchedEffect(searchRequestId) {
        if (searchRequestId > 0) searchVisible = true
    }
    LaunchedEffect(imageRequestId) {
        if (imageRequestId > 0 && imageImportJob == null) imagePicker.launch("image/*")
    }

    BackHandler(enabled = currentPageKey != HomeEntryKey.TODAY || searchVisible) {
        when {
            searchVisible -> {
                searchVisible = false
                updateQuery("")
            }
            else -> onPageChange(HomeEntryKey.TODAY)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0),
            topBar = {
                HyperTopBar(
                    title = when (currentPageKey) {
                        HomeEntryKey.TODAY -> "今日"
                        HomeEntryKey.ALL -> "全部"
                        HomeEntryKey.NOTE -> "随口记"
                        else -> "设置"
                    },
                    settingsPage = currentPageKey == HomeEntryKey.SETTINGS,
                    onAdd = onAddEventClick,
                    onSearch = {
                        searchVisible = true
                    },
                    onImageRecognition = { imagePicker.launch("image/*") },
                    onUpdate = { onNavigateToSettings(SettingsDestination.AppUpdate) },
                    onAbout = { onNavigateToSettings(SettingsDestination.About) },
                )
            },
            bottomBar = {
                HyperNavigationBar(
                    items = navItems,
                    selected = currentPageKey,
                    today = state.today.dayOfMonth,
                    onSelect = onPageChange,
                )
            },
            snackbarHost = {
                SnackbarHost(state = snackbarHostState)
            },
        ) { innerPadding ->
            val searchAreaHeight = if (searchVisible) 62.dp else 0.dp
            val contentInsets = HyperHomeContentInsets(
                top = innerPadding.calculateTopPadding() + searchAreaHeight,
                bottom = innerPadding.calculateBottomPadding(),
            )
            Box(modifier = Modifier.fillMaxSize()) {
                CompositionLocalProvider(LocalHyperHomeContentInsets provides contentInsets) {
                    AnimatedContent(
                        targetState = currentPageKey,
                        modifier = Modifier.fillMaxSize(),
                        transitionSpec = {
                            fadeIn(tween(180)) togetherWith fadeOut(tween(140)) using SizeTransform(clip = false)
                        },
                        label = "hyperos_home_page",
                    ) { page ->
                        when (page) {
                            HomeEntryKey.TODAY -> HyperTodayPage(
                                state = state,
                                query = todayQuery,
                                uiSize = uiSize,
                                onAction = onAction,
                                onEditItem = onEditItem,
                                onRequestDeleteItem = onRequestDeleteItem,
                                onOpenWeatherDetail = onOpenWeatherDetail,
                            )
                            HomeEntryKey.ALL -> allEventsContent(allQuery, 0.dp)
                            HomeEntryKey.NOTE -> quickMemoContent(memoQuery, 0.dp)
                            else -> HyperSettingsOverview(
                                query = settingsQuery,
                                onNavigate = onNavigateToSettings,
                            )
                        }
                    }
                }
                if (searchVisible) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = innerPadding.calculateTopPadding()),
                    ) {
                        HyperSearchField(
                            query = queryForPage(),
                            label = when (currentPageKey) {
                                HomeEntryKey.TODAY, HomeEntryKey.ALL -> "搜索日程"
                                HomeEntryKey.NOTE -> "搜索随口记"
                                else -> "搜索设置"
                            },
                            onQueryChange = ::updateQuery,
                            onClose = {
                                searchVisible = false
                                updateQuery("")
                            },
                        )
                    }
                }
            }
        }

    }
}

@Composable
private fun HyperTopBar(
    title: String,
    settingsPage: Boolean,
    onAdd: () -> Unit,
    onSearch: () -> Unit,
    onImageRecognition: () -> Unit,
    onUpdate: () -> Unit,
    onAbout: () -> Unit,
) {
    SmallTopAppBar(
        title = "",
        color = MiuixTheme.colorScheme.surface,
        navigationIconPadding = 28.dp,
        navigationIcon = {
            MiuixText(
                text = title,
                color = MiuixTheme.colorScheme.onSurface,
                fontSize = MiuixTheme.textStyles.title3.fontSize,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        },
        actions = {
            if (settingsPage) {
                IconButton(onClick = onSearch) {
                    Icon(MiuixIcons.Normal.Search, "搜索", modifier = Modifier.size(24.dp))
                }
            } else {
                IconButton(onClick = onAdd) {
                    Icon(MiuixIcons.Normal.Add, "新建", modifier = Modifier.size(24.dp))
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            val menuItems = if (settingsPage) {
                listOf(
                    DropdownItem(
                        text = "软件更新",
                        icon = { modifier -> Icon(MiuixIcons.Normal.Update, null, modifier = modifier) },
                        onClick = onUpdate,
                    ),
                    DropdownItem(
                        text = "关于",
                        icon = { modifier -> Icon(MiuixIcons.Normal.Info, null, modifier = modifier) },
                        onClick = onAbout,
                    ),
                )
            } else {
                listOf(
                    DropdownItem(
                        text = "搜索",
                        icon = { modifier -> Icon(MiuixIcons.Normal.Search, null, modifier = modifier) },
                        onClick = onSearch,
                    ),
                    DropdownItem(
                        text = "图片识别",
                        icon = { modifier -> Icon(MiuixIcons.Normal.Image, null, modifier = modifier) },
                        onClick = onImageRecognition,
                    ),
                )
            }
            MiuixMenuButton(items = menuItems)
        },
    )
}

@Composable
private fun MiuixMenuButton(items: List<DropdownItem>) {
    val entry = remember(items) { DropdownEntry(items = items) }
    OverlayIconDropdownMenu(
        entry = entry,
        backgroundColor = Color.Transparent,
    ) {
        Icon(
            imageVector = MiuixIcons.Normal.More,
            contentDescription = "更多",
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun HyperSearchField(
    query: String,
    label: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    SearchBar(
        modifier = Modifier
            .fillMaxWidth()
            .background(MiuixTheme.colorScheme.surface),
        inputField = {
            InputField(
                query = query,
                onQueryChange = onQueryChange,
                onSearch = {},
                expanded = true,
                onExpandedChange = {},
                label = label,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        expanded = true,
        onExpandedChange = { if (!it) onClose() },
        outsideEndAction = {
            MiuixTextButton(
                text = "取消",
                onClick = onClose,
                minWidth = 56.dp,
            )
        },
        content = {},
    )
}

@Composable
private fun HyperNavigationBar(
    items: List<String>,
    selected: String,
    today: Int,
    onSelect: (String) -> Unit,
) {
    NavigationBar(
        color = MiuixTheme.colorScheme.surface,
        showDivider = false,
        mode = NavigationBarDisplayMode.IconAndText,
    ) {
        items.forEach { key ->
            val isSelected = selected == key
            val (icon, label) = when (key) {
                HomeEntryKey.TODAY -> thDateIcon(today) to "今日"
                HomeEntryKey.ALL -> MiuixIcons.Normal.Months to "全部"
                HomeEntryKey.NOTE -> MiuixIcons.Normal.Community to "随口记"
                else -> MiuixIcons.Normal.Settings to "设置"
            }
            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelect(key) },
                icon = icon,
                label = label,
            )
        }
    }
}

private fun thDateIcon(day: Int): ImageVector = when (day.coerceIn(1, 31)) {
    1 -> MiuixIcons.Normal.Th1
    2 -> MiuixIcons.Normal.Th2
    3 -> MiuixIcons.Normal.Th3
    4 -> MiuixIcons.Normal.Th4
    5 -> MiuixIcons.Normal.Th5
    6 -> MiuixIcons.Normal.Th6
    7 -> MiuixIcons.Normal.Th7
    8 -> MiuixIcons.Normal.Th8
    9 -> MiuixIcons.Normal.Th9
    10 -> MiuixIcons.Normal.Th10
    11 -> MiuixIcons.Normal.Th11
    12 -> MiuixIcons.Normal.Th12
    13 -> MiuixIcons.Normal.Th13
    14 -> MiuixIcons.Normal.Th14
    15 -> MiuixIcons.Normal.Th15
    16 -> MiuixIcons.Normal.Th16
    17 -> MiuixIcons.Normal.Th17
    18 -> MiuixIcons.Normal.Th18
    19 -> MiuixIcons.Normal.Th19
    20 -> MiuixIcons.Normal.Th20
    21 -> MiuixIcons.Normal.Th21
    22 -> MiuixIcons.Normal.Th22
    23 -> MiuixIcons.Normal.Th23
    24 -> MiuixIcons.Normal.Th24
    25 -> MiuixIcons.Normal.Th25
    26 -> MiuixIcons.Normal.Th26
    27 -> MiuixIcons.Normal.Th27
    28 -> MiuixIcons.Normal.Th28
    29 -> MiuixIcons.Normal.Th29
    30 -> MiuixIcons.Normal.Th30
    else -> MiuixIcons.Normal.Th31
}

@Composable
private fun HyperTodayPage(
    state: HomePageUiState,
    query: String,
    uiSize: Int,
    onAction: (HomePageUiAction) -> Unit,
    onEditItem: (ScheduleDisplayItem) -> Unit,
    onRequestDeleteItem: (ScheduleDisplayItem) -> Unit,
    onOpenWeatherDetail: () -> Unit,
) {
    val contentInsets = LocalHyperHomeContentInsets.current
    val todayEvents = remember(state.currentDateEvents, query) {
        state.currentDateEvents.filterByQuery(query)
    }
    val tomorrowEvents = remember(state.tomorrowEvents, query) {
        state.tomorrowEvents.filterByQuery(query)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentInsets.top + 12.dp,
            bottom = contentInsets.bottom + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            HyperDateCard(
                state = state,
                onSelectDate = { onAction(HomePageUiAction.SelectDate(it)) },
                onOpenWeatherDetail = onOpenWeatherDetail,
            )
        }
        item {
            HyperSectionTitle(
                if (state.selectedDate == state.today) "今日安排"
                else state.selectedDate.format(DateTimeFormatter.ofPattern("M月d日安排")),
            )
        }
        if (todayEvents.isEmpty()) {
            item { HyperEmptyText(if (query.isBlank()) "今日暂无日程" else "未找到相关日程") }
        } else {
            items(todayEvents, key = { it.stableKey }) { item ->
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    HyperEventItem(
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
            }
        }
        if (state.settings.showTomorrowEvents && state.selectedDate == state.today && tomorrowEvents.isNotEmpty()) {
            item { HyperSectionTitle("明日安排") }
            items(tomorrowEvents, key = { "tomorrow_${it.stableKey}" }) { item ->
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    HyperEventItem(
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
            }
        }
    }
}

@Composable
private fun HyperDateCard(
    state: HomePageUiState,
    onSelectDate: (java.time.LocalDate) -> Unit,
    onOpenWeatherDetail: () -> Unit,
) {
    val haptics = rememberAppHaptics(state.settings.hapticFeedbackEnabled)
    val isToday = state.selectedDate == state.today
    val weatherData = state.weatherData
    val topBarColor = if (isToday) {
        MiuixTheme.colorScheme.primary
    } else {
        MiuixTheme.colorScheme.surfaceVariant
    }
    val topContentColor = if (isToday) {
        MiuixTheme.colorScheme.onPrimary
    } else {
        MiuixTheme.colorScheme.onSurface
    }

    MiuixCard(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
            .aspectRatio(0.95f)
            .pointerInput(state.selectedDate) {
                var totalDrag = 0f
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (totalDrag < -50) {
                            onSelectDate(state.selectedDate.plusDays(1))
                        } else if (totalDrag > 50) {
                            onSelectDate(state.selectedDate.minusDays(1))
                        }
                        totalDrag = 0f
                    },
                    onDragCancel = { totalDrag = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        totalDrag += dragAmount
                    },
                )
            },
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(0.dp),
        colors = MiuixCardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surfaceContainer,
            contentColor = MiuixTheme.colorScheme.onSurfaceContainer,
        ),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(0.2f)
                    .fillMaxWidth()
                    .background(topBarColor)
                    .clickable { onSelectDate(state.today) },
            ) {
                if (weatherData != null) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .clip(RoundedCornerShape(50))
                            .clickable {
                                haptics.click()
                                onOpenWeatherDetail()
                            }
                            .padding(horizontal = 22.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(WeatherIconMapper.iconRes(weatherData)),
                            contentDescription = weatherData.text.ifBlank { "天气" },
                            modifier = Modifier.size(30.dp),
                            tint = topContentColor,
                        )
                        Spacer(Modifier.width(10.dp))
                        MiuixText(
                            text = buildString {
                                append(weatherData.temperature.ifBlank { "--" })
                                append("°C")
                                if (weatherData.text.isNotBlank()) {
                                    append(" · ")
                                    append(weatherData.text)
                                }
                            },
                            style = MiuixTheme.textStyles.title3,
                            fontWeight = FontWeight.ExtraBold,
                            color = topContentColor,
                            maxLines = 1,
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .weight(0.8f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp),
                ) {
                    MiuixText(
                        state.selectedDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.CHINESE),
                        style = MiuixTheme.textStyles.title3,
                        color = MiuixTheme.colorScheme.onSurfaceContainer,
                    )
                    Spacer(Modifier.width(8.dp))
                    MiuixText(
                        LunarCalendarUtils.getLunarDate(state.selectedDate),
                        style = MiuixTheme.textStyles.title3,
                        color = MiuixTheme.colorScheme.onSurfaceContainer,
                    )
                }
                MiuixText(
                    text = state.selectedDate.dayOfMonth.toString(),
                    fontSize = 140.sp,
                    fontWeight = FontWeight.Black,
                    lineHeight = 140.sp,
                    color = MiuixTheme.colorScheme.onSurfaceContainer,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        haptics.selection()
                        onSelectDate(state.today)
                    },
                )
                MiuixText(
                    "${state.selectedDate.year}年${state.selectedDate.monthValue}月",
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                )
            }
        }
    }
}

@Composable
private fun HyperSectionTitle(title: String) {
    MiuixText(
        text = title,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        color = MiuixTheme.colorScheme.primary,
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun HyperEmptyText(text: String) {
    MiuixText(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp),
        textAlign = TextAlign.Center,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

private fun List<ScheduleDisplayItem>.filterByQuery(query: String): List<ScheduleDisplayItem> =
    if (query.isBlank()) this else filter { item ->
        item.title.contains(query, ignoreCase = true) ||
            item.description.contains(query, ignoreCase = true) ||
            item.location.contains(query, ignoreCase = true)
    }

private data class HyperSettingsItem(
    val title: String,
    val summary: String,
    val destination: SettingsDestination,
    val icon: ImageVector,
)

@Composable
private fun HyperSettingsOverview(
    query: String,
    onNavigate: (SettingsDestination) -> Unit,
) {
    val contentInsets = LocalHyperHomeContentInsets.current
    val groups = remember {
        listOf(
            "日程与课程" to listOf(
                HyperSettingsItem("课程管理", "管理课程和课表内容", SettingsDestination.CourseManage, MiuixIcons.Normal.All),
                HyperSettingsItem("作息表管理", "设置课程时间段", SettingsDestination.TimeTableManage, MiuixIcons.Normal.Timer),
                HyperSettingsItem("学期配置", "设置学期起止和周数", SettingsDestination.SemesterConfig, MiuixIcons.Normal.Months),
            ),
            "功能设置" to listOf(
                HyperSettingsItem("偏好设置", "通知、识别和操作选项", SettingsDestination.Preference, MiuixIcons.Normal.Tune),
                HyperSettingsItem("模型配置", "配置识别模型和 API", SettingsDestination.AI, MiuixIcons.Normal.Scan),
                HyperSettingsItem("天气", "天气数据和风险提醒", SettingsDestination.Weather, MiuixIcons.Normal.Theme),
                HyperSettingsItem("主题设置", "深色模式、壁纸和显示效果", SettingsDestination.Theme, MiuixIcons.Normal.Theme),
            ),
            "数据与高级" to listOf(
                HyperSettingsItem("归档", "查看已归档日程", SettingsDestination.Archives, MiuixIcons.Normal.All),
                HyperSettingsItem("数据备份", "导入或导出应用数据", SettingsDestination.Backup, MiuixIcons.Normal.Backup),
                HyperSettingsItem("实验室", "实验功能和开发者选项", SettingsDestination.Laboratory, MiuixIcons.Normal.Settings),
            ),
        )
    }
    val normalized = query.trim()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = contentInsets.top + 14.dp,
            end = 16.dp,
            bottom = contentInsets.bottom + 14.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        groups.forEach { (groupTitle, entries) ->
            val visibleEntries = entries.filter {
                normalized.isBlank() ||
                    it.title.contains(normalized, ignoreCase = true) ||
                    it.summary.contains(normalized, ignoreCase = true)
            }
            if (visibleEntries.isNotEmpty()) {
                item(key = "settings_title_$groupTitle") {
                    HyperSectionTitle(groupTitle)
                }
                item(key = "settings_group_$groupTitle") {
                    MiuixCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 16.dp,
                        insideMargin = PaddingValues(0.dp),
                    ) {
                        visibleEntries.forEach { entry ->
                            ArrowPreference(
                                title = entry.title,
                                summary = entry.summary,
                                startAction = {
                                    Icon(
                                        imageVector = entry.icon,
                                        contentDescription = null,
                                        tint = MiuixTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .padding(end = 12.dp)
                                            .size(24.dp),
                                    )
                                },
                                onClick = { onNavigate(entry.destination) },
                            )
                        }
                    }
                }
            }
        }
        if (groups.all { (_, entries) ->
                entries.none {
                    normalized.isBlank() || it.title.contains(normalized, true) || it.summary.contains(normalized, true)
                }
            }
        ) {
            item { HyperEmptyText("未找到相关设置") }
        }
    }
}
