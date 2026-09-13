package com.antgskds.calendarassistant.feature.recognition.ui.connector
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.feature.recognition.application.ai.ApiModelProvider
import com.antgskds.calendarassistant.feature.recognition.application.ai.ModelListResult
import com.antgskds.calendarassistant.feature.cloudsync.domain.WebDavConnectionInput
import com.antgskds.calendarassistant.feature.cloudsync.domain.WebDavConnectionTestResult
import com.antgskds.calendarassistant.shared.ui.material.component.AppCard
import com.antgskds.calendarassistant.shared.ui.material.component.ToastType
import com.antgskds.calendarassistant.shared.ui.material.component.UniversalToast
import com.antgskds.calendarassistant.shared.ui.interaction.rememberAppHaptics
import com.antgskds.calendarassistant.shared.ui.material.settings.SliderSettingItem
import com.antgskds.calendarassistant.shared.ui.material.settings.SwitchSettingItem
import com.antgskds.calendarassistant.app.ui.state.MainViewModel
import com.antgskds.calendarassistant.app.ui.state.SettingsViewModel
import com.antgskds.calendarassistant.feature.recognition.ui.contract.AiSettingsUiAction
import com.antgskds.calendarassistant.feature.recognition.ui.contract.AiSettingsUiState
import com.antgskds.calendarassistant.feature.recognition.ui.render.AiSettingsScreen
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val PROVIDER_DEEPSEEK = "DeepSeek"
private const val PROVIDER_OPENAI = "OpenAI"
private const val PROVIDER_GEMINI = "Gemini"
private const val PROVIDER_CUSTOM = "自定义"

private val MODEL_LIST_UNSUPPORTED_STATUSES = setOf(404, 405, 501)

private data class ProviderPreset(
    val modelsUrl: String,
    val endpointBuilder: (String) -> String
)

private val providerPresets = mapOf(
    PROVIDER_DEEPSEEK to ProviderPreset(
        modelsUrl = "https://api.deepseek.com/v1/models",
        endpointBuilder = { "https://api.deepseek.com/v1/chat/completions" }
    ),
    PROVIDER_OPENAI to ProviderPreset(
        modelsUrl = "https://api.openai.com/v1/models",
        endpointBuilder = { "https://api.openai.com/v1/chat/completions" }
    ),
    PROVIDER_GEMINI to ProviderPreset(
        modelsUrl = "https://generativelanguage.googleapis.com/v1beta/models",
        endpointBuilder = { model ->
            val fallback = if (model.isBlank()) "gemini-3.1-flash" else model
            "https://generativelanguage.googleapis.com/v1beta/models/$fallback:generateContent"
        }
    )
)

