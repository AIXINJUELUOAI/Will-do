package com.antgskds.calendarassistant.ui.page_display.settings

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import com.antgskds.calendarassistant.core.ai.ApiModelProvider
import com.antgskds.calendarassistant.core.ai.ModelListResult
import com.antgskds.calendarassistant.ui.components.AppCard
import com.antgskds.calendarassistant.ui.components.ToastType
import com.antgskds.calendarassistant.ui.components.UniversalToast
import com.antgskds.calendarassistant.ui.haptic.rememberAppHaptics
import com.antgskds.calendarassistant.ui.viewmodel.MainViewModel
import com.antgskds.calendarassistant.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

private const val PROVIDER_DEEPSEEK = "DeepSeek"
private const val PROVIDER_OPENAI = "OpenAI"
private const val PROVIDER_GEMINI = "Gemini"
private const val PROVIDER_CUSTOM = "自定义"

private data class ProviderPreset(
    val models: List<String>,
    val endpointBuilder: (String) -> String
)

private val providerPresets = mapOf(
    PROVIDER_DEEPSEEK to ProviderPreset(
        models = listOf("deepseek-v4-flash", "deepseek-v4-pro"),
        endpointBuilder = { "https://api.deepseek.com/chat/completions" }
    ),
    PROVIDER_OPENAI to ProviderPreset(
        models = listOf("gpt-5.4", "gpt-5.4-mini", "gpt-5.3", "gpt-5.2"),
        endpointBuilder = { "https://api.openai.com/v1/chat/completions" }
    ),
    PROVIDER_GEMINI to ProviderPreset(
        models = listOf("gemini-3.1", "gemini-3.1-flash", "gemini-3-deep-think", "gemini-2.5"),
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
    var textFetchedSignature by remember { mutableStateOf<String?>(null) }

    var mmModelUrl by remember(settings) { mutableStateOf(settings.mmModelUrl) }
    var mmModelName by remember(settings) { mutableStateOf(settings.mmModelName) }
    var mmModelKey by remember(settings) { mutableStateOf(settings.mmModelKey) }
    var mmProvider by remember(settings) { mutableStateOf(detectPresetProvider(settings.mmModelUrl, settings.mmModelName)) }
    var mmCustomModels by remember { mutableStateOf(emptyList<String>()) }
    var mmFetchedSignature by remember { mutableStateOf<String?>(null) }

    var isProviderExpanded by remember { mutableStateOf(false) }
    var isModelExpanded by remember { mutableStateOf(false) }
    var actionLoading by remember { mutableStateOf(false) }

    val activeProvider = if (isMultimodalEnabled) mmProvider else textProvider
    val activeModelUrl = if (isMultimodalEnabled) mmModelUrl else textModelUrl
    val activeModelName = if (isMultimodalEnabled) mmModelName else textModelName
    val activeModelKey = if (isMultimodalEnabled) mmModelKey else textModelKey
    val activeCustomModels = if (isMultimodalEnabled) mmCustomModels else textCustomModels
    val activeFetchedSignature = if (isMultimodalEnabled) mmFetchedSignature else textFetchedSignature
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
    }

    fun setActiveKey(value: String) {
        if (isMultimodalEnabled) mmModelKey = value else textModelKey = value
    }

    fun setActiveCustomModels(value: List<String>) {
        if (isMultimodalEnabled) mmCustomModels = value else textCustomModels = value
    }

    fun setActiveFetchedSignature(value: String?) {
        if (isMultimodalEnabled) mmFetchedSignature = value else textFetchedSignature = value
    }

    fun applyProviderPreset(provider: String) {
        setActiveProvider(provider)
        if (provider == PROVIDER_CUSTOM) {
            setActiveFetchedSignature(null)
            setActiveCustomModels(emptyList())
            return
        }

        val preset = providerPresets[provider] ?: return
        val nextModel = if (effectiveModelName in preset.models) {
            effectiveModelName
        } else {
            preset.models.firstOrNull().orEmpty()
        }
        setActiveName(nextModel)
        setActiveUrl(preset.endpointBuilder(nextModel))
        setActiveFetchedSignature(null)
        setActiveCustomModels(emptyList())
    }

    suspend fun saveCurrent(url: String, name: String, key: String) {
        if (isMultimodalEnabled) {
            viewModel.updateMultimodalAiSettings(key.trim(), name.trim(), url.trim())
        } else {
            viewModel.updateAiSettings(key.trim(), name.trim(), url.trim())
        }
        showToast("配置保存成功")
    }

    suspend fun fetchCustomModelsThenWaitChoose() {
        if (activeModelUrl.isBlank() || activeModelKey.isBlank()) {
            showToast("请先填写 API 地址和 API Key", ToastType.ERROR)
            return
        }

        val normalizedUrl = normalizeCustomApiUrl(activeModelUrl)
        if (normalizedUrl != activeModelUrl) {
            setActiveUrl(normalizedUrl)
        }

        val signature = "${normalizedUrl.trim()}|${activeModelKey.trim()}"
        actionLoading = true
        when (val result = ApiModelProvider.fetchAvailableModels(activeModelKey.trim(), normalizedUrl.trim())) {
            is ModelListResult.Success -> {
                setActiveCustomModels(result.models)
                setActiveFetchedSignature(signature)
                if (activeModelName !in result.models) {
                    setActiveName("")
                }
                isModelExpanded = true
                showToast("连接成功")
            }
            is ModelListResult.Failure -> {
                val errorCode = Regex("\\b(\\d{3})\\b")
                    .find(result.message)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?: "UNKNOWN"
                showToast("连接失败 $errorCode", ToastType.ERROR)
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
            val preset = providerPresets[activeProvider] ?: return
            val finalModel = if (effectiveModelName.isBlank()) {
                preset.models.firstOrNull().orEmpty()
            } else {
                effectiveModelName
            }
            val finalUrl = preset.endpointBuilder(finalModel)
            setActiveName(finalModel)
            setActiveUrl(finalUrl)
            saveCurrent(finalUrl, finalModel, activeModelKey)
            return
        }

        if (activeModelUrl.isBlank() || activeModelKey.isBlank()) {
            showToast("请先填写 API 地址和 API Key", ToastType.ERROR)
            return
        }

        val normalizedUrl = normalizeCustomApiUrl(activeModelUrl)
        val signature = "${normalizedUrl.trim()}|${activeModelKey.trim()}"
        val fetchedReady = activeFetchedSignature == signature && activeCustomModels.isNotEmpty()

        if (!fetchedReady) {
            fetchCustomModelsThenWaitChoose()
            return
        }

        if (effectiveModelName.isBlank()) {
            isModelExpanded = true
            showToast("请先选择模型名称", ToastType.ERROR)
            return
        }

        saveCurrent(normalizedUrl, effectiveModelName, activeModelKey)
    }

    val modelOptions = if (activeProvider == PROVIDER_CUSTOM) {
        activeCustomModels
    } else {
        providerPresets[activeProvider]?.models.orEmpty()
    }
    val modelDisplay = when {
        effectiveModelName.isNotBlank() -> effectiveModelName
        activeProvider == PROVIDER_CUSTOM -> "点击保存按钮测试并拉取模型"
        else -> "请选择模型"
    }
    val activeSignature = "${normalizeCustomApiUrl(activeModelUrl).trim()}|${activeModelKey.trim()}"
    val customModelsFetched = activeProvider == PROVIDER_CUSTOM &&
            activeFetchedSignature == activeSignature &&
            activeCustomModels.isNotEmpty()
    val saveHintText = when {
        activeProvider != PROVIDER_CUSTOM -> "点击右下角按钮保存配置"
        !customModelsFetched -> "点击右下角按钮测试连接并拉取模型列表"
        effectiveModelName.isBlank() -> "请选择模型名称，然后再次点击右下角按钮保存配置"
        else -> "再次点击右下角按钮保存配置"
    }

    val onModelUrlChange: (String) -> Unit = { newValue ->
        setActiveUrl(newValue)
        if (activeProvider == PROVIDER_CUSTOM) {
            setActiveFetchedSignature(null)
            setActiveCustomModels(emptyList())
        }
    }

    val onModelNameChange: (String) -> Unit = { newValue -> setActiveName(newValue) }
    val onModelKeyChange: (String) -> Unit = {
        setActiveKey(it)
        if (activeProvider == PROVIDER_CUSTOM) {
            setActiveFetchedSignature(null)
            setActiveCustomModels(emptyList())
        }
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
                .padding(bottom = 120.dp + bottomInset),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("参数配置", style = sectionTitleStyle)
            Text(
                text = "当前模式：$modeLabel（在偏好设置中切换）",
                style = cardSubtitleStyle
            )

            AiConfigForm(
                selectedProvider = activeProvider,
                currentUrl = activeModelUrl,
                currentModel = modelDisplay,
                currentApiKey = activeModelKey,
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
                    if (activeProvider == PROVIDER_GEMINI) {
                        val endpoint = providerPresets[PROVIDER_GEMINI]?.endpointBuilder?.invoke(model).orEmpty()
                        if (endpoint.isNotBlank()) onModelUrlChange(endpoint)
                    }
                    isModelExpanded = false
                },
                onKeyChange = onModelKeyChange,
                cardTitleStyle = cardTitleStyle,
                cardValueStyle = cardValueStyle,
                cardSubtitleStyle = cardSubtitleStyle,
                customMode = activeProvider == PROVIDER_CUSTOM
            )

            Spacer(modifier = Modifier.height(120.dp))
            Text(
                text = saveHintText,
                modifier = Modifier
                    .fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                textAlign = TextAlign.Center
            )
        }

        FloatingActionButton(
            onClick = {
                if (!actionLoading) {
                    haptics.click()
                    scope.launch { onSaveClick() }
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

    LaunchedEffect(isMultimodalEnabled) {
        isProviderExpanded = false
        isModelExpanded = false
    }
}

@Composable
private fun AiConfigForm(
    selectedProvider: String,
    currentUrl: String,
    currentModel: String,
    currentApiKey: String,
    modelOptions: List<String>,
    modelHint: String,
    isProviderExpanded: Boolean,
    isModelExpanded: Boolean,
    onProviderExpandedChange: (Boolean) -> Unit,
    onModelExpandedChange: (Boolean) -> Unit,
    onProviderSelected: (String) -> Unit,
    onUrlChange: (String) -> Unit,
    onModelSelected: (String) -> Unit,
    onKeyChange: (String) -> Unit,
    cardTitleStyle: TextStyle,
    cardValueStyle: TextStyle,
    cardSubtitleStyle: TextStyle,
    customMode: Boolean
) {
    val canExpandModel = modelOptions.isNotEmpty()
    val canToggleModel = canExpandModel || isModelExpanded

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
    }
}

@Composable
private fun MyDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp),
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
                .onFocusChanged { focusState -> isFocused = focusState.isFocused },
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
    val normalizedUrl = normalizePresetComparableUrl(url)
    val normalizedModel = modelName.trim()
    if (normalizedUrl.isBlank() || normalizedModel.isBlank()) return PROVIDER_CUSTOM

    return providerPresets.entries.firstOrNull { (_, preset) ->
        normalizedModel in preset.models &&
            normalizePresetComparableUrl(preset.endpointBuilder(normalizedModel)) == normalizedUrl
    }?.key ?: PROVIDER_CUSTOM
}

private fun normalizePresetComparableUrl(url: String): String {
    return url.trim().trimEnd('/').lowercase()
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
