package com.antgskds.calendarassistant.feature.weather.ui.connector
import com.antgskds.calendarassistant.shared.ui.edition.EditionButton
import com.antgskds.calendarassistant.shared.ui.edition.EditionCategoricalPreference
import com.antgskds.calendarassistant.shared.ui.edition.EditionOptionalCategoricalSettingItem

import com.antgskds.calendarassistant.shared.ui.material.settings.*
import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.feature.weather.domain.WeatherApiAdapter
import com.antgskds.calendarassistant.feature.weather.domain.WeatherIconMapper
import com.antgskds.calendarassistant.feature.weather.domain.WeatherCatalogLocation
import com.antgskds.calendarassistant.feature.weather.domain.WeatherCatalogProvince
import com.antgskds.calendarassistant.feature.weather.domain.WeatherLocationCatalog
import com.antgskds.calendarassistant.feature.weather.domain.WeatherRepository
import com.antgskds.calendarassistant.feature.weather.domain.WeatherSyncWorker
import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import com.antgskds.calendarassistant.feature.weather.domain.model.displayLocationName
import com.antgskds.calendarassistant.shared.ui.material.component.AppCard
import com.antgskds.calendarassistant.shared.ui.material.component.AppModalBottomSheet
import com.antgskds.calendarassistant.shared.ui.material.component.ToastType
import com.antgskds.calendarassistant.shared.ui.material.component.UniversalSnackbar
import com.antgskds.calendarassistant.shared.ui.interaction.LocalAppHapticsEnabled
import com.antgskds.calendarassistant.shared.ui.interaction.rememberAppHaptics
import com.antgskds.calendarassistant.shared.ui.permission.rememberPermissionGate
import com.antgskds.calendarassistant.app.ui.state.SettingsViewModel
import com.antgskds.calendarassistant.feature.weather.ui.contract.WeatherSettingsUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherSettingsPage(
    viewModel: SettingsViewModel,
    uiSize: Int = 2,
    onOpenWeatherDetail: () -> Unit = {},
    showCacheSection: Boolean = true,
) {
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    val appContext = context.applicationContext
    val app = appContext as App
    val weatherData by app.weatherQueryApi.weatherData.collectAsState()
    val locationCatalog = remember(appContext) { WeatherLocationCatalog.load(appContext) }
    MaterialWeatherSettingsScreen(
        state = WeatherSettingsUiState(settings, weatherData, locationCatalog),
        uiSize = uiSize,
        onOpenWeatherDetail = onOpenWeatherDetail,
        showCacheSection = showCacheSection,
        persistWeather = { draft ->
            viewModel.updateWeatherSettings(
                enabled = draft.weatherEnabled, provider = WeatherApiAdapter.PROVIDER_QWEATHER,
                apiUrl = draft.weatherApiUrl, apiKey = draft.weatherApiKey,
                refreshInterval = draft.weatherRefreshInterval, showInFloating = draft.showWeatherInFloating,
                locationMode = draft.weatherLocationMode, manualLocationId = draft.weatherManualLocationId,
                manualLocationName = draft.weatherManualLocationName, manualAdm1 = draft.weatherManualAdm1,
                manualAdm2 = draft.weatherManualAdm2, manualCountry = draft.weatherManualCountry,
                manualLat = draft.weatherManualLat, manualLon = draft.weatherManualLon,
                warningEnabled = draft.weatherWarningEnabled, riskWarningEnabled = draft.weatherRiskWarningEnabled,
                warningLookaheadHours = draft.weatherWarningLookaheadHours,
                floatingWeatherForecastRange = draft.floatingWeatherForecastRange
            )
            if (draft.weatherManualLocationId != settings.weatherManualLocationId || draft.weatherLocationMode != settings.weatherLocationMode) {
                app.weatherOperationApi.clearCache()
            }
            WeatherSyncWorker.syncForSettings(appContext, draft)
        },
        refreshWeather = { draft -> app.weatherOperationApi.forceRefresh(draft).map { Unit } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialWeatherSettingsScreen(
    state: WeatherSettingsUiState,
    uiSize: Int = 2,
    onOpenWeatherDetail: () -> Unit,
    showCacheSection: Boolean = true,
    persistWeather: suspend (MySettings) -> Unit,
    refreshWeather: suspend (MySettings) -> Result<Unit>
) {
    val settings = state.settings
    val weatherData = state.weatherData
    val locationCatalog = state.locationCatalog
    val haptics = rememberAppHaptics(settings.hapticFeedbackEnabled)
    val context = LocalContext.current
    val appContext = context.applicationContext
    val focusManager = LocalFocusManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val permissionGate = rememberPermissionGate(snackbarHostState)
    var currentToastType by remember { mutableStateOf(ToastType.INFO) }
    val density = LocalDensity.current
    val bottomInset = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }

    var enabled by remember(settings) { mutableStateOf(settings.weatherEnabled) }
    var apiUrl by remember(settings) {
        mutableStateOf(
            settings.weatherApiUrl.ifBlank {
                WeatherApiAdapter.defaultUrl(WeatherApiAdapter.PROVIDER_QWEATHER)
            }
        )
    }
    var apiKey by remember(settings) { mutableStateOf(settings.weatherApiKey) }
    var locationMode by remember(settings) { mutableStateOf(normalizeWeatherLocationMode(settings.weatherLocationMode)) }
    var selectedLocation by remember(settings) {
        mutableStateOf(
            settings.weatherManualLocationId.takeIf { it.isNotBlank() }?.let {
                WeatherCatalogLocation(
                    id = settings.weatherManualLocationId,
                    name = settings.weatherManualLocationName,
                    provinceName = settings.weatherManualAdm1,
                    cityName = settings.weatherManualAdm2,
                    country = settings.weatherManualCountry,
                    latitude = settings.weatherManualLat,
                    longitude = settings.weatherManualLon,
                    adCode = "",
                    sortName = settings.weatherManualLocationName
                )
            }
        )
    }
    var refreshInterval by remember(settings) { mutableIntStateOf(WeatherRepository.normalizeRefreshIntervalMinutes(settings.weatherRefreshInterval)) }
    var showInFloating by remember(settings) { mutableStateOf(settings.showWeatherInFloating) }
    var floatingWeatherRange by remember(settings) { mutableIntStateOf(settings.floatingWeatherForecastRange.coerceIn(0, 2)) }
    var warningEnabled by remember(settings) { mutableStateOf(settings.weatherWarningEnabled) }
    var riskWarningEnabled by remember(settings) { mutableStateOf(settings.weatherRiskWarningEnabled) }
    var warningLookaheadHours by remember(settings) { mutableIntStateOf(settings.weatherWarningLookaheadHours.coerceIn(1, 168)) }
    var hasLocationPermission by remember { mutableStateOf(hasLocationPermissionGranted(context)) }
    var actionLoading by remember { mutableStateOf(false) }
    var showLocationSheet by remember { mutableStateOf(false) }
    var isLocationModeExpanded by remember { mutableStateOf(false) }

    fun showToast(message: String, type: ToastType) {
        if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        currentToastType = type
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Short)
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasLocationPermission = hasLocationPermissionGranted(context)
        permissionGate.resumePending()
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        permissionGate.resumePending()
    }

    LaunchedEffect(Unit) {
        hasLocationPermission = hasLocationPermissionGranted(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasLocationPermission = hasLocationPermissionGranted(context)
                permissionGate.resumePending()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun requestLocationPermission() {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    fun hasNotificationPermission(): Boolean {
        val runtimeGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return runtimeGranted && context.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() == true
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

    val sectionTitleStyle = MaterialTheme.typography.titleMedium.copy(
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary
    )
    val cardTitleStyle = MaterialTheme.typography.bodyLarge.copy(
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface
    )
    val cardValueStyle = MaterialTheme.typography.bodyLarge.copy(
        fontWeight = FontWeight.Normal,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    val cardSubtitleStyle = MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    )

    fun normalizeDraft(forceEnable: Boolean = enabled): MySettings {
        val normalizedProvider = WeatherApiAdapter.PROVIDER_QWEATHER
        val rawUrl = apiUrl.trim()
        val normalizedUrl = when {
            rawUrl.isBlank() -> WeatherApiAdapter.defaultUrl(normalizedProvider)
            normalizedProvider == WeatherApiAdapter.PROVIDER_QWEATHER &&
                !rawUrl.startsWith("https://") &&
                !rawUrl.startsWith("http://") -> "https://$rawUrl"
            else -> rawUrl
        }
        return settings.copy(
            weatherEnabled = forceEnable,
            weatherProvider = normalizedProvider,
            weatherApiUrl = normalizedUrl,
            weatherApiKey = apiKey.trim(),
            weatherCity = selectedLocation?.name.orEmpty(),
            weatherLocationMode = normalizeWeatherLocationMode(locationMode),
            weatherManualLocationId = selectedLocation?.id.orEmpty(),
            weatherManualLocationName = selectedLocation?.name.orEmpty(),
            weatherManualAdm1 = selectedLocation?.provinceName.orEmpty(),
            weatherManualAdm2 = selectedLocation?.cityName.orEmpty(),
            weatherManualCountry = selectedLocation?.country.orEmpty(),
            weatherManualLat = selectedLocation?.latitude ?: 0.0,
            weatherManualLon = selectedLocation?.longitude ?: 0.0,
            weatherWarningEnabled = warningEnabled,
            weatherRiskWarningEnabled = riskWarningEnabled,
            weatherWarningLookaheadHours = warningLookaheadHours.coerceIn(1, 168),
            weatherRefreshInterval = WeatherRepository.normalizeRefreshIntervalMinutes(refreshInterval),
            showWeatherInFloating = showInFloating,
            floatingWeatherForecastRange = floatingWeatherRange.coerceIn(0, 2)
        )
    }

    fun validateDraft(draft: MySettings): Boolean {
        if (draft.weatherApiKey.isBlank()) {
            showToast("请先填写 API Key", ToastType.ERROR)
            return false
        }
        if (draft.weatherApiUrl.isBlank()) {
            showToast("请填写API Host", ToastType.ERROR)
            return false
        }
        if (draft.weatherLocationMode != WeatherRepository.LOCATION_MODE_AUTO && draft.weatherManualLocationId.isBlank()) {
            showToast("请先选择手动天气位置", ToastType.ERROR)
            showLocationSheet = true
            return false
        }
        val needsLocationPermission = draft.weatherLocationMode == WeatherRepository.LOCATION_MODE_AUTO
        if (needsLocationPermission && !hasLocationPermission) {
            permissionGate.require(
                permissionName = "定位权限",
                isGranted = { hasLocationPermissionGranted(context) },
                requestPermission = ::requestLocationPermission,
                onGranted = {
                    hasLocationPermission = true
                    enabled = true
                },
            )
            return false
        }
        return true
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalAppHapticsEnabled provides settings.hapticFeedbackEnabled) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { focusManager.clearFocus() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 120.dp + bottomInset),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("天气来源", style = sectionTitleStyle)

            AppCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    SwitchSettingItem(
                        title = "启用天气",
                        subtitle = "未启用时主页与悬浮窗保持原状",
                        checked = enabled,
                        onCheckedChange = {
                            if (!it) {
                                enabled = false
                            } else if (locationMode == WeatherRepository.LOCATION_MODE_MANUAL) {
                                enabled = true
                            } else {
                                permissionGate.require(
                                    permissionName = "定位权限",
                                    isGranted = { hasLocationPermissionGranted(context) },
                                    requestPermission = ::requestLocationPermission,
                                    onGranted = {
                                        hasLocationPermission = true
                                        enabled = true
                                    },
                                )
                            }
                        },
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle
                    )

                    WeatherDivider()

                    WeatherStaticValueItem(
                        title = "服务提供商",
                        value = "和风",
                        cardTitleStyle = cardTitleStyle,
                        cardValueStyle = cardValueStyle
                    )

                    WeatherDivider()

                    WeatherTextInputItem(
                        title = "API Key",
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        placeholder = "点击输入 Key",
                        cardTitleStyle = cardTitleStyle,
                        cardValueStyle = cardValueStyle,
                        cardSubtitleStyle = cardSubtitleStyle
                    )

                    WeatherDivider()

                    WeatherTextInputItem(
                        title = "API Host",
                        value = apiUrl,
                        onValueChange = { apiUrl = it },
                        placeholder = "如 abcxyz.qweatherapi.com",
                        cardTitleStyle = cardTitleStyle,
                        cardValueStyle = cardValueStyle,
                        cardSubtitleStyle = cardSubtitleStyle
                    )

                    WeatherDivider()

                    WeatherExpandableSelectionItem(
                        title = "位置来源",
                        currentValue = locationModeLabel(locationMode),
                        isExpanded = isLocationModeExpanded,
                        onToggle = { isLocationModeExpanded = !isLocationModeExpanded },
                        options = listOf(
                            WeatherRepository.LOCATION_MODE_AUTO to "自动定位",
                            WeatherRepository.LOCATION_MODE_MANUAL to "手动定位"
                        ),
                        onOptionSelected = { value, _ ->
                            isLocationModeExpanded = false
                            if (value == WeatherRepository.LOCATION_MODE_MANUAL) {
                                locationMode = value
                                showLocationSheet = true
                            } else {
                                permissionGate.require(
                                    permissionName = "定位权限",
                                    isGranted = { hasLocationPermissionGranted(context) },
                                    requestPermission = ::requestLocationPermission,
                                    onGranted = {
                                        hasLocationPermission = true
                                        locationMode = WeatherRepository.LOCATION_MODE_AUTO
                                    },
                                )
                            }
                        },
                        cardTitleStyle = cardTitleStyle,
                        cardValueStyle = cardValueStyle
                    )

                }
            }

            Text("显示与刷新", style = sectionTitleStyle)

            AppCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    EditionCategoricalPreference(
                        title = "刷新频率",
                        summary = when (refreshInterval) {
                            15 -> "每 15 分钟刷新一次"
                            30 -> "每 30 分钟刷新一次"
                            else -> "每 60 分钟刷新一次"
                        },
                        options = listOf("15分钟", "30分钟", "60分钟"),
                        selectedIndex = when (refreshInterval) {
                            15 -> 0
                            30 -> 1
                            else -> 2
                        },
                        onSelectedIndexChange = {
                            refreshInterval = when (it) {
                                0 -> 15
                                1 -> 30
                                else -> 60
                            }
                        },
                    )

                    WeatherDivider()

                    EditionOptionalCategoricalSettingItem(
                        title = "悬浮窗显示天气",
                        subtitle = "在悬浮窗顶部显示天气摘要卡片",
                        checked = showInFloating,
                        onCheckedChange = { showInFloating = it },
                        optionTitle = "悬浮窗天气范围",
                        optionSummary = floatingWeatherRangeLabel(floatingWeatherRange),
                        options = listOf("24小时", "3天", "5天"),
                        selectedIndex = floatingWeatherRange.coerceIn(0, 2),
                        onSelectedIndexChange = { floatingWeatherRange = it },
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle
                    )

                    WeatherDivider()

                    SwitchSettingItem(
                        title = "官方天气预警",
                        subtitle = "气象部门正式发布的预警信号",
                        checked = warningEnabled,
                        onCheckedChange = { checked ->
                            requireNotificationWhenEnabling(checked) {
                                warningEnabled = checked
                            }
                        },
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle
                    )

                    WeatherDivider()

                    EditionOptionalCategoricalSettingItem(
                        title = "天气风险提醒",
                        subtitle = "根据未来${warningLookaheadHours}小时预报推断风险并提醒",
                        checked = riskWarningEnabled,
                        onCheckedChange = { checked ->
                            requireNotificationWhenEnabling(checked) {
                                riskWarningEnabled = checked
                            }
                        },
                        optionTitle = "风险扫描范围",
                        optionSummary = "未来 ${warningLookaheadHours} 小时",
                        options = listOf("12小时", "24小时", "48小时"),
                        selectedIndex = when (warningLookaheadHours) {
                            12 -> 0
                            24 -> 1
                            else -> 2
                        },
                        onSelectedIndexChange = {
                            warningLookaheadHours = when (it) {
                                0 -> 12
                                1 -> 24
                                else -> 48
                            }
                        },
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle
                    )

                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            val draft = normalizeDraft()
                            if (draft.weatherEnabled && !validateDraft(draft)) {
                                haptics.error()
                                return@launch
                            }

                            persistWeather(draft)
                            haptics.confirm()
                            showToast("天气配置已保存", ToastType.SUCCESS)
                            if (!draft.weatherEnabled) return@launch
                            actionLoading = true
                            try {
                                val result = try {
                                    refreshWeather(draft)
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (error: Exception) {
                                    Result.failure(error)
                                }
                                if (result.isSuccess) {
                                    showToast("天气连接成功", ToastType.SUCCESS)
                                } else {
                                    haptics.error()
                                    val message = result.exceptionOrNull()?.message?.replace("HTTP ", "").orEmpty().take(18)
                                    showToast(
                                        if (message.isBlank()) "连接失败，不影响已保存配置" else "连接失败:$message（不影响已保存配置）",
                                        ToastType.ERROR
                                    )
                                }
                            } finally {
                                actionLoading = false
                            }
                        }
                    },
                    enabled = !actionLoading,
                ) {
                    if (actionLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (actionLoading) "连接中" else "连接")
                }
            }

            if (showCacheSection) weatherData?.let { data ->
                Text("当前缓存", style = sectionTitleStyle)
                AppCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(WeatherIconMapper.iconRes(data)),
                                contentDescription = data.text.ifBlank { "天气" },
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${data.text.ifBlank { "天气" }} ${data.temperature}°C",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Text(
                            text = "${data.displayLocationName()} · 湿度 ${data.humidity.ifBlank { "--" }}% · 风力 ${data.windDir.ifBlank { "--" }} ${data.windScale.ifBlank { "--" }}",
                            style = cardSubtitleStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                WeatherDetailEntryCard(onClick = onOpenWeatherDetail)
            }

            Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp + bottomInset),
            snackbar = { data -> UniversalSnackbar(data = data, type = currentToastType) }
        )

        if (showLocationSheet) {
            WeatherLocationPickerSheet(
                provinces = locationCatalog,
                initialLocation = selectedLocation,
                onDismiss = { showLocationSheet = false },
                onConfirm = { location ->
                    selectedLocation = location
                    showLocationSheet = false
                    locationMode = WeatherRepository.LOCATION_MODE_MANUAL
                },
                cardSubtitleStyle = cardSubtitleStyle
            )
        }
    }
    }
}

