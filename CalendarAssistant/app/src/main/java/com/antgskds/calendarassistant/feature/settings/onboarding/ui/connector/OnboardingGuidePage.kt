package com.antgskds.calendarassistant.feature.settings.onboarding.ui.connector
import com.antgskds.calendarassistant.shared.ui.edition.EditionTextField
import com.antgskds.calendarassistant.shared.ui.edition.EditionButton
import com.antgskds.calendarassistant.shared.ui.edition.EditionSlider
import com.antgskds.calendarassistant.shared.ui.edition.EditionSwitch
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
import android.util.Log
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.automirrored.rounded.ViewSidebar
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.alpha
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
import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import com.antgskds.calendarassistant.feature.settings.data.model.RecognitionMode
import com.antgskds.calendarassistant.platform.accessibility.TextAccessibilityService
import com.antgskds.calendarassistant.platform.receiver.SmsNotificationListenerService
import com.antgskds.calendarassistant.shared.ui.interaction.rememberAppHaptics
import com.antgskds.calendarassistant.shared.ui.material.component.AppSettingsCard
import com.antgskds.calendarassistant.shared.ui.material.component.UniversalToast
import com.antgskds.calendarassistant.shared.ui.material.component.ToastType
import com.antgskds.calendarassistant.shared.util.OsUtils
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val ONBOARDING_LOG_TAG = "OnboardingGuide"

