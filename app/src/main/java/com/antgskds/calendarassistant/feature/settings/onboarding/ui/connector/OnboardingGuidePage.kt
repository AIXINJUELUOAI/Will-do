package com.antgskds.calendarassistant.feature.settings.onboarding.ui.connector

import com.antgskds.calendarassistant.shared.ui.material.component.LocalAppPageBottomPadding
import com.antgskds.calendarassistant.shared.ui.edition.EditionTextField
import com.antgskds.calendarassistant.shared.ui.edition.EditionButton
import com.antgskds.calendarassistant.shared.ui.edition.EditionCategoricalPreference

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.CalendarContract
import android.provider.Settings
import com.antgskds.calendarassistant.shared.util.AppLogger as Log
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.antgskds.calendarassistant.app.ui.state.SettingsViewModel
import com.antgskds.calendarassistant.app.ui.state.MainViewModel
import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import com.antgskds.calendarassistant.feature.settings.data.model.RecognitionMode
import com.antgskds.calendarassistant.feature.settings.laboratory.ui.connector.LaboratorySettingsContent
import com.antgskds.calendarassistant.feature.settings.laboratory.ui.connector.LaboratoryItemVisibility
import com.antgskds.calendarassistant.feature.settings.laboratory.ui.contract.LaboratoryUiAction
import com.antgskds.calendarassistant.feature.settings.laboratory.ui.contract.LaboratoryUiState
import com.antgskds.calendarassistant.feature.settings.preference.ui.connector.PreferenceSection
import com.antgskds.calendarassistant.feature.settings.preference.ui.connector.PreferenceItemVisibility
import com.antgskds.calendarassistant.feature.settings.preference.ui.connector.PreferenceSettingsPage
import com.antgskds.calendarassistant.feature.recognition.ui.connector.AiSettingsPage
import com.antgskds.calendarassistant.feature.weather.ui.connector.WeatherSettingsPage
import com.antgskds.calendarassistant.platform.accessibility.TextAccessibilityService
import com.antgskds.calendarassistant.platform.permission.PrivilegedPermissionController
import com.antgskds.calendarassistant.platform.permission.PrivilegedPermissionKey
import com.antgskds.calendarassistant.platform.receiver.SmsNotificationListenerService
import com.antgskds.calendarassistant.shared.ui.edition.EditionSwitch
import com.antgskds.calendarassistant.shared.ui.interaction.rememberAppHaptics
import com.antgskds.calendarassistant.shared.ui.material.component.AppSettingsCard
import com.antgskds.calendarassistant.shared.ui.material.component.UniversalSnackbar
import com.antgskds.calendarassistant.shared.ui.material.component.ToastType
import com.antgskds.calendarassistant.shared.ui.material.component.PredictiveFloatingActionCard
import com.antgskds.calendarassistant.shared.ui.material.settings.ActionSettingItem
import com.antgskds.calendarassistant.shared.ui.material.settings.SliderSettingItem
import com.antgskds.calendarassistant.shared.ui.material.settings.SwitchSettingItem
import com.antgskds.calendarassistant.shared.ui.permission.rememberPermissionGate
import com.antgskds.calendarassistant.shared.util.OsUtils
import com.antgskds.calendarassistant.shared.util.PrivilegeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val ONBOARDING_LOG_TAG = "OnboardingGuide"
private const val ROOT_PROMPT_PREFS = "onboarding_root_prompt"
private const val ROOT_PROMPT_SHOWN = "root_prompt_shown"

