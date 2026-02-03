package com.antgskds.calendarassistant.ui.page_display.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.ui.components.UniversalToast
import com.antgskds.calendarassistant.ui.components.ToastType
import com.antgskds.calendarassistant.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsPage(
    viewModel: SettingsViewModel,
    uiSize: Int = 2
) {
    val settings by viewModel.settings.collectAsState()
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var modelUrl by remember(settings) { mutableStateOf(settings.modelUrl) }
    var modelName by remember(settings) { mutableStateOf(settings.modelName) }
    var modelKey by remember(settings) { mutableStateOf(settings.modelKey) }

    val fabSize = when (uiSize) { 1 -> 56.dp; 2 -> 64.dp; else -> 72.dp }
    val fabIconSize = when (uiSize) { 1 -> 24.dp; 2 -> 28.dp; else -> 32.dp }

    // --- 样式定义优化 (Material 3) ---
    // 板块标题：加粗 + 主色
    val sectionTitleStyle = MaterialTheme.typography.titleMedium.copy(
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary
    )
    // 卡片标题：中等字重 + 黑色 (OnSurface)
    val cardTitleStyle = MaterialTheme.typography.bodyLarge.copy(
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface
    )
    // 右侧数值/只读状态：常规字重 + 灰色 (OnSurfaceVariant)
    val cardValueStyle = MaterialTheme.typography.bodyLarge.copy(
        fontWeight = FontWeight.Normal,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    // 副标题/提示：Grey + Transparent
    val cardSubtitleStyle = MaterialTheme.typography.bodyLarge.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    )
    val contentBodyStyle = MaterialTheme.typography.bodyMedium

    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val focusManager = LocalFocusManager.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) { focusManager.clearFocus() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
                .padding(bottom = 120.dp + bottomInset),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 参数配置板块标题
            Text("参数配置", style = sectionTitleStyle)

            AiConfigForm(
                initialUrl = modelUrl,
                initialName = modelName,
                initialKey = modelKey,
                onUrlChange = { modelUrl = it },
                onNameChange = { modelName = it },
                onKeyChange = { modelKey = it },
                cardTitleStyle = cardTitleStyle,
                cardValueStyle = cardValueStyle,
                cardSubtitleStyle = cardSubtitleStyle,
                contentBodyStyle = contentBodyStyle
            )
        }

        FloatingActionButton(
            onClick = {
                viewModel.updateAiSettings(modelKey.trim(), modelName.trim(), modelUrl.trim())
                scope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(message = "配置保存成功", duration = SnackbarDuration.Short)
                }
            },
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 32.dp + bottomInset)
                .size(fabSize)
        ) {
            Icon(Icons.Default.Check, contentDescription = "保存", modifier = Modifier.size(fabIconSize))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp + bottomInset),
            snackbar = { data -> UniversalToast(message = data.visuals.message, type = ToastType.SUCCESS) }
        )
    }
}