@Composable
private fun WeatherDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

@Composable
private fun WeatherStaticValueItem(
    title: String,
    value: String,
    cardTitleStyle: TextStyle,
    cardValueStyle: TextStyle
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = title, style = cardTitleStyle)
        Text(
            text = value,
            style = cardValueStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WeatherClickableValueItem(
    title: String,
    value: String,
    onClick: () -> Unit,
    cardTitleStyle: TextStyle,
    cardValueStyle: TextStyle
) {
    val haptics = rememberAppHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { haptics.selection(); onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = title, style = cardTitleStyle)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                style = cardValueStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun WeatherDetailEntryCard(
    onClick: () -> Unit
) {
    val haptics = rememberAppHaptics()
    AppCard(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        onClick = { haptics.click(); onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "天气详情",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "24小时趋势、未来一周和天气预警",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeatherLocationPickerSheet(
    provinces: List<WeatherCatalogProvince>,
    initialLocation: WeatherCatalogLocation?,
    onDismiss: () -> Unit,
    onConfirm: (WeatherCatalogLocation) -> Unit,
    cardSubtitleStyle: TextStyle
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = rememberAppHaptics()
    var selectedProvince by remember(provinces, initialLocation) {
        mutableStateOf(
            provinces.firstOrNull { it.name == initialLocation?.provinceName } ?: provinces.firstOrNull()
        )
    }
    var selectedCity by remember(selectedProvince, initialLocation) {
        mutableStateOf(
            selectedProvince?.cities?.firstOrNull { it.id == initialLocation?.cityName } ?: selectedProvince?.cities?.firstOrNull()
        )
    }
    var selectedLocation by remember(selectedCity, initialLocation) {
        mutableStateOf(
            selectedCity?.locations?.firstOrNull { it.id == initialLocation?.id } ?: selectedCity?.locations?.firstOrNull()
        )
    }
    var selectedTab by remember { mutableIntStateOf(0) }

    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "选择天气位置",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = buildString {
                    append(selectedProvince?.name ?: "省份")
                    selectedCity?.name?.let { append(" / ").append(it) }
                    selectedLocation?.name?.let { append(" / ").append(it) }
                },
                style = cardSubtitleStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            TabRow(selectedTabIndex = selectedTab) {
                listOf("省份", "城市", "区县").forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { haptics.selection(); selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            when (selectedTab) {
                0 -> WeatherPickerList(
                    items = provinces,
                    selected = selectedProvince,
                    label = { it.name },
                    subtitle = { "${it.cities.size} 个城市" },
                    onSelected = { province ->
                        selectedProvince = province
                        selectedCity = province.cities.firstOrNull()
                        selectedLocation = selectedCity?.locations?.firstOrNull()
                        selectedTab = 1
                    }
                )
                1 -> WeatherPickerList(
                    items = selectedProvince?.cities.orEmpty(),
                    selected = selectedCity,
                    label = { it.name },
                    subtitle = { "${it.locations.size} 个位置" },
                    onSelected = { city ->
                        selectedCity = city
                        selectedLocation = city.locations.firstOrNull()
                        selectedTab = 2
                    }
                )
                else -> WeatherPickerList(
                    items = selectedCity?.locations.orEmpty(),
                    selected = selectedLocation,
                    label = { it.name },
                    subtitle = { location -> manualLocationLabel(location) },
                    onSelected = { location -> selectedLocation = location }
                )
            }

            EditionButton(
                onClick = { haptics.confirm(); selectedLocation?.let(onConfirm) },
                enabled = selectedLocation != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存")
            }
        }
    }
}

@Composable
private fun <T> WeatherPickerList(
    items: List<T>,
    selected: T?,
    label: (T) -> String,
    subtitle: (T) -> String,
    onSelected: (T) -> Unit
) {
    val haptics = rememberAppHaptics()
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .height(420.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(items) { item ->
            val isSelected = item == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { haptics.selection(); onSelected(item) }
                    .padding(horizontal = 4.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label(item),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle(item),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun WeatherExpandableSelectionItem(
    title: String,
    currentValue: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    options: List<Pair<String, String>>,
    onOptionSelected: (String, String) -> Unit,
    cardTitleStyle: TextStyle,
    cardValueStyle: TextStyle
) {
    val haptics = rememberAppHaptics()
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { haptics.selection(); onToggle() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = title, style = cardTitleStyle)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = currentValue,
                    style = cardValueStyle,
                    color = if (isExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                options.forEach { (value, label) ->
                    val selected = label == currentValue
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { haptics.selection(); onOptionSelected(value, label) }
                            .heightIn(min = 48.dp)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            style = cardValueStyle
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherTextInputItem(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    cardTitleStyle: TextStyle,
    cardValueStyle: TextStyle,
    cardSubtitleStyle: TextStyle
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    var fieldValue by remember(value) {
        mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
    }
    val isPasswordField = title == "API Key"
    val visualTransformation = if (isPasswordField && !isFocused && value.isNotEmpty()) {
        PasswordVisualTransformation()
    } else {
        VisualTransformation.None
    }

    LaunchedEffect(value) {
        if (value != fieldValue.text) {
            fieldValue = TextFieldValue(text = value, selection = TextRange(value.length))
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = cardTitleStyle,
            modifier = Modifier.width(100.dp)
        )

        BasicTextField(
            value = fieldValue,
            onValueChange = { newValue ->
                fieldValue = newValue
                onValueChange(newValue.text)
            },
            textStyle = cardValueStyle.copy(
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End
            ),
            visualTransformation = visualTransformation,
            singleLine = true,
            interactionSource = interactionSource,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onFocusChanged { focusState -> isFocused = focusState.isFocused },
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 24.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = cardSubtitleStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}

private fun hasLocationPermissionGranted(context: Context): Boolean {
    val fineGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val coarseGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    return fineGranted || coarseGranted
}

private fun locationModeLabel(mode: String): String {
    return when (normalizeWeatherLocationMode(mode)) {
        WeatherRepository.LOCATION_MODE_AUTO -> "自动定位"
        else -> "手动定位"
    }
}

private fun normalizeWeatherLocationMode(mode: String): String {
    return when (mode) {
        WeatherRepository.LOCATION_MODE_MANUAL -> WeatherRepository.LOCATION_MODE_MANUAL
        else -> WeatherRepository.LOCATION_MODE_AUTO
    }
}

private fun floatingWeatherRangeLabel(range: Int): String {
    return when (range.coerceIn(0, 2)) {
        0 -> "展开后显示未来 24 小时天气"
        1 -> "展开后显示未来 3 天天气"
        else -> "展开后显示未来 5 天天气"
    }
}

private fun manualLocationLabel(location: WeatherCatalogLocation): String {
    return buildString {
        if (location.cityName.isNotBlank() && location.cityName != location.provinceName) {
            append(location.cityName.removeSuffix("市"))
            append(" ")
        }
        append(location.name)
    }
}