@Composable
fun OnboardingGuidePage(
    settingsViewModel: SettingsViewModel,
    uiSize: Int = 2,
    onFinish: (() -> Unit)? = null,
    onImportConfig: (() -> Unit)? = null,
) {
    val settings by settingsViewModel.settings.collectAsState()
    val syncStatus by settingsViewModel.syncStatus.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptics = rememberAppHaptics(settings.hapticFeedbackEnabled)
    var page by remember { mutableIntStateOf(0) }
    var setupMode by remember { mutableStateOf<OnboardingSetupMode?>(null) }
    var permissionSnapshot by remember { mutableStateOf(readOnboardingPermissions(context)) }
    val blockedFeatureWarnings = remember { mutableStateMapOf<FeatureKey, String>() }
    val steps = remember(settings, syncStatus.isEnabled, setupMode) {
        buildOnboardingSteps(settings, syncStatus.isEnabled, setupMode)
    }
    val currentStep = steps.getOrElse(page) { steps.last() }

    fun refreshPermissions() {
        permissionSnapshot = readOnboardingPermissions(context)
        settingsViewModel.refreshSyncStatus()
    }

    fun toast(message: String, type: ToastType = ToastType.INFO) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
        if (type == ToastType.ERROR) haptics.error() else haptics.confirm()
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
            PermissionKey.SHIZUKU_ROOT -> Toast.makeText(context, "Shizuku/Root 属于高级能力，请在系统工具中单独配置", Toast.LENGTH_LONG).show()
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

    fun skipCurrentConfig() {
        haptics.selection()
        if (page < steps.lastIndex) {
            page += 1
        } else {
            finishOnboarding()
        }
    }

    fun applyAuthorRecommended() {
        setupMode = OnboardingSetupMode.AUTHOR_RECOMMENDED
        blockedFeatureWarnings.clear()
        if (permissionSnapshot.overlay) settingsViewModel.updatePreference(floatingWindow = true)
        if (permissionSnapshot.overlay && permissionSnapshot.microphone) settingsViewModel.updatePreference(voiceInputEnabled = true)
        if (liveNotificationRequirementMet(permissionSnapshot)) settingsViewModel.updatePreference(liveCapsule = true)
        if (permissionSnapshot.notification) {
            settingsViewModel.updatePreference(dailySummary = true)
            settingsViewModel.updatePreference(advanceReminderEnabled = true)
            settingsViewModel.updatePreference(braceletModeEnabled = true)
        }
        if (permissionSnapshot.calendar) settingsViewModel.toggleCalendarSync(true)
        if (permissionSnapshot.location || settings.weatherManualLocationId.isNotBlank()) {
            updateWeather(enabled = true, warningEnabled = permissionSnapshot.notification, riskWarningEnabled = permissionSnapshot.notification)
        }
        settingsViewModel.updatePreference(courseFeatureEnabled = false)
        toast("已应用作者推荐")
        page = 2
    }

    LaunchedEffect(Unit) {
        refreshPermissions()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshPermissions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(steps.size) {
        if (page > steps.lastIndex) page = steps.lastIndex.coerceAtLeast(0)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            OnboardingHeader(
                page = page,
                totalPages = steps.size,
                step = currentStep,
                permissionSnapshot = permissionSnapshot,
                featureCount = onboardingFeatureItems(settings, syncStatus.isEnabled, permissionSnapshot).size,
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (currentStep) {
                    OnboardingStep.PERMISSIONS -> PermissionStep(
                        snapshot = permissionSnapshot,
                        onOpenPermission = ::openPermission,
                    )
                    OnboardingStep.SETUP_MODE -> SetupModeStep(
                        selectedMode = setupMode,
                        onAuthorRecommended = {
                            haptics.confirm()
                            applyAuthorRecommended()
                        },
                        onCustom = {
                            haptics.confirm()
                            setupMode = OnboardingSetupMode.CUSTOM
                            page = 2
                        },
                        onImport = {
                            haptics.confirm()
                            setupMode = OnboardingSetupMode.IMPORT_CONFIG
                            if (onImportConfig != null) {
                                onImportConfig()
                            } else {
                                toast("请在备份页面导入配置")
                            }
                        },
                    )
                    OnboardingStep.FEATURES -> FeatureStep(
                        settings = settings,
                        syncEnabled = syncStatus.isEnabled,
                        snapshot = permissionSnapshot,
                        warnings = blockedFeatureWarnings,
                        onFeatureChange = ::setFeature,
                        onOpenPermissionPage = { page = 0 },
                    )
                    OnboardingStep.SCHEDULE_REMINDER -> ScheduleReminderConfigStep(
                        settings = settings,
                        syncEnabled = syncStatus.isEnabled,
                        snapshot = permissionSnapshot,
                        onFeatureChange = ::setFeature,
                        onOpenPermission = ::openPermission,
                        onAdvanceReminderMinutesChange = {
                            settingsViewModel.updatePreference(advanceReminderMinutes = it)
                        },
                    )
                    OnboardingStep.FLOATING_QUICK_MEMO -> FloatingQuickMemoConfigStep(
                        settings = settings,
                        snapshot = permissionSnapshot,
                        onFeatureChange = ::setFeature,
                        onOpenPermission = ::openPermission,
                        onFloatingEventRangeChange = {
                            settingsViewModel.updatePreference(floatingEventRange = it)
                        },
                        onFloatingExpandSideChange = {
                            settingsViewModel.updatePreference(floatingExpandSide = it)
                        },
                        onEdgeBarEnabledChange = {
                            settingsViewModel.updateEdgeBarSettings(enabled = it)
                        },
                        onEdgeBarHeightChange = {
                            settingsViewModel.updateEdgeBarSettings(heightDp = it)
                        },
                        onEdgeBarAlphaChange = {
                            settingsViewModel.updateEdgeBarSettings(alpha = it)
                        },
                        onFloatingBallEnabledChange = {
                            settingsViewModel.updatePreference(floatingBallEnabled = it)
                        },
                        onFloatingBallSizeChange = {
                            settingsViewModel.updatePreference(floatingBallSizeDp = it)
                        },
                        onFloatingBallAlphaChange = {
                            settingsViewModel.updatePreference(floatingBallAlpha = it)
                        },
                        onFloatingTextQuickMemoAutoPinChange = {
                            settingsViewModel.updatePreference(floatingTextQuickMemoAutoPinEnabled = it)
                        },
                        onVoiceQuickMemoAutoPinChange = {
                            settingsViewModel.updatePreference(voiceQuickMemoAutoPinEnabled = it)
                        },
                    )
                    OnboardingStep.MODEL -> AiConfigStep(
                        settings = settings,
                        onMultimodalChange = { settingsViewModel.updatePreference(useMultimodalAi = it) },
                        onDisableThinkingChange = { settingsViewModel.updatePreference(disableThinking = it) },
                        onLocalSemanticChange = { settingsViewModel.updatePreference(localSemanticEnabled = it) },
                        onSaveAiConfig = { key, name, url ->
                            settingsViewModel.updateAiSettings(key = key, name = name, url = url)
                            toast("AI 配置已保存")
                        },
                    )
                    OnboardingStep.WEATHER -> WeatherConfigStep(
                        settings = settings,
                        snapshot = permissionSnapshot,
                        onWeatherEnabledChange = { updateWeather(enabled = it) },
                        onWeatherWarningChange = { updateWeather(warningEnabled = it, riskWarningEnabled = it) },
                        onShowInFloatingChange = { updateWeather(showInFloating = it) },
                        onRefreshIntervalChange = { updateWeather(refreshInterval = it) },
                        onWarningLookaheadChange = { updateWeather(warningLookaheadHours = it) },
                        onFloatingForecastRangeChange = { updateWeather(floatingWeatherForecastRange = it) },
                        onSaveWeatherConfig = { apiUrl, apiKey ->
                            updateWeather(apiUrl = apiUrl, apiKey = apiKey)
                            toast("天气配置已保存")
                        },
                        onRequestLocationPermission = { openPermission(PermissionKey.LOCATION) },
                    )
                    OnboardingStep.COURSE -> CourseConfigStep(
                        settings = settings,
                        onCourseFeatureChange = { setFeature(FeatureKey.COURSE, it) },
                    )
                }

            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            OnboardingActionRow(
                page = page,
                totalPages = steps.size,
                currentStep = currentStep,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
                onPrevious = {
                    haptics.selection()
                    page = (page - 1).coerceAtLeast(0)
                },
                onSkip = ::skipCurrentConfig,
                onNextOrFinish = ::goNextOrFinish,
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
            snackbar = { data -> UniversalToast(message = data.visuals.message, type = ToastType.INFO) }
        )
    }
}

@Composable
private fun OnboardingActionRow(
    page: Int,
    totalPages: Int,
    currentStep: OnboardingStep,
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
        if (currentStep.isSkippableConfig) {
            TextButton(onClick = onSkip) {
                Text("跳过")
            }
        } else {
            Spacer(Modifier.width(1.dp))
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
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 10.dp, bottom = 12.dp),
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
    onOpenPermission: (PermissionKey) -> Unit,
) {
    val allItems = onboardingPermissionItems(snapshot)
    val requiredItems = allItems.filter { it.level == RequirementLevel.REQUIRED }
    val recommendedItems = allItems.filter { it.level == RequirementLevel.RECOMMENDED }
    val optionalItems = allItems.filter { it.level == RequirementLevel.OPTIONAL }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PermissionSection(
            title = "必要权限",
            description = "建议首次使用前开启，否则核心功能可能无法正常工作。",
            items = requiredItems,
            onOpenPermission = onOpenPermission,
        )
        PermissionSection(
            title = "建议配置",
            description = "用于提升提醒准时性、后台稳定性和日程同步体验。",
            items = recommendedItems,
            onOpenPermission = onOpenPermission,
        )
        PermissionSection(
            title = "可选能力",
            description = "只在你需要对应功能时开启，之后也可以在设置中调整。",
            items = optionalItems,
            onOpenPermission = onOpenPermission,
        )
    }
}

@Composable
private fun PermissionSection(
    title: String,
    description: String,
    items: List<PermissionItem>,
    onOpenPermission: (PermissionKey) -> Unit,
) {
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AppSettingsCard {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                items.forEachIndexed { index, item ->
                    PermissionModernRow(item = item, onClick = { onOpenPermission(item.key) })
                    if (index != items.lastIndex) HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                }
            }
        }
    }
}