@Composable
private fun AiConfigForm(
    initialUrl: String,
    initialName: String,
    initialKey: String,
    onUrlChange: (String) -> Unit,
    onNameChange: (String) -> Unit,
    onKeyChange: (String) -> Unit,
    cardTitleStyle: TextStyle,
    cardValueStyle: TextStyle, // 传入的是灰色的样式
    cardSubtitleStyle: TextStyle,
    contentBodyStyle: TextStyle
) {
    val currentUrl = initialUrl
    val currentModel = initialName

    val initialProvider = when {
        currentUrl.contains("deepseek") -> "DeepSeek"
        currentUrl.contains("openai") -> "OpenAI"
        currentUrl.contains("googleapis") -> "Gemini"
        currentUrl.isBlank() && currentModel.isBlank() -> "DeepSeek"
        else -> "自定义"
    }

    var selectedProvider by remember { mutableStateOf(initialProvider) }
    var isProviderExpanded by remember { mutableStateOf(initialKey.isBlank()) }
    var isModelExpanded by remember { mutableStateOf(initialKey.isBlank()) }

    LaunchedEffect(initialKey) {
        if (initialKey.isBlank()) {
            isProviderExpanded = true
            isModelExpanded = true
        }
    }

    LaunchedEffect(selectedProvider) {
        if (selectedProvider != "自定义") {
            onUrlChange(
                when (selectedProvider) {
                    "DeepSeek" -> "https://api.deepseek.com/chat/completions"
                    "OpenAI" -> "https://api.openai.com/v1/chat/completions"
                    "Gemini" -> "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"
                    else -> ""
                }
            )
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            // 1. 服务提供商
            ExpandableSelectionItem(
                title = "服务提供商",
                currentValue = selectedProvider,
                isExpanded = isProviderExpanded,
                onToggle = { isProviderExpanded = !isProviderExpanded },
                options = listOf("DeepSeek", "OpenAI", "Gemini", "自定义"),
                onOptionSelected = {
                    selectedProvider = it
                    isProviderExpanded = false
                    // 切换到"自定义"时自动清空模型名称和 API 地址
                    if (it == "自定义") {
                        onNameChange("")
                        onUrlChange("")
                    }
                },
                cardTitleStyle = cardTitleStyle,
                cardValueStyle = cardValueStyle // 灰色显示
            )

            MyDivider()

            // 2. 模型名称
            if (selectedProvider != "自定义") {
                val models = when(selectedProvider) {
                    "DeepSeek" -> listOf("deepseek-chat", "deepseek-coder", "deepseek-reasoner")
                    "OpenAI" -> listOf("gpt-4o-mini", "gpt-4o", "gpt-3.5-turbo")
                    "Gemini" -> listOf("gemini-1.5-flash", "gemini-1.5-pro")
                    else -> emptyList()
                }

                ExpandableSelectionItem(
                    title = "模型名称",
                    currentValue = initialName.ifBlank { "请选择模型" },
                    isExpanded = isModelExpanded,
                    onToggle = { isModelExpanded = !isModelExpanded },
                    options = models,
                    onOptionSelected = {
                        onNameChange(it)
                        isModelExpanded = false
                        if (selectedProvider == "Gemini") {
                            onUrlChange("https://generativelanguage.googleapis.com/v1beta/models/$it:generateContent")
                        }
                    },
                    cardTitleStyle = cardTitleStyle,
                    cardValueStyle = cardValueStyle
                )
            } else {
                TextInputItem(
                    title = "模型名称",
                    value = initialName,
                    onValueChange = onNameChange,
                    placeholder = "输入模型名称",
                    cardTitleStyle = cardTitleStyle,
                    cardValueStyle = cardValueStyle,
                    cardSubtitleStyle = cardSubtitleStyle
                )
            }

            MyDivider()

            // 3. API Key
            TextInputItem(
                title = "API Key",
                value = initialKey,
                onValueChange = onKeyChange,
                placeholder = "点击输入 Key",
                cardTitleStyle = cardTitleStyle,
                cardValueStyle = cardValueStyle,
                cardSubtitleStyle = cardSubtitleStyle
            )

            MyDivider()

            // 4. API Endpoint
            TextInputItem(
                title = "API 地址",
                value = initialUrl,
                onValueChange = onUrlChange,
                readOnly = selectedProvider != "自定义",
                placeholder = "API 请求地址",
                cardTitleStyle = cardTitleStyle,
                cardValueStyle = cardValueStyle,
                cardSubtitleStyle = cardSubtitleStyle
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
            Text(
                text = title,
                style = cardTitleStyle
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = currentValue,
                    // 右侧显示当前值：如果是展开状态，稍微高亮一下（Primary），否则用灰色（cardValueStyle）
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
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOptionSelected(option) }
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
    val isPasswordField = title == "API Key"
    val visualTransformation = if (isPasswordField && !isFocused && value.isNotEmpty()) {
        PasswordVisualTransformation()
    } else {
        VisualTransformation.None
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

        Box(
            modifier = Modifier
                .weight(1f)
                .clickable(
                    enabled = !readOnly,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    focusRequester.requestFocus()
                },
            contentAlignment = Alignment.CenterEnd
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = cardSubtitleStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                readOnly = readOnly,
                textStyle = cardValueStyle.copy(
                    color = if (readOnly) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.End
                ),
                visualTransformation = visualTransformation,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState -> isFocused = focusState.isFocused }
            )
        }
    }
}