@Suppress("UNUSED_PARAMETER")
@Composable
fun AiSettingsPage(
    viewModel: SettingsViewModel,
    mainViewModel: MainViewModel,
    uiSize: Int = 2
) {
    val settings by viewModel.settings.collectAsState()
    val webDavSyncStatus by viewModel.webDavSyncStatus.collectAsState()
    AiSettingsScreen(
        state = AiSettingsUiState(
            settings = settings,
            webDavPasswordStored = viewModel.hasStoredWebDavPassword(),
            syncPassphraseStored = viewModel.hasStoredSyncPassphrase(),
            syncStatus = webDavSyncStatus,
        ),
        uiSize = uiSize,
        onAction = { action ->
            when (action) {
                is AiSettingsUiAction.SaveTextModel -> viewModel.updateAiSettings(action.key, action.name, action.url)
                is AiSettingsUiAction.SaveMultimodalModel -> viewModel.updateMultimodalAiSettings(action.key, action.name, action.url)
                is AiSettingsUiAction.SetAgentAccess -> viewModel.updateAgentAccessOptions(accessEnabled = action.enabled)
                is AiSettingsUiAction.SetThirdPartyAgentAccess -> {
                    viewModel.updateAgentAccessOptions(thirdPartyAccessEnabled = action.enabled)
                }
                is AiSettingsUiAction.SetAgentConnectionManagement -> {
                    viewModel.updateAgentAccessOptions(connectionManagementEnabled = action.enabled)
                }
            }
        },
        fetchModels = ApiModelProvider::fetchAvailableModels,
        testWebDavConnection = viewModel::testAndSaveWebDavConnection,
        readStoredWebDavPassword = viewModel::readStoredWebDavPassword,
        readStoredSyncPassphrase = viewModel::readStoredSyncPassphrase,
        onSyncEnabledChange = { viewModel.updateWebDavSyncOptions(enabled = it) },
        onWifiOnlyChange = { viewModel.updateWebDavSyncOptions(wifiOnly = it) },
        onForegroundSyncIntervalChange = {
            viewModel.updateWebDavSyncOptions(foregroundIntervalSeconds = it)
        },
        onSyncNow = viewModel::syncWebDavNow,
    )
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun MaterialAiSettingsScreen(
    state: AiSettingsUiState,
    uiSize: Int = 2,
    onAction: (AiSettingsUiAction) -> Unit,
    fetchModels: suspend (String, String) -> ModelListResult,
    testWebDavConnection: suspend (WebDavConnectionInput) -> WebDavConnectionTestResult,
    readStoredWebDavPassword: () -> String,
    readStoredSyncPassphrase: () -> String,
    onSyncEnabledChange: (Boolean) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
    onForegroundSyncIntervalChange: (Int) -> Unit,
    onSyncNow: () -> Unit,
) {
    val settings = state.settings
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = rememberAppHaptics(settings.hapticFeedbackEnabled)
    val focusManager = LocalFocusManager.current
    var currentToastType by remember { mutableStateOf(ToastType.SUCCESS) }

    val isMultimodalEnabled = settings.useMultimodalAi

    var textModelUrl by remember(settings) { mutableStateOf(settings.modelUrl) }
    var textModelName by remember(settings) { mutableStateOf(settings.modelName) }
    var textModelKey by remember(settings) { mutableStateOf(settings.modelKey) }
    var textProvider by remember(settings) { mutableStateOf(detectPresetProvider(settings.modelUrl, settings.modelName)) }
    var textCustomModels by remember { mutableStateOf(emptyList<String>()) }
    var textConnected by remember { mutableStateOf(false) }
    var textModelFetchFailed by remember { mutableStateOf(false) }

    var mmModelUrl by remember(settings) { mutableStateOf(settings.mmModelUrl) }
    var mmModelName by remember(settings) { mutableStateOf(settings.mmModelName) }
    var mmModelKey by remember(settings) { mutableStateOf(settings.mmModelKey) }
    var mmProvider by remember(settings) { mutableStateOf(detectPresetProvider(settings.mmModelUrl, settings.mmModelName)) }
    var mmCustomModels by remember { mutableStateOf(emptyList<String>()) }
    var mmConnected by remember { mutableStateOf(false) }
    var mmModelFetchFailed by remember { mutableStateOf(false) }

    var isProviderExpanded by remember { mutableStateOf(false) }
    var isModelExpanded by remember { mutableStateOf(false) }
    var actionLoading by remember { mutableStateOf(false) }
    var webDavLoading by remember { mutableStateOf(false) }
    var webDavBaseUrl by remember(settings.webDavBaseUrl) { mutableStateOf(settings.webDavBaseUrl) }
    var webDavUsername by remember(settings.webDavUsername) { mutableStateOf(settings.webDavUsername) }
    var webDavPassword by remember { mutableStateOf("") }
    var webDavPasswordStored by remember(state.webDavPasswordStored) {
        mutableStateOf(state.webDavPasswordStored)
    }
    var syncPassphrase by remember { mutableStateOf("") }
    var syncPassphraseStored by remember(state.syncPassphraseStored) { mutableStateOf(state.syncPassphraseStored) }

    val activeProvider = if (isMultimodalEnabled) mmProvider else textProvider
    val activeModelUrl = if (isMultimodalEnabled) mmModelUrl else textModelUrl
    val activeModelName = if (isMultimodalEnabled) mmModelName else textModelName
    val activeModelKey = if (isMultimodalEnabled) mmModelKey else textModelKey
    val activeCustomModels = if (isMultimodalEnabled) mmCustomModels else textCustomModels
    val activeConnected = if (isMultimodalEnabled) mmConnected else textConnected
    val activeModelFetchFailed = if (isMultimodalEnabled) mmModelFetchFailed else textModelFetchFailed
    val effectiveModelName = if (
        activeProvider == PROVIDER_CUSTOM &&
        activeModelUrl.isBlank() &&
        activeModelKey.isBlank() &&
        activeCustomModels.isEmpty() &&
        activeModelName == "gpt-3.5-turbo"
    ) "" else activeModelName

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
    val cardSubtitleStyle = MaterialTheme.typography.bodyLarge.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    )

    fun showToast(message: String, type: ToastType = ToastType.SUCCESS) {
        currentToastType = type
        if (type == ToastType.ERROR) haptics.error() else haptics.confirm()
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Short)
        }
    }

    fun setActiveProvider(value: String) {
        if (isMultimodalEnabled) mmProvider = value else textProvider = value
    }

    fun setActiveUrl(value: String) {
        if (isMultimodalEnabled) mmModelUrl = value else textModelUrl = value
    }

    fun setActiveName(value: String) {
        if (isMultimodalEnabled) mmModelName = value else textModelName = value
        // 模板地址随模型更新（Gemini 的路径包含模型名），不重置已连接状态。
        providerPresets[activeProvider]?.let { preset ->
            setActiveUrl(preset.endpointBuilder(value.trim()))
        }
    }

    fun setActiveKey(value: String) {
        if (isMultimodalEnabled) mmModelKey = value else textModelKey = value
    }

    fun setActiveCustomModels(value: List<String>) {
        if (isMultimodalEnabled) mmCustomModels = value else textCustomModels = value
    }

    fun setActiveConnected(value: Boolean) {
        if (isMultimodalEnabled) mmConnected = value else textConnected = value
    }

    fun setActiveModelFetchFailed(value: Boolean) {
        if (isMultimodalEnabled) mmModelFetchFailed = value else textModelFetchFailed = value
    }

    fun applyProviderPreset(provider: String) {
        setActiveProvider(provider)
        setActiveName("")
        setActiveUrl(providerPresets[provider]?.endpointBuilder?.invoke("").orEmpty())
        setActiveConnected(false)
        setActiveModelFetchFailed(false)
        setActiveCustomModels(emptyList())
    }

    suspend fun saveCurrent(url: String, name: String, key: String) {
        if (isMultimodalEnabled) {
            onAction(AiSettingsUiAction.SaveMultimodalModel(key.trim(), name.trim(), url.trim()))
        } else {
            onAction(AiSettingsUiAction.SaveTextModel(key.trim(), name.trim(), url.trim()))
        }
        showToast("配置保存成功")
    }

    suspend fun onConnectClick() {
        val modelsUrl = if (activeProvider == PROVIDER_CUSTOM) {
            if (activeModelUrl.isBlank() || activeModelKey.isBlank()) {
                showToast("请先填写 API 地址和 API Key", ToastType.ERROR)
                return
            }
            val normalized = normalizeCustomApiUrl(activeModelUrl)
            if (normalized != activeModelUrl) {
                setActiveUrl(normalized)
            }
            normalized
        } else {
            if (activeModelKey.isBlank()) {
                showToast("请先填写 API Key", ToastType.ERROR)
                return
            }
            providerPresets[activeProvider]?.modelsUrl ?: return
        }

        actionLoading = true
        when (val result = fetchModels(activeModelKey.trim(), modelsUrl.trim())) {
            is ModelListResult.Success -> {
                val preferVision = isMultimodalEnabled && activeProvider == PROVIDER_DEEPSEEK
                val models = if (preferVision) {
                    result.models.sortedBy { if (it.contains("vision", ignoreCase = true)) 0 else 1 }
                } else result.models
                setActiveConnected(true)
                setActiveModelFetchFailed(models.isEmpty())
                setActiveCustomModels(models)
                if (models.isEmpty()) {
                    showToast("连接成功，但无法获取模型列表，请手动输入模型", ToastType.INFO)
                } else {
                    if (activeModelName !in models ||
                        (preferVision && !activeModelName.contains("vision", ignoreCase = true))) {
                        setActiveName(if (preferVision) models.firstOrNull {
                            it.contains("vision", ignoreCase = true)
                        }.orEmpty() else "")
                    }
                    isModelExpanded = true
                    showToast("连接成功")
                }
            }
            is ModelListResult.Failure -> {
                if (result.statusCode in MODEL_LIST_UNSUPPORTED_STATUSES) {
                    setActiveConnected(true)
                    setActiveModelFetchFailed(true)
                    setActiveCustomModels(emptyList())
                    showToast("连接成功，但无法获取模型列表，请手动输入模型", ToastType.INFO)
                } else {
                    setActiveConnected(false)
                    setActiveModelFetchFailed(false)
                    showToast("连接失败：${result.message}", ToastType.ERROR)
                }
            }
        }
        actionLoading = false
    }

    suspend fun onSaveClick() {
        if (activeProvider != PROVIDER_CUSTOM) {
            if (activeModelKey.isBlank()) {
                showToast("请先填写 API Key", ToastType.ERROR)
                return
            }
            if (effectiveModelName.isBlank()) {
                if (activeModelFetchFailed) {
                    showToast("请输入模型名称", ToastType.ERROR)
                } else {
                    isModelExpanded = true
                    showToast("请先选择模型名称", ToastType.ERROR)
                }
                return
            }
            val preset = providerPresets[activeProvider] ?: return
            val finalUrl = preset.endpointBuilder(effectiveModelName)
            setActiveName(effectiveModelName)
            setActiveUrl(finalUrl)
            saveCurrent(finalUrl, effectiveModelName, activeModelKey)
            return
        }

        if (activeModelUrl.isBlank() || activeModelKey.isBlank()) {
            showToast("请先填写 API 地址和 API Key", ToastType.ERROR)
            return
        }

        if (effectiveModelName.isBlank()) {
            showToast("请输入模型名称", ToastType.ERROR)
            return
        }

        val normalizedUrl = normalizeCustomApiUrl(activeModelUrl)
        saveCurrent(normalizedUrl, effectiveModelName, activeModelKey)
    }

    suspend fun onTestWebDavClick() {
        if (webDavBaseUrl.isBlank()) {
            showToast("请填写 WebDAV 地址", ToastType.ERROR)
            return
        }
        if (webDavPassword.isBlank() && !webDavPasswordStored) {
            showToast("请填写 WebDAV 密码", ToastType.ERROR)
            return
        }
        if (syncPassphrase.isBlank() && !syncPassphraseStored) {
            showToast("请填写同步密码", ToastType.ERROR)
            return
        }
        webDavLoading = true
        val result = try {
            testWebDavConnection(
                WebDavConnectionInput(
                    baseUrl = webDavBaseUrl,
                    username = webDavUsername,
                    password = webDavPassword,
                    syncPassphrase = syncPassphrase,
                )
            )
        } finally {
            webDavLoading = false
        }
        if (result.success) {
            if (webDavPassword.isNotBlank()) webDavPasswordStored = true
            if (syncPassphrase.isNotBlank()) syncPassphraseStored = true
            showToast(result.message)
        } else {
            showToast(result.message, ToastType.ERROR)
        }
    }

    val modelOptions = activeCustomModels
    val modelDisplay = when {
        effectiveModelName.isNotBlank() -> effectiveModelName
        activeConnected -> "请选择模型"
        else -> "连接后选择模型"
    }
    val onModelUrlChange: (String) -> Unit = { newValue ->
        setActiveUrl(newValue)
        setActiveConnected(false)
        setActiveModelFetchFailed(false)
        setActiveCustomModels(emptyList())
    }

    val onModelNameChange: (String) -> Unit = { newValue -> setActiveName(newValue) }
    val onModelKeyChange: (String) -> Unit = {
        setActiveKey(it)
        setActiveConnected(false)
        setActiveModelFetchFailed(false)
        setActiveCustomModels(emptyList())
    }

    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val modeLabel = if (isMultimodalEnabled) "多模态AI" else "文本AI"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(pass = PointerEventPass.Final)
                    val up = waitForUpOrCancellation(pass = PointerEventPass.Final)
                    if (up != null && !down.isConsumed && !up.isConsumed) {
                        focusManager.clearFocus()
                    }
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
                .padding(bottom = 24.dp + bottomInset),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("参数配置", style = sectionTitleStyle)
            Text(
                text = "当前模式：$modeLabel（在偏好设置中切换）",
                style = cardSubtitleStyle
            )
            if (isMultimodalEnabled && activeProvider == PROVIDER_DEEPSEEK) {
                Text(
                    text = "图片识别请选择 deepseek-v4-flash-vision-exp 等视觉模型，文本模型不支持图片。",
                    style = cardSubtitleStyle
                )
            }

            AiConfigForm(
                selectedProvider = activeProvider,
                currentUrl = activeModelUrl,
                currentModel = modelDisplay,
                currentApiKey = activeModelKey,
                modelName = effectiveModelName,
                modelOptions = modelOptions,
                modelHint = "",
                isProviderExpanded = isProviderExpanded,
                isModelExpanded = isModelExpanded,
                onProviderExpandedChange = { isProviderExpanded = it },
                onModelExpandedChange = { isModelExpanded = it },
                onProviderSelected = { provider ->
                    applyProviderPreset(provider)
                    isProviderExpanded = false
                    isModelExpanded = false
                },
                onUrlChange = onModelUrlChange,
                onModelSelected = { model ->
                    onModelNameChange(model)
                    isModelExpanded = false
                },
                onModelNameChange = onModelNameChange,
                onKeyChange = onModelKeyChange,
                cardTitleStyle = cardTitleStyle,
                cardValueStyle = cardValueStyle,
                cardSubtitleStyle = cardSubtitleStyle,
                customMode = activeProvider == PROVIDER_CUSTOM,
                connected = activeConnected,
                manualModel = activeModelFetchFailed,
                loading = actionLoading,
                onConnect = {
                    if (!actionLoading) {
                        haptics.click()
                        focusManager.clearFocus()
                        scope.launch {
                            if (activeConnected) onSaveClick() else onConnectClick()
                        }
                    }
                },
            )

            Text("Agent 访问", style = sectionTitleStyle)
            AppCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    SwitchSettingItem(
                        title = "允许 Agent 访问",
                        subtitle = "允许 Agent 访问、操作 WillDo 数据",
                        checked = settings.agentApiEnabled,
                        onCheckedChange = { onAction(AiSettingsUiAction.SetAgentAccess(it)) },
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle,
                    )
                    AnimatedVisibility(
                        visible = settings.agentApiEnabled,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            MyDivider()
                            SwitchSettingItem(
                                title = "允许第三方 Agent 访问",
                                subtitle = "允许第三方 Agent 访问、操作 WillDo 数据",
                                checked = settings.agentThirdPartyAccessEnabled,
                                onCheckedChange = {
                                    onAction(AiSettingsUiAction.SetThirdPartyAgentAccess(it))
                                },
                                cardTitleStyle = cardTitleStyle,
                                cardSubtitleStyle = cardSubtitleStyle,
                            )
                            MyDivider()
                            SwitchSettingItem(
                                title = "允许 Agent 管理连接配置",
                                subtitle = "允许 Agent 管理模型、天气、WebDAV 连接",
                                checked = settings.agentConnectionManagementEnabled,
                                onCheckedChange = {
                                    onAction(AiSettingsUiAction.SetAgentConnectionManagement(it))
                                },
                                cardTitleStyle = cardTitleStyle,
                                cardSubtitleStyle = cardSubtitleStyle,
                            )
                        }
                    }
                }
            }

            Text("WebDAV 连接", style = sectionTitleStyle)
            WebDavConfigForm(
                baseUrl = webDavBaseUrl,
                username = webDavUsername,
                password = webDavPassword,
                passwordStored = webDavPasswordStored,
                syncPassphrase = syncPassphrase,
                syncPassphraseStored = syncPassphraseStored,
                syncEnabled = settings.webDavSyncEnabled,
                wifiOnly = settings.webDavWifiOnly,
                foregroundSyncIntervalSeconds = settings.webDavForegroundSyncIntervalSeconds,
                loading = webDavLoading,
                onBaseUrlChange = { webDavBaseUrl = it },
                onUsernameChange = { webDavUsername = it },
                onPasswordChange = { webDavPassword = it },
                onPasswordFocusChange = { focused ->
                    if (focused && webDavPasswordStored && webDavPassword.isBlank()) {
                        webDavPassword = readStoredWebDavPassword()
                    }
                },
                onSyncPassphraseChange = { syncPassphrase = it },
                onSyncPassphraseFocusChange = { focused ->
                    if (focused && syncPassphraseStored && syncPassphrase.isBlank()) {
                        syncPassphrase = readStoredSyncPassphrase()
                    }
                },
                onSyncEnabledChange = onSyncEnabledChange,
                onWifiOnlyChange = onWifiOnlyChange,
                onForegroundSyncIntervalChange = onForegroundSyncIntervalChange,
                onTest = {
                    focusManager.clearFocus()
                    scope.launch { onTestWebDavClick() }
                },
                cardTitleStyle = cardTitleStyle,
                cardValueStyle = cardValueStyle,
                cardSubtitleStyle = cardSubtitleStyle,
            )

            AppCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("同步状态", style = cardTitleStyle)
                    Text(state.syncStatus.message, style = cardSubtitleStyle)
                    if (state.syncStatus.pendingAssetCount > 0) {
                        Text("${state.syncStatus.pendingAssetCount} 个附件等待同步", style = cardSubtitleStyle)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Button(onClick = onSyncNow) { Text("立即同步") }
                    }
                }
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

    LaunchedEffect(isMultimodalEnabled) {
        isProviderExpanded = false
        isModelExpanded = false
    }
}

