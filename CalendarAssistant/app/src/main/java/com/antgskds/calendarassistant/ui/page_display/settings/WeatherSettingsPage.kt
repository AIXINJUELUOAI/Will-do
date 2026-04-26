package com.antgskds.calendarassistant.ui.page_display.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.core.weather.WeatherApiAdapter
import com.antgskds.calendarassistant.core.weather.WeatherIconMapper
import com.antgskds.calendarassistant.core.weather.WeatherSyncWorker
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.ui.components.ToastType
import com.antgskds.calendarassistant.ui.components.UniversalToast
import com.antgskds.calendarassistant.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@Composable
fun WeatherSettingsPage(
    viewModel: SettingsViewModel,
    uiSize: Int = 2
) {
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    val appContext = context.applicationContext
    val app = appContext as App
    val focusManager = LocalFocusManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val weatherData by app.weatherQueryApi.weatherData.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
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
    var refreshInterval by remember(settings) { mutableIntStateOf(settings.weatherRefreshInterval.coerceAtLeast(1)) }
    var showInFloating by remember(settings) { mutableStateOf(settings.showWeatherInFloating) }
    var hasLocationPermission by remember { mutableStateOf(hasLocationPermissionGranted(context)) }
    var pendingEnableByPermissionRequest by remember { mutableStateOf(false) }
    var actionLoading by remember { mutableStateOf(false) }

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
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasLocationPermission = granted
        if (pendingEnableByPermissionRequest) {
            enabled = granted
            pendingEnableByPermissionRequest = false
            if (!granted) {
                showToast("需要定位权限才能启用天气", ToastType.ERROR)
            }
        }
    }

    LaunchedEffect(Unit) {
        hasLocationPermission = hasLocationPermissionGranted(context)
    }

    LaunchedEffect(enabled) {
        if (enabled && !hasLocationPermission) {
            pendingEnableByPermissionRequest = true
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
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
            weatherCity = "",
            weatherRefreshInterval = refreshInterval.coerceIn(1, 6),
            showWeatherInFloating = showInFloating
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
        if (!hasLocationPermission) {
            pendingEnableByPermissionRequest = true
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            showToast("请先授予定位权限", ToastType.ERROR)
            return false
        }
        return true
    }

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

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    SwitchSettingItem(
                        title = "启用天气",
                        subtitle = "未启用时主页与悬浮窗保持原状",
                        checked = enabled,
                        onCheckedChange = {
                            if (!it) {
                                enabled = false
                            } else if (hasLocationPermission) {
                                enabled = true
                            } else {
                                pendingEnableByPermissionRequest = true
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
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
                }
            }

            Text("显示与刷新", style = sectionTitleStyle)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    SliderSettingItem(
                        title = "刷新频率",
                        subtitle = when (refreshInterval) {
                            1 -> "每 1 小时刷新一次"
                            3 -> "每 3 小时刷新一次"
                            else -> "每 6 小时刷新一次"
                        },
                        value = when (refreshInterval) {
                            1 -> 0f
                            3 -> 1f
                            else -> 2f
                        },
                        onValueChange = {
                            refreshInterval = when (it.toInt()) {
                                0 -> 1
                                1 -> 3
                                else -> 6
                            }
                        },
                        valueRange = 0f..2f,
                        steps = 1,
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle,
                        cardValueStyle = cardValueStyle,
                        showValueAsNumber = false,
                        valueUnit = ""
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("1h", style = cardSubtitleStyle)
                        Text("3h", style = cardSubtitleStyle)
                        Text("6h", style = cardSubtitleStyle)
                    }

                    WeatherDivider()

                    SwitchSettingItem(
                        title = "悬浮窗显示天气",
                        subtitle = "在悬浮窗顶部显示天气摘要卡片",
                        checked = showInFloating,
                        onCheckedChange = { showInFloating = it },
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle
                    )

                }
            }

            weatherData?.let { data ->
                Text("当前缓存", style = sectionTitleStyle)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
                            text = "${data.city.ifBlank { "当前位置" }} · 湿度 ${data.humidity.ifBlank { "--" }}% · 风力 ${data.windDir.ifBlank { "--" }} ${data.windScale.ifBlank { "--" }}",
                            style = cardSubtitleStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }

        FloatingActionButton(
            onClick = {
                if (actionLoading) return@FloatingActionButton
                scope.launch {
                    val draft = normalizeDraft()
                    if (draft.weatherEnabled && !validateDraft(draft)) {
                        return@launch
                    }

                    viewModel.updateWeatherSettings(
                        enabled = draft.weatherEnabled,
                        provider = WeatherApiAdapter.PROVIDER_QWEATHER,
                        apiUrl = draft.weatherApiUrl,
                        apiKey = draft.weatherApiKey,
                        refreshInterval = draft.weatherRefreshInterval,
                        showInFloating = draft.showWeatherInFloating
                    )
                    WeatherSyncWorker.syncForSettings(appContext, draft)
                    showToast("天气配置已保存", ToastType.SUCCESS)

                    if (!draft.weatherEnabled) {
                        return@launch
                    }

                    actionLoading = true
                    try {
                        val result = app.weatherOperationApi.forceRefresh(draft)
                        if (result.isSuccess) {
                            showToast("天气连接成功", ToastType.SUCCESS)
                        } else {
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
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 32.dp + bottomInset)
                .size(72.dp)
        ) {
            if (actionLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(30.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 3.dp
                )
            } else {
                Icon(Icons.Default.Check, contentDescription = "保存", modifier = Modifier.size(34.dp))
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp + bottomInset),
            snackbar = { data -> UniversalToast(message = data.visuals.message, type = currentToastType) }
        )
    }
}

@Composable
private fun WeatherDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp),
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
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
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
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOptionSelected(value, label) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            fontWeight = if (label == currentValue) FontWeight.Bold else FontWeight.Normal,
                            color = if (label == currentValue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
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
