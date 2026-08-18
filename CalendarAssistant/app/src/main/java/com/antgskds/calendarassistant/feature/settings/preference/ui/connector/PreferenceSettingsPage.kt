package com.antgskds.calendarassistant.feature.settings.preference.ui.connector
import com.antgskds.calendarassistant.shared.ui.edition.EditionCheckbox
import com.antgskds.calendarassistant.shared.ui.edition.EditionButton
import com.antgskds.calendarassistant.shared.ui.edition.EditionCategoricalPreference
import com.antgskds.calendarassistant.shared.ui.edition.EditionDropdownActionSettingItem
import com.antgskds.calendarassistant.shared.ui.edition.EditionSwitchSliderSettingItem

import com.antgskds.calendarassistant.shared.ui.material.settings.*
import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.core.content.ContextCompat
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.platform.floating.EdgeBarService
import com.antgskds.calendarassistant.platform.floating.FloatingBallService
import com.antgskds.calendarassistant.platform.receiver.SmsNotificationListenerService
import com.antgskds.calendarassistant.platform.accessibility.TextAccessibilityService
import com.antgskds.calendarassistant.shared.ui.material.component.AppModalBottomSheet
import com.antgskds.calendarassistant.shared.ui.material.component.AppSettingsCard
import com.antgskds.calendarassistant.shared.ui.material.component.AppAlertDialog
import com.antgskds.calendarassistant.shared.ui.material.component.CenteredDialogTitle
import com.antgskds.calendarassistant.shared.ui.material.component.ToastType
import com.antgskds.calendarassistant.shared.ui.material.component.UniversalSnackbar
import com.antgskds.calendarassistant.shared.ui.material.component.WheelPicker
import com.antgskds.calendarassistant.feature.settings.data.model.FloatingBallGestureAction
import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import com.antgskds.calendarassistant.feature.settings.data.model.QuickMemoRecordingDisplayMode
import com.antgskds.calendarassistant.feature.quickmemo.data.asr.QuickMemoAsrModelStatus
import com.antgskds.calendarassistant.feature.quickmemo.data.asr.QuickMemoAsrModelStore
import com.antgskds.calendarassistant.shared.ui.interaction.HapticValueChangeEffect
import com.antgskds.calendarassistant.shared.ui.interaction.LocalAppHapticsEnabled
import com.antgskds.calendarassistant.shared.ui.interaction.rememberAppHaptics
import com.antgskds.calendarassistant.shared.ui.permission.rememberPermissionGate
import com.antgskds.calendarassistant.app.ui.state.SettingsViewModel
import com.antgskds.calendarassistant.feature.settings.preference.ui.contract.PreferenceUiController
import com.antgskds.calendarassistant.feature.settings.preference.ui.connector.PreferenceUiControllerAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

enum class PreferenceSection {
    DISPLAY,
    QUICK_MEMO,
    OPERATION,
    NOTIFICATION,
    AI,
    SCHEDULE,
    COURSE,
    SCREENSHOT,
}

data class PreferenceItemVisibility(
    val showUiSize: Boolean = true,
    val showTomorrowEvents: Boolean = true,
    val showBottomBarEditor: Boolean = true,
    val showWidgetSettings: Boolean = true,
    val showHapticFeedback: Boolean = true,
    val showNetworkSpeedCapsule: Boolean = true,
    val showAutoArchive: Boolean = true,
    val showScheduleColors: Boolean = true,
)

@Composable
fun PreferenceSettingsPage(
    viewModel: SettingsViewModel,
    uiSize: Int = 2,
    visibleSections: Set<PreferenceSection> = PreferenceSection.entries.toSet(),
    itemVisibility: PreferenceItemVisibility = PreferenceItemVisibility(),
    footerContent: @Composable ColumnScope.() -> Unit = {},
    onNavigateToBottomBarEditor: () -> Unit = {},
    onNavigateToWidgetSettings: () -> Unit = {},
    onNavigateToScheduleColors: () -> Unit = {},
    onNavigateToSemesterConfig: () -> Unit = {},
    onNavigateToCourseManage: () -> Unit = {},
    onNavigateToTimeTableManage: () -> Unit = {},
) {
    MaterialPreferenceSettingsScreen(
        controller = remember(viewModel) { PreferenceUiControllerAdapter(viewModel) },
        uiSize = uiSize,
        visibleSections = visibleSections,
        itemVisibility = itemVisibility,
        footerContent = footerContent,
        onNavigateToBottomBarEditor = onNavigateToBottomBarEditor,
        onNavigateToWidgetSettings = onNavigateToWidgetSettings,
        onNavigateToScheduleColors = onNavigateToScheduleColors,
        onNavigateToSemesterConfig = onNavigateToSemesterConfig,
        onNavigateToCourseManage = onNavigateToCourseManage,
        onNavigateToTimeTableManage = onNavigateToTimeTableManage,
    )
}