@Composable
private fun WebDavConfigForm(
    baseUrl: String,
    username: String,
    password: String,
    passwordStored: Boolean,
    syncPassphrase: String,
    syncPassphraseStored: Boolean,
    syncEnabled: Boolean,
    wifiOnly: Boolean,
    foregroundSyncIntervalSeconds: Int,
    loading: Boolean,
    onBaseUrlChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordFocusChange: (Boolean) -> Unit,
    onSyncPassphraseChange: (String) -> Unit,
    onSyncPassphraseFocusChange: (Boolean) -> Unit,
    onSyncEnabledChange: (Boolean) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
    onForegroundSyncIntervalChange: (Int) -> Unit,
    onTest: () -> Unit,
    cardTitleStyle: TextStyle,
    cardValueStyle: TextStyle,
    cardSubtitleStyle: TextStyle,
) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            SwitchSettingItem(
                title = "多设备同步",
                subtitle = "同步日程、随口记及其附件",
                checked = syncEnabled,
                onCheckedChange = onSyncEnabledChange,
                cardTitleStyle = cardTitleStyle,
                cardSubtitleStyle = cardSubtitleStyle,
            )

            SwitchSettingItem(
                title = "仅在 Wi-Fi 下同步",
                subtitle = "移动网络下暂停自动同步",
                checked = wifiOnly,
                onCheckedChange = onWifiOnlyChange,
                cardTitleStyle = cardTitleStyle,
                cardSubtitleStyle = cardSubtitleStyle,
            )
            SliderSettingItem(
                title = "前台同步间隔",
                subtitle = "应用在前台时自动同步的时间间隔",
                value = foregroundSyncIntervalSeconds.toFloat(),
                onValueChange = {
                    onForegroundSyncIntervalChange(it.roundToInt().coerceIn(1, 300))
                },
                valueRange = 1f..300f,
                steps = 0,
                cardTitleStyle = cardTitleStyle,
                cardSubtitleStyle = cardSubtitleStyle,
                cardValueStyle = cardValueStyle,
                showValueAsNumber = true,
                valueUnit = " 秒",
            )
            MyDivider()
            TextInputItem(
                title = "服务器地址",
                value = baseUrl,
                onValueChange = onBaseUrlChange,
                placeholder = "https://example.com/dav",
                cardTitleStyle = cardTitleStyle,
                cardValueStyle = cardValueStyle,
                cardSubtitleStyle = cardSubtitleStyle,
            )
            MyDivider()
            TextInputItem(
                title = "用户名",
                value = username,
                onValueChange = onUsernameChange,
                placeholder = "WebDAV 用户名",
                cardTitleStyle = cardTitleStyle,
                cardValueStyle = cardValueStyle,
                cardSubtitleStyle = cardSubtitleStyle,
            )
            MyDivider()
            TextInputItem(
                title = "WebDAV 密码",
                value = password,
                onValueChange = onPasswordChange,
                placeholder = if (passwordStored) "••••••••" else "点击输入密码",
                secret = true,
                onFocusChange = onPasswordFocusChange,
                cardTitleStyle = cardTitleStyle,
                cardValueStyle = cardValueStyle,
                cardSubtitleStyle = cardSubtitleStyle,
            )
            MyDivider()
            TextInputItem(
                title = "同步密码",
                value = syncPassphrase,
                onValueChange = onSyncPassphraseChange,
                placeholder = if (syncPassphraseStored) "••••••••" else "点击输入同步密码",
                secret = true,
                onFocusChange = onSyncPassphraseFocusChange,
                cardTitleStyle = cardTitleStyle,
                cardValueStyle = cardValueStyle,
                cardSubtitleStyle = cardSubtitleStyle,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(
                    onClick = onTest,
                    enabled = !loading,
                ) {
                    if (loading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("连接")
                    }
                }
            }
        }
    }
}