@Composable
private fun SetupModeStep(
    selectedMode: OnboardingSetupMode?,
    onAuthorRecommended: () -> Unit,
    onCustom: () -> Unit,
    onImport: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SetupModeCard(
            icon = Icons.Rounded.AutoAwesome,
            title = "作者推荐",
            subtitle = "按推荐方案开启常用功能，适合第一次使用。",
            selected = selectedMode == OnboardingSetupMode.AUTHOR_RECOMMENDED,
            onClick = onAuthorRecommended,
        )
        SetupModeCard(
            icon = Icons.Rounded.Tune,
            title = "自己配置",
            subtitle = "逐项选择功能开关，再按需进入后续配置页。",
            selected = selectedMode == OnboardingSetupMode.CUSTOM,
            onClick = onCustom,
        )
        SetupModeCard(
            icon = Icons.Rounded.UploadFile,
            title = "导入配置",
            subtitle = "从备份文件恢复设置、日程、随口记等数据。",
            selected = selectedMode == OnboardingSetupMode.IMPORT_CONFIG,
            onClick = onImport,
        )
    }
}

@Composable
private fun SetupModeCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    AppSettingsCard(
        modifier = Modifier.clickable { onClick() },
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.56f),
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = permissionGrantedColor(),
                    modifier = Modifier.size(22.dp),
                )
            }
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
    AppSettingsCard {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            features.forEachIndexed { index, item ->
                FeatureModernRow(
                    item = item,
                    warning = warnings[item.key],
                    onCheckedChange = { onFeatureChange(item.key, it) },
                    onOpenPermissionPage = onOpenPermissionPage,
                )
                if (index != features.lastIndex) HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
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
    AppSettingsCard {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            OnboardingConfigRow(
                icon = Icons.Rounded.NotificationsActive,
                title = "每日提醒",
                subtitle = "今日提醒和明日预告",
                checked = settings.isDailySummaryEnabled,
                onCheckedChange = { onFeatureChange(FeatureKey.DAILY_SUMMARY, it) },
                enabled = snapshot.notification,
                onClick = if (snapshot.notification) null else ({ onOpenPermission(PermissionKey.NOTIFICATION) }),
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
            OnboardingConfigRow(
                icon = Icons.Rounded.Timer,
                title = "提前提醒",
                subtitle = "日程开始前提前提醒",
                checked = settings.isAdvanceReminderEnabled,
                onCheckedChange = { onFeatureChange(FeatureKey.ADVANCE_REMINDER, it) },
                enabled = snapshot.notification,
                onClick = if (snapshot.notification) null else ({ onOpenPermission(PermissionKey.NOTIFICATION) }),
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
            OnboardingConfigRow(
                icon = Icons.Rounded.Sync,
                title = "日历同步",
                subtitle = "与系统日历同步日程",
                checked = syncEnabled,
                onCheckedChange = { onFeatureChange(FeatureKey.CALENDAR_SYNC, it) },
                enabled = snapshot.calendar,
                onClick = if (snapshot.calendar) null else ({ onOpenPermission(PermissionKey.CALENDAR) }),
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
            OnboardingConfigRow(
                icon = Icons.Rounded.DynamicFeed,
                title = "实况通知",
                subtitle = "以胶囊/灵动形态显示提醒",
                checked = settings.isLiveCapsuleEnabled,
                onCheckedChange = { onFeatureChange(FeatureKey.LIVE_NOTIFICATION, it) },
                enabled = liveNotificationRequirementMet(snapshot),
                onClick = if (liveNotificationRequirementMet(snapshot)) null else ({ onOpenPermission(PermissionKey.LIVE_NOTIFICATION) }),
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
            OnboardingConfigRow(
                icon = Icons.Rounded.Watch,
                title = "手环模式",
                subtitle = "开启后，将同步发送一条普通通知以同步到手环",
                checked = settings.braceletModeEnabled,
                onCheckedChange = { onFeatureChange(FeatureKey.BRACELET_MODE, it) },
                enabled = snapshot.notification,
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
            OnboardingSliderRow(
                title = "提前分钟",
                subtitle = "日程开始前多久提醒",
                value = settings.advanceReminderMinutes.toFloat(),
                valueRange = 5f..120f,
                steps = 22,
                valueText = "${settings.advanceReminderMinutes} 分钟",
                onValueChangeFinished = { onAdvanceReminderMinutesChange(it.roundToInt()) },
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
    AppSettingsCard {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            OnboardingConfigRow(
                icon = Icons.Rounded.OpenInBrowser,
                title = "日程悬浮",
                subtitle = "桌面悬浮窗、侧边栏、悬浮球入口",
                checked = settings.isFloatingWindowEnabled,
                onCheckedChange = { onFeatureChange(FeatureKey.FLOATING_WINDOW, it) },
                enabled = snapshot.overlay,
                onClick = if (snapshot.overlay) null else ({ onOpenPermission(PermissionKey.OVERLAY) }),
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
            OnboardingSliderRow(
                title = "日程范围",
                subtitle = floatingEventRangeLabel(settings.floatingEventRange),
                value = settings.floatingEventRange.toFloat(),
                valueRange = 0f..2f,
                steps = 1,
                valueText = floatingEventRangeLabel(settings.floatingEventRange),
                onValueChangeFinished = { onFloatingEventRangeChange(it.roundToInt().coerceIn(0, 2)) },
                enabled = settings.isFloatingWindowEnabled,
                categoricalLabels = listOf("全部日程", "今日日程", "今日+明日"),
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
            OnboardingConfigRow(
                icon = Icons.Rounded.Mic,
                title = "随口记",
                subtitle = "文字/语音随口记入口",
                checked = settings.voiceInputEnabled,
                onCheckedChange = { onFeatureChange(FeatureKey.QUICK_MEMO, it) },
                enabled = snapshot.overlay && snapshot.microphone,
                onClick = if (snapshot.overlay && snapshot.microphone) null else ({ onOpenPermission(if (!snapshot.overlay) PermissionKey.OVERLAY else PermissionKey.MICROPHONE) }),
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
            OnboardingConfigRow(
                icon = Icons.AutoMirrored.Rounded.ViewSidebar,
                title = "侧边栏",
                subtitle = "屏幕边缘滑动呼出悬浮入口",
                checked = settings.edgeBarEnabled,
                onCheckedChange = onEdgeBarEnabledChange,
                enabled = snapshot.overlay && (settings.isFloatingWindowEnabled || settings.voiceInputEnabled),
                onClick = if (snapshot.overlay) null else ({ onOpenPermission(PermissionKey.OVERLAY) }),
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
            OnboardingTwoOptionRow(
                title = "展开方向",
                subtitle = "悬浮窗从哪一侧滑入",
                leftText = "左侧",
                rightText = "右侧",
                selectedRight = settings.floatingExpandSide == "RIGHT",
                onSelectLeft = { onFloatingExpandSideChange("LEFT") },
                onSelectRight = { onFloatingExpandSideChange("RIGHT") },
                enabled = settings.isFloatingWindowEnabled || settings.voiceInputEnabled,
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
            OnboardingSliderRow(
                title = "侧栏高度",
                subtitle = "调整侧边栏触发区域高度",
                value = settings.edgeBarHeightDp.toFloat(),
                valueRange = 80f..220f,
                steps = 13,
                valueText = "${settings.edgeBarHeightDp} dp",
                onValueChangeFinished = { onEdgeBarHeightChange(it.roundToInt()) },
                enabled = settings.edgeBarEnabled,
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
            OnboardingSliderRow(
                title = "侧栏透明",
                subtitle = "调整侧边栏可见程度",
                value = settings.edgeBarAlpha * 100f,
                valueRange = 10f..100f,
                steps = 8,
                valueText = "${(settings.edgeBarAlpha * 100f).roundToInt()}%",
                onValueChangeFinished = { onEdgeBarAlphaChange((it / 100f).coerceIn(0.1f, 1f)) },
                enabled = settings.edgeBarEnabled,
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
            OnboardingConfigRow(
                icon = Icons.Rounded.RadioButtonChecked,
                title = "悬浮球",
                subtitle = "桌面常驻圆形快捷入口",
                checked = settings.floatingBallEnabled,
                onCheckedChange = onFloatingBallEnabledChange,
                enabled = snapshot.overlay && (settings.isFloatingWindowEnabled || settings.voiceInputEnabled),
                onClick = if (snapshot.overlay) null else ({ onOpenPermission(PermissionKey.OVERLAY) }),
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
            OnboardingSliderRow(
                title = "悬浮球大小",
                subtitle = "调整悬浮球显示尺寸",
                value = settings.floatingBallSizeDp.toFloat(),
                valueRange = 40f..80f,
                steps = 7,
                valueText = "${settings.floatingBallSizeDp} dp",
                onValueChangeFinished = { onFloatingBallSizeChange(it.roundToInt()) },
                enabled = settings.floatingBallEnabled,
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
            OnboardingSliderRow(
                title = "悬浮球透明",
                subtitle = "调整悬浮球可见程度",
                value = settings.floatingBallAlpha * 100f,
                valueRange = 10f..100f,
                steps = 8,
                valueText = "${(settings.floatingBallAlpha * 100f).roundToInt()}%",
                onValueChangeFinished = { onFloatingBallAlphaChange((it / 100f).coerceIn(0.1f, 1f)) },
                enabled = settings.floatingBallEnabled,
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
            OnboardingConfigRow(
                icon = Icons.Rounded.PushPin,
                title = "文本挂起",
                subtitle = "文本随口记保存后同步挂到实况通知",
                checked = settings.floatingTextQuickMemoAutoPinEnabled,
                onCheckedChange = onFloatingTextQuickMemoAutoPinChange,
                enabled = settings.voiceInputEnabled && settings.isLiveCapsuleEnabled,
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
            OnboardingConfigRow(
                icon = Icons.Rounded.PushPin,
                title = "语音挂起",
                subtitle = "语音随口记转写完成后同步挂到实况通知",
                checked = settings.voiceQuickMemoAutoPinEnabled,
                onCheckedChange = onVoiceQuickMemoAutoPinChange,
                enabled = settings.voiceInputEnabled && settings.isLiveCapsuleEnabled,
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
            OnboardingConfigRow(
                icon = Icons.AutoMirrored.Rounded.VolumeUp,
                title = "音量键快捷",
                subtitle = "通过无障碍监听音量键长按触发随口记",
                checked = settings.volumeUpLongPressEnabled,
                onCheckedChange = { onFeatureChange(FeatureKey.VOLUME_SHORTCUT, it) },
                enabled = snapshot.accessibility,
                onClick = if (snapshot.accessibility) null else ({ onOpenPermission(PermissionKey.ACCESSIBILITY) }),
            )
        }
    }
}

@Composable
private fun CourseConfigStep(
    settings: MySettings,
    onCourseFeatureChange: (Boolean) -> Unit,
) {
    AppSettingsCard {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            OnboardingConfigRow(
                icon = Icons.Rounded.School,
                title = "课程功能",
                subtitle = "课程表、学期设置和课程提醒入口",
                checked = settings.courseFeatureEnabled,
                onCheckedChange = onCourseFeatureChange,
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
            OnboardingConfigRow(
                icon = Icons.Rounded.DateRange,
                title = "学期设置",
                subtitle = if (settings.semesterStartDate.isBlank()) "未设置，可稍后在课程设置中完善" else "已设置：${settings.semesterStartDate}",
                status = if (settings.semesterStartDate.isBlank()) "未配置" else "已配置",
                statusGranted = settings.semesterStartDate.isNotBlank(),
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
            OnboardingConfigRow(
                icon = Icons.Rounded.TableRows,
                title = "上课时间",
                subtitle = if (settings.timeTableJson.isBlank()) "未配置，可稍后导入或手动编辑课程表" else "已配置课程时间表",
                status = if (settings.timeTableJson.isBlank()) "未配置" else "已配置",
                statusGranted = settings.timeTableJson.isNotBlank(),
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
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        AppSettingsCard {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                OnboardingConfigRow(
                    icon = Icons.Rounded.Cloud,
                    title = "天气服务",
                    subtitle = "开启天气卡片和天气提醒基础能力",
                    checked = settings.weatherEnabled,
                    onCheckedChange = onWeatherEnabledChange,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                OnboardingConfigRow(
                    icon = Icons.Rounded.Warning,
                    title = "天气预警",
                    subtitle = "同步开启天气预警和天气风险提醒",
                    checked = settings.weatherWarningEnabled || settings.weatherRiskWarningEnabled,
                    onCheckedChange = onWeatherWarningChange,
                    enabled = settings.weatherEnabled,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                OnboardingConfigRow(
                    icon = Icons.Rounded.OpenInBrowser,
                    title = "悬浮窗显示天气",
                    subtitle = "在悬浮窗内显示天气信息",
                    checked = settings.showWeatherInFloating,
                    onCheckedChange = onShowInFloatingChange,
                    enabled = settings.weatherEnabled,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                OnboardingSliderRow(
                    title = "刷新间隔",
                    subtitle = "天气数据自动刷新频率",
                    value = settings.weatherRefreshInterval.toFloat(),
                    valueRange = 15f..180f,
                    steps = 10,
                    valueText = "${settings.weatherRefreshInterval} 分钟",
                    onValueChangeFinished = { onRefreshIntervalChange(it.roundToInt()) },
                    enabled = settings.weatherEnabled,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                OnboardingSliderRow(
                    title = "预警范围",
                    subtitle = "检查未来多久内的天气风险",
                    value = settings.weatherWarningLookaheadHours.toFloat(),
                    valueRange = 6f..72f,
                    steps = 10,
                    valueText = "${settings.weatherWarningLookaheadHours} 小时",
                    onValueChangeFinished = { onWarningLookaheadChange(it.roundToInt()) },
                    enabled = settings.weatherEnabled && (settings.weatherWarningEnabled || settings.weatherRiskWarningEnabled),
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                OnboardingSliderRow(
                    title = "预测范围",
                    subtitle = floatingWeatherForecastRangeLabel(settings.floatingWeatherForecastRange),
                    value = settings.floatingWeatherForecastRange.toFloat(),
                    valueRange = 0f..2f,
                    steps = 1,
                    valueText = floatingWeatherForecastRangeLabel(settings.floatingWeatherForecastRange),
                    onValueChangeFinished = { onFloatingForecastRangeChange(it.roundToInt().coerceIn(0, 2)) },
                    enabled = settings.weatherEnabled && settings.showWeatherInFloating,
                    categoricalLabels = listOf("24小时", "3天", "5天"),
                )
            }
        }

        AppSettingsCard {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                OnboardingConfigRow(
                    icon = Icons.Rounded.LocationOn,
                    title = "定位状态",
                    subtitle = if (snapshot.location) "已获得定位权限，可自动定位城市" else "未获得定位权限，也可以之后手动选择城市",
                    status = if (snapshot.location) "已就绪" else "去授权",
                    statusGranted = snapshot.location,
                    onClick = if (snapshot.location) null else onRequestLocationPermission,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                OnboardingConfigRow(
                    icon = Icons.Rounded.Place,
                    title = "手动城市",
                    subtitle = settings.weatherManualLocationName.ifBlank { "未选择，可稍后在天气设置中选择" },
                    status = if (settings.weatherManualLocationId.isNotBlank()) "已配置" else "未配置",
                    statusGranted = settings.weatherManualLocationId.isNotBlank(),
                )
            }
        }

        AppSettingsCard {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("天气接口配置", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
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
                CapsuleNavigationButton(
                    text = "保存天气配置",
                    onClick = { onSaveWeatherConfig(weatherApiUrl.trim(), weatherApiKey.trim()) },
                )
            }
        }
    }
}

@Composable
private fun AiConfigStep(
    settings: MySettings,
    onMultimodalChange: (Boolean) -> Unit,
    onDisableThinkingChange: (Boolean) -> Unit,
    onLocalSemanticChange: (Boolean) -> Unit,
    onSaveAiConfig: (String, String, String) -> Unit,
) {
    var aiKey by remember(settings.modelKey) { mutableStateOf(settings.modelKey) }
    var aiName by remember(settings.modelName) { mutableStateOf(settings.modelName) }
    var aiUrl by remember(settings.modelUrl) { mutableStateOf(settings.modelUrl) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        AppSettingsCard {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                OnboardingConfigRow(
                    icon = Icons.Rounded.SmartToy,
                    title = "AI 模型",
                    subtitle = settings.modelName.ifBlank { "未配置模型名称，AI 识别暂不可用" },
                    status = if (settings.modelKey.isNotBlank() && settings.modelUrl.isNotBlank()) "已配置" else "未配置",
                    statusGranted = settings.modelKey.isNotBlank() && settings.modelUrl.isNotBlank(),
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                OnboardingConfigRow(
                    icon = Icons.Rounded.Link,
                    title = "接口地址",
                    subtitle = settings.modelUrl.ifBlank { "未填写接口地址" },
                    status = if (settings.modelUrl.isNotBlank()) "已配置" else "未配置",
                    statusGranted = settings.modelUrl.isNotBlank(),
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                OnboardingConfigRow(
                    icon = Icons.Rounded.AutoAwesome,
                    title = "识别模式",
                    subtitle = RecognitionMode.label(settings.recognitionMode),
                    status = "当前",
                    statusGranted = true,
                )
            }
        }

        AppSettingsCard {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("AI 接口配置", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
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
                CapsuleNavigationButton(
                    text = "保存 AI 配置",
                    onClick = { onSaveAiConfig(aiKey.trim(), aiName.trim(), aiUrl.trim()) },
                )
            }
        }

        AppSettingsCard {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                OnboardingConfigRow(
                    icon = Icons.Rounded.ImageSearch,
                    title = "多模态 AI",
                    subtitle = "用于图片识别等能力，需要额外配置多模态模型",
                    checked = settings.useMultimodalAi,
                    onCheckedChange = onMultimodalChange,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                OnboardingConfigRow(
                    icon = Icons.Rounded.Psychology,
                    title = "禁用思考",
                    subtitle = "适用于不需要 reasoning 的模型或场景",
                    checked = settings.disableThinking,
                    onCheckedChange = onDisableThinkingChange,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                OnboardingConfigRow(
                    icon = Icons.Rounded.Memory,
                    title = "本地语义",
                    subtitle = "使用本地语义能力辅助识别，未配置本地模型时可跳过",
                    checked = settings.isLocalSemanticEnabled,
                    onCheckedChange = onLocalSemanticChange,
                )
            }
        }
    }
}

@Composable
private fun OnboardingSliderRow(
    title: String,
    subtitle: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueText: String,
    onValueChangeFinished: (Float) -> Unit,
    enabled: Boolean = true,
    categoricalLabels: List<String>? = null,
) {
    if (categoricalLabels != null) {
        EditionCategoricalPreference(
            title = title,
            summary = subtitle,
            options = categoricalLabels,
            selectedIndex = (value - valueRange.start).roundToInt().coerceIn(categoricalLabels.indices),
            onSelectedIndexChange = { onValueChangeFinished(valueRange.start + it) },
            enabled = enabled,
        )
        return
    }
    var sliderValue by remember(value) { mutableStateOf(value) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.56f)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        EditionSlider(
            value = sliderValue.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = {
                sliderValue = it
                onValueChangeFinished(it)
            },
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
        )
    }
}

@Composable
private fun OnboardingTwoOptionRow(
    title: String,
    subtitle: String,
    leftText: String,
    rightText: String,
    selectedRight: Boolean,
    onSelectLeft: () -> Unit,
    onSelectRight: () -> Unit,
    enabled: Boolean = true,
) {
    EditionCategoricalPreference(
        title = title,
        summary = subtitle,
        options = listOf(leftText, rightText),
        selectedIndex = if (selectedRight) 1 else 0,
        onSelectedIndexChange = { if (it == 1) onSelectRight() else onSelectLeft() },
        enabled = enabled,
    )
}

@Composable
private fun OnboardingConfigRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    enabled: Boolean = true,
    status: String? = null,
    statusGranted: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val rowModifier = if (onClick != null) Modifier.clickable { onClick() } else Modifier
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(rowModifier)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f),
            modifier = Modifier.size(34.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        if (checked != null && onCheckedChange != null) {
            EditionSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        } else if (status != null) {
            ConfigStatusChip(text = status, granted = statusGranted)
        }
    }
}

@Composable
private fun ConfigStatusChip(text: String, granted: Boolean) {
    Surface(
        color = if (granted) permissionGrantedColor().copy(alpha = 0.14f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f),
        contentColor = if (granted) permissionGrantedColor() else MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
        )
    }
}

@Composable
private fun PermissionModernRow(
    item: PermissionItem,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = permissionIconContainerColor(item),
            modifier = Modifier.size(34.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = getPermissionIcon(item.key),
                    contentDescription = null,
                    tint = permissionIconTint(item),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
            Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        if (item.uiState == PermissionUiState.GRANTED || item.uiState == PermissionUiState.SYSTEM_ALLOWED) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.alpha(0.72f)) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = permissionGrantedColor(),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = permissionStatusText(item),
                    style = MaterialTheme.typography.labelMedium,
                    color = permissionGrantedColor(),
                    fontWeight = FontWeight.Bold,
                )
            }
        } else {
            PermissionStatusChip(
                granted = false,
                text = permissionStatusText(item),
                level = item.level,
            )
        }
    }
}

@Composable
private fun permissionIconContainerColor(item: PermissionItem): Color = when {
    item.uiState == PermissionUiState.GRANTED || item.uiState == PermissionUiState.SYSTEM_ALLOWED -> permissionGrantedColor().copy(alpha = 0.14f)
    else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.56f)
}

@Composable
private fun permissionIconTint(item: PermissionItem): Color = when {
    item.uiState == PermissionUiState.GRANTED || item.uiState == PermissionUiState.SYSTEM_ALLOWED -> permissionGrantedColor()
    else -> MaterialTheme.colorScheme.primary
}

private fun permissionStatusText(item: PermissionItem): String = when (item.uiState) {
    PermissionUiState.GRANTED -> "已开启"
    PermissionUiState.SYSTEM_ALLOWED -> "系统允许"
    PermissionUiState.NEEDS_ACTION -> "去授权"
    PermissionUiState.NEEDS_CONFIRMATION -> "去确认"
}

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

@Composable
private fun permissionGrantedColor(): Color = Color(0xFF2E7D32)

@Composable
private fun FeatureModernRow(
    item: FeatureItem,
    warning: String?,
    onCheckedChange: (Boolean) -> Unit,
    onOpenPermissionPage: () -> Unit,
) {
    val blocked = !item.requirementsMet
    val rowModifier = if (blocked) {
        Modifier.clickable { onOpenPermissionPage() }
    } else {
        Modifier
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(rowModifier)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.38f),
            modifier = Modifier.size(34.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = getFeatureIcon(item.key),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
            Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (blocked || warning != null) {
                Row(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clickable { onOpenPermissionPage() },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = warning ?: item.warning,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        EditionSwitch(
            checked = item.enabled,
            onCheckedChange = onCheckedChange,
            enabled = item.requirementsMet,
        )
    }
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
    PermissionKey.SHIZUKU_ROOT -> Icons.Rounded.Terminal
}

private fun getFeatureIcon(key: FeatureKey): ImageVector = when (key) {
    FeatureKey.FLOATING_WINDOW -> Icons.Rounded.OpenInBrowser
    FeatureKey.QUICK_MEMO -> Icons.Rounded.Mic
    FeatureKey.LIVE_NOTIFICATION -> Icons.Rounded.DynamicFeed
    FeatureKey.DAILY_SUMMARY -> Icons.Rounded.WbSunny
    FeatureKey.CALENDAR_SYNC -> Icons.Rounded.Sync
    FeatureKey.ADVANCE_REMINDER -> Icons.Rounded.Timer
    FeatureKey.WEATHER -> Icons.Rounded.Cloud
    FeatureKey.WEATHER_ALERT -> Icons.Rounded.Warning
    FeatureKey.BRACELET_MODE -> Icons.Rounded.Watch
    FeatureKey.SMS_RECOGNITION -> Icons.Rounded.Sms
    FeatureKey.VOLUME_SHORTCUT -> Icons.AutoMirrored.Rounded.VolumeUp
    FeatureKey.COURSE -> Icons.Rounded.School
}

@Composable
private fun StatusPill(text: String, container: Color) {
    Surface(color = container, shape = RoundedCornerShape(999.dp)) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun CapsuleNavigationButton(
    text: String,
    onClick: () -> Unit,
    filled: Boolean = true,
) {
    Surface(
        modifier = Modifier.clickable { onClick() },
        color = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.56f),
        contentColor = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(999.dp),
        tonalElevation = 3.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 11.dp),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        )
    }
}

@Composable
private fun PermissionStatusChip(
    granted: Boolean,
    text: String,
    level: RequirementLevel = RequirementLevel.RECOMMENDED,
) {
    val container = if (granted) {
        permissionGrantedColor().copy(alpha = 0.14f)
    } else when (level) {
        RequirementLevel.REQUIRED -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.64f)
        RequirementLevel.RECOMMENDED -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f)
        RequirementLevel.OPTIONAL -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.40f)
    }
    val content = if (granted) {
        permissionGrantedColor()
    } else {
        MaterialTheme.colorScheme.primary
    }
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(999.dp)) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
        )
    }
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
    PermissionItem(PermissionKey.SHIZUKU_ROOT, "高级能力", "Shizuku/Root 状态", "高级后台识别能力；未配置时功能会降级", RequirementLevel.OPTIONAL, snapshot.shizukuRoot, "去授权"),
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

private fun readOnboardingPermissions(context: Context): PermissionSnapshot {
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
        notification = areAppNotificationsEnabled(context),
        liveNotification = livePermissionGranted,
        liveNotificationDescription = if (OsUtils.isColorOsLike()) {
            "系统未开放状态读取。请进入通知设置，确认“流体云显示实时活动”已开启，否则实况胶囊可能不显示。"
        } else {
            "部分系统需要进入应用通知设置确认"
        },
        overlay = overlayEnabled,
        microphone = hasPermission(context, Manifest.permission.RECORD_AUDIO),
        calendar = hasPermission(context, Manifest.permission.READ_CALENDAR) &&
            hasPermission(context, Manifest.permission.WRITE_CALENDAR) &&
            canAccessCalendarProvider(context),
        exactAlarm = canScheduleExactAlarms(context),
        batteryOptimization = isIgnoringBatteryOptimizations(context),
        autostart = false,
        location = hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) || hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION),
        accessibility = isAccessibilityEnabled(context),
        notificationListener = isNotificationListenerEnabled(context),
        sms = hasPermission(context, Manifest.permission.READ_SMS) && hasPermission(context, Manifest.permission.RECEIVE_SMS),
        shizukuRoot = false,
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
            append("shizukuRoot=${snapshot.shizukuRoot}")
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

private fun buildOnboardingSteps(
    settings: MySettings,
    syncEnabled: Boolean,
    setupMode: OnboardingSetupMode?
): List<OnboardingStep> {
    return buildList {
        add(OnboardingStep.PERMISSIONS)
        add(OnboardingStep.SETUP_MODE)
        if (setupMode == null || setupMode == OnboardingSetupMode.IMPORT_CONFIG) return@buildList
        if (setupMode == OnboardingSetupMode.CUSTOM) add(OnboardingStep.FEATURES)
        if (shouldShowScheduleReminderStep(settings, syncEnabled)) add(OnboardingStep.SCHEDULE_REMINDER)
        if (settings.isFloatingWindowEnabled || settings.voiceInputEnabled || settings.volumeUpLongPressEnabled) add(OnboardingStep.FLOATING_QUICK_MEMO)
        if (setupMode == OnboardingSetupMode.AUTHOR_RECOMMENDED || shouldShowModelStep(settings)) add(OnboardingStep.MODEL)
        if (settings.weatherEnabled || settings.weatherWarningEnabled || settings.weatherRiskWarningEnabled) add(OnboardingStep.WEATHER)
        if (settings.courseFeatureEnabled) add(OnboardingStep.COURSE)
    }
}

private fun shouldShowScheduleReminderStep(settings: MySettings, syncEnabled: Boolean): Boolean {
    return settings.isDailySummaryEnabled ||
        settings.isAdvanceReminderEnabled ||
        settings.isLiveCapsuleEnabled ||
        settings.braceletModeEnabled ||
        syncEnabled
}

private fun shouldShowModelStep(settings: MySettings): Boolean {
    return settings.modelKey.isNotBlank() ||
        settings.modelName.isNotBlank() ||
        settings.modelUrl.isNotBlank() ||
        settings.useMultimodalAi ||
        settings.isLocalSemanticEnabled
}

private enum class OnboardingSetupMode { AUTHOR_RECOMMENDED, CUSTOM, IMPORT_CONFIG }

private enum class OnboardingStep(
    val title: String,
    val description: String,
    val isSkippableConfig: Boolean = false,
) {
    PERMISSIONS(
        "配置基础权限",
        "开启必要权限，确保随口记、提醒、日历同步和实况通知稳定运行。"
    ),
    SETUP_MODE(
        "选择配置方式",
        "你可以使用作者推荐，也可以自己逐项配置，老用户可直接导入备份。"
    ),
    FEATURES(
        "选择启用功能",
        "根据已授权的权限开启功能。缺少权限的功能会保持关闭，之后也可以在设置中调整。"
    ),
    SCHEDULE_REMINDER(
        "日程和提醒",
        "配置每日提醒、提前提醒、日历同步、实况通知和手环模式。",
        isSkippableConfig = true,
    ),
    FLOATING_QUICK_MEMO(
        "悬浮窗和随口记",
        "配置日程悬浮、随口记、音量键快捷和相关入口。",
        isSkippableConfig = true,
    ),
    MODEL(
        "模型配置",
        "配置 AI 接口、多模态、本地语义等识别能力。",
        isSkippableConfig = true,
    ),
    WEATHER(
        "天气配置",
        "配置天气服务、天气预警、定位和天气接口。",
        isSkippableConfig = true,
    ),
    COURSE(
        "课程配置",
        "配置课程功能、学期信息和上课时间表。",
        isSkippableConfig = true,
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
    SHIZUKU_ROOT,
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
    val shizukuRoot: Boolean,
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
