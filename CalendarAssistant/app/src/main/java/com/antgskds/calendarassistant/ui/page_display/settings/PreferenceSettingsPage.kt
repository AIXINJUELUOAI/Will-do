package com.antgskds.calendarassistant.ui.page_display.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.platform.floating.EdgeBarService
import com.antgskds.calendarassistant.platform.floating.FloatingBallService
import com.antgskds.calendarassistant.platform.receiver.SmsNotificationListenerService
import com.antgskds.calendarassistant.ui.components.AppModalBottomSheet
import com.antgskds.calendarassistant.ui.components.AppSettingsCard
import com.antgskds.calendarassistant.ui.components.PredictiveFloatingActionCard
import com.antgskds.calendarassistant.ui.components.ToastType
import com.antgskds.calendarassistant.ui.components.UniversalToast
import com.antgskds.calendarassistant.data.model.FloatingBallGestureAction
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.ui.haptic.HapticValueChangeEffect
import com.antgskds.calendarassistant.ui.haptic.LocalAppHapticsEnabled
import com.antgskds.calendarassistant.ui.haptic.rememberAppHaptics
import com.antgskds.calendarassistant.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun PreferenceSettingsPage(
    viewModel: SettingsViewModel,
    uiSize: Int = 2,
    onNavigateToBottomBarEditor: () -> Unit = {},
    onNavigateToWidgetSettings: () -> Unit = {},
    onNavigateToScheduleColors: () -> Unit = {},
    onNavigateToSemesterConfig: () -> Unit = {},
    onNavigateToCourseManage: () -> Unit = {},
    onNavigateToTimeTableManage: () -> Unit = {}
) {
    val settings by viewModel.settings.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val availableSyncCalendars by viewModel.availableSyncCalendars.collectAsState()
    val context = LocalContext.current
    val app = context.applicationContext as? App
    val lifecycleOwner = LocalLifecycleOwner.current
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var currentToastType by remember { mutableStateOf(ToastType.INFO) }
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var showSourceCalendarSheet by remember { mutableStateOf(false) }
    var showEventDurationPicker by remember { mutableStateOf(false) }
    var showRecognitionModePicker by remember { mutableStateOf(false) }
    var showDailySummaryMorningTimePicker by remember { mutableStateOf(false) }
    var showDailySummaryEveningTimePicker by remember { mutableStateOf(false) }
    var floatingBallActionPicker by remember { mutableStateOf<FloatingBallActionSlot?>(null) }

    val selectedSourceCalendars by remember(syncStatus.sourceCalendarIds, availableSyncCalendars) {
        derivedStateOf {
            availableSyncCalendars.filter { syncStatus.sourceCalendarIds.contains(it.id) }
        }
    }
    val selectedSourceSummary by remember(syncStatus.sourceCalendarIds, selectedSourceCalendars) {
        derivedStateOf {
            formatSelectedCalendarSummary(syncStatus.sourceCalendarIds, selectedSourceCalendars)
        }
    }
    // Toast 辅助函数
    fun showToast(message: String, type: ToastType = ToastType.INFO) {
        currentToastType = type
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    var hasOverlayPermission by remember {
        mutableStateOf(app?.permissionCenter?.canDrawOverlays(context) ?: Settings.canDrawOverlays(context))
    }

    fun refreshOverlayPermission() {
        hasOverlayPermission = app?.permissionCenter?.canDrawOverlays(context)
            ?: Settings.canDrawOverlays(context)
    }

    LaunchedEffect(Unit) {
        refreshOverlayPermission()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshOverlayPermission()
                viewModel.refreshSyncStatus()
                viewModel.refreshSyncCalendars()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun openOverlayPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        context.startActivity(intent)
    }

    fun startEdgeBarService() {
        app?.floatingCenter?.startEdgeBarServiceIfPermitted()
            ?: context.startService(Intent(context, EdgeBarService::class.java))
    }

    fun stopEdgeBarService() {
        app?.floatingCenter?.stopEdgeBarService()
            ?: context.stopService(Intent(context, EdgeBarService::class.java))
    }

    fun startFloatingBallService() {
        app?.floatingCenter?.startFloatingBallServiceIfPermitted()
            ?: context.startService(Intent(context, FloatingBallService::class.java))
    }

    fun stopFloatingBallService() {
        app?.floatingCenter?.stopFloatingBallService()
            ?: context.stopService(Intent(context, FloatingBallService::class.java))
    }

    // --- 字体样式优化 ---
    val sectionTitleStyle = MaterialTheme.typography.titleMedium.copy(
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary
    )
    val cardTitleStyle = MaterialTheme.typography.bodyLarge.copy(
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface
    )
    val cardSubtitleStyle = MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    )
    val cardValueStyle = MaterialTheme.typography.bodyMedium.copy(
        fontWeight = FontWeight.Normal,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    // 日历权限请求
    var showPermissionDialog by remember { mutableStateOf(false) }
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            viewModel.enableCalendarSyncAndSyncNow { result ->
                // initCalendarObserver removed - sync handled by StoreRootNode
                viewModel.refreshSyncCalendars()
                if (result.isSuccess) {
                    snackbarHostState.showSnackbar("日历同步已开启，并已立即同步")
                } else {
                    snackbarHostState.showSnackbar("日历同步开启失败：${result.exceptionOrNull()?.message ?: "未知错误"}")
                }
            }
        } else {
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = "需要日历权限才能使用同步功能",
                    actionLabel = "去设置",
                    duration = SnackbarDuration.Long
                )
                if (result == SnackbarResult.ActionPerformed) {
                    try {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        context.startActivity(Intent(Settings.ACTION_SETTINGS))
                    }
                }
            }
        }
    }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (!allGranted) {
            viewModel.updatePreference(smsMonitoring = false)
        } else if (!SmsNotificationListenerService.isEnabled(context)) {
            Toast.makeText(context, "建议开启通知监听兜底（系统短信）", Toast.LENGTH_SHORT).show()
            SmsNotificationListenerService.requestEnable(context)
        }
    }

    val requestCalendarPermission = {
        showPermissionDialog = false
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        } else {
            emptyArray()
        }
        calendarPermissionLauncher.launch(permissions)
    }

    CompositionLocalProvider(LocalAppHapticsEnabled provides settings.hapticFeedbackEnabled) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
                .padding(bottom = 80.dp + bottomInset),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ================== 显示板块 ==================
            Text("显示", style = sectionTitleStyle)
            SettingsCard {
                    SliderSettingItem(
                        title = "界面大小",
                        subtitle = "调整界面缩放（相对于设备原生大小）",
                        value = settings.uiSize.toFloat(),
                        onValueChange = { viewModel.updateUiSize(it.toInt()) },
                        valueRange = 1f..3f,
                        steps = 1, // 离散：1, 2, 3
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle,
                        cardValueStyle = cardValueStyle,
                        showValueAsNumber = false // 显示文字：小/中/大
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    SwitchSettingItem(
                        title = "显示明日日程",
                        subtitle = "在今日日程列表底部预览明日安排",
                        checked = settings.showTomorrowEvents,
                        onCheckedChange = { viewModel.updatePreference(showTomorrow = it) },
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    ActionSettingItem(
                        title = "底栏编辑",
                        subtitle = "自定义首页底栏顺序和默认启动页",
                        value = "",
                        icon = Icons.Default.ChevronRight,
                        enabled = true,
                        onClick = onNavigateToBottomBarEditor,
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle,
                        cardValueStyle = cardValueStyle
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    ActionSettingItem(
                        title = "桌面小组件",
                        subtitle = "预览小组件并调整主题、透明度等显示设置",
                        value = "",
                        icon = Icons.Default.ChevronRight,
                        enabled = true,
                        onClick = onNavigateToWidgetSettings,
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle,
                        cardValueStyle = cardValueStyle,
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    SwitchSettingItem(
                        title = "悬浮日程",
                        subtitle = "用于悬浮窗日程显示与编辑",
                        checked = settings.isFloatingWindowEnabled,
                        onCheckedChange = { isChecked ->
                            if (isChecked && !hasOverlayPermission) {
                                openOverlayPermissionSettings()
                                return@SwitchSettingItem
                            }
                            viewModel.updatePreference(
                                floatingWindow = isChecked,
                                edgeBarEnabled = if (isChecked) settings.edgeBarEnabled else false,
                                floatingBallEnabled = if (isChecked) settings.floatingBallEnabled else false
                            )
                            if (!isChecked) {
                                stopEdgeBarService()
                                stopFloatingBallService()
                            } else {
                                if (settings.edgeBarEnabled) startEdgeBarService()
                                if (settings.floatingBallEnabled) startFloatingBallService()
                            }
                        },
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle
                    )
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "需要悬浮窗权限才能正常使用",
                            style = cardSubtitleStyle,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
            }

            AnimatedVisibility(
                visible = settings.isFloatingWindowEnabled,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                SettingsCard {
                        FloatingEventRangeSlider(
                            title = "悬浮窗日程范围",
                            subtitle = when (settings.floatingEventRange) {
                                0 -> "显示全部日程"
                                1 -> "只显示今日日程"
                                2 -> "显示今日和明日日程"
                                else -> "显示全部日程"
                            },
                            eventRange = settings.floatingEventRange,
                            onEventRangeChange = { range ->
                                viewModel.updatePreference(floatingEventRange = range)
                            },
                            cardTitleStyle = cardTitleStyle,
                            cardSubtitleStyle = cardSubtitleStyle
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(start = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        SideChoiceSettingItem(
                            title = "悬浮窗展开方向",
                            subtitle = if (settings.floatingExpandSide == "LEFT") {
                                "所有入口从左侧滑入"
                            } else {
                                "所有入口从右侧滑入"
                            },
                            selectedSide = settings.floatingExpandSide,
                            onSideSelected = { side -> viewModel.updatePreference(floatingExpandSide = side) },
                            cardTitleStyle = cardTitleStyle,
                            cardSubtitleStyle = cardSubtitleStyle
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(start = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        SwitchSettingItem(
                            title = "侧边栏入口",
                            subtitle = "在屏幕边缘显示窄条，滑动呼出悬浮日程",
                            checked = settings.edgeBarEnabled,
                            onCheckedChange = { isChecked ->
                                if (isChecked && !hasOverlayPermission) {
                                    openOverlayPermissionSettings()
                                    return@SwitchSettingItem
                                }
                                viewModel.updateEdgeBarSettings(enabled = isChecked)
                                if (isChecked) {
                                    startEdgeBarService()
                                } else {
                                    stopEdgeBarService()
                                }
                            },
                            cardTitleStyle = cardTitleStyle,
                            cardSubtitleStyle = cardSubtitleStyle
                        )

                        AnimatedVisibility(
                            visible = settings.edgeBarEnabled,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 16.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                                SideChoiceSettingItem(
                                    title = "侧边栏位置",
                                    subtitle = if (settings.edgeBarSide == "LEFT") {
                                        "唤起条固定在屏幕左侧"
                                    } else {
                                        "唤起条固定在屏幕右侧"
                                    },
                                    selectedSide = settings.edgeBarSide,
                                    onSideSelected = { side -> viewModel.updateEdgeBarSettings(side = side) },
                                    cardTitleStyle = cardTitleStyle,
                                    cardSubtitleStyle = cardSubtitleStyle
                                )

                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 16.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )

                                SliderSettingItem(
                                    title = "纵向位置",
                                    subtitle = "上下位置百分比",
                                    value = settings.edgeBarYPercent,
                                    onValueChange = { viewModel.updateEdgeBarSettings(yPercent = it.roundToInt().toFloat()) },
                                    valueRange = 0f..100f,
                                    steps = 0,
                                    cardTitleStyle = cardTitleStyle,
                                    cardSubtitleStyle = cardSubtitleStyle,
                                    cardValueStyle = cardValueStyle,
                                    showValueAsNumber = true,
                                    valueUnit = "%"
                                )

                                SliderSettingItem(
                                    title = "宽度",
                                    subtitle = "侧边条宽度",
                                    value = settings.edgeBarWidthDp.toFloat(),
                                    onValueChange = { viewModel.updateEdgeBarSettings(widthDp = it.roundToInt()) },
                                    valueRange = 4f..20f,
                                    steps = 0,
                                    cardTitleStyle = cardTitleStyle,
                                    cardSubtitleStyle = cardSubtitleStyle,
                                    cardValueStyle = cardValueStyle,
                                    showValueAsNumber = true,
                                    valueUnit = "dp"
                                )

                                SliderSettingItem(
                                    title = "高度",
                                    subtitle = "侧边条高度",
                                    value = settings.edgeBarHeightDp.toFloat(),
                                    onValueChange = { viewModel.updateEdgeBarSettings(heightDp = it.roundToInt()) },
                                    valueRange = 60f..240f,
                                    steps = 0,
                                    cardTitleStyle = cardTitleStyle,
                                    cardSubtitleStyle = cardSubtitleStyle,
                                    cardValueStyle = cardValueStyle,
                                    showValueAsNumber = true,
                                    valueUnit = "dp"
                                )

                                SliderSettingItem(
                                    title = "颜色深浅",
                                    subtitle = "调整透明度，0% 时完全透明",
                                    value = settings.edgeBarAlpha * 100f,
                                    onValueChange = { viewModel.updateEdgeBarSettings(alpha = it.roundToInt() / 100f) },
                                    valueRange = 0f..100f,
                                    steps = 0,
                                    cardTitleStyle = cardTitleStyle,
                                    cardSubtitleStyle = cardSubtitleStyle,
                                    cardValueStyle = cardValueStyle,
                                    showValueAsNumber = true,
                                    valueUnit = "%"
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    AssistChip(
                                        onClick = {
                                            viewModel.updateEdgeBarSettings(
                                                enabled = true,
                                                side = "RIGHT",
                                                yPercent = 50f,
                                                widthDp = 8,
                                                heightDp = 120,
                                                alpha = 0.4f
                                            )
                                            startEdgeBarService()
                                        },
                                        label = { Text("恢复默认") }
                                    )
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(start = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        SwitchSettingItem(
                            title = "悬浮球入口",
                            subtitle = "显示可拖动悬浮球，单击、双击和长按可分别设置动作",
                            checked = settings.floatingBallEnabled,
                            onCheckedChange = { isChecked ->
                                if (isChecked && !hasOverlayPermission) {
                                    openOverlayPermissionSettings()
                                    return@SwitchSettingItem
                                }
                                viewModel.updatePreference(floatingBallEnabled = isChecked)
                                if (isChecked) {
                                    startFloatingBallService()
                                } else {
                                    stopFloatingBallService()
                                }
                            },
                            cardTitleStyle = cardTitleStyle,
                            cardSubtitleStyle = cardSubtitleStyle
                        )

                        AnimatedVisibility(
                            visible = settings.floatingBallEnabled,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 16.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                                ActionSettingItem(
                                    title = "单击动作",
                                    subtitle = "当前：${FloatingBallGestureAction.label(settings.floatingBallSingleTapAction)}",
                                    value = "",
                                    icon = Icons.Default.ChevronRight,
                                    enabled = true,
                                    onClick = { floatingBallActionPicker = FloatingBallActionSlot.SINGLE_TAP },
                                    cardTitleStyle = cardTitleStyle,
                                    cardSubtitleStyle = cardSubtitleStyle,
                                    cardValueStyle = cardValueStyle
                                )

                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 16.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                                ActionSettingItem(
                                    title = "双击动作",
                                    subtitle = "当前：${FloatingBallGestureAction.label(settings.floatingBallDoubleTapAction)}",
                                    value = "",
                                    icon = Icons.Default.ChevronRight,
                                    enabled = true,
                                    onClick = { floatingBallActionPicker = FloatingBallActionSlot.DOUBLE_TAP },
                                    cardTitleStyle = cardTitleStyle,
                                    cardSubtitleStyle = cardSubtitleStyle,
                                    cardValueStyle = cardValueStyle
                                )

                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 16.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                                ActionSettingItem(
                                    title = "长按动作",
                                    subtitle = "当前：${FloatingBallGestureAction.label(settings.floatingBallLongPressAction)}",
                                    value = "",
                                    icon = Icons.Default.ChevronRight,
                                    enabled = true,
                                    onClick = { floatingBallActionPicker = FloatingBallActionSlot.LONG_PRESS },
                                    cardTitleStyle = cardTitleStyle,
                                    cardSubtitleStyle = cardSubtitleStyle,
                                    cardValueStyle = cardValueStyle
                                )

                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 16.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                                SliderSettingItem(
                                    title = "尺寸",
                                    subtitle = "悬浮球直径",
                                    value = settings.floatingBallSizeDp.toFloat(),
                                    onValueChange = { viewModel.updatePreference(floatingBallSizeDp = it.roundToInt()) },
                                    valueRange = 44f..72f,
                                    steps = 0,
                                    cardTitleStyle = cardTitleStyle,
                                    cardSubtitleStyle = cardSubtitleStyle,
                                    cardValueStyle = cardValueStyle,
                                    showValueAsNumber = true,
                                    valueUnit = "dp"
                                )

                                SliderSettingItem(
                                    title = "透明度",
                                    subtitle = "调整悬浮球可见程度",
                                    value = settings.floatingBallAlpha * 100f,
                                    onValueChange = { viewModel.updatePreference(floatingBallAlpha = it.roundToInt() / 100f) },
                                    valueRange = 20f..100f,
                                    steps = 0,
                                    cardTitleStyle = cardTitleStyle,
                                    cardSubtitleStyle = cardSubtitleStyle,
                                    cardValueStyle = cardValueStyle,
                                    showValueAsNumber = true,
                                    valueUnit = "%"
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    AssistChip(
                                        onClick = {
                                            viewModel.updatePreference(
                                                floatingBallEnabled = true,
                                                floatingBallXPercent = 86f,
                                                floatingBallYPercent = 50f,
                                                floatingBallSizeDp = 56,
                                                floatingBallAlpha = 0.9f,
                                                floatingBallSingleTapAction = FloatingBallGestureAction.OPEN_QUICK_MEMO,
                                                floatingBallDoubleTapAction = FloatingBallGestureAction.QUICK_RECOGNITION,
                                                floatingBallLongPressAction = FloatingBallGestureAction.QUICK_MEMO_RECORDING
                                            )
                                            startFloatingBallService()
                                        },
                                        label = { Text("恢复默认") }
                                    )
                                }
                            }
                        }
                }
            }

            // ================== 操作板块 ==================
            Text("操作", style = sectionTitleStyle)
            SettingsCard {
                    SwitchSettingItem(
                        title = "触感反馈",
                        subtitle = "点击、长按和滑动到阈值时提供轻微反馈",
                        checked = settings.hapticFeedbackEnabled,
                        onCheckedChange = { viewModel.updatePreference(hapticFeedbackEnabled = it) },
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    VolumeLongPressSettingItem(
                        title = "长按音量+",
                        subtitle = "自定义长按音量+动作",
                        checked = settings.volumeUpLongPressEnabled,
                        action = settings.volumeUpLongPressAction,
                        onCheckedChange = { isChecked ->
                            viewModel.updatePreference(
                                volumeUpLongPressEnabled = isChecked,
                                volumeUpLongPressAction = if (isChecked) settings.volumeUpLongPressAction.coerceIn(1, 3) else settings.volumeUpLongPressAction
                            )
                        },
                        onActionChange = { action ->
                            viewModel.updatePreference(volumeUpLongPressAction = action)
                        },
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle
                    )
                    SwitchSettingItem(
                        title = "短信自动解析取件码",
                        subtitle = "监听短信自动识别快递取件码并入库",
                        checked = settings.isSmsMonitoringEnabled,
                        onCheckedChange = { isChecked ->
                            if (isChecked) {
                                smsPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.RECEIVE_SMS,
                                        Manifest.permission.READ_SMS
                                    )
                                )
                            }
                            viewModel.updatePreference(smsMonitoring = isChecked)
                        },
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle
                    )
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "需要短信读取权限",
                            style = cardSubtitleStyle,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
            }

            // ================== 通知板块 ==================
            Text("通知", style = sectionTitleStyle)
            SettingsCard {
                    SwitchSettingItem(
                        title = "每日提醒",
                        subtitle = "今日 ${formatMinuteOfDay(settings.dailySummaryMorningMinuteOfDay)}，明日 ${formatMinuteOfDay(settings.dailySummaryEveningMinuteOfDay)}",
                        checked = settings.isDailySummaryEnabled,
                        onCheckedChange = { isChecked ->
                            viewModel.updatePreference(dailySummary = isChecked)
                            if (isChecked) {
                                app?.runtimeCenter?.scheduleDailySummary()
                            }
                        },
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle
                    )

                    AnimatedVisibility(
                        visible = settings.isDailySummaryEnabled,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 16.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                            DailySummaryTimeSettingItem(
                                title = "今日提醒时间",
                                subtitle = "推送今日日程汇总",
                                minuteOfDay = settings.dailySummaryMorningMinuteOfDay,
                                onClick = { showDailySummaryMorningTimePicker = true },
                                cardTitleStyle = cardTitleStyle,
                                cardSubtitleStyle = cardSubtitleStyle,
                                cardValueStyle = cardValueStyle
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 16.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                            DailySummaryTimeSettingItem(
                                title = "明日预告时间",
                                subtitle = "推送明日日程预告",
                                minuteOfDay = settings.dailySummaryEveningMinuteOfDay,
                                onClick = { showDailySummaryEveningTimePicker = true },
                                cardTitleStyle = cardTitleStyle,
                                cardSubtitleStyle = cardSubtitleStyle,
                                cardValueStyle = cardValueStyle
                            )
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    SwitchSettingItem(
                        title = "实况胶囊通知",
                        subtitle = "日程开始时显示实况通知",
                        checked = settings.isLiveCapsuleEnabled,
                        onCheckedChange = { isChecked ->
                            viewModel.updatePreference(liveCapsule = isChecked)
                            if (isChecked) showToast("实况胶囊已开启", ToastType.INFO)
                        },
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle
                    )

                    AnimatedVisibility(
                        visible = settings.isLiveCapsuleEnabled,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 16.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                            SwitchSettingItem(
                                title = "取件码聚合 (Beta)",
                                subtitle = "当有多个取件码时合并显示为一个胶囊",
                                checked = settings.isPickupAggregationEnabled,
                                onCheckedChange = { isChecked ->
                                    viewModel.updatePreference(pickupAggregation = isChecked)
                                },
                                cardTitleStyle = cardTitleStyle,
                                cardSubtitleStyle = cardSubtitleStyle
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    AdvanceReminderSettingItem(
                        title = "日程提前提醒",
                        subtitle = if (settings.isAdvanceReminderEnabled)
                            "提前 ${settings.advanceReminderMinutes} 分钟"
                        else
                            "日程开始时",
                        checked = settings.isAdvanceReminderEnabled,
                        minutes = settings.advanceReminderMinutes,
                        onCheckedChange = { isChecked ->
                            viewModel.updatePreference(advanceReminderEnabled = isChecked)
                            if (isChecked && settings.advanceReminderMinutes > 0) {
                                val hasDuplicate = viewModel.hasDuplicateAdvanceReminder(settings.advanceReminderMinutes)
                                if (hasDuplicate) {
                                    showToast("检测到可能存在的重复提醒", ToastType.INFO)
                                }
                            }
                        },
                        onMinutesChange = { minutes ->
                            viewModel.updatePreference(advanceReminderMinutes = minutes)
                        },
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    SwitchSettingItem(
                        title = "网速胶囊",
                        subtitle = "在状态栏显示下载速度",
                        checked = settings.isNetworkSpeedCapsuleEnabled,
                        onCheckedChange = { isChecked ->
                            viewModel.updatePreference(networkSpeedCapsule = isChecked)
                        },
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle
                    )
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "网速胶囊会覆盖其他胶囊显示",
                            style = cardSubtitleStyle,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

            }


            // ================== AI 板块 ==================
            Text("AI", style = sectionTitleStyle)
            SettingsCard {
                    RecognitionModeSettingItem(
                        mode = settings.recognitionMode,
                        onClick = { showRecognitionModePicker = true },
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle,
                        cardValueStyle = cardValueStyle
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    SwitchSettingItem(
                        title = "使用多模态AI",
                        subtitle = "开启后图片识别将使用多模态模型",
                        checked = settings.useMultimodalAi,
                        onCheckedChange = { isChecked ->
                            viewModel.updatePreference(useMultimodalAi = isChecked)
                            showToast(if (isChecked) "已切换为多模态AI" else "已切换为文本AI")
                        },
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    SwitchSettingItem(
                        title = "关闭思考",
                        subtitle = "仅适配 OpenAI",
                        checked = settings.disableThinking,
                        onCheckedChange = { isChecked ->
                            viewModel.updatePreference(disableThinking = isChecked)
                            showToast(if (isChecked) "快速模式已开启" else "快速模式已关闭")
                        },
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle
                    )

            }

            // ================== 日程板块 ==================
            Text("日程", style = sectionTitleStyle)
            SettingsCard {
                    SwitchSettingItem(
                        title = "日历同步",
                        subtitle = "将课程和日程同步到系统日历",
                        checked = syncStatus.isEnabled,
                        onCheckedChange = { isChecked ->
                            if (isChecked) {
                                if (app?.permissionCenter?.hasCalendarPermissions(context) == true) {
                                    viewModel.enableCalendarSyncAndSyncNow { result ->
                                        // initCalendarObserver removed - sync handled by StoreRootNode
                                        if (result.isSuccess) {
                                            showToast("日历同步已开启，并已立即同步")
                                        } else {
                                            showToast("日历同步开启失败", ToastType.ERROR)
                                        }
                                    }
                                } else {
                                    showPermissionDialog = true
                                }
                            } else {
                                viewModel.toggleCalendarSync(false)
                                showToast("日历同步已关闭")
                            }
                        },
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle
                    )

                    AnimatedVisibility(
                        visible = syncStatus.isEnabled,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 16.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )

                            ActionSettingItem(
                                title = "同步来源日历",
                                subtitle = selectedSourceSummary,
                                value = if (syncStatus.sourceCalendarIds.isEmpty()) {
                                    "未选择"
                                } else {
                                    "${syncStatus.sourceCalendarIds.size} 个"
                                },
                                enabled = syncStatus.hasPermission,
                                onClick = {
                                    if (syncStatus.hasPermission) {
                                        showSourceCalendarSheet = true
                                    } else {
                                        showPermissionDialog = true
                                    }
                                },
                                cardTitleStyle = cardTitleStyle,
                                cardSubtitleStyle = cardSubtitleStyle,
                                cardValueStyle = cardValueStyle
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(start = 16.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )

                            SliderSettingItem(
                                title = "兜底同步频率",
                                subtitle = "仅作兜底轮询，优先即时监听",
                                value = syncStatus.syncIntervalSeconds.toFloat(),
                                onValueChange = { seconds ->
                                    viewModel.updateSyncIntervalSeconds(seconds.toInt())
                                },
                                valueRange = 1f..300f,
                                steps = 0,
                                cardTitleStyle = cardTitleStyle,
                                cardSubtitleStyle = cardSubtitleStyle,
                                cardValueStyle = cardValueStyle,
                                showValueAsNumber = true,
                                valueUnit = "s"
                            )

                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    SwitchSettingItem(
                        title = "自动归档",
                        subtitle = "日程过期后立即自动归档",
                        checked = settings.autoArchiveEnabled,
                        onCheckedChange = { isChecked ->
                            viewModel.updatePreference(autoArchive = isChecked)
                        },
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    EventDurationSettingItem(
                        title = "日程默认持续时间",
                        subtitle = "所有识别日程都按该时长生成结束时间",
                        durationMinutes = settings.defaultEventDurationMinutes,
                        onClick = { showEventDurationPicker = true },
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle,
                        cardValueStyle = cardValueStyle
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    ActionSettingItem(
                        title = "日程颜色",
                        subtitle = "自定义新建和识别日程使用的色盘",
                        value = "${settings.eventColorPaletteHex.size} 个",
                        enabled = true,
                        onClick = onNavigateToScheduleColors,
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle,
                        cardValueStyle = cardValueStyle
                    )
            }

            // ================== 课表板块 ==================
            Text("课表", style = sectionTitleStyle)
            SettingsCard {
                    SwitchSettingItem(
                        title = "启用课表功能",
                        subtitle = "关闭后无法在主页下滑进入课表",
                        checked = settings.courseFeatureEnabled,
                        onCheckedChange = { isChecked ->
                            viewModel.updatePreference(courseFeatureEnabled = isChecked)
                        },
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle
                    )

                    AnimatedVisibility(
                        visible = settings.courseFeatureEnabled,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 16.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )

                            ActionSettingItem(
                                title = "学期配置",
                                subtitle = "设置第一周、当前周次和学期总周数",
                                value = "",
                                icon = Icons.Default.ChevronRight,
                                enabled = true,
                                onClick = onNavigateToSemesterConfig,
                                cardTitleStyle = cardTitleStyle,
                                cardSubtitleStyle = cardSubtitleStyle,
                                cardValueStyle = cardValueStyle
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(start = 16.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )

                            ActionSettingItem(
                                title = "管理所有课程",
                                subtitle = "添加、修改或删除课程",
                                value = "",
                                icon = Icons.Default.ChevronRight,
                                enabled = true,
                                onClick = onNavigateToCourseManage,
                                cardTitleStyle = cardTitleStyle,
                                cardSubtitleStyle = cardSubtitleStyle,
                                cardValueStyle = cardValueStyle
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(start = 16.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )

                            ActionSettingItem(
                                title = "作息时间设置",
                                subtitle = "设置每日节次时间段",
                                value = "",
                                icon = Icons.Default.ChevronRight,
                                enabled = true,
                                onClick = onNavigateToTimeTableManage,
                                cardTitleStyle = cardTitleStyle,
                                cardSubtitleStyle = cardSubtitleStyle,
                                cardValueStyle = cardValueStyle
                            )
                        }
                    }
            }

            // ================== 截图板块 (新) ==================
            // 注意：现在它在 Column 内部，位于“日程”卡片之后
            Text("截图", style = sectionTitleStyle)
            SettingsCard {
                    SliderSettingItem(
                        title = "截图延迟",
                        subtitle = "截图与分析之间的等待时间",
                        value = MySettings.normalizeScreenshotDelayMs(settings.screenshotDelayMs).toFloat(),
                        onValueChange = { viewModel.updateScreenshotDelay(it.toLong()) },
                        valueRange = MySettings.SCREENSHOT_DELAY_MIN_MS.toFloat()..MySettings.SCREENSHOT_DELAY_MAX_MS.toFloat(),
                        steps = 0, // 0 = 无极调节
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle,
                        cardValueStyle = cardValueStyle,
                        showValueAsNumber = true, // 开启数字显示
                        valueUnit = "ms"
                    )
            }

        } // <--- Column 结束在这里，确保所有板块都在里面

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp + bottomInset),
            snackbar = { data -> UniversalToast(message = data.visuals.message, type = currentToastType) }
        )

        PredictiveFloatingActionCard(
            visible = showPermissionDialog,
            title = "需要日历权限",
            content = "为了让您在系统日历中查看和管理课程与日程，需要授予应用读取和写入日历的权限。",
            confirmText = "授予权限",
            dismissText = "取消",
            isDestructive = false,
            isLoading = false,
            predictiveBackEnabled = settings.predictiveBackEnabled,
            onConfirm = requestCalendarPermission,
            onDismiss = { showPermissionDialog = false }
        )

        if (showSourceCalendarSheet) {
            SourceCalendarPickerSheet(
                calendars = availableSyncCalendars,
                initialSelection = syncStatus.sourceCalendarIds.toSet(),
                onDismiss = { showSourceCalendarSheet = false },
                onConfirm = { selectedIds ->
                    viewModel.updateSourceCalendars(selectedIds) { result ->
                        if (result.isSuccess) {
                            showToast("同步来源日历已更新")
                            showSourceCalendarSheet = false
                        } else {
                            showToast("更新同步来源失败", ToastType.ERROR)
                        }
                    }
                }
            )
        }

        if (showEventDurationPicker) {
            EventDurationPickerDialog(
                selectedDuration = settings.defaultEventDurationMinutes,
                onDismiss = { showEventDurationPicker = false },
                onConfirm = { duration ->
                    viewModel.updatePreference(defaultEventDurationMinutes = duration)
                    showEventDurationPicker = false
                }
            )
        }

        if (showRecognitionModePicker) {
            RecognitionModePickerDialog(
                selectedMode = settings.recognitionMode,
                onDismiss = { showRecognitionModePicker = false },
                onConfirm = { mode ->
                    viewModel.updatePreference(recognitionMode = mode)
                    showRecognitionModePicker = false
                }
            )
        }

        if (showDailySummaryMorningTimePicker) {
            DailySummaryTimePickerDialog(
                selectedMinuteOfDay = settings.dailySummaryMorningMinuteOfDay,
                onDismiss = { showDailySummaryMorningTimePicker = false },
                title = "今日提醒时间",
                onConfirm = { minuteOfDay ->
                    viewModel.updateDailySummaryTimes(
                        morningMinuteOfDay = minuteOfDay,
                        onUpdated = { app?.runtimeCenter?.scheduleDailySummary() }
                    )
                    showDailySummaryMorningTimePicker = false
                }
            )
        }

        if (showDailySummaryEveningTimePicker) {
            DailySummaryTimePickerDialog(
                selectedMinuteOfDay = settings.dailySummaryEveningMinuteOfDay,
                onDismiss = { showDailySummaryEveningTimePicker = false },
                title = "明日预告时间",
                onConfirm = { minuteOfDay ->
                    viewModel.updateDailySummaryTimes(
                        eveningMinuteOfDay = minuteOfDay,
                        onUpdated = { app?.runtimeCenter?.scheduleDailySummary() }
                    )
                    showDailySummaryEveningTimePicker = false
                }
            )
        }

        floatingBallActionPicker?.let { slot ->
            FloatingBallActionPickerDialog(
                slot = slot,
                selectedAction = slot.currentAction(settings),
                onDismiss = { floatingBallActionPicker = null },
                onActionSelected = { action ->
                    when (slot) {
                        FloatingBallActionSlot.SINGLE_TAP -> viewModel.updatePreference(floatingBallSingleTapAction = action)
                        FloatingBallActionSlot.DOUBLE_TAP -> viewModel.updatePreference(floatingBallDoubleTapAction = action)
                        FloatingBallActionSlot.LONG_PRESS -> viewModel.updatePreference(floatingBallLongPressAction = action)
                    }
                    floatingBallActionPicker = null
                }
            )
        }
    }
    }
}

private enum class FloatingBallActionSlot(val title: String) {
    SINGLE_TAP("单击动作"),
    DOUBLE_TAP("双击动作"),
    LONG_PRESS("长按动作")
}

private fun FloatingBallActionSlot.currentAction(settings: MySettings): Int {
    return when (this) {
        FloatingBallActionSlot.SINGLE_TAP -> settings.floatingBallSingleTapAction
        FloatingBallActionSlot.DOUBLE_TAP -> settings.floatingBallDoubleTapAction
        FloatingBallActionSlot.LONG_PRESS -> settings.floatingBallLongPressAction
    }
}

@Composable
private fun FloatingBallActionPickerDialog(
    slot: FloatingBallActionSlot,
    selectedAction: Int,
    onDismiss: () -> Unit,
    onActionSelected: (Int) -> Unit
) {
    val haptics = rememberAppHaptics()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(slot.title) },
        text = {
            Column {
                FloatingBallGestureAction.ALL.forEach { action ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptics.selection()
                                onActionSelected(action)
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = FloatingBallGestureAction.normalize(selectedAction) == action,
                            onClick = {
                                haptics.selection()
                                onActionSelected(action)
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(FloatingBallGestureAction.label(action))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

private fun formatMinuteOfDay(minuteOfDay: Int): String {
    val safeMinuteOfDay = MySettings.normalizeDailySummaryMinuteOfDay(minuteOfDay)
    return "%02d:%02d".format(safeMinuteOfDay / 60, safeMinuteOfDay % 60)
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    AppSettingsCard(content = content)
}

// SwitchSettingItem 已抽至 SettingsRowComponents.kt（公共组件，同包，无需改 import）

// SideChoiceSettingItem 已抽至 SettingsRowComponents.kt

@Composable
private fun TwoOptionSettingItem(
    title: String,
    subtitle: String,
    selectedValue: Int,
    firstValue: Int,
    firstLabel: String,
    secondValue: Int,
    secondLabel: String,
    onValueSelected: (Int) -> Unit,
    cardTitleStyle: TextStyle,
    cardSubtitleStyle: TextStyle
) {
    val haptics = rememberAppHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = cardTitleStyle)
            Text(subtitle, style = cardSubtitleStyle)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (selectedValue == firstValue) {
                Button(onClick = { haptics.selection(); onValueSelected(firstValue) }) { Text(firstLabel) }
            } else {
                OutlinedButton(onClick = { haptics.selection(); onValueSelected(firstValue) }) { Text(firstLabel) }
            }
            if (selectedValue == secondValue) {
                Button(onClick = { haptics.selection(); onValueSelected(secondValue) }) { Text(secondLabel) }
            } else {
                OutlinedButton(onClick = { haptics.selection(); onValueSelected(secondValue) }) { Text(secondLabel) }
            }
        }
    }
}

// ActionSettingItem 已抽至 SettingsRowComponents.kt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceCalendarPickerSheet(
    calendars: List<com.antgskds.calendarassistant.calendar.models.stubs.CalendarManager.CalendarInfo>,
    initialSelection: Set<Long>,
    onDismiss: () -> Unit,
    onConfirm: (List<Long>) -> Unit
) {
    var selectedIds by remember(initialSelection, calendars) {
        mutableStateOf(initialSelection.intersect(calendars.map { it.id }.toSet()))
    }
    val haptics = rememberAppHaptics()
    val groupedCalendars = remember(calendars) {
        calendars.groupBy { calendar ->
            buildAccountGroupTitle(calendar)
        }
    }

    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            Text("同步来源日历", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "勾选后，这些系统日历中的日程会同步到 APP；在 APP 中修改导入日程时，会回写到原日历。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (calendars.isEmpty()) {
                Text(
                    text = "当前没有可读取的系统日历。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                TextButton(onClick = { haptics.selection(); selectedIds = calendars.map { it.id }.toSet() }) {
                    Text("全选")
                }

                groupedCalendars.forEach { (groupTitle, groupCalendars) ->
                    Text(
                        text = groupTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )

                    groupCalendars.forEach { calendar ->
                        val checked = selectedIds.contains(calendar.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    haptics.selection()
                                    selectedIds = if (checked) {
                                        selectedIds - calendar.id
                                    } else {
                                        selectedIds + calendar.id
                                    }
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    haptics.selection()
                                    selectedIds = if (isChecked) {
                                        selectedIds + calendar.id
                                    } else {
                                        selectedIds - calendar.id
                                    }
                                }
                            )
                            Column(modifier = Modifier.padding(start = 12.dp)) {
                                Text(calendar.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = buildCalendarMetaLine(calendar),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { haptics.confirm(); onConfirm(calendars.filter { selectedIds.contains(it.id) }.map { it.id }) },
                enabled = calendars.isNotEmpty() && selectedIds.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存")
            }
        }
    }
}

private fun formatSelectedCalendarSummary(
    selectedIds: List<Long>,
    selectedCalendars: List<com.antgskds.calendarassistant.calendar.models.stubs.CalendarManager.CalendarInfo>
): String {
    if (selectedIds.isEmpty()) {
        return "请选择需要从系统同步进 APP 的日历"
    }

    if (selectedCalendars.isEmpty()) {
        return "已选择 ${selectedIds.size} 个日历"
    }

    val names = selectedCalendars.map { it.name }.distinct()
    return if (names.size <= 2) {
        names.joinToString("、")
    } else {
        names.take(2).joinToString("、") + " 等 ${names.size} 个日历"
    }
}

private fun buildAccountGroupTitle(
    calendar: com.antgskds.calendarassistant.calendar.models.stubs.CalendarManager.CalendarInfo
): String {
    val accountName = calendar.accountName?.takeIf { it.isNotBlank() } ?: "本地账户"
    val accountType = calendar.accountType?.takeIf { it.isNotBlank() }
    return if (accountType != null) {
        "$accountName  ($accountType)"
    } else {
        accountName
    }
}

private fun buildCalendarMetaLine(
    calendar: com.antgskds.calendarassistant.calendar.models.stubs.CalendarManager.CalendarInfo
): String {
    val tags = mutableListOf<String>()
    if (!calendar.isVisible) tags += "已隐藏"
    if (!calendar.syncEvents) tags += "未启用系统同步"
    if (!calendar.isWritable) tags += "只读"
    return if (tags.isEmpty()) {
        "ID: ${calendar.id}"
    } else {
        "ID: ${calendar.id}  ${tags.joinToString(" · ")}"
    }
}

// SliderSettingItem 已抽至 SettingsRowComponents.kt

// VolumeLongPressSettingItem 已抽至 SettingsRowComponents.kt

// AdvanceReminderSettingItem 已抽至 SettingsRowComponents.kt

// EventDuration 簇（行/选择对话框/选项/格式化）已抽至 SettingsRowComponents.kt

@Composable
fun FloatingEventRangeSlider(
    title: String,
    subtitle: String,
    eventRange: Int,
    onEventRangeChange: (Int) -> Unit,
    cardTitleStyle: TextStyle,
    cardSubtitleStyle: TextStyle
) {
    HapticValueChangeEffect(valueKey = eventRange)
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        // 标题行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = cardTitleStyle)
                Text(subtitle, style = cardSubtitleStyle)
            }
        }

        // 滑块区域
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
        ) {
            // 标签行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "全部日程", style = cardSubtitleStyle)
                Text(text = "今日日程", style = cardSubtitleStyle)
                Text(text = "今日+明日", style = cardSubtitleStyle)
            }
            Slider(
                value = eventRange.toFloat(),
                onValueChange = { onEventRangeChange(it.toInt()) },
                valueRange = 0f..2f,
                steps = 1
            )
        }
    }
}