@Composable
private fun AiConfigForm(
    selectedProvider: String,
    currentUrl: String,
    currentModel: String,
    currentApiKey: String,
    modelName: String,
    modelOptions: List<String>,
    modelHint: String,
    isProviderExpanded: Boolean,
    isModelExpanded: Boolean,
    onProviderExpandedChange: (Boolean) -> Unit,
    onModelExpandedChange: (Boolean) -> Unit,
    onProviderSelected: (String) -> Unit,
    onUrlChange: (String) -> Unit,
    onModelSelected: (String) -> Unit,
    onModelNameChange: (String) -> Unit,
    onKeyChange: (String) -> Unit,
    cardTitleStyle: TextStyle,
    cardValueStyle: TextStyle,
    cardSubtitleStyle: TextStyle,
    customMode: Boolean,
    connected: Boolean,
    manualModel: Boolean,
    loading: Boolean,
    onConnect: () -> Unit,
) {
    val canExpandModel = modelOptions.isNotEmpty()
    val canToggleModel = canExpandModel || isModelExpanded
    val buttonText = if (connected) "保存" else "连接"
    val buttonEnabled = !loading &&
        currentApiKey.isNotBlank() &&
        (!customMode || currentUrl.isNotBlank()) &&
        (!connected || modelName.isNotBlank())

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            ExpandableSelectionItem(
                title = "服务提供商",
                currentValue = selectedProvider,
                isExpanded = isProviderExpanded,
                onToggle = { onProviderExpandedChange(!isProviderExpanded) },
                options = listOf(PROVIDER_DEEPSEEK, PROVIDER_OPENAI, PROVIDER_GEMINI, PROVIDER_CUSTOM),
                onOptionSelected = onProviderSelected,
                cardTitleStyle = cardTitleStyle,
                cardValueStyle = cardValueStyle
            )

            MyDivider()

            TextInputItem(
                title = "API 地址",
                value = currentUrl,
                onValueChange = onUrlChange,
                placeholder = if (customMode) "输入 API 地址" else "按模板自动生成",
                readOnly = !customMode,
                cardTitleStyle = cardTitleStyle,
                cardValueStyle = cardValueStyle,
                cardSubtitleStyle = cardSubtitleStyle
            )

            MyDivider()

            TextInputItem(
                title = "API Key",
                value = currentApiKey,
                onValueChange = onKeyChange,
                placeholder = "点击输入 Key",
                cardTitleStyle = cardTitleStyle,
                cardValueStyle = cardValueStyle,
                cardSubtitleStyle = cardSubtitleStyle
            )

            MyDivider()

            if (manualModel) {
                TextInputItem(
                    title = "模型名称",
                    value = modelName,
                    onValueChange = onModelNameChange,
                    placeholder = "输入模型名称",
                    cardTitleStyle = cardTitleStyle,
                    cardValueStyle = cardValueStyle,
                    cardSubtitleStyle = cardSubtitleStyle
                )
            } else {
                ExpandableSelectionItem(
                    title = "模型名称",
                    currentValue = currentModel,
                    isExpanded = isModelExpanded,
                    onToggle = {
                        if (canToggleModel) {
                            onModelExpandedChange(!isModelExpanded)
                        }
                    },
                    options = modelOptions,
                    emptyHint = modelHint,
                    onOptionSelected = onModelSelected,
                    cardTitleStyle = cardTitleStyle,
                    cardValueStyle = cardValueStyle,
                    toggleEnabled = canToggleModel
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(
                    onClick = onConnect,
                    enabled = buttonEnabled,
                ) {
                    if (loading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(buttonText)
                    }
                }
            }
        }
    }
}