@Composable
fun MaterialPreferenceSettingsScreen(
    controller: PreferenceUiController,
    uiSize: Int = 2,
    visibleSections: Set<PreferenceSection> = PreferenceSection.entries.toSet(),
    itemVisibility: PreferenceItemVisibility = PreferenceItemVisibility(),
    footerContent: @Composable ColumnScope.() -> Unit = {},
    onNavigateToBottomBarEditor: () -> Unit = {},
    onNavigateToWidgetSettings: () -> Unit = {},
    onNavigateToScheduleColors: () -> Unit = {},
    onNavigateToSemesterConfig: () -> Unit = {},
    onNavigateToCourseManage: () -> Unit = {},
    onNavigateToTimeTableManage: () -> Unit = {},
) {
    val settings by controller.settings.collectAsState()
    val syncStatus by controller.syncStatus.collectAsState()
    val availableSyncCalendars by controller.availableSyncCalendars.collectAsState()
    val context = LocalContext.current
    val app = context.applicationContext as? App
    val lifecycleOwner = LocalLifecycleOwner.current
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val permissionGate = rememberPermissionGate(snackbarHostState)
    val scope = rememberCoroutineScope()
    var currentToastType by remember { mutableStateOf(ToastType.INFO) }
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var showSourceCalendarSheet by remember { mutableStateOf(false) }
    var showEventDurationPicker by remember { mutableStateOf(false) }
    var showRecognitionModePicker by remember { mutableStateOf(false) }
    var showDailySummaryMorningTimePicker by remember { mutableStateOf(false) }
    var showDailySummaryEveningTimePicker by remember { mutableStateOf(false) }
    var gestureActionPicker by remember { mutableStateOf<FloatingGestureActionPickerState?>(null) }
    var quickMemoAsrModelStatus by remember { mutableStateOf(QuickMemoAsrModelStore.status(context)) }

    val quickMemoAsrModelImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                QuickMemoAsrModelStore.importModelFile(context, uri)
            }
            quickMemoAsrModelStatus = QuickMemoAsrModelStore.status(context)
            val message = result.fold(
                onSuccess = { fileName -> "已导入 $fileName" },
                onFailure = { error -> error.message ?: "模型导入失败" }
            )
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

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
                controller.refreshSyncStatus()
                controller.refreshSyncCalendars()
                permissionGate.resumePending()
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

    // 权限请求统一由 PermissionGate 续接，授权返回后再执行原开启动作。
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        permissionGate.resumePending()
    }
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionGate.resumePending()
    }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionGate.resumePending()
    }
    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        permissionGate.resumePending()
    }

    fun hasNotificationPermission(): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        val runtimeGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return runtimeGranted && manager?.areNotificationsEnabled() == true
    }

    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            })
        }
    }

    fun hasSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    fun hasCalendarPermission(): Boolean =
        app?.permissionCenter?.hasCalendarPermissions(context) == true ||
            (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED)

    fun hasRecordAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun requestAccessibilityPermission() {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    fun requestSmsPermission() {
        smsPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_SMS
            )
        )
    }

    fun requestRecordAudioPermission() {
        recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun enableSmsMonitoring() {
        controller.updatePreference(smsMonitoring = true)
        (context.applicationContext as? App)?.refreshSmsObserver(enabled = true)
        if (!SmsNotificationListenerService.isEnabled(context)) {
            Toast.makeText(context, "建议开启通知监听兜底（系统短信）", Toast.LENGTH_SHORT).show()
        }
    }

    fun requestCalendarPermission() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        } else {
            emptyArray()
        }
        calendarPermissionLauncher.launch(permissions)
    }

    fun enableCalendarSync() {
        controller.enableCalendarSyncAndSyncNow { result ->
            controller.refreshSyncCalendars()
            if (result.isSuccess) {
                showToast("日历同步已开启，并已立即同步")
            } else {
                showToast("日历同步开启失败", ToastType.ERROR)
            }
        }
    }

    fun requireNotificationWhenEnabling(enabled: Boolean, update: () -> Unit) {
        if (enabled) {
            permissionGate.require(
                permissionName = "通知权限",
                isGranted = ::hasNotificationPermission,
                requestPermission = ::requestNotificationPermission,
                onGranted = update,
            )
        } else {
            update()
        }
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
            if (PreferenceSection.DISPLAY in visibleSections) {
            Text("显示", style = sectionTitleStyle)
            SettingsCard {
                if (itemVisibility.showUiSize) {
                    SliderSettingItem(
                        title = "界面大小",
                        subtitle = "调整界面缩放（相对于设备原生大小）",
                        value = settings.uiSize.toFloat(),
                        onValueChange = { controller.updateUiSize(it.toInt()) },
                        valueRange = 1f..3f,
                        steps = 1, // 离散：1, 2, 3
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle,
                        cardValueStyle = cardValueStyle,
                        showValueAsNumber = false // 显示文字：小/中/大
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
                if (itemVisibility.showTomorrowEvents) {
                    SwitchSettingItem(
                        title = "显示明日日程",
                        subtitle = "在今日日程列表底部预览明日安排",
                        checked = settings.showTomorrowEvents,
                        onCheckedChange = { controller.updatePreference(showTomorrow = it) },
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
                if (itemVisibility.showBottomBarEditor) {
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
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
                if (itemVisibility.showWidgetSettings) {
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
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
                    SwitchSettingItem(
                        title = "悬浮日程",
                        subtitle = "在悬浮窗中显示日程",
                        checked = settings.isFloatingWindowEnabled,
                        onCheckedChange = { isChecked ->
                            val updateFloatingWindow = {
                                controller.updatePreference(
                                    floatingWindow = isChecked,
                                    edgeBarEnabled = if (isChecked || settings.voiceInputEnabled) settings.edgeBarEnabled else false,
                                    floatingBallEnabled = if (isChecked || settings.voiceInputEnabled) settings.floatingBallEnabled else false
                                )
                                if (!isChecked && !settings.voiceInputEnabled) {
                                    stopEdgeBarService()
                                    stopFloatingBallService()
                                } else {
                                    if (settings.edgeBarEnabled) startEdgeBarService()
                                    if (settings.floatingBallEnabled) startFloatingBallService()
                                }
                            }
                            if (isChecked) {
                                permissionGate.require(
                                    permissionName = "悬浮窗权限",
                                    isGranted = { hasOverlayPermission },
                                    requestPermission = ::openOverlayPermissionSettings,
                                    onGranted = updateFloatingWindow,
                                )
                            } else {
                                updateFloatingWindow()
                            }
                        },
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle
                    )
            }

            AnimatedVisibility(
                visible = settings.isFloatingWindowEnabled || settings.voiceInputEnabled,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                SettingsCard {
                    if (settings.isFloatingWindowEnabled) {
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
                                controller.updatePreference(floatingEventRange = range)
                            },
                            cardTitleStyle = cardTitleStyle,
                            cardSubtitleStyle = cardSubtitleStyle
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }

                        SideChoiceSettingItem(
                            title = "悬浮窗展开方向",
                            subtitle = if (settings.floatingExpandSide == "LEFT") {
                                "所有入口从左侧滑入"
                            } else {
                                "所有入口从右侧滑入"
                            },
                            selectedSide = settings.floatingExpandSide,
                            onSideSelected = { side -> controller.updatePreference(floatingExpandSide = side) },
                            cardTitleStyle = cardTitleStyle,
                            cardSubtitleStyle = cardSubtitleStyle
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        SwitchSettingItem(
                            title = "侧边栏入口",
                            subtitle = "在屏幕边缘显示侧边栏",
                            checked = settings.edgeBarEnabled,
                            onCheckedChange = { isChecked ->
                                val updateEdgeBar = {
                                    controller.updateEdgeBarSettings(enabled = isChecked)
                                    if (isChecked) {
                                        startEdgeBarService()
                                    } else {
                                        stopEdgeBarService()
                                    }
                                }
                                if (isChecked) {
                                    permissionGate.require(
                                        permissionName = "悬浮窗权限",
                                        isGranted = { hasOverlayPermission },
                                        requestPermission = ::openOverlayPermissionSettings,
                                        onGranted = updateEdgeBar,
                                    )
                                } else {
                                    updateEdgeBar()
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
                                    modifier = Modifier.padding(horizontal = 16.dp),
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
                                    onSideSelected = { side -> controller.updateEdgeBarSettings(side = side) },
                                    cardTitleStyle = cardTitleStyle,
                                    cardSubtitleStyle = cardSubtitleStyle
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                                FloatingGestureActionSettingItem(
                                    title = "单击动作",
                                    currentAction = settings.edgeBarSingleTapAction,
                                    onActionSelected = { controller.updatePreference(edgeBarSingleTapAction = it) },
                                    onNativeClick = {
                                        gestureActionPicker = FloatingGestureActionPickerState(
                                            FloatingGestureActionTarget.EDGE_BAR,
                                            FloatingGestureActionSlot.SINGLE_TAP
                                        )
                                    },
                                    cardTitleStyle = cardTitleStyle,
                                    cardSubtitleStyle = cardSubtitleStyle,
                                    cardValueStyle = cardValueStyle
                                )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                                FloatingGestureActionSettingItem(
                                    title = "双击动作",
                                    currentAction = settings.edgeBarDoubleTapAction,
                                    onActionSelected = { controller.updatePreference(edgeBarDoubleTapAction = it) },
                                    onNativeClick = {
                                        gestureActionPicker = FloatingGestureActionPickerState(
                                            FloatingGestureActionTarget.EDGE_BAR,
                                            FloatingGestureActionSlot.DOUBLE_TAP
                                        )
                                    },
                                    cardTitleStyle = cardTitleStyle,
                                    cardSubtitleStyle = cardSubtitleStyle,
                                    cardValueStyle = cardValueStyle
                                )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                                FloatingGestureActionSettingItem(
                                    title = "长按动作",
                                    currentAction = settings.edgeBarLongPressAction,
                                    onActionSelected = { controller.updatePreference(edgeBarLongPressAction = it) },
                                    onNativeClick = {
                                        gestureActionPicker = FloatingGestureActionPickerState(
                                            FloatingGestureActionTarget.EDGE_BAR,
                                            FloatingGestureActionSlot.LONG_PRESS
                                        )
                                    },
                                    cardTitleStyle = cardTitleStyle,
                                    cardSubtitleStyle = cardSubtitleStyle,
                                    cardValueStyle = cardValueStyle
                                )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                                SliderSettingItem(
                                    title = "纵向位置",
                                    subtitle = "上下位置百分比",
                                    value = settings.edgeBarYPercent,
                                    onValueChange = { controller.updateEdgeBarSettings(yPercent = it.roundToInt().toFloat()) },
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
                                    onValueChange = { controller.updateEdgeBarSettings(widthDp = it.roundToInt()) },
                                    valueRange = 4f..100f,
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
                                    onValueChange = { controller.updateEdgeBarSettings(heightDp = it.roundToInt()) },
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
                                    onValueChange = { controller.updateEdgeBarSettings(alpha = it.roundToInt() / 100f) },
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
                                            controller.updateEdgeBarSettings(
                                                enabled = true,
                                                side = "RIGHT",
                                                yPercent = 50f,
                                                widthDp = 8,
                                                heightDp = 120,
                                                alpha = 0.4f,
                                                singleTapAction = FloatingBallGestureAction.OPEN_FLOATING_SCHEDULE,
                                                doubleTapAction = FloatingBallGestureAction.QUICK_RECOGNITION,
                                                longPressAction = FloatingBallGestureAction.QUICK_MEMO_RECORDING
                                            )
                                            startEdgeBarService()
                                        },
                                        shape = RoundedCornerShape(50.dp),
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            labelColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        border = null,
                                        label = { Text("恢复默认") }
                                    )
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        SwitchSettingItem(
                            title = "悬浮球入口",
                            subtitle = "在屏幕中显示悬浮球",
                            checked = settings.floatingBallEnabled,
                            onCheckedChange = { isChecked ->
                                val updateFloatingBall = {
                                    controller.updatePreference(floatingBallEnabled = isChecked)
                                    if (isChecked) {
                                        startFloatingBallService()
                                    } else {
                                        stopFloatingBallService()
                                    }
                                }
                                if (isChecked) {
                                    permissionGate.require(
                                        permissionName = "悬浮窗权限",
                                        isGranted = { hasOverlayPermission },
                                        requestPermission = ::openOverlayPermissionSettings,
                                        onGranted = updateFloatingBall,
                                    )
                                } else {
                                    updateFloatingBall()
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
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                                FloatingGestureActionSettingItem(
                                    title = "单击动作",
                                    currentAction = settings.floatingBallSingleTapAction,
                                    onActionSelected = { controller.updatePreference(floatingBallSingleTapAction = it) },
                                    onNativeClick = {
                                        gestureActionPicker = FloatingGestureActionPickerState(
                                            FloatingGestureActionTarget.FLOATING_BALL,
                                            FloatingGestureActionSlot.SINGLE_TAP
                                        )
                                    },
                                    cardTitleStyle = cardTitleStyle,
                                    cardSubtitleStyle = cardSubtitleStyle,
                                    cardValueStyle = cardValueStyle
                                )

                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                                FloatingGestureActionSettingItem(
                                    title = "双击动作",
                                    currentAction = settings.floatingBallDoubleTapAction,
                                    onActionSelected = { controller.updatePreference(floatingBallDoubleTapAction = it) },
                                    onNativeClick = {
                                        gestureActionPicker = FloatingGestureActionPickerState(
                                            FloatingGestureActionTarget.FLOATING_BALL,
                                            FloatingGestureActionSlot.DOUBLE_TAP
                                        )
                                    },
                                    cardTitleStyle = cardTitleStyle,
                                    cardSubtitleStyle = cardSubtitleStyle,
                                    cardValueStyle = cardValueStyle
                                )

                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                                FloatingGestureActionSettingItem(
                                    title = "长按动作",
                                    currentAction = settings.floatingBallLongPressAction,
                                    onActionSelected = { controller.updatePreference(floatingBallLongPressAction = it) },
                                    onNativeClick = {
                                        gestureActionPicker = FloatingGestureActionPickerState(
                                            FloatingGestureActionTarget.FLOATING_BALL,
                                            FloatingGestureActionSlot.LONG_PRESS
                                        )
                                    },
                                    cardTitleStyle = cardTitleStyle,
                                    cardSubtitleStyle = cardSubtitleStyle,
                                    cardValueStyle = cardValueStyle
                                )

                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                                SliderSettingItem(
                                    title = "尺寸",
                                    subtitle = "悬浮球直径",
                                    value = settings.floatingBallSizeDp.toFloat(),
                                    onValueChange = { controller.updatePreference(floatingBallSizeDp = it.roundToInt()) },
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
                                    onValueChange = { controller.updatePreference(floatingBallAlpha = it.roundToInt() / 100f) },
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
                                            controller.updatePreference(
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
                                        shape = RoundedCornerShape(50.dp),
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            labelColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        border = null,
                                        label = { Text("恢复默认") }
                                    )
                                }
                            }
                        }
                }
            }

            }

            // ================== 随口记板块 ==================
            if (PreferenceSection.QUICK_MEMO in visibleSections) {
            Text("随口记", style = sectionTitleStyle)
            QuickMemoPreferenceCard(
                settings = settings,
                asrModelStatus = quickMemoAsrModelStatus,
                cardTitleStyle = cardTitleStyle,
                cardSubtitleStyle = cardSubtitleStyle,
                onVoiceInputEnabledChange = { enabled ->
                    controller.updatePreference(voiceInputEnabled = enabled)
                    if (enabled) {
                        app?.runtimeCenter?.startEdgeBarIfNeeded()
                    } else if (!settings.isFloatingWindowEnabled) {
                        stopEdgeBarService()
                        stopFloatingBallService()
                    }
                },
                onFloatingLongPressChange = { enabled ->
                    if (enabled) {
                        permissionGate.require(
                            permissionName = "麦克风权限",
                            isGranted = ::hasRecordAudioPermission,
                            requestPermission = ::requestRecordAudioPermission,
                            onGranted = { controller.updatePreference(floatingVoiceLongPressEnabled = true) },
                        )
                    } else {
                        controller.updatePreference(floatingVoiceLongPressEnabled = false)
                    }
                },
                onRecordingDisplayModeChange = { mode ->
                    controller.updatePreference(quickMemoRecordingDisplayMode = mode)
                },
                onAutoStopEnabledChange = { enabled ->
                    controller.updateQuickMemoAutoStop(enabled = enabled)
                },
                onAutoStopSecondsChange = { seconds ->
                    controller.updateQuickMemoAutoStop(seconds = seconds)
                },
                onTextAutoPinChange = { enabled ->
                    if (enabled) {
                        permissionGate.require(
                            permissionName = "通知权限",
                            isGranted = ::hasNotificationPermission,
                            requestPermission = ::requestNotificationPermission,
                            onGranted = { controller.updatePreference(floatingTextQuickMemoAutoPinEnabled = true) },
                        )
                    } else {
                        controller.updatePreference(floatingTextQuickMemoAutoPinEnabled = false)
                    }
                },
                onVoiceAutoPinChange = { enabled ->
                    if (enabled) {
                        permissionGate.require(
                            permissionName = "通知权限",
                            isGranted = ::hasNotificationPermission,
                            requestPermission = ::requestNotificationPermission,
                            onGranted = { controller.updatePreference(voiceQuickMemoAutoPinEnabled = true) },
                        )
                    } else {
                        controller.updatePreference(voiceQuickMemoAutoPinEnabled = false)
                    }
                },
                onImportAsrModel = { quickMemoAsrModelImportLauncher.launch(arrayOf("*/*")) }
            )

            }

            // ================== 操作板块 ==================
            if (PreferenceSection.OPERATION in visibleSections) {
            Text("操作", style = sectionTitleStyle)
            SettingsCard {
                if (itemVisibility.showHapticFeedback) {
                    SwitchSettingItem(
                        title = "触感反馈",
                        subtitle = "点击、长按和滑动到阈值时提供轻微反馈",
                        checked = settings.hapticFeedbackEnabled,
                        onCheckedChange = { controller.updatePreference(hapticFeedbackEnabled = it) },
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
                    VolumeLongPressSettingItem(
                        title = "长按音量+",
                        subtitle = "自定义长按音量+动作",
                        checked = settings.volumeUpLongPressEnabled,
                        action = settings.volumeUpLongPressAction,
                        onCheckedChange = { isChecked ->
                            val updateVolumeShortcut = {
                                controller.updatePreference(
                                    volumeUpLongPressEnabled = isChecked,
                                    volumeUpLongPressAction = if (isChecked) settings.volumeUpLongPressAction.coerceIn(1, 3) else settings.volumeUpLongPressAction
                                )
                            }
                            if (isChecked) {
                                permissionGate.require(
                                    permissionName = "无障碍权限",
                                    isGranted = { TextAccessibilityService.isConnected() },
                                    requestPermission = ::requestAccessibilityPermission,
                                    onGranted = updateVolumeShortcut,
                                )
                            } else {
                                updateVolumeShortcut()
                            }
                        },
                        onActionChange = { action ->
                            controller.updatePreference(volumeUpLongPressAction = action)
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
                                permissionGate.require(
                                    permissionName = "短信权限",
                                    isGranted = ::hasSmsPermission,
                                    requestPermission = ::requestSmsPermission,
                                    onGranted = ::enableSmsMonitoring,
                                )
                            } else {
                                (context.applicationContext as? App)?.refreshSmsObserver(enabled = false)
                                controller.updatePreference(smsMonitoring = false)
                            }
                        },
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle
                    )
            }

            }

            // ================== 通知板块 ==================
            if (PreferenceSection.NOTIFICATION in visibleSections) {
            Text("通知", style = sectionTitleStyle)
            SettingsCard {
                    SwitchSettingItem(
                        title = "每日提醒",
                        subtitle = "今日 ${formatMinuteOfDay(settings.dailySummaryMorningMinuteOfDay)}，明日 ${formatMinuteOfDay(settings.dailySummaryEveningMinuteOfDay)}",
                        checked = settings.isDailySummaryEnabled,
                        onCheckedChange = { isChecked ->
                            requireNotificationWhenEnabling(isChecked) {
                                controller.updatePreference(dailySummary = isChecked)
                                if (isChecked) {
                                    app?.runtimeCenter?.scheduleDailySummary()
                                }
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
                                    modifier = Modifier.padding(horizontal = 16.dp),
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
                            modifier = Modifier.padding(horizontal = 16.dp),
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
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                    SwitchSettingItem(
                        title = "实况通知",
                        subtitle = "日程开始时显示实况通知",
                        checked = settings.isLiveCapsuleEnabled,
                        onCheckedChange = { isChecked ->
                            requireNotificationWhenEnabling(isChecked) {
                                controller.updatePreference(liveCapsule = isChecked)
                                if (isChecked) showToast("实况通知已开启", ToastType.INFO)
                            }
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
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            SwitchSettingItem(
                                title = "取件码聚合（Beta）",
                                subtitle = "当同时存在多个取件码时合并显示为一个实况通知",
                                checked = settings.isPickupAggregationEnabled,
                                onCheckedChange = { isChecked ->
                                    controller.updatePreference(pickupAggregation = isChecked)
                                },
                                cardTitleStyle = cardTitleStyle,
                                cardSubtitleStyle = cardSubtitleStyle
                            )
                        }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
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
                            requireNotificationWhenEnabling(isChecked) {
                                controller.updatePreference(advanceReminderEnabled = isChecked)
                                if (isChecked && settings.advanceReminderMinutes > 0) {
                                    val hasDuplicate = controller.hasDuplicateAdvanceReminder(settings.advanceReminderMinutes)
                                    if (hasDuplicate) {
                                        showToast("检测到可能存在的重复提醒", ToastType.INFO)
                                    }
                                }
                            }
                        },
                        onMinutesChange = { minutes ->
                            controller.updatePreference(advanceReminderMinutes = minutes)
                        },
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    val transitAutoCheckInOptions = MySettings.TRANSIT_AUTO_CHECK_IN_MINUTE_OPTIONS
                    val transitAutoCheckInMinutes = MySettings.normalizeTransitAutoCheckInMinutes(
                        settings.transitAutoCheckInMinutes
                    )
                    val transitAutoCheckInIndex = transitAutoCheckInOptions.indexOf(transitAutoCheckInMinutes)
                    EditionSwitchSliderSettingItem(
                        title = "列车与航班信息自动切换",
                        subtitle = if (settings.transitAutoCheckInEnabled) {
                            "出发前 ${transitAutoCheckInMinutes} 分钟自动标记为已检票或已登机"
                        } else {
                            "出发前自动切换到车次、航班和座位信息"
                        },
                        checked = settings.transitAutoCheckInEnabled,
                        onCheckedChange = { enabled ->
                            requireNotificationWhenEnabling(enabled) {
                                controller.updatePreference(transitAutoCheckInEnabled = enabled)
                            }
                        },
                        optionTitle = "自动切换时间",
                        optionSummary = "可选 10、15 或 30 分钟",
                        value = transitAutoCheckInIndex.toFloat(),
                        valueText = "${transitAutoCheckInMinutes} 分钟",
                        onValueChange = { value ->
                            val index = value.roundToInt().coerceIn(transitAutoCheckInOptions.indices)
                            controller.updatePreference(
                                transitAutoCheckInMinutes = transitAutoCheckInOptions[index]
                            )
                        },
                        valueRange = 0f..transitAutoCheckInOptions.lastIndex.toFloat(),
                        steps = transitAutoCheckInOptions.size - 2,
                        valueLabels = transitAutoCheckInOptions.map { "$it 分钟" },
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle
                    )

                if (itemVisibility.showNetworkSpeedCapsule) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    SwitchSettingItem(
                        title = "网速胶囊",
                        subtitle = "在状态栏显示下载速度",
                        checked = settings.isNetworkSpeedCapsuleEnabled,
                        onCheckedChange = { isChecked ->
                            requireNotificationWhenEnabling(isChecked) {
                                controller.updatePreference(networkSpeedCapsule = isChecked)
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
                            text = "网速胶囊会覆盖其他胶囊显示",
                            style = cardSubtitleStyle,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

            }


            }

            // ================== AI 板块 ==================
            if (PreferenceSection.AI in visibleSections) {
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
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    SwitchSettingItem(
                        title = "使用多模态AI",
                        subtitle = "开启后图片识别将使用多模态模型",
                        checked = settings.useMultimodalAi,
                        onCheckedChange = { isChecked ->
                            controller.updatePreference(useMultimodalAi = isChecked)
                            showToast(if (isChecked) "已切换为多模态AI" else "已切换为文本AI")
                        },
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    SwitchSettingItem(
                        title = "关闭思考",
                        subtitle = "仅适配 OpenAI",
                        checked = settings.disableThinking,
                        onCheckedChange = { isChecked ->
                            controller.updatePreference(disableThinking = isChecked)
                            showToast(if (isChecked) "快速模式已开启" else "快速模式已关闭")
                        },
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle
                    )

            }

            }

            // ================== 日程板块 ==================
            if (PreferenceSection.SCHEDULE in visibleSections) {
            Text("日程", style = sectionTitleStyle)
            SettingsCard {
                    SwitchSettingItem(
                        title = "日历同步",
                        subtitle = "将课程和日程同步到系统日历",
                        checked = syncStatus.isEnabled,
                        onCheckedChange = { isChecked ->
                            if (isChecked) {
                                permissionGate.require(
                                    permissionName = "日历读写权限",
                                    isGranted = ::hasCalendarPermission,
                                    requestPermission = ::requestCalendarPermission,
                                    onGranted = ::enableCalendarSync,
                                )
                            } else {
                                controller.toggleCalendarSync(false)
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
                                    modifier = Modifier.padding(horizontal = 16.dp),
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
                                enabled = true,
                                onClick = {
                                    permissionGate.require(
                                        permissionName = "日历读写权限",
                                        isGranted = ::hasCalendarPermission,
                                        requestPermission = ::requestCalendarPermission,
                                        onGranted = { showSourceCalendarSheet = true },
                                    )
                                },
                                cardTitleStyle = cardTitleStyle,
                                cardSubtitleStyle = cardSubtitleStyle,
                                cardValueStyle = cardValueStyle
                            )

                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )

                            SliderSettingItem(
                                title = "兜底同步频率",
                                subtitle = "仅作兜底轮询，优先即时监听",
                                value = syncStatus.syncIntervalSeconds.toFloat(),
                                onValueChange = { seconds ->
                                    controller.updateSyncIntervalSeconds(seconds.toInt())
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

                if (itemVisibility.showAutoArchive) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                    SwitchSettingItem(
                        title = "自动归档",
                        subtitle = "日程过期后立即自动归档",
                        checked = settings.autoArchiveEnabled,
                        onCheckedChange = { isChecked ->
                            controller.updatePreference(autoArchive = isChecked)
                        },
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle
                    )
                }

                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
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
                if (itemVisibility.showScheduleColors) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
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
            }

            }

            // ================== 课表板块 ==================
            if (PreferenceSection.COURSE in visibleSections) {
            Text("课表", style = sectionTitleStyle)
            SettingsCard {
                    SwitchSettingItem(
                        title = "主页下滑进入课表",
                        subtitle = "开启后可在主页下滑进入课表",
                        checked = settings.courseFeatureEnabled,
                        onCheckedChange = { isChecked ->
                            controller.updatePreference(courseFeatureEnabled = isChecked)
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
                                modifier = Modifier.padding(horizontal = 16.dp),
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
                                modifier = Modifier.padding(horizontal = 16.dp),
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
                                modifier = Modifier.padding(horizontal = 16.dp),
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

            }

            // ================== 截图板块 (新) ==================
            if (PreferenceSection.SCREENSHOT in visibleSections) {
            // 注意：现在它在 Column 内部，位于“日程”卡片之后
            Text("截图", style = sectionTitleStyle)
            SettingsCard {
                    SliderSettingItem(
                        title = "截图延迟",
                        subtitle = "截图与分析之间的等待时间",
                        value = MySettings.normalizeScreenshotDelayMs(settings.screenshotDelayMs).toFloat(),
                        onValueChange = { controller.updateScreenshotDelay(it.toLong()) },
                        valueRange = MySettings.SCREENSHOT_DELAY_MIN_MS.toFloat()..MySettings.SCREENSHOT_DELAY_MAX_MS.toFloat(),
                        steps = 0, // 0 = 无极调节
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle,
                        cardValueStyle = cardValueStyle,
                        showValueAsNumber = true, // 开启数字显示
                        valueUnit = "ms"
                    )
            }
            }

            footerContent()

        } // <--- Column 结束在这里，确保所有板块都在里面

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp + bottomInset),
            snackbar = { data -> UniversalSnackbar(data = data, type = currentToastType) }
        )

        if (showSourceCalendarSheet) {
            SourceCalendarPickerSheet(
                calendars = availableSyncCalendars,
                initialSelection = syncStatus.sourceCalendarIds.toSet(),
                onDismiss = { showSourceCalendarSheet = false },
                onConfirm = { selectedIds ->
                    controller.updateSourceCalendars(selectedIds) { result ->
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
                    controller.updatePreference(defaultEventDurationMinutes = duration)
                    showEventDurationPicker = false
                }
            )
        }

        if (showRecognitionModePicker) {
            RecognitionModePickerDialog(
                selectedMode = settings.recognitionMode,
                onDismiss = { showRecognitionModePicker = false },
                onConfirm = { mode ->
                    controller.updatePreference(recognitionMode = mode)
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
                    controller.updateDailySummaryTimes(
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
                    controller.updateDailySummaryTimes(
                        eveningMinuteOfDay = minuteOfDay,
                        onUpdated = { app?.runtimeCenter?.scheduleDailySummary() }
                    )
                    showDailySummaryEveningTimePicker = false
                }
            )
        }

        gestureActionPicker?.let { request ->
            FloatingGestureActionPickerDialog(
                request = request,
                selectedAction = request.currentAction(settings),
                onDismiss = { gestureActionPicker = null },
                onActionSelected = { action ->
                    when (request.target) {
                        FloatingGestureActionTarget.EDGE_BAR -> when (request.slot) {
                            FloatingGestureActionSlot.SINGLE_TAP -> controller.updatePreference(edgeBarSingleTapAction = action)
                            FloatingGestureActionSlot.DOUBLE_TAP -> controller.updatePreference(edgeBarDoubleTapAction = action)
                            FloatingGestureActionSlot.LONG_PRESS -> controller.updatePreference(edgeBarLongPressAction = action)
                        }
                        FloatingGestureActionTarget.FLOATING_BALL -> when (request.slot) {
                            FloatingGestureActionSlot.SINGLE_TAP -> controller.updatePreference(floatingBallSingleTapAction = action)
                            FloatingGestureActionSlot.DOUBLE_TAP -> controller.updatePreference(floatingBallDoubleTapAction = action)
                            FloatingGestureActionSlot.LONG_PRESS -> controller.updatePreference(floatingBallLongPressAction = action)
                        }
                    }
                    gestureActionPicker = null
                }
            )
        }
    }
    }
}

@Composable
private fun QuickMemoPreferenceCard(
    settings: MySettings,
    asrModelStatus: QuickMemoAsrModelStatus,
    cardTitleStyle: TextStyle,
    cardSubtitleStyle: TextStyle,
    onVoiceInputEnabledChange: (Boolean) -> Unit,
    onFloatingLongPressChange: (Boolean) -> Unit,
    onRecordingDisplayModeChange: (Int) -> Unit,
    onAutoStopEnabledChange: (Boolean) -> Unit,
    onAutoStopSecondsChange: (Int) -> Unit,
    onTextAutoPinChange: (Boolean) -> Unit,
    onVoiceAutoPinChange: (Boolean) -> Unit,
    onImportAsrModel: () -> Unit,
) {
    SettingsCard {
        SwitchSettingItem(
            title = "随口记",
            subtitle = "随口记功能总开关",
            checked = settings.voiceInputEnabled,
            onCheckedChange = onVoiceInputEnabledChange,
            cardTitleStyle = cardTitleStyle,
            cardSubtitleStyle = cardSubtitleStyle
        )

        AnimatedVisibility(
            visible = settings.voiceInputEnabled,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                QuickMemoPreferenceDivider()
                SwitchSettingItem(
                    title = "悬浮窗长按随口记",
                    subtitle = "呼出悬浮窗后，再次长按音量+开始随口记录音",
                    checked = settings.floatingVoiceLongPressEnabled,
                    onCheckedChange = onFloatingLongPressChange,
                    cardTitleStyle = cardTitleStyle,
                    cardSubtitleStyle = cardSubtitleStyle
                )

                QuickMemoPreferenceDivider()
                QuickMemoRecordingDisplayPreference(
                    mode = settings.quickMemoRecordingDisplayMode,
                    onModeChange = onRecordingDisplayModeChange,
                    cardTitleStyle = cardTitleStyle,
                    cardSubtitleStyle = cardSubtitleStyle,
                )

                QuickMemoPreferenceDivider()
                val normalizedSeconds = MySettings.normalizeQuickMemoAutoStopSeconds(
                    settings.quickMemoAutoStopSeconds
                )
                HapticValueChangeEffect(valueKey = normalizedSeconds)
                EditionSwitchSliderSettingItem(
                    title = "自动结束录音",
                    subtitle = if (settings.quickMemoAutoStopEnabled) {
                        "录音 ${normalizedSeconds} 秒后自动保存"
                    } else {
                        "关闭时由用户手动结束并保存录音"
                    },
                    checked = settings.quickMemoAutoStopEnabled,
                    onCheckedChange = onAutoStopEnabledChange,
                    optionTitle = "自动结束时长",
                    optionSummary = "录音达到设定时长后自动保存",
                    value = normalizedSeconds.toFloat(),
                    valueText = "${normalizedSeconds} 秒",
                    onValueChange = { onAutoStopSecondsChange(it.roundToInt()) },
                    valueRange = MySettings.QUICK_MEMO_AUTO_STOP_MIN_SECONDS.toFloat()..
                        MySettings.QUICK_MEMO_AUTO_STOP_MAX_SECONDS.toFloat(),
                    steps = 0,
                    cardTitleStyle = cardTitleStyle,
                    cardSubtitleStyle = cardSubtitleStyle
                )

                QuickMemoPreferenceDivider()
                SwitchSettingItem(
                    title = "文本随口记同步挂起",
                    subtitle = "随口记文本保存后，同步挂起到实况通知",
                    checked = settings.floatingTextQuickMemoAutoPinEnabled,
                    onCheckedChange = onTextAutoPinChange,
                    cardTitleStyle = cardTitleStyle,
                    cardSubtitleStyle = cardSubtitleStyle
                )

                QuickMemoPreferenceDivider()
                SwitchSettingItem(
                    title = "语音随口记同步挂起",
                    subtitle = "随口记语音转写后，同步挂起到实况通知",
                    checked = settings.voiceQuickMemoAutoPinEnabled,
                    onCheckedChange = onVoiceAutoPinChange,
                    cardTitleStyle = cardTitleStyle,
                    cardSubtitleStyle = cardSubtitleStyle
                )

                QuickMemoPreferenceDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "语音转写模型",
                            style = cardTitleStyle,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = formatQuickMemoAsrModelStatus(asrModelStatus),
                            style = cardSubtitleStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    EditionButton(onClick = onImportAsrModel) {
                        Text(if (asrModelStatus.ready) "更换模型" else "导入模型")
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickMemoRecordingDisplayPreference(
    mode: Int,
    onModeChange: (Int) -> Unit,
    cardTitleStyle: TextStyle,
    cardSubtitleStyle: TextStyle,
) {
    val normalizedMode = QuickMemoRecordingDisplayMode.normalize(mode)
    TwoOptionSettingItem(
        title = "录音展示",
        subtitle = if (normalizedMode == QuickMemoRecordingDisplayMode.FLOATING_WINDOW) {
            "所有入口录音时使用悬浮窗"
        } else {
            "所有入口录音时使用实况通知"
        },
        selectedValue = normalizedMode,
        firstValue = QuickMemoRecordingDisplayMode.LIVE_CAPSULE,
        firstLabel = "实况通知",
        secondValue = QuickMemoRecordingDisplayMode.FLOATING_WINDOW,
        secondLabel = "悬浮窗",
        onValueSelected = onModeChange,
        cardTitleStyle = cardTitleStyle,
        cardSubtitleStyle = cardSubtitleStyle,
    )
}

@Composable
private fun QuickMemoPreferenceDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

private fun formatQuickMemoAsrModelStatus(status: QuickMemoAsrModelStatus): String {
    return when {
        status.ready -> "已导入本地模型，可离线转写"
        !status.modelReady && !status.tokensReady -> "未导入模型，请依次导入 model.int8.onnx 和 tokens.txt"
        !status.modelReady -> "缺少 model.int8.onnx 或 model.onnx"
        else -> "缺少 tokens.txt"
    }
}

@Composable
private fun FloatingGestureActionSettingItem(
    title: String,
    currentAction: Int,
    onActionSelected: (Int) -> Unit,
    onNativeClick: () -> Unit,
    cardTitleStyle: TextStyle,
    cardSubtitleStyle: TextStyle,
    cardValueStyle: TextStyle,
) {
    val actions = FloatingBallGestureAction.ALL
    val normalizedAction = FloatingBallGestureAction.normalize(currentAction)
    EditionDropdownActionSettingItem(
        title = title,
        subtitle = "当前：${FloatingBallGestureAction.label(normalizedAction)}",
        options = actions.map { action ->
            if (action == FloatingBallGestureAction.NONE) "无" else FloatingBallGestureAction.label(action)
        },
        selectedIndex = actions.indexOf(normalizedAction).coerceAtLeast(0),
        onSelectedIndexChange = { onActionSelected(actions[it]) },
        icon = Icons.Default.ChevronRight,
        onNativeClick = onNativeClick,
        cardTitleStyle = cardTitleStyle,
        cardSubtitleStyle = cardSubtitleStyle,
        cardValueStyle = cardValueStyle,
    )
}

private data class FloatingGestureActionPickerState(
    val target: FloatingGestureActionTarget,
    val slot: FloatingGestureActionSlot
) {
    val title: String = "${target.title}${slot.title}"
}

private enum class FloatingGestureActionTarget(val title: String) {
    EDGE_BAR("侧边栏"),
    FLOATING_BALL("悬浮球")
}

private enum class FloatingGestureActionSlot(val title: String) {
    SINGLE_TAP("单击动作"),
    DOUBLE_TAP("双击动作"),
    LONG_PRESS("长按动作")
}

private fun FloatingGestureActionPickerState.currentAction(settings: MySettings): Int {
    return when (target) {
        FloatingGestureActionTarget.EDGE_BAR -> when (slot) {
            FloatingGestureActionSlot.SINGLE_TAP -> settings.edgeBarSingleTapAction
            FloatingGestureActionSlot.DOUBLE_TAP -> settings.edgeBarDoubleTapAction
            FloatingGestureActionSlot.LONG_PRESS -> settings.edgeBarLongPressAction
        }
        FloatingGestureActionTarget.FLOATING_BALL -> when (slot) {
            FloatingGestureActionSlot.SINGLE_TAP -> settings.floatingBallSingleTapAction
            FloatingGestureActionSlot.DOUBLE_TAP -> settings.floatingBallDoubleTapAction
            FloatingGestureActionSlot.LONG_PRESS -> settings.floatingBallLongPressAction
        }
    }
}

@Composable
private fun FloatingGestureActionPickerDialog(
    request: FloatingGestureActionPickerState,
    selectedAction: Int,
    onDismiss: () -> Unit,
    onActionSelected: (Int) -> Unit
) {
    val options = FloatingBallGestureAction.ALL
    val selectedActionIndex = options.indexOf(FloatingBallGestureAction.normalize(selectedAction)).coerceAtLeast(0)
    var selectedIndex by remember(request, selectedAction) { mutableIntStateOf(selectedActionIndex) }
    val haptics = rememberAppHaptics()
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { CenteredDialogTitle(request.title) },
        text = {
            WheelPicker(
                items = options.map { FloatingBallGestureAction.label(it) },
                initialIndex = selectedActionIndex,
                onSelectionChanged = { selectedIndex = it }
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    haptics.confirm()
                    onActionSelected(options[selectedIndex])
                }
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = { haptics.click(); onDismiss() }) {
                Text("取消")
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
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
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = cardTitleStyle)
            Text(subtitle, style = cardSubtitleStyle)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (selectedValue == firstValue) {
                Button(onClick = { haptics.selection(); onValueSelected(firstValue) }) {
                    Text(firstLabel)
                }
            } else {
                OutlinedButton(onClick = { haptics.selection(); onValueSelected(firstValue) }) {
                    Text(firstLabel)
                }
            }
            if (selectedValue == secondValue) {
                Button(onClick = { haptics.selection(); onValueSelected(secondValue) }) {
                    Text(secondLabel)
                }
            } else {
                OutlinedButton(onClick = { haptics.selection(); onValueSelected(secondValue) }) {
                    Text(secondLabel)
                }
            }
        }
    }
}

// ActionSettingItem 已抽至 SettingsRowComponents.kt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceCalendarPickerSheet(
    calendars: List<com.antgskds.calendarassistant.feature.schedule.api.model.CalendarManager.CalendarInfo>,
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
                text = "将日程同步到系统日历",
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
                            EditionCheckbox(
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
            EditionButton(
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
    selectedCalendars: List<com.antgskds.calendarassistant.feature.schedule.api.model.CalendarManager.CalendarInfo>
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
    calendar: com.antgskds.calendarassistant.feature.schedule.api.model.CalendarManager.CalendarInfo
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
    calendar: com.antgskds.calendarassistant.feature.schedule.api.model.CalendarManager.CalendarInfo
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
    EditionCategoricalPreference(
        title = title,
        summary = subtitle,
        options = listOf("全部日程", "今日日程", "今日+明日"),
        selectedIndex = eventRange.coerceIn(0, 2),
        onSelectedIndexChange = onEventRangeChange,
        titleTextStyle = cardTitleStyle,
        summaryTextStyle = cardSubtitleStyle,
        showSelectedValue = false,
        labelsAboveSlider = true,
        sliderTopPadding = 12.dp,
    )
}