@Composable
fun OnboardingGuidePage(
    settingsViewModel: SettingsViewModel,
    mainViewModel: MainViewModel,
    uiSize: Int = 2,
    onFinish: (() -> Unit)? = null,
) {
    val settings by settingsViewModel.settings.collectAsState()
    val syncStatus by settingsViewModel.syncStatus.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val permissionGate = rememberPermissionGate(snackbarHostState)
    val haptics = rememberAppHaptics(settings.hapticFeedbackEnabled)
    var page by remember { mutableIntStateOf(0) }
    val simulateRoot = settings.developerSimulateRootEnabled
    var privilegedModeActive by remember(simulateRoot) {
        mutableStateOf(simulateRoot || PrivilegeManager.privilegeType == PrivilegeManager.PrivilegeType.ROOT)
    }
    var permissionSnapshot by remember(simulateRoot) {
        mutableStateOf(readOnboardingPermissions(context, simulateRoot))
    }
    var showRootConsent by remember { mutableStateOf(false) }
    var rootRequesting by remember { mutableStateOf(false) }
    var busyPermissionKey by remember { mutableStateOf<PrivilegedPermissionKey?>(null) }
    var bulkPermissionBusy by remember { mutableStateOf(false) }
    val blockedFeatureWarnings = remember { mutableStateMapOf<FeatureKey, String>() }
    var currentToastType by remember { mutableStateOf(ToastType.INFO) }
    val steps = remember { buildOnboardingSteps() }
    val currentStep = steps.getOrElse(page) { steps.last() }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        permissionSnapshot = readOnboardingPermissions(context, simulateRoot)
        settingsViewModel.refreshSyncStatus()
        permissionGate.resumePending()
    }

    fun refreshPermissions() {
        privilegedModeActive = simulateRoot || PrivilegeManager.privilegeType == PrivilegeManager.PrivilegeType.ROOT
        permissionSnapshot = readOnboardingPermissions(context, simulateRoot)
        settingsViewModel.refreshSyncStatus()
    }

    fun toast(message: String, type: ToastType = ToastType.INFO) {
        currentToastType = type
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
        if (type == ToastType.ERROR) haptics.error() else haptics.confirm()
    }

    fun setPrivilegedPermission(key: PrivilegedPermissionKey, enabled: Boolean) {
        if (!privilegedModeActive || busyPermissionKey != null || bulkPermissionBusy) return
        scope.launch {
            busyPermissionKey = key
            val result = PrivilegedPermissionController.setEnabled(
                context = context,
                key = key,
                enabled = enabled,
                simulateRoot = simulateRoot,
            )
            refreshPermissions()
            busyPermissionKey = null
            if (result.success) {
                toast(if (enabled) "权限已开启" else "权限已关闭")
            } else {
                toast(result.message.ifBlank { "权限变更失败" }, ToastType.ERROR)
            }
        }
    }

    fun enableAllPrivilegedPermissions() {
        if (!privilegedModeActive || bulkPermissionBusy || busyPermissionKey != null) return
        scope.launch {
            bulkPermissionBusy = true
            val results = PrivilegedPermissionController.enableAll(context, simulateRoot)
            refreshPermissions()
            bulkPermissionBusy = false
            val successCount = results.count { it.success }
            val failedCount = results.size - successCount
            toast(
                if (failedCount == 0) {
                    "已开启全部可管理权限"
                } else {
                    "已开启 $successCount 项，$failedCount 项仍需手动确认"
                },
                if (failedCount == 0) ToastType.SUCCESS else ToastType.INFO,
            )
        }
    }

    fun openPermission(permissionKey: PermissionKey) {
        haptics.selection()
        Log.d(ONBOARDING_LOG_TAG, "OpenPermission key=$permissionKey")
        when (permissionKey) {
            PermissionKey.NOTIFICATION -> {
                if (areAppNotificationsEnabled(context)) {
                    toast("通知权限已获得")
                } else {
                    toast("正在打开权限管理页")
                    openAppPermissionSettings(context)
                }
            }
            PermissionKey.LIVE_NOTIFICATION -> {
                toast("正在打开通知设置，请确认实况通知权限")
                openAppNotificationSettings(context)
            }
            PermissionKey.OVERLAY -> {
                if (Settings.canDrawOverlays(context)) {
                    toast("悬浮窗权限已获得")
                } else {
                    toast("正在打开悬浮窗授权页")
                    openOverlaySettings(context)
                }
            }
            PermissionKey.MICROPHONE -> {
                if (hasPermission(context, Manifest.permission.RECORD_AUDIO)) {
                    toast("麦克风权限已获得")
                } else {
                    toast("正在打开权限管理页")
                    openAppPermissionSettings(context)
                }
            }
            PermissionKey.CALENDAR -> {
                if (hasPermission(context, Manifest.permission.READ_CALENDAR) && hasPermission(context, Manifest.permission.WRITE_CALENDAR)) {
                    toast("日历读写权限已获得")
                } else {
                    toast("正在打开权限管理页")
                    openAppPermissionSettings(context)
                }
            }
            PermissionKey.EXACT_ALARM -> {
                toast("正在打开精准闹钟授权页")
                openExactAlarmSettings(context)
            }
            PermissionKey.BATTERY -> {
                toast("正在打开电池优化设置")
                openBatterySettings(context)
            }
            PermissionKey.AUTOSTART -> {
                Toast.makeText(context, "不同系统入口不同，请在应用详情中开启自启动/后台运行", Toast.LENGTH_LONG).show()
                openAppDetails(context)
            }
            PermissionKey.LOCATION -> {
                if (hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) || hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)) {
                    toast("定位权限已获得")
                } else {
                    toast("正在打开权限管理页")
                    openAppPermissionSettings(context)
                }
            }
            PermissionKey.ACCESSIBILITY -> {
                toast("正在打开无障碍设置")
                openAccessibilitySettings(context)
            }
            PermissionKey.NOTIFICATION_LISTENER -> {
                toast("正在打开通知监听设置")
                openNotificationListenerSettings(context)
            }
            PermissionKey.SMS -> {
                if (hasPermission(context, Manifest.permission.RECEIVE_SMS) && hasPermission(context, Manifest.permission.READ_SMS)) {
                    toast("短信权限已获得")
                } else {
                    toast("正在打开权限管理页")
                    openAppPermissionSettings(context)
                }
            }
            PermissionKey.ROOT_ACCESS -> when {
                simulateRoot -> toast("当前正在使用模拟 Root 权限")
                PrivilegeManager.privilegeType == PrivilegeManager.PrivilegeType.ROOT -> toast("Root 权限已授权")
                PrivilegeManager.hasRootBinary() -> showRootConsent = true
                else -> toast("未检测到 Root 环境，相关权限仍可手动授予", ToastType.INFO)
            }
        }
    }

    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openAppNotificationSettings(context)
        }
    }

    fun blockFeature(featureKey: FeatureKey, message: String) {
        blockedFeatureWarnings[featureKey] = message
        page = 0
        toast(message, ToastType.ERROR)
    }

    fun updateWeather(
        enabled: Boolean = settings.weatherEnabled,
        warningEnabled: Boolean = settings.weatherWarningEnabled,
        riskWarningEnabled: Boolean = settings.weatherRiskWarningEnabled,
        showInFloating: Boolean = settings.showWeatherInFloating,
        apiUrl: String = settings.weatherApiUrl,
        apiKey: String = settings.weatherApiKey,
        refreshInterval: Int = settings.weatherRefreshInterval,
        warningLookaheadHours: Int = settings.weatherWarningLookaheadHours,
        floatingWeatherForecastRange: Int = settings.floatingWeatherForecastRange,
    ) {
        settingsViewModel.updateWeatherSettings(
            enabled = enabled,
            provider = settings.weatherProvider,
            apiUrl = apiUrl,
            apiKey = apiKey,
            refreshInterval = refreshInterval,
            showInFloating = showInFloating,
            locationMode = settings.weatherLocationMode,
            manualLocationId = settings.weatherManualLocationId,
            manualLocationName = settings.weatherManualLocationName,
            manualAdm1 = settings.weatherManualAdm1,
            manualAdm2 = settings.weatherManualAdm2,
            manualCountry = settings.weatherManualCountry,
            manualLat = settings.weatherManualLat,
            manualLon = settings.weatherManualLon,
            warningEnabled = warningEnabled,
            riskWarningEnabled = riskWarningEnabled,
            warningLookaheadHours = warningLookaheadHours,
            floatingWeatherForecastRange = floatingWeatherForecastRange,
        )
    }

    fun setFeature(featureKey: FeatureKey, enabled: Boolean) {
        blockedFeatureWarnings.remove(featureKey)
        if (enabled) {
            val missing = missingFeatureRequirement(featureKey, permissionSnapshot)
            if (missing != null) {
                blockFeature(featureKey, missing)
                return
            }
        }
        when (featureKey) {
            FeatureKey.FLOATING_WINDOW -> settingsViewModel.updatePreference(floatingWindow = enabled)
            FeatureKey.QUICK_MEMO -> settingsViewModel.updatePreference(voiceInputEnabled = enabled)
            FeatureKey.LIVE_NOTIFICATION -> settingsViewModel.updatePreference(liveCapsule = enabled)
            FeatureKey.DAILY_SUMMARY -> settingsViewModel.updatePreference(dailySummary = enabled)
            FeatureKey.CALENDAR_SYNC -> settingsViewModel.toggleCalendarSync(enabled)
            FeatureKey.ADVANCE_REMINDER -> settingsViewModel.updatePreference(advanceReminderEnabled = enabled)
            FeatureKey.WEATHER -> updateWeather(enabled = enabled)
            FeatureKey.WEATHER_ALERT -> updateWeather(warningEnabled = enabled, riskWarningEnabled = enabled)
            FeatureKey.BRACELET_MODE -> settingsViewModel.updatePreference(braceletModeEnabled = enabled)
            FeatureKey.SMS_RECOGNITION -> settingsViewModel.updatePreference(smsMonitoring = enabled)
            FeatureKey.VOLUME_SHORTCUT -> settingsViewModel.updatePreference(volumeUpLongPressEnabled = enabled)
            FeatureKey.COURSE -> settingsViewModel.updatePreference(courseFeatureEnabled = enabled)
        }
    }

    fun handleLaboratoryAction(action: LaboratoryUiAction) {
        when (action) {
            is LaboratoryUiAction.SetBraceletMode -> {
                settingsViewModel.updatePreference(braceletModeEnabled = action.enabled)
            }
            is LaboratoryUiAction.SetForceInstantCodeTime -> {
                settingsViewModel.updatePreference(forceInstantCodeTimeToNow = action.enabled)
            }
            is LaboratoryUiAction.SetPredictiveBack -> {
                settingsViewModel.updatePreference(predictiveBackEnabled = action.enabled)
            }
            is LaboratoryUiAction.SetClipboardRecognition -> {
                settingsViewModel.updatePreference(clipboardCodeRecognitionEnabled = action.enabled)
                if (action.enabled) {
                    toast("已开启，打开 WillDo 时将检查剪贴板并询问是否创建日程")
                }
            }
            LaboratoryUiAction.OpenDeveloper -> Unit
        }
    }

    fun finishOnboarding() {
        if (onFinish != null) {
            onFinish()
        } else {
            toast("引导页调试完成，可返回开发者页继续测试")
        }
    }

    fun goNextOrFinish() {
        haptics.confirm()
        if (page < steps.lastIndex) {
            page += 1
        } else {
            finishOnboarding()
        }
    }

    fun skipOnboarding() {
        haptics.selection()
        finishOnboarding()
    }

    LaunchedEffect(Unit) {
        refreshPermissions()
    }

    LaunchedEffect(simulateRoot) {
        PrivilegedPermissionController.clearSimulation()
        if (simulateRoot) {
            privilegedModeActive = true
            refreshPermissions()
        }
    }

    LaunchedEffect(currentStep, simulateRoot) {
        if (simulateRoot) return@LaunchedEffect
        if (currentStep != OnboardingStep.PERMISSIONS || privilegedModeActive) return@LaunchedEffect
        val prefs = context.getSharedPreferences(ROOT_PROMPT_PREFS, Context.MODE_PRIVATE)
        val rootAvailable = withContext(Dispatchers.IO) { PrivilegeManager.hasRootBinary() }
        if (!rootAvailable) return@LaunchedEffect
        if (PrivilegeManager.wasRootPreviouslyGranted(context)) {
            rootRequesting = true
            val granted = PrivilegeManager.requestRootAccess(context)
            rootRequesting = false
            privilegedModeActive = granted
            refreshPermissions()
        } else if (!prefs.getBoolean(ROOT_PROMPT_SHOWN, false)) {
            showRootConsent = true
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissions()
                permissionGate.resumePending()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(steps.size) {
        if (page > steps.lastIndex) page = steps.lastIndex.coerceAtLeast(0)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 960.dp)
                .fillMaxSize(),
        ) {
            OnboardingHeader(
                page = page,
                totalPages = steps.size,
                step = currentStep,
                permissionSnapshot = permissionSnapshot,
                featureCount = onboardingFeatureItems(settings, syncStatus.isEnabled, permissionSnapshot).size,
            )

            Box(modifier = Modifier.weight(1f)) {
                // 底部引导操作行已留出安全区，嵌入的设置内容不重复预留。
                androidx.compose.runtime.CompositionLocalProvider(LocalAppPageBottomPadding provides 0.dp) {
                    when (currentStep) {
                        OnboardingStep.PERMISSIONS -> Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            PermissionStep(
                                snapshot = permissionSnapshot,
                                privilegedModeActive = privilegedModeActive,
                                busyPermissionKey = busyPermissionKey,
                                bulkPermissionBusy = bulkPermissionBusy,
                                onOpenPermission = ::openPermission,
                                onPermissionChange = ::setPrivilegedPermission,
                                onEnableAll = ::enableAllPrivilegedPermissions,
                            )
                        }
                        OnboardingStep.SCHEDULE_REMINDER -> PreferenceSettingsPage(
                            viewModel = settingsViewModel,
                            uiSize = uiSize,
                            visibleSections = setOf(
                                PreferenceSection.OPERATION,
                                PreferenceSection.NOTIFICATION,
                                PreferenceSection.SCHEDULE,
                            ),
                            itemVisibility = PreferenceItemVisibility(
                                showHapticFeedback = false,
                                showNetworkSpeedCapsule = false,
                                showAutoArchive = false,
                                showScheduleColors = false,
                            ),
                            footerContent = {
                                LaboratorySettingsContent(
                                    state = LaboratoryUiState(settings),
                                    onAction = ::handleLaboratoryAction,
                                    showDeveloperEntry = false,
                                    itemVisibility = LaboratoryItemVisibility(
                                        showForceInstantCodeTime = false,
                                        showPredictiveBack = false,
                                    ),
                                    onBraceletModeChange = { enabled ->
                                        if (enabled) {
                                            permissionGate.require(
                                                permissionName = "通知权限",
                                                isGranted = { areAppNotificationsEnabled(context) },
                                                requestPermission = ::requestNotificationPermission,
                                                onGranted = { settingsViewModel.updatePreference(braceletModeEnabled = true) },
                                            )
                                        } else {
                                            settingsViewModel.updatePreference(braceletModeEnabled = false)
                                        }
                                    },
                                )
                            },
                            onNavigateToScheduleColors = {
                                toast("可在完成引导后进入日程颜色设置")
                            },
                        )
                        OnboardingStep.FLOATING_QUICK_MEMO -> PreferenceSettingsPage(
                            viewModel = settingsViewModel,
                            uiSize = uiSize,
                            visibleSections = setOf(
                                PreferenceSection.DISPLAY,
                                PreferenceSection.QUICK_MEMO,
                            ),
                            itemVisibility = PreferenceItemVisibility(
                                showUiSize = false,
                                showTomorrowEvents = false,
                                showBottomBarEditor = false,
                                showWidgetSettings = false,
                            ),
                            onNavigateToBottomBarEditor = {
                                toast("可在完成引导后进入底栏编辑")
                            },
                            onNavigateToWidgetSettings = {
                                toast("可在完成引导后进入桌面小组件设置")
                            },
                        )
                        OnboardingStep.MODEL -> AiSettingsPage(
                            viewModel = settingsViewModel,
                            mainViewModel = mainViewModel,
                            uiSize = uiSize,
                        )
                        OnboardingStep.WEATHER -> WeatherSettingsPage(
                            viewModel = settingsViewModel,
                            uiSize = uiSize,
                            showCacheSection = false,
                            onOpenWeatherDetail = {
                                toast("完成天气配置后，可从设置页查看天气详情")
                            },
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            OnboardingActionRow(
                page = page,
                totalPages = steps.size,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .padding(bottom = LocalAppPageBottomPadding.current),
                onPrevious = {
                    haptics.selection()
                    page = (page - 1).coerceAtLeast(0)
                },
                onSkip = ::skipOnboarding,
                onNextOrFinish = ::goNextOrFinish,
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .padding(bottom = LocalAppPageBottomPadding.current),
            snackbar = { data -> UniversalSnackbar(data = data, type = currentToastType) }
        )

    }

    PredictiveFloatingActionCard(
        visible = showRootConsent,
        title = "使用 Root 管理权限",
        content = "授权 Root 后，可在当前页面直接开启或关闭大部分系统权限。厂商专属权限仍需手动确认。",
        confirmText = "申请 Root",
        dismissText = "暂不使用",
        isLoading = rootRequesting,
        allowDismissWhileLoading = false,
        predictiveBackEnabled = settings.predictiveBackEnabled,
        onConfirm = {
            if (rootRequesting) return@PredictiveFloatingActionCard
            context.getSharedPreferences(ROOT_PROMPT_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(ROOT_PROMPT_SHOWN, true)
                .apply()
            rootRequesting = true
            scope.launch {
                val granted = PrivilegeManager.requestRootAccess(context)
                rootRequesting = false
                showRootConsent = false
                privilegedModeActive = granted
                refreshPermissions()
                toast(if (granted) "Root 权限已授权" else "Root 权限未授予", if (granted) ToastType.SUCCESS else ToastType.ERROR)
            }
        },
        onDismiss = {
            context.getSharedPreferences(ROOT_PROMPT_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(ROOT_PROMPT_SHOWN, true)
                .apply()
            showRootConsent = false
        },
    )
}

@Composable
private fun OnboardingActionRow(
    page: Int,
    totalPages: Int,
    modifier: Modifier = Modifier,
    onPrevious: () -> Unit,
    onSkip: () -> Unit,
    onNextOrFinish: () -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onSkip) {
            Text("跳过")
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (page > 0) {
                TextButton(onClick = onPrevious) {
                    Text("上一步")
                }
            }
            EditionButton(onClick = onNextOrFinish) {
                Text(if (page == totalPages - 1) "完成" else "下一步")
            }
        }
    }
}

@Composable
private fun OnboardingHeader(
    page: Int,
    totalPages: Int,
    step: OnboardingStep,
    permissionSnapshot: PermissionSnapshot,
    featureCount: Int,
) {
    val progress by animateFloatAsState(targetValue = (page + 1f) / totalPages.coerceAtLeast(1), label = "onboardingProgress")
    val title = step.title
    val description = step.description
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 10.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "${page + 1}/$totalPages",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(999.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
private fun PermissionStep(
    snapshot: PermissionSnapshot,
    privilegedModeActive: Boolean,
    busyPermissionKey: PrivilegedPermissionKey?,
    bulkPermissionBusy: Boolean,
    onOpenPermission: (PermissionKey) -> Unit,
    onPermissionChange: (PrivilegedPermissionKey, Boolean) -> Unit,
    onEnableAll: () -> Unit,
) {
    val allItems = onboardingPermissionItems(snapshot)
    val requiredItems = allItems.filter { it.level == RequirementLevel.REQUIRED }
    val recommendedItems = allItems.filter { it.level == RequirementLevel.RECOMMENDED }
    val optionalItems = allItems.filter { it.level == RequirementLevel.OPTIONAL }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (privilegedModeActive) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Root 权限管理",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    )
                    Text(
                        text = "可直接管理的项目已切换为开关",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                EditionButton(
                    onClick = onEnableAll,
                    enabled = !bulkPermissionBusy && busyPermissionKey == null,
                ) {
                    Text(if (bulkPermissionBusy) "开启中" else "一键开启")
                }
            }
        }
        PermissionSection(
            title = "必要权限",
            description = "建议首次使用前开启，否则核心功能可能无法正常工作。",
            items = requiredItems,
            privilegedModeActive = privilegedModeActive,
            busyPermissionKey = busyPermissionKey,
            bulkPermissionBusy = bulkPermissionBusy,
            onOpenPermission = onOpenPermission,
            onPermissionChange = onPermissionChange,
        )
        PermissionSection(
            title = "建议配置",
            description = "用于提升提醒准时性、后台稳定性和日程同步体验。",
            items = recommendedItems,
            privilegedModeActive = privilegedModeActive,
            busyPermissionKey = busyPermissionKey,
            bulkPermissionBusy = bulkPermissionBusy,
            onOpenPermission = onOpenPermission,
            onPermissionChange = onPermissionChange,
        )
        PermissionSection(
            title = "可选能力",
            description = "只在你需要对应功能时开启，之后也可以在设置中调整。",
            items = optionalItems,
            privilegedModeActive = privilegedModeActive,
            busyPermissionKey = busyPermissionKey,
            bulkPermissionBusy = bulkPermissionBusy,
            onOpenPermission = onOpenPermission,
            onPermissionChange = onPermissionChange,
        )
    }
}

@Composable
private fun PermissionSection(
    title: String,
    description: String,
    items: List<PermissionItem>,
    privilegedModeActive: Boolean,
    busyPermissionKey: PrivilegedPermissionKey?,
    bulkPermissionBusy: Boolean,
    onOpenPermission: (PermissionKey) -> Unit,
    onPermissionChange: (PrivilegedPermissionKey, Boolean) -> Unit,
) {
    if (items.isEmpty()) return
    val sectionTitleStyle = MaterialTheme.typography.titleMedium.copy(
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary,
    )
    val cardTitleStyle = MaterialTheme.typography.bodyLarge.copy(
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
    )
    val cardSubtitleStyle = MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
    )
    val cardValueStyle = MaterialTheme.typography.bodyMedium.copy(
        fontWeight = FontWeight.Normal,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = sectionTitleStyle,
            )
            Text(
                text = description,
                style = cardSubtitleStyle,
            )
        }
        AppSettingsCard {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                items.forEachIndexed { index, item ->
                    PermissionSettingItem(
                        item = item,
                        privilegedModeActive = privilegedModeActive,
                        busyPermissionKey = busyPermissionKey,
                        bulkPermissionBusy = bulkPermissionBusy,
                        onClick = { onOpenPermission(item.key) },
                        onPermissionChange = onPermissionChange,
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle,
                        cardValueStyle = cardValueStyle,
                    )
                    if (index != items.lastIndex) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun PermissionSettingItem(
    item: PermissionItem,
    privilegedModeActive: Boolean,
    busyPermissionKey: PrivilegedPermissionKey?,
    bulkPermissionBusy: Boolean,
    onClick: () -> Unit,
    onPermissionChange: (PrivilegedPermissionKey, Boolean) -> Unit,
    cardTitleStyle: androidx.compose.ui.text.TextStyle,
    cardSubtitleStyle: androidx.compose.ui.text.TextStyle,
    cardValueStyle: androidx.compose.ui.text.TextStyle,
) {
    val privilegedKey = privilegedPermissionKey(item.key)
    val showSwitch = privilegedModeActive && privilegedKey != null
    val granted = item.uiState == PermissionUiState.GRANTED ||
        item.uiState == PermissionUiState.SYSTEM_ALLOWED
    val accentColor = if (granted) permissionGrantedColor() else MaterialTheme.colorScheme.primary
    val containerColor = if (granted) {
        permissionGrantedColor().copy(alpha = 0.14f)
    } else {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.56f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (showSwitch) Modifier else Modifier.clickable(onClick = onClick))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = containerColor,
            modifier = Modifier.size(34.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = getPermissionIcon(item.key),
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(text = item.title, style = cardTitleStyle)
            Text(text = item.subtitle, style = cardSubtitleStyle)
        }
        Spacer(modifier = Modifier.width(12.dp))
        if (showSwitch && privilegedKey != null) {
            EditionSwitch(
                checked = granted,
                onCheckedChange = { enabled -> onPermissionChange(privilegedKey, enabled) },
                enabled = !bulkPermissionBusy && busyPermissionKey == null,
            )
        } else {
            Text(
                text = permissionStatusText(item),
                style = cardValueStyle,
                color = accentColor,
            )
        }
    }
}

@Composable
private fun FeatureStep(
    settings: MySettings,
    syncEnabled: Boolean,
    snapshot: PermissionSnapshot,
    warnings: Map<FeatureKey, String>,
    onFeatureChange: (FeatureKey, Boolean) -> Unit,
    onOpenPermissionPage: () -> Unit,
) {
    val features = onboardingFeatureItems(settings, syncEnabled, snapshot)
    val cardTitleStyle = MaterialTheme.typography.bodyLarge.copy(
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
    )
    val cardSubtitleStyle = MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
    )
    AppSettingsCard {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            features.forEachIndexed { index, item ->
                val warning = warnings[item.key].takeUnless { it.isNullOrBlank() }
                    ?: item.warning.takeUnless { item.requirementsMet || it.isBlank() }
                SwitchSettingItem(
                    title = item.title,
                    subtitle = if (warning == null) item.subtitle else "${item.subtitle}；$warning",
                    checked = item.enabled,
                    onCheckedChange = { onFeatureChange(item.key, it) },
                    cardTitleStyle = cardTitleStyle,
                    cardSubtitleStyle = cardSubtitleStyle,
                    enabled = item.requirementsMet,
                    onDisabledClick = onOpenPermissionPage,
                )
                if (index != features.lastIndex) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

@Composable
private fun ScheduleReminderConfigStep(
    settings: MySettings,
    syncEnabled: Boolean,
    snapshot: PermissionSnapshot,
    onFeatureChange: (FeatureKey, Boolean) -> Unit,
    onOpenPermission: (PermissionKey) -> Unit,
    onAdvanceReminderMinutesChange: (Int) -> Unit,
) {
    val cardTitleStyle = MaterialTheme.typography.bodyLarge.copy(
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
    )
    val cardSubtitleStyle = MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
    )
    val cardValueStyle = MaterialTheme.typography.bodyMedium.copy(
        fontWeight = FontWeight.Normal,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    AppSettingsCard {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            SwitchSettingItem(
                title = "每日提醒",
                subtitle = "今日提醒和明日预告",
                checked = settings.isDailySummaryEnabled,
                onCheckedChange = { onFeatureChange(FeatureKey.DAILY_SUMMARY, it) },
                cardTitleStyle = cardTitleStyle,
                cardSubtitleStyle = cardSubtitleStyle,
                enabled = snapshot.notification,
                onDisabledClick = if (snapshot.notification) null else ({ onOpenPermission(PermissionKey.NOTIFICATION) }),
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            SwitchSettingItem(
                title = "提前提醒",
                subtitle = "日程开始前提前提醒",
                checked = settings.isAdvanceReminderEnabled,
                onCheckedChange = { onFeatureChange(FeatureKey.ADVANCE_REMINDER, it) },
                cardTitleStyle = cardTitleStyle,
                cardSubtitleStyle = cardSubtitleStyle,
                enabled = snapshot.notification,
                onDisabledClick = if (snapshot.notification) null else ({ onOpenPermission(PermissionKey.NOTIFICATION) }),
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            SwitchSettingItem(
                title = "日历同步",
                subtitle = "与系统日历同步日程",
                checked = syncEnabled,
                onCheckedChange = { onFeatureChange(FeatureKey.CALENDAR_SYNC, it) },
                cardTitleStyle = cardTitleStyle,
                cardSubtitleStyle = cardSubtitleStyle,
                enabled = snapshot.calendar,
                onDisabledClick = if (snapshot.calendar) null else ({ onOpenPermission(PermissionKey.CALENDAR) }),
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            SwitchSettingItem(
                title = "实况通知",
                subtitle = "以胶囊/灵动形态显示提醒",
                checked = settings.isLiveCapsuleEnabled,
                onCheckedChange = { onFeatureChange(FeatureKey.LIVE_NOTIFICATION, it) },
                cardTitleStyle = cardTitleStyle,
                cardSubtitleStyle = cardSubtitleStyle,
                enabled = liveNotificationRequirementMet(snapshot),
                onDisabledClick = if (liveNotificationRequirementMet(snapshot)) null else ({ onOpenPermission(PermissionKey.LIVE_NOTIFICATION) }),
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            SwitchSettingItem(
                title = "手环模式",
                subtitle = "开启后，将同步发送一条普通通知以同步到手环",
                checked = settings.braceletModeEnabled,
                onCheckedChange = { onFeatureChange(FeatureKey.BRACELET_MODE, it) },
                cardTitleStyle = cardTitleStyle,
                cardSubtitleStyle = cardSubtitleStyle,
                enabled = snapshot.notification,
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            SliderSettingItem(
                title = "提前分钟",
                subtitle = "日程开始前多久提醒",
                value = settings.advanceReminderMinutes.toFloat(),
                onValueChange = { onAdvanceReminderMinutesChange(it.roundToInt()) },
                valueRange = 5f..120f,
                steps = 22,
                cardTitleStyle = cardTitleStyle,
                cardSubtitleStyle = cardSubtitleStyle,
                cardValueStyle = cardValueStyle,
                showValueAsNumber = true,
                valueUnit = " 分钟",
                enabled = settings.isAdvanceReminderEnabled,
            )
        }
    }
}

@Composable
private fun FloatingQuickMemoConfigStep(
    settings: MySettings,
    snapshot: PermissionSnapshot,
    onFeatureChange: (FeatureKey, Boolean) -> Unit,
    onOpenPermission: (PermissionKey) -> Unit,
    onFloatingEventRangeChange: (Int) -> Unit,
    onFloatingExpandSideChange: (String) -> Unit,
    onEdgeBarEnabledChange: (Boolean) -> Unit,
    onEdgeBarHeightChange: (Int) -> Unit,
    onEdgeBarAlphaChange: (Float) -> Unit,
    onFloatingBallEnabledChange: (Boolean) -> Unit,
    onFloatingBallSizeChange: (Int) -> Unit,
    onFloatingBallAlphaChange: (Float) -> Unit,
    onFloatingTextQuickMemoAutoPinChange: (Boolean) -> Unit,
    onVoiceQuickMemoAutoPinChange: (Boolean) -> Unit,
) {
    val cardTitleStyle = MaterialTheme.typography.bodyLarge.copy(
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
    )
    val cardSubtitleStyle = MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
    )
    val cardValueStyle = MaterialTheme.typography.bodyMedium.copy(
        fontWeight = FontWeight.Normal,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    AppSettingsCard {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            SwitchSettingItem(
                title = "日程悬浮",
                subtitle = "桌面悬浮窗、侧边栏、悬浮球入口",
                checked = settings.isFloatingWindowEnabled,
                onCheckedChange = { onFeatureChange(FeatureKey.FLOATING_WINDOW, it) },
                cardTitleStyle = cardTitleStyle,
                cardSubtitleStyle = cardSubtitleStyle,
                enabled = snapshot.overlay,
                onDisabledClick = if (snapshot.overlay) null else ({ onOpenPermission(PermissionKey.OVERLAY) }),
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            EditionCategoricalPreference(
                title = "日程范围",
                summary = floatingEventRangeLabel(settings.floatingEventRange),
                options = listOf("全部日程", "今日日程", "今日+明日"),
                selectedIndex = settings.floatingEventRange.coerceIn(0, 2),
                onSelectedIndexChange = onFloatingEventRangeChange,
                enabled = settings.isFloatingWindowEnabled,
                titleTextStyle = cardTitleStyle,
                summaryTextStyle = cardSubtitleStyle,
                showSelectedValue = false,
                labelsAboveSlider = true,
                sliderTopPadding = 12.dp,
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            SwitchSettingItem(
                title = "随口记",
                subtitle = "文字/语音随口记入口",
                checked = settings.voiceInputEnabled,
                onCheckedChange = { onFeatureChange(FeatureKey.QUICK_MEMO, it) },
                cardTitleStyle = cardTitleStyle,
                cardSubtitleStyle = cardSubtitleStyle,
                enabled = snapshot.overlay && snapshot.microphone,
                onDisabledClick = if (snapshot.overlay && snapshot.microphone) null else ({ onOpenPermission(if (!snapshot.overlay) PermissionKey.OVERLAY else PermissionKey.MICROPHONE) }),
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            SwitchSettingItem(
                title = "侧边栏",
                subtitle = "屏幕边缘滑动呼出悬浮入口",
                checked = settings.edgeBarEnabled,
                onCheckedChange = onEdgeBarEnabledChange,
                cardTitleStyle = cardTitleStyle,
                cardSubtitleStyle = cardSubtitleStyle,
                enabled = snapshot.overlay && (settings.isFloatingWindowEnabled || settings.voiceInputEnabled),
                onDisabledClick = if (snapshot.overlay) null else ({ onOpenPermission(PermissionKey.OVERLAY) }),
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            EditionCategoricalPreference(
                title = "展开方向",
                summary = "悬浮窗从哪一侧滑入",
                options = listOf("左侧", "右侧"),
                selectedIndex = if (settings.floatingExpandSide == "RIGHT") 1 else 0,
                onSelectedIndexChange = { onFloatingExpandSideChange(if (it == 1) "RIGHT" else "LEFT") },
                enabled = settings.isFloatingWindowEnabled || settings.voiceInputEnabled,
                titleTextStyle = cardTitleStyle,
                summaryTextStyle = cardSubtitleStyle,
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            SliderSettingItem(
                title = "侧栏高度",
                subtitle = "调整侧边栏触发区域高度",
                value = settings.edgeBarHeightDp.toFloat(),
                onValueChange = { onEdgeBarHeightChange(it.roundToInt()) },
                valueRange = 80f..220f,
                steps = 13,
                cardTitleStyle = cardTitleStyle,
                cardSubtitleStyle = cardSubtitleStyle,
                cardValueStyle = cardValueStyle,
                showValueAsNumber = true,
                valueUnit = " dp",
                enabled = settings.edgeBarEnabled,
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            SliderSettingItem(
                title = "侧栏透明",
                subtitle = "调整侧边栏可见程度",
                value = settings.edgeBarAlpha * 100f,
                onValueChange = { onEdgeBarAlphaChange((it / 100f).coerceIn(0.1f, 1f)) },
                valueRange = 10f..100f,
                steps = 8,
                cardTitleStyle = cardTitleStyle,
                cardSubtitleStyle = cardSubtitleStyle,
                cardValueStyle = cardValueStyle,
                showValueAsNumber = true,
                valueUnit = "%",
                enabled = settings.edgeBarEnabled,
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            SwitchSettingItem(
                title = "悬浮球",
                subtitle = "桌面常驻圆形快捷入口",
                checked = settings.floatingBallEnabled,
                onCheckedChange = onFloatingBallEnabledChange,
                cardTitleStyle = cardTitleStyle,
                cardSubtitleStyle = cardSubtitleStyle,
                enabled = snapshot.overlay && (settings.isFloatingWindowEnabled || settings.voiceInputEnabled),
                onDisabledClick = if (snapshot.overlay) null else ({ onOpenPermission(PermissionKey.OVERLAY) }),
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            SliderSettingItem(
                title = "悬浮球大小",
                subtitle = "调整悬浮球显示尺寸",
                value = settings.floatingBallSizeDp.toFloat(),
                onValueChange = { onFloatingBallSizeChange(it.roundToInt()) },
                valueRange = 40f..80f,
                steps = 7,
                cardTitleStyle = cardTitleStyle,
                cardSubtitleStyle = cardSubtitleStyle,
                cardValueStyle = cardValueStyle,
                showValueAsNumber = true,
                valueUnit = " dp",
                enabled = settings.floatingBallEnabled,
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            SliderSettingItem(
                title = "悬浮球透明",
                subtitle = "调整悬浮球可见程度",
                value = settings.floatingBallAlpha * 100f,
                onValueChange = { onFloatingBallAlphaChange((it / 100f).coerceIn(0.1f, 1f)) },
                valueRange = 10f..100f,
                steps = 8,
                cardTitleStyle = cardTitleStyle,
                cardSubtitleStyle = cardSubtitleStyle,
                cardValueStyle = cardValueStyle,
                showValueAsNumber = true,
                valueUnit = "%",
                enabled = settings.floatingBallEnabled,
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            SwitchSettingItem(
                title = "文本挂起",
                subtitle = "文本随口记保存后同步挂到实况通知",
                checked = settings.floatingTextQuickMemoAutoPinEnabled,
                onCheckedChange = onFloatingTextQuickMemoAutoPinChange,
                cardTitleStyle = cardTitleStyle,
                cardSubtitleStyle = cardSubtitleStyle,
                enabled = settings.voiceInputEnabled && settings.isLiveCapsuleEnabled,
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            SwitchSettingItem(
                title = "语音挂起",
                subtitle = "语音随口记转写完成后同步挂到实况通知",
                checked = settings.voiceQuickMemoAutoPinEnabled,
                onCheckedChange = onVoiceQuickMemoAutoPinChange,
                cardTitleStyle = cardTitleStyle,
                cardSubtitleStyle = cardSubtitleStyle,
                enabled = settings.voiceInputEnabled && settings.isLiveCapsuleEnabled,
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            SwitchSettingItem(
                title = "音量键快捷",
                subtitle = "通过无障碍监听音量键长按触发随口记",
                checked = settings.volumeUpLongPressEnabled,
                onCheckedChange = { onFeatureChange(FeatureKey.VOLUME_SHORTCUT, it) },
                cardTitleStyle = cardTitleStyle,
                cardSubtitleStyle = cardSubtitleStyle,
                enabled = snapshot.accessibility,
                onDisabledClick = if (snapshot.accessibility) null else ({ onOpenPermission(PermissionKey.ACCESSIBILITY) }),
            )
        }
    }
}

@Composable
private fun CourseConfigStep(
    settings: MySettings,
    onCourseFeatureChange: (Boolean) -> Unit,
    onImportCourses: () -> Unit,
) {
    val cardTitleStyle = MaterialTheme.typography.bodyLarge.copy(
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
    )
    val cardSubtitleStyle = MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
    )
    val cardValueStyle = MaterialTheme.typography.bodyMedium.copy(
        fontWeight = FontWeight.Normal,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    AppSettingsCard {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            SwitchSettingItem(
                title = "课程功能",
                subtitle = "课程表、学期设置和课程提醒入口",
                checked = settings.courseFeatureEnabled,
                onCheckedChange = onCourseFeatureChange,
                cardTitleStyle = cardTitleStyle,
                cardSubtitleStyle = cardSubtitleStyle,
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            ActionSettingItem(
                title = "导入 WakeUp 课表",
                subtitle = "支持 WakeUp 文件和分享口令，可同步学期与作息时间",
                value = "导入",
                enabled = settings.courseFeatureEnabled,
                onClick = onImportCourses,
                cardTitleStyle = cardTitleStyle,
                cardSubtitleStyle = cardSubtitleStyle,
                cardValueStyle = cardValueStyle,
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            ActionSettingItem(
                title = "学期设置",
                subtitle = if (settings.semesterStartDate.isBlank()) "未设置，可稍后在课程设置中完善" else "已设置：${settings.semesterStartDate}",
                value = if (settings.semesterStartDate.isBlank()) "未配置" else "已配置",
                enabled = false,
                onClick = {},
                cardTitleStyle = cardTitleStyle,
                cardSubtitleStyle = cardSubtitleStyle,
                cardValueStyle = cardValueStyle,
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            ActionSettingItem(
                title = "上课时间",
                subtitle = if (settings.timeTableJson.isBlank()) "未配置，可稍后导入或手动编辑课程表" else "已配置课程时间表",
                value = if (settings.timeTableJson.isBlank()) "未配置" else "已配置",
                enabled = false,
                onClick = {},
                cardTitleStyle = cardTitleStyle,
                cardSubtitleStyle = cardSubtitleStyle,
                cardValueStyle = cardValueStyle,
            )
        }
    }
}

@Composable
private fun WeatherConfigStep(
    settings: MySettings,
    snapshot: PermissionSnapshot,
    onWeatherEnabledChange: (Boolean) -> Unit,
    onWeatherWarningChange: (Boolean) -> Unit,
    onShowInFloatingChange: (Boolean) -> Unit,
    onRefreshIntervalChange: (Int) -> Unit,
    onWarningLookaheadChange: (Int) -> Unit,
    onFloatingForecastRangeChange: (Int) -> Unit,
    onSaveWeatherConfig: (String, String) -> Unit,
    onRequestLocationPermission: () -> Unit,
) {
    var weatherApiUrl by remember(settings.weatherApiUrl) { mutableStateOf(settings.weatherApiUrl) }
    var weatherApiKey by remember(settings.weatherApiKey) { mutableStateOf(settings.weatherApiKey) }
    val cardTitleStyle = MaterialTheme.typography.bodyLarge.copy(
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
    )
    val cardSubtitleStyle = MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
    )
    val cardValueStyle = MaterialTheme.typography.bodyMedium.copy(
        fontWeight = FontWeight.Normal,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        AppSettingsCard {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                SwitchSettingItem(
                    title = "天气服务",
                    subtitle = "开启天气卡片和天气提醒基础能力",
                    checked = settings.weatherEnabled,
                    onCheckedChange = onWeatherEnabledChange,
                    cardTitleStyle = cardTitleStyle,
                    cardSubtitleStyle = cardSubtitleStyle,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SwitchSettingItem(
                    title = "天气预警",
                    subtitle = "同步开启天气预警和天气风险提醒",
                    checked = settings.weatherWarningEnabled || settings.weatherRiskWarningEnabled,
                    onCheckedChange = onWeatherWarningChange,
                    cardTitleStyle = cardTitleStyle,
                    cardSubtitleStyle = cardSubtitleStyle,
                    enabled = settings.weatherEnabled,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SwitchSettingItem(
                    title = "悬浮窗显示天气",
                    subtitle = "在悬浮窗内显示天气信息",
                    checked = settings.showWeatherInFloating,
                    onCheckedChange = onShowInFloatingChange,
                    cardTitleStyle = cardTitleStyle,
                    cardSubtitleStyle = cardSubtitleStyle,
                    enabled = settings.weatherEnabled,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SliderSettingItem(
                    title = "刷新间隔",
                    subtitle = "天气数据自动刷新频率",
                    value = settings.weatherRefreshInterval.toFloat(),
                    onValueChange = { onRefreshIntervalChange(it.roundToInt()) },
                    valueRange = 15f..180f,
                    steps = 10,
                    cardTitleStyle = cardTitleStyle,
                    cardSubtitleStyle = cardSubtitleStyle,
                    cardValueStyle = cardValueStyle,
                    showValueAsNumber = true,
                    valueUnit = " 分钟",
                    enabled = settings.weatherEnabled,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SliderSettingItem(
                    title = "预警范围",
                    subtitle = "检查未来多久内的天气风险",
                    value = settings.weatherWarningLookaheadHours.toFloat(),
                    onValueChange = { onWarningLookaheadChange(it.roundToInt()) },
                    valueRange = 6f..72f,
                    steps = 10,
                    cardTitleStyle = cardTitleStyle,
                    cardSubtitleStyle = cardSubtitleStyle,
                    cardValueStyle = cardValueStyle,
                    showValueAsNumber = true,
                    valueUnit = " 小时",
                    enabled = settings.weatherEnabled && (settings.weatherWarningEnabled || settings.weatherRiskWarningEnabled),
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                EditionCategoricalPreference(
                    title = "预测范围",
                    summary = floatingWeatherForecastRangeLabel(settings.floatingWeatherForecastRange),
                    options = listOf("24小时", "3天", "5天"),
                    selectedIndex = settings.floatingWeatherForecastRange.coerceIn(0, 2),
                    onSelectedIndexChange = onFloatingForecastRangeChange,
                    enabled = settings.weatherEnabled && settings.showWeatherInFloating,
                    titleTextStyle = cardTitleStyle,
                    summaryTextStyle = cardSubtitleStyle,
                    valueTextStyle = cardValueStyle,
                )
            }
        }

        AppSettingsCard {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                ActionSettingItem(
                    title = "定位状态",
                    subtitle = if (snapshot.location) "已获得定位权限，可自动定位城市" else "未获得定位权限，也可以之后手动选择城市",
                    value = if (snapshot.location) "已就绪" else "去授权",
                    enabled = !snapshot.location,
                    onClick = onRequestLocationPermission,
                    cardTitleStyle = cardTitleStyle,
                    cardSubtitleStyle = cardSubtitleStyle,
                    cardValueStyle = cardValueStyle,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ActionSettingItem(
                    title = "手动城市",
                    subtitle = settings.weatherManualLocationName.ifBlank { "未选择，可稍后在天气设置中选择" },
                    value = if (settings.weatherManualLocationId.isNotBlank()) "已配置" else "未配置",
                    enabled = false,
                    onClick = {},
                    cardTitleStyle = cardTitleStyle,
                    cardSubtitleStyle = cardSubtitleStyle,
                    cardValueStyle = cardValueStyle,
                )
            }
        }

        AppSettingsCard {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("天气接口配置", style = cardTitleStyle)
                EditionTextField(
                    value = weatherApiUrl,
                    onValueChange = { weatherApiUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("天气 API URL") },
                    singleLine = true,
                )
                EditionTextField(
                    value = weatherApiKey,
                    onValueChange = { weatherApiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("天气 API Key") },
                    singleLine = true,
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    EditionButton(onClick = { onSaveWeatherConfig(weatherApiUrl.trim(), weatherApiKey.trim()) }) {
                        Text("保存天气配置")
                    }
                }
            }
        }
    }
}

@Composable
private fun AiConfigStep(
    settings: MySettings,
    onDisableThinkingChange: (Boolean) -> Unit,
    onLocalSemanticChange: (Boolean) -> Unit,
    onSaveAiConfig: (String, String, String) -> Unit,
) {
    var aiKey by remember(settings.mmModelKey) { mutableStateOf(settings.mmModelKey) }
    var aiName by remember(settings.mmModelName) { mutableStateOf(settings.mmModelName) }
    var aiUrl by remember(settings.mmModelUrl) { mutableStateOf(settings.mmModelUrl) }
    val cardTitleStyle = MaterialTheme.typography.bodyLarge.copy(
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
    )
    val cardSubtitleStyle = MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
    )
    val cardValueStyle = MaterialTheme.typography.bodyMedium.copy(
        fontWeight = FontWeight.Normal,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        AppSettingsCard {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                ActionSettingItem(
                    title = "AI 模型",
                    subtitle = settings.mmModelName.ifBlank { "未配置模型名称，AI 识别暂不可用" },
                    value = if (settings.mmModelKey.isNotBlank() && settings.mmModelUrl.isNotBlank()) "已配置" else "未配置",
                    enabled = false,
                    onClick = {},
                    cardTitleStyle = cardTitleStyle,
                    cardSubtitleStyle = cardSubtitleStyle,
                    cardValueStyle = cardValueStyle,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ActionSettingItem(
                    title = "接口地址",
                    subtitle = settings.mmModelUrl.ifBlank { "未填写接口地址" },
                    value = if (settings.mmModelUrl.isNotBlank()) "已配置" else "未配置",
                    enabled = false,
                    onClick = {},
                    cardTitleStyle = cardTitleStyle,
                    cardSubtitleStyle = cardSubtitleStyle,
                    cardValueStyle = cardValueStyle,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ActionSettingItem(
                    title = "识别模式",
                    subtitle = RecognitionMode.label(settings.recognitionMode),
                    value = "当前",
                    enabled = false,
                    onClick = {},
                    cardTitleStyle = cardTitleStyle,
                    cardSubtitleStyle = cardSubtitleStyle,
                    cardValueStyle = cardValueStyle,
                )
            }
        }

        AppSettingsCard {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("AI 接口配置", style = cardTitleStyle)
                EditionTextField(
                    value = aiName,
                    onValueChange = { aiName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("模型名称") },
                    singleLine = true,
                )
                EditionTextField(
                    value = aiUrl,
                    onValueChange = { aiUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("AI API URL") },
                    singleLine = true,
                )
                EditionTextField(
                    value = aiKey,
                    onValueChange = { aiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("AI API Key") },
                    singleLine = true,
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    EditionButton(onClick = { onSaveAiConfig(aiKey.trim(), aiName.trim(), aiUrl.trim()) }) {
                        Text("保存 AI 配置")
                    }
                }
            }
        }

        AppSettingsCard {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                SwitchSettingItem(
                    title = "禁用思考",
                    subtitle = "适用于不需要 reasoning 的模型或场景",
                    checked = settings.disableThinking,
                    onCheckedChange = onDisableThinkingChange,
                    cardTitleStyle = cardTitleStyle,
                    cardSubtitleStyle = cardSubtitleStyle,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SwitchSettingItem(
                    title = "本地语义",
                    subtitle = "使用本地语义能力辅助识别，未配置本地模型时可跳过",
                    checked = settings.isLocalSemanticEnabled,
                    onCheckedChange = onLocalSemanticChange,
                    cardTitleStyle = cardTitleStyle,
                    cardSubtitleStyle = cardSubtitleStyle,
                )
            }
        }
    }
}

private fun permissionStatusText(item: PermissionItem): String = when (item.uiState) {
    PermissionUiState.GRANTED -> "已开启"
    PermissionUiState.SYSTEM_ALLOWED -> "系统允许"
    PermissionUiState.NEEDS_ACTION -> "去授权"
    PermissionUiState.NEEDS_CONFIRMATION -> "去确认"
}

private fun privilegedPermissionKey(key: PermissionKey): PrivilegedPermissionKey? = when (key) {
    PermissionKey.NOTIFICATION -> PrivilegedPermissionKey.NOTIFICATION
    PermissionKey.OVERLAY -> PrivilegedPermissionKey.OVERLAY
    PermissionKey.MICROPHONE -> PrivilegedPermissionKey.MICROPHONE
    PermissionKey.ACCESSIBILITY -> PrivilegedPermissionKey.ACCESSIBILITY
    PermissionKey.CALENDAR -> PrivilegedPermissionKey.CALENDAR
    PermissionKey.EXACT_ALARM -> PrivilegedPermissionKey.EXACT_ALARM
    PermissionKey.BATTERY -> PrivilegedPermissionKey.BATTERY
    PermissionKey.LOCATION -> PrivilegedPermissionKey.LOCATION
    PermissionKey.NOTIFICATION_LISTENER -> PrivilegedPermissionKey.NOTIFICATION_LISTENER
    PermissionKey.SMS -> PrivilegedPermissionKey.SMS
    PermissionKey.LIVE_NOTIFICATION,
    PermissionKey.AUTOSTART,
    PermissionKey.ROOT_ACCESS -> null
}

private fun getPermissionIcon(key: PermissionKey): ImageVector = when (key) {
    PermissionKey.NOTIFICATION -> Icons.Rounded.Notifications
    PermissionKey.LIVE_NOTIFICATION -> Icons.Rounded.Notifications
    PermissionKey.OVERLAY -> Icons.Rounded.Layers
    PermissionKey.MICROPHONE -> Icons.Rounded.Mic
    PermissionKey.CALENDAR -> Icons.Rounded.CalendarToday
    PermissionKey.EXACT_ALARM -> Icons.Rounded.Alarm
    PermissionKey.BATTERY -> Icons.Rounded.BatterySaver
    PermissionKey.AUTOSTART -> Icons.Rounded.RocketLaunch
    PermissionKey.LOCATION -> Icons.Rounded.LocationOn
    PermissionKey.ACCESSIBILITY -> Icons.Rounded.Accessibility
    PermissionKey.NOTIFICATION_LISTENER -> Icons.Rounded.MarkEmailRead
    PermissionKey.SMS -> Icons.Rounded.Sms
    PermissionKey.ROOT_ACCESS -> Icons.Rounded.Terminal
}

private fun permissionGrantedColor(): Color = Color(0xFF2E7D32)

private fun floatingEventRangeLabel(range: Int): String = when (range.coerceIn(0, 2)) {
    0 -> "全部日程"
    1 -> "今日日程"
    else -> "今日和明日"
}

private fun floatingWeatherForecastRangeLabel(range: Int): String = when (range.coerceIn(0, 2)) {
    0 -> "当前天气"
    1 -> "今日预测"
    else -> "今日和明日"
}

private fun onboardingPermissionItems(snapshot: PermissionSnapshot): List<PermissionItem> = listOf(
    PermissionItem(PermissionKey.NOTIFICATION, "基础权限", "通知权限", "普通提醒、每日提醒、天气提醒和手环模式依赖它", RequirementLevel.REQUIRED, snapshot.notification, "去授权"),
    PermissionItem(
        PermissionKey.LIVE_NOTIFICATION,
        "基础权限",
        "实况通知权限",
        snapshot.liveNotificationDescription,
        RequirementLevel.REQUIRED,
        snapshot.liveNotification,
        "去授权",
        uiStateOverride = if (OsUtils.isColorOsLike()) {
            PermissionUiState.NEEDS_CONFIRMATION
        } else {
            PermissionUiState.SYSTEM_ALLOWED
        },
    ),
    PermissionItem(PermissionKey.OVERLAY, "随口记与悬浮窗", "悬浮窗权限", "悬浮窗、悬浮球、侧边栏和浮窗随口记依赖它", RequirementLevel.REQUIRED, snapshot.overlay, "去授权"),
    PermissionItem(PermissionKey.MICROPHONE, "随口记与悬浮窗", "麦克风权限", "语音随口记、录音和转写前录音依赖它", RequirementLevel.REQUIRED, snapshot.microphone, "去授权"),
    PermissionItem(PermissionKey.ACCESSIBILITY, "随口记与悬浮窗", "无障碍服务", "音量键长按、部分系统级快捷入口依赖它", RequirementLevel.REQUIRED, snapshot.accessibility, "去授权"),
    PermissionItem(PermissionKey.CALENDAR, "日程与提醒", "日历读写权限", "系统日历同步、导入后写入系统日历依赖它", RequirementLevel.RECOMMENDED, snapshot.calendar, "去授权"),
    PermissionItem(
        PermissionKey.EXACT_ALARM,
        "日程与提醒",
        "精准闹钟权限",
        "日程提醒和实况通知提前显示会更准时",
        RequirementLevel.RECOMMENDED,
        snapshot.exactAlarm,
        "去授权",
        uiStateOverride = if (snapshot.exactAlarm) PermissionUiState.SYSTEM_ALLOWED else null,
    ),
    PermissionItem(PermissionKey.BATTERY, "后台稳定性", "电池优化白名单", "后台提醒、每日提醒和实况通知稳定性依赖它", RequirementLevel.RECOMMENDED, snapshot.batteryOptimization, "去授权"),
    PermissionItem(PermissionKey.AUTOSTART, "后台稳定性", "自启动/后台运行", "开机后恢复提醒和后台任务稳定性依赖它", RequirementLevel.RECOMMENDED, snapshot.autostart, "去授权"),
    PermissionItem(PermissionKey.LOCATION, "可选能力", "定位权限", "用于启用自动定位", RequirementLevel.OPTIONAL, snapshot.location, "去授权"),
    PermissionItem(PermissionKey.NOTIFICATION_LISTENER, "可选能力", "通知监听权限", "部分通知识别和系统通知读取依赖它", RequirementLevel.OPTIONAL, snapshot.notificationListener, "去授权"),
    PermissionItem(PermissionKey.SMS, "可选能力", "短信权限", "短信识别、取件码/验证码类识别依赖它；敏感权限默认不强推", RequirementLevel.OPTIONAL, snapshot.sms, "去授权"),
    PermissionItem(PermissionKey.ROOT_ACCESS, "高级能力", "Root 权限", "Root 设备可在授权后直接管理本页支持的权限", RequirementLevel.OPTIONAL, snapshot.rootAccess, "去授权"),
)

private fun onboardingFeatureItems(settings: MySettings, syncEnabled: Boolean, snapshot: PermissionSnapshot): List<FeatureItem> = listOf(
    FeatureItem(FeatureKey.FLOATING_WINDOW, "悬浮窗", "桌面悬浮窗、侧边栏、悬浮球入口", settings.isFloatingWindowEnabled, snapshot.overlay, "需要先开启悬浮窗权限"),
    FeatureItem(FeatureKey.QUICK_MEMO, "随口记", "文字/语音随口记入口，语音能力需要麦克风", settings.voiceInputEnabled, snapshot.microphone && snapshot.overlay, "需要先开启麦克风权限和悬浮窗权限"),
    FeatureItem(FeatureKey.LIVE_NOTIFICATION, "实况通知", "日程、识别、天气等以胶囊/灵动形态显示", settings.isLiveCapsuleEnabled, liveNotificationRequirementMet(snapshot), "需要先开启通知权限，并确认实况通知权限已开启"),
    FeatureItem(FeatureKey.DAILY_SUMMARY, "每日提醒", "今日提醒和明日预告", settings.isDailySummaryEnabled, snapshot.notification, "需要先开启通知权限"),
    FeatureItem(FeatureKey.CALENDAR_SYNC, "日历同步", "与系统日历同步日程", syncEnabled, snapshot.calendar, "需要先开启日历读写权限"),
    FeatureItem(FeatureKey.ADVANCE_REMINDER, "提前提醒", "日程开始前提前提醒", settings.isAdvanceReminderEnabled, snapshot.notification, "需要先开启通知权限"),
    FeatureItem(FeatureKey.WEATHER, "天气服务", "天气卡片和天气提醒基础能力", settings.weatherEnabled, snapshot.location || settings.weatherManualLocationId.isNotBlank(), "需要开启定位权限，或先在天气设置中选择手动城市"),
    FeatureItem(FeatureKey.WEATHER_ALERT, "天气预警", "天气预警和风险提醒通知", settings.weatherWarningEnabled || settings.weatherRiskWarningEnabled, snapshot.notification && settings.weatherEnabled, "需要先开启通知权限和天气服务"),
    FeatureItem(FeatureKey.BRACELET_MODE, "手环模式", "开启后，将同步发送一条普通通知以同步到手环", settings.braceletModeEnabled, snapshot.notification, "需要先开启通知权限"),
    FeatureItem(FeatureKey.COURSE, "课程", "课程表、学期设置和课程提醒", settings.courseFeatureEnabled, true, "课程功能可稍后在设置中完善"),
    FeatureItem(FeatureKey.SMS_RECOGNITION, "短信识别", "短信取件码/验证码类识别，敏感功能默认关闭", settings.isSmsMonitoringEnabled, snapshot.sms, "需要先开启短信权限"),
    FeatureItem(FeatureKey.VOLUME_SHORTCUT, "音量键快捷随口记", "通过无障碍监听音量键长按触发快捷功能", settings.volumeUpLongPressEnabled, snapshot.accessibility, "需要先开启无障碍服务"),
)

private fun missingFeatureRequirement(featureKey: FeatureKey, snapshot: PermissionSnapshot): String? = when (featureKey) {
    FeatureKey.FLOATING_WINDOW -> if (!snapshot.overlay) "需要先开启悬浮窗权限" else null
    FeatureKey.QUICK_MEMO -> if (!snapshot.microphone || !snapshot.overlay) "需要先开启麦克风权限和悬浮窗权限" else null
    FeatureKey.LIVE_NOTIFICATION -> if (!liveNotificationRequirementMet(snapshot)) "需要先开启通知权限，并确认实况通知权限已开启" else null
    FeatureKey.DAILY_SUMMARY -> if (!snapshot.notification) "需要先开启通知权限" else null
    FeatureKey.CALENDAR_SYNC -> if (!snapshot.calendar) "需要先开启日历读写权限" else null
    FeatureKey.ADVANCE_REMINDER -> if (!snapshot.notification) "需要先开启通知权限" else null
    FeatureKey.WEATHER -> if (!snapshot.location) "需要先开启定位权限，或在天气设置中选择手动城市" else null
    FeatureKey.WEATHER_ALERT -> if (!snapshot.notification) "需要先开启通知权限" else null
    FeatureKey.BRACELET_MODE -> if (!snapshot.notification) "需要先开启通知权限" else null
    FeatureKey.SMS_RECOGNITION -> if (!snapshot.sms) "需要先开启短信权限" else null
    FeatureKey.VOLUME_SHORTCUT -> if (!snapshot.accessibility) "需要先开启无障碍服务" else null
    FeatureKey.COURSE -> null
}

private fun liveNotificationRequirementMet(snapshot: PermissionSnapshot): Boolean =
    snapshot.notification && (OsUtils.isColorOsLike() || snapshot.liveNotification)

private fun readOnboardingPermissions(
    context: Context,
    simulateRoot: Boolean = false,
): PermissionSnapshot {
    PrivilegeManager.refreshPrivilege()
    val livePermissionGranted = if (OsUtils.isColorOsLike()) {
        false
    } else {
        true
    }
    logRuntimePermission(context, Manifest.permission.POST_NOTIFICATIONS)
    logRuntimePermission(context, Manifest.permission.RECORD_AUDIO)
    logRuntimePermission(context, Manifest.permission.READ_CALENDAR)
    logRuntimePermission(context, Manifest.permission.WRITE_CALENDAR)
    logRuntimePermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
    logRuntimePermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
    logRuntimePermission(context, Manifest.permission.READ_SMS)
    logRuntimePermission(context, Manifest.permission.RECEIVE_SMS)
    logRuntimePermission(context, "android.permission.POST_PROMOTED_NOTIFICATIONS")
    val overlayEnabled = Settings.canDrawOverlays(context)
    Log.d(ONBOARDING_LOG_TAG, "SystemSwitch overlay=$overlayEnabled")

    val snapshot = PermissionSnapshot(
        notification = PrivilegedPermissionController.isEnabled(
            context,
            PrivilegedPermissionKey.NOTIFICATION,
            simulateRoot,
        ),
        liveNotification = livePermissionGranted,
        liveNotificationDescription = if (OsUtils.isColorOsLike()) {
            "系统未开放状态读取。请进入通知设置，确认“流体云显示实时活动”已开启，否则实况胶囊可能不显示。"
        } else {
            "部分系统需要进入应用通知设置确认"
        },
        overlay = PrivilegedPermissionController.isEnabled(context, PrivilegedPermissionKey.OVERLAY, simulateRoot),
        microphone = PrivilegedPermissionController.isEnabled(context, PrivilegedPermissionKey.MICROPHONE, simulateRoot),
        calendar = PrivilegedPermissionController.isEnabled(context, PrivilegedPermissionKey.CALENDAR, simulateRoot) &&
            (simulateRoot || canAccessCalendarProvider(context)),
        exactAlarm = PrivilegedPermissionController.isEnabled(context, PrivilegedPermissionKey.EXACT_ALARM, simulateRoot),
        batteryOptimization = PrivilegedPermissionController.isEnabled(context, PrivilegedPermissionKey.BATTERY, simulateRoot),
        autostart = false,
        location = PrivilegedPermissionController.isEnabled(context, PrivilegedPermissionKey.LOCATION, simulateRoot),
        accessibility = PrivilegedPermissionController.isEnabled(context, PrivilegedPermissionKey.ACCESSIBILITY, simulateRoot),
        notificationListener = PrivilegedPermissionController.isEnabled(context, PrivilegedPermissionKey.NOTIFICATION_LISTENER, simulateRoot),
        sms = PrivilegedPermissionController.isEnabled(context, PrivilegedPermissionKey.SMS, simulateRoot),
        rootAccess = simulateRoot || PrivilegeManager.privilegeType == PrivilegeManager.PrivilegeType.ROOT,
    )
    Log.d(
        ONBOARDING_LOG_TAG,
        buildString {
            append("PermissionSnapshot ")
            append("package=${context.packageName}, ")
            append("sdk=${Build.VERSION.SDK_INT}, ")
            append("manufacturer=${Build.MANUFACTURER}, ")
            append("brand=${Build.BRAND}, ")
            append("model=${Build.MODEL}, ")
            append("isColorOsLike=${OsUtils.isColorOsLike()}, ")
            append("notification=${snapshot.notification}, ")
            append("liveNotification=${snapshot.liveNotification}, ")
            append("overlay=${snapshot.overlay}, ")
            append("microphone=${snapshot.microphone}, ")
            append("calendar=${snapshot.calendar}, ")
            append("exactAlarm=${snapshot.exactAlarm}, ")
            append("batteryOptimization=${snapshot.batteryOptimization}, ")
            append("autostart=${snapshot.autostart}, ")
            append("location=${snapshot.location}, ")
            append("accessibility=${snapshot.accessibility}, ")
            append("notificationListener=${snapshot.notificationListener}, ")
            append("sms=${snapshot.sms}, ")
            append("rootAccess=${snapshot.rootAccess}")
        }
    )
    return snapshot
}

private fun hasPermission(context: Context, permission: String): Boolean =
    PermissionChecker.checkSelfPermission(context, permission) == PermissionChecker.PERMISSION_GRANTED

private fun canAccessCalendarProvider(context: Context): Boolean {
    return try {
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID),
            null,
            null,
            null,
        )?.use { cursor ->
            cursor.count >= 0
        } == true
    } catch (e: SecurityException) {
        Log.w(ONBOARDING_LOG_TAG, "CalendarProviderAccess denied: ${e.message}", e)
        false
    } catch (e: Exception) {
        Log.w(ONBOARDING_LOG_TAG, "CalendarProviderAccess failed: ${e.message}", e)
        false
    }.also {
        Log.d(ONBOARDING_LOG_TAG, "SystemProbe calendarProviderAccess=$it")
    }
}

private fun logRuntimePermission(context: Context, permission: String) {
    val checkResult = ContextCompat.checkSelfPermission(context, permission)
    val permissionCheckerResult = PermissionChecker.checkSelfPermission(context, permission)
    val requestedFlagGranted = runCatching {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        }
        val permissions = packageInfo.requestedPermissions.orEmpty()
        val flags = packageInfo.requestedPermissionsFlags ?: intArrayOf()
        val index = permissions.indexOf(permission)
        if (index >= 0 && index < flags.size) {
            flags[index] and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0
        } else {
            null
        }
    }.getOrElse { error ->
        Log.w(ONBOARDING_LOG_TAG, "PermissionFlagReadFailed permission=$permission error=${error.message}", error)
        null
    }
    Log.d(
        ONBOARDING_LOG_TAG,
        "RuntimePermission permission=$permission checkSelf=${permissionResultName(checkResult)} " +
            "permissionChecker=${permissionCheckerResultName(permissionCheckerResult)} " +
            "requestedFlagGranted=$requestedFlagGranted"
    )
}

private fun permissionResultName(result: Int): String = when (result) {
    PackageManager.PERMISSION_GRANTED -> "GRANTED"
    PackageManager.PERMISSION_DENIED -> "DENIED"
    else -> result.toString()
}

private fun permissionCheckerResultName(result: Int): String = when (result) {
    PermissionChecker.PERMISSION_GRANTED -> "GRANTED"
    PermissionChecker.PERMISSION_DENIED -> "DENIED"
    PermissionChecker.PERMISSION_DENIED_APP_OP -> "DENIED_APP_OP"
    else -> result.toString()
}

private fun areAppNotificationsEnabled(context: Context): Boolean {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    val enabled = notificationManager?.areNotificationsEnabled() == true
    Log.d(ONBOARDING_LOG_TAG, "SystemSwitch notificationManager.areNotificationsEnabled=$enabled")
    return enabled
}

private fun canScheduleExactAlarms(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        Log.d(ONBOARDING_LOG_TAG, "SystemSwitch exactAlarm=true reason=sdk<31")
        return true
    }
    val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return false
    val enabled = alarmManager.canScheduleExactAlarms()
    Log.d(ONBOARDING_LOG_TAG, "SystemSwitch exactAlarm=$enabled")
    return enabled
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val powerManager = context.getSystemService(PowerManager::class.java) ?: return false
    val enabled = powerManager.isIgnoringBatteryOptimizations(context.packageName)
    Log.d(ONBOARDING_LOG_TAG, "SystemSwitch ignoringBatteryOptimizations=$enabled")
    return enabled
}

private fun isAccessibilityEnabled(context: Context): Boolean {
    val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        ?: return false
    val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(
        android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
    )
    val serviceNames = enabledServices.map { it.resolveInfo.serviceInfo.name }
    val enabled = enabledServices.any {
        it.resolveInfo.serviceInfo.packageName == context.packageName &&
            it.resolveInfo.serviceInfo.name == TextAccessibilityService::class.java.name
    }
    Log.d(ONBOARDING_LOG_TAG, "SystemSwitch accessibility=$enabled enabledServices=$serviceNames")
    return enabled
}

private fun isNotificationListenerEnabled(context: Context): Boolean =
    SmsNotificationListenerService.isEnabled(context).also {
        Log.d(ONBOARDING_LOG_TAG, "SystemSwitch notificationListener=$it")
    }

private fun openAppNotificationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
    }
    context.startActivity(intent.addNewTask())
}

private fun openOverlaySettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")).addNewTask())
}

private fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")).addNewTask())
    } else {
        openAppDetails(context)
    }
}

private fun openBatterySettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addNewTask())
}

private fun openAccessibilitySettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addNewTask())
}

private fun openNotificationListenerSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addNewTask())
}

private fun openAppPermissionSettings(context: Context) {
    val candidates = listOf(
        Intent("android.intent.action.MANAGE_APP_PERMISSIONS").apply {
            putExtra("android.intent.extra.PACKAGE_NAME", context.packageName)
        },
        Intent("android.settings.APP_PERMISSION_SETTINGS").apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            data = Uri.fromParts("package", context.packageName, null)
        },
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
    )
    for (intent in candidates) {
        val started = runCatching {
            context.startActivity(intent.addNewTask())
        }.isSuccess
        if (started) return
    }
    openAppDetails(context)
}

private fun openAppDetails(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }.addNewTask()
    )
}

private fun Intent.addNewTask(): Intent = apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

private fun buildOnboardingSteps(): List<OnboardingStep> = listOf(
    OnboardingStep.PERMISSIONS,
    OnboardingStep.SCHEDULE_REMINDER,
    OnboardingStep.FLOATING_QUICK_MEMO,
    OnboardingStep.MODEL,
    OnboardingStep.WEATHER,
)

private enum class OnboardingStep(
    val title: String,
    val description: String,
) {
    PERMISSIONS(
        "配置基础权限",
        "开启必要权限，确保随口记、提醒、日历同步和实况通知稳定运行。"
    ),
    SCHEDULE_REMINDER(
        "日程和提醒",
        "配置每日提醒、提前提醒、日历同步、实况通知和手环模式。",
    ),
    FLOATING_QUICK_MEMO(
        "悬浮窗和随口记",
        "配置日程悬浮、随口记、音量键快捷和相关入口。",
    ),
    MODEL(
        "模型与连接",
        "配置支持图片输入的 AI 模型及识别偏好。",
    ),
    WEATHER(
        "天气配置",
        "配置天气服务、天气预警、定位和天气接口。",
    ),
}

private enum class PermissionKey {
    NOTIFICATION,
    LIVE_NOTIFICATION,
    OVERLAY,
    MICROPHONE,
    CALENDAR,
    EXACT_ALARM,
    BATTERY,
    AUTOSTART,
    LOCATION,
    ACCESSIBILITY,
    NOTIFICATION_LISTENER,
    SMS,
    ROOT_ACCESS,
}

private enum class FeatureKey {
    FLOATING_WINDOW,
    QUICK_MEMO,
    LIVE_NOTIFICATION,
    DAILY_SUMMARY,
    CALENDAR_SYNC,
    ADVANCE_REMINDER,
    WEATHER,
    WEATHER_ALERT,
    BRACELET_MODE,
    SMS_RECOGNITION,
    VOLUME_SHORTCUT,
    COURSE,
}

private enum class RequirementLevel { REQUIRED, RECOMMENDED, OPTIONAL }

private enum class PermissionUiState { GRANTED, SYSTEM_ALLOWED, NEEDS_ACTION, NEEDS_CONFIRMATION }

private data class PermissionSnapshot(
    val notification: Boolean,
    val liveNotification: Boolean,
    val liveNotificationDescription: String,
    val overlay: Boolean,
    val microphone: Boolean,
    val calendar: Boolean,
    val exactAlarm: Boolean,
    val batteryOptimization: Boolean,
    val autostart: Boolean,
    val location: Boolean,
    val accessibility: Boolean,
    val notificationListener: Boolean,
    val sms: Boolean,
    val rootAccess: Boolean,
)

private data class PermissionItem(
    val key: PermissionKey,
    val group: String,
    val title: String,
    val subtitle: String,
    val level: RequirementLevel,
    val granted: Boolean,
    val actionLabel: String,
    val uiStateOverride: PermissionUiState? = null,
) {
    val uiState: PermissionUiState
        get() = uiStateOverride ?: if (granted) PermissionUiState.GRANTED else PermissionUiState.NEEDS_ACTION
}

private data class FeatureItem(
    val key: FeatureKey,
    val title: String,
    val subtitle: String,
    val enabled: Boolean,
    val requirementsMet: Boolean,
    val warning: String,
)