@Composable
private fun MyDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

@Composable
private fun ExpandableSelectionItem(
    title: String,
    currentValue: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    options: List<String>,
    onOptionSelected: (String) -> Unit,
    cardTitleStyle: TextStyle,
    cardValueStyle: TextStyle,
    emptyHint: String = "",
    toggleEnabled: Boolean = true
) {
    val haptics = rememberAppHaptics()
    Column(modifier = Modifier.fillMaxWidth()) {
        val rowModifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .then(
                if (toggleEnabled) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { haptics.click(); onToggle() }
                } else {
                    Modifier
                }
            )

        Row(
            modifier = rowModifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = title, style = cardTitleStyle)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = currentValue,
                    style = cardValueStyle,
                    color = if (isExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = if (toggleEnabled) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            if (options.isEmpty()) {
                Text(
                    text = emptyHint,
                    style = cardValueStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    options.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { haptics.selection(); onOptionSelected(option) }
                                .heightIn(min = 48.dp)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = option,
                                fontWeight = if (option == currentValue) FontWeight.Bold else FontWeight.Normal,
                                color = if (option == currentValue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                style = cardValueStyle
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TextInputItem(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    readOnly: Boolean = false,
    secret: Boolean = false,
    onFocusChange: ((Boolean) -> Unit)? = null,
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
    val isPasswordField = title == "API Key" || secret
    val visualTransformation = if (isPasswordField && value.isNotEmpty() && !isFocused) {
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
            modifier = Modifier.width(124.dp),
            maxLines = 1,
        )

        BasicTextField(
            value = fieldValue,
            onValueChange = { newValue ->
                fieldValue = newValue
                onValueChange(newValue.text)
            },
            readOnly = readOnly,
            textStyle = cardValueStyle.copy(
                color = if (readOnly) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End
            ),
            visualTransformation = visualTransformation,
            singleLine = true,
            interactionSource = interactionSource,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onFocusChanged { focusState ->
                    val focused = focusState.isFocused
                    if (focused != isFocused) {
                        isFocused = focused
                        onFocusChange?.invoke(focused)
                    }
                },
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 24.dp),
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

private fun detectPresetProvider(url: String, modelName: String): String {
    val normalizedUrl = url.trim().trimEnd('/').lowercase()
    if (normalizedUrl.isBlank()) return PROVIDER_CUSTOM

    return when {
        normalizedUrl.contains("api.deepseek.com") -> PROVIDER_DEEPSEEK
        normalizedUrl.contains("api.openai.com") -> PROVIDER_OPENAI
        normalizedUrl.contains("generativelanguage.googleapis.com") ||
            normalizedUrl.contains("googleapis") ||
            normalizedUrl.contains("gemini") -> PROVIDER_GEMINI
        else -> PROVIDER_CUSTOM
    }
}

private fun normalizeCustomApiUrl(rawUrl: String): String {
    val trimmed = rawUrl.trim().trimEnd('/')
    if (trimmed.isBlank()) return trimmed

    val lower = trimmed.lowercase()
    if (lower.contains(":generatecontent")) return trimmed
    if (lower.endsWith("/v1/models")) return trimmed
    if (lower.contains("/chat/completions")) return trimmed
    if (lower.contains("googleapis") || lower.contains("generativelanguage") || lower.contains("gemini")) {
        return trimmed
    }
    if (lower.endsWith("/v1")) {
        return "$trimmed/chat/completions"
    }
    if (lower.contains("/v1/")) {
        return trimmed
    }
    return "$trimmed/v1/chat/completions"
}
