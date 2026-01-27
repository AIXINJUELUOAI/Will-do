package com.antgskds.calendarassistant.ui.page_display.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.ui.components.UniversalToast
import com.antgskds.calendarassistant.ui.components.ToastType
import com.antgskds.calendarassistant.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsPage(
    viewModel: SettingsViewModel
) {
    val settings by viewModel.settings.collectAsState()
    val scrollState = rememberScrollState()

    // 1. 状态提升：在最外层管理 Snackbar 状态
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 2. 使用 Box 填满全屏，作为根容器
    Box(modifier = Modifier.fillMaxSize()) {

        // 内容区域（可滚动）
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("模型配置", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "配置用于自然语言解析和OCR识别的 AI 模型。推荐使用 DeepSeek 或 OpenAI。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 3. 传入回调函数：当表单保存时，通知外层显示 Snackbar
            AiConfigForm(settings) { key, name, url ->
                viewModel.updateAiSettings(key, name, url)

                scope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(
                        message = "配置保存成功",
                        duration = SnackbarDuration.Short
                    )
                }
            }

            // 底部增加一点留白，防止内容滚动到底部时被 Toast 遮挡（可选）
            Spacer(modifier = Modifier.height(80.dp))
        }

        // 4. 将 SnackbarHost 放在最外层的 Box 底部
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter) // 绝对对齐到屏幕底部
                .padding(bottom = 32.dp),      // 距离屏幕底部的距离（这里控制高低）
            snackbar = { data ->
                UniversalToast(message = data.visuals.message, type = ToastType.SUCCESS)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiConfigForm(
    settings: MySettings,
    onSaveAndShowMessage: (String, String, String) -> Unit // 修改回调签名
) {
    val currentUrl = settings.modelUrl
    val currentModel = settings.modelName

    val initialProvider = when {
        currentUrl.contains("deepseek") -> "DeepSeek"
        currentUrl.contains("openai") -> "OpenAI"
        currentUrl.contains("googleapis") -> "Gemini"
        currentUrl.isBlank() && currentModel.isBlank() -> "DeepSeek"
        else -> "自定义"
    }

    var selectedProvider by remember { mutableStateOf(initialProvider) }
    var expandedProvider by remember { mutableStateOf(false) }
    var expandedModel by remember { mutableStateOf(false) }

    var modelUrl by remember { mutableStateOf(settings.modelUrl) }
    var modelName by remember { mutableStateOf(settings.modelName) }
    var modelKey by remember { mutableStateOf(settings.modelKey) }

    LaunchedEffect(selectedProvider) {
        if (selectedProvider != "自定义") {
            modelUrl = when (selectedProvider) {
                "DeepSeek" -> "https://api.deepseek.com/chat/completions"
                "OpenAI" -> "https://api.openai.com/v1/chat/completions"
                "Gemini" -> "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"
                else -> ""
            }
        }
    }

    // 这里不需要 Box 和 SnackbarHost 了，只保留表单列
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ExposedDropdownMenuBox(
            expanded = expandedProvider,
            onExpandedChange = { expandedProvider = !expandedProvider }
        ) {
            OutlinedTextField(
                value = selectedProvider,
                onValueChange = {},
                readOnly = true,
                label = { Text("服务提供商") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedProvider) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expandedProvider,
                onDismissRequest = { expandedProvider = false }
            ) {
                listOf("DeepSeek", "OpenAI", "Gemini", "自定义").forEach { item ->
                    DropdownMenuItem(text = { Text(item) }, onClick = { selectedProvider = item; expandedProvider = false })
                }
            }
        }

        if (selectedProvider != "自定义") {
            val models = when(selectedProvider) {
                "DeepSeek" -> listOf("deepseek-chat", "deepseek-coder", "deepseek-reasoner")
                "OpenAI" -> listOf("gpt-4o-mini", "gpt-4o", "gpt-3.5-turbo")
                "Gemini" -> listOf("gemini-1.5-flash", "gemini-1.5-pro")
                else -> emptyList()
            }
            ExposedDropdownMenuBox(
                expanded = expandedModel,
                onExpandedChange = { expandedModel = !expandedModel }
            ) {
                OutlinedTextField(
                    value = modelName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("模型名称") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedModel) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = expandedModel, onDismissRequest = { expandedModel = false }) {
                    models.forEach { item ->
                        DropdownMenuItem(text = { Text(item) }, onClick = {
                            modelName = item; expandedModel = false
                            if (selectedProvider == "Gemini") modelUrl = "https://generativelanguage.googleapis.com/v1beta/models/$item:generateContent"
                        })
                    }
                }
            }
        } else {
            OutlinedTextField(value = modelName, onValueChange = { modelName = it }, label = { Text("Model Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        }

        OutlinedTextField(value = modelKey, onValueChange = { modelKey = it }, label = { Text("API Key") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = modelUrl, onValueChange = { modelUrl = it }, label = { Text("API Endpoint URL") }, readOnly = selectedProvider != "自定义", modifier = Modifier.fillMaxWidth(), singleLine = true)

        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(onClick = {
                // 触发外层的回调
                onSaveAndShowMessage(modelKey.trim(), modelName.trim(), modelUrl.trim())
            }) {
                Text("保存配置")
            }
        }
    }
}

