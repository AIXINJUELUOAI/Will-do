package com.antgskds.calendarassistant.feature.recognition.ui.render

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.feature.recognition.application.ai.ModelListResult
import com.antgskds.calendarassistant.feature.recognition.ui.contract.AiSettingsUiAction
import com.antgskds.calendarassistant.feature.recognition.ui.contract.AiSettingsUiState
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val DeepSeek = "DeepSeek"
private const val OpenAi = "OpenAI"
private const val Gemini = "Gemini"
private const val Custom = "自定义"

private data class HyperProviderPreset(
    val models: List<String>,
    val endpoint: (String) -> String,
)

private val HyperProviderPresets = linkedMapOf(
    DeepSeek to HyperProviderPreset(
        models = listOf("deepseek-v4-flash", "deepseek-v4-pro"),
        endpoint = { "https://api.deepseek.com/chat/completions" },
    ),
    OpenAi to HyperProviderPreset(
        models = listOf("gpt-5.4", "gpt-5.4-mini", "gpt-5.3", "gpt-5.2"),
        endpoint = { "https://api.openai.com/v1/chat/completions" },
    ),
    Gemini to HyperProviderPreset(
        models = listOf("gemini-3.1", "gemini-3.1-flash", "gemini-3-deep-think", "gemini-2.5"),
        endpoint = { model ->
            "https://generativelanguage.googleapis.com/v1beta/models/${model.ifBlank { "gemini-3.1-flash" }}:generateContent"
        },
    ),
)

@Composable
fun AiSettingsScreen(
    state: AiSettingsUiState,
    uiSize: Int,
    onAction: (AiSettingsUiAction) -> Unit,
    fetchModels: suspend (String, String) -> ModelListResult,
) {
    val settings = state.settings
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val multimodal = settings.useMultimodalAi
    val initialUrl = if (multimodal) settings.mmModelUrl else settings.modelUrl
    val initialName = if (multimodal) settings.mmModelName else settings.modelName
    val initialKey = if (multimodal) settings.mmModelKey else settings.modelKey

    var provider by remember(multimodal, initialUrl, initialName) {
        mutableStateOf(detectHyperProvider(initialUrl, initialName))
    }
    var modelUrl by remember(multimodal, initialUrl) { mutableStateOf(initialUrl) }
    var modelName by remember(multimodal, initialName) { mutableStateOf(initialName) }
    var modelKey by remember(multimodal, initialKey) { mutableStateOf(initialKey) }
    var customModels by remember(multimodal) { mutableStateOf(emptyList<String>()) }
    var fetchedSignature by remember(multimodal) { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var keyVisible by remember(multimodal) { mutableStateOf(false) }

    val providers = HyperProviderPresets.keys.toList() + Custom
    val providerIndex = providers.indexOf(provider).coerceAtLeast(0)
    val modelOptions = if (provider == Custom) customModels else HyperProviderPresets[provider]?.models.orEmpty()
    val modelIndex = modelOptions.indexOf(modelName).coerceAtLeast(0)
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    fun message(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    fun applyProvider(index: Int) {
        provider = providers[index]
        customModels = emptyList()
        fetchedSignature = null
        HyperProviderPresets[provider]?.let { preset ->
            modelName = preset.models.firstOrNull().orEmpty()
            modelUrl = preset.endpoint(modelName)
        }
    }

    fun save(url: String, name: String, key: String) {
        if (multimodal) {
            onAction(AiSettingsUiAction.SaveMultimodalModel(key, name, url))
        } else {
            onAction(AiSettingsUiAction.SaveTextModel(key, name, url))
        }
        message("配置保存成功")
    }

    fun handleSave() {
        if (loading) return
        if (modelKey.isBlank()) {
            message("请先填写 API Key")
            return
        }
        val preset = HyperProviderPresets[provider]
        if (preset != null) {
            val finalModel = modelName.ifBlank { preset.models.firstOrNull().orEmpty() }
            save(preset.endpoint(finalModel), finalModel, modelKey.trim())
            return
        }
        if (modelUrl.isBlank()) {
            message("请先填写 API 地址")
            return
        }
        val normalizedUrl = normalizeHyperCustomUrl(modelUrl)
        val signature = "${normalizedUrl.trim()}|${modelKey.trim()}"
        if (fetchedSignature != signature || customModels.isEmpty()) {
            scope.launch {
                loading = true
                try {
                    when (val result = fetchModels(modelKey.trim(), normalizedUrl)) {
                        is ModelListResult.Success -> {
                            customModels = result.models
                            fetchedSignature = signature
                            modelUrl = normalizedUrl
                            if (modelName !in result.models) modelName = result.models.firstOrNull().orEmpty()
                            message(if (result.models.isEmpty()) "未获取到模型" else "连接成功，请确认模型")
                        }
                        is ModelListResult.Failure -> message("连接失败：${result.message.take(24)}")
                    }
                } catch (error: Exception) {
                    message("连接失败：${error.message.orEmpty().take(24)}")
                } finally {
                    loading = false
                }
            }
            return
        }
        if (modelName.isBlank()) {
            message("请选择模型名称")
            return
        }
        save(normalizedUrl, modelName.trim(), modelKey.trim())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .padding(bottom = 24.dp + bottomInset),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "参数配置",
            color = MiuixTheme.colorScheme.primary,
            fontSize = MiuixTheme.textStyles.title3.fontSize,
        )
        Text(
            text = "当前模式：${if (multimodal) "多模态 AI" else "文本 AI"}（在偏好设置中切换）",
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = MiuixTheme.textStyles.body2.fontSize,
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(vertical = 6.dp),
        ) {
            WindowDropdownPreference(
                items = providers,
                selectedIndex = providerIndex,
                title = "服务商",
                summary = "选择预设服务商或自定义兼容接口",
                onSelectedIndexChange = ::applyProvider,
            )
            WindowDropdownPreference(
                items = modelOptions.ifEmpty { listOf("请先测试连接") },
                selectedIndex = if (modelOptions.isEmpty()) 0 else modelIndex,
                title = "模型名称",
                summary = if (modelOptions.isEmpty()) "自定义接口需要先拉取模型列表" else modelName,
                enabled = modelOptions.isNotEmpty(),
                onSelectedIndexChange = { index ->
                    modelName = modelOptions[index]
                    HyperProviderPresets[provider]?.let { modelUrl = it.endpoint(modelName) }
                },
            )
        }

        if (provider == Custom) {
            TextField(
                value = modelUrl,
                onValueChange = {
                    modelUrl = it
                    fetchedSignature = null
                    customModels = emptyList()
                },
                modifier = Modifier.fillMaxWidth(),
                label = "API 地址",
                useLabelAsPlaceholder = true,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
        } else {
            TextField(
                value = modelUrl,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                label = "API 地址",
                useLabelAsPlaceholder = true,
                singleLine = true,
                readOnly = true,
            )
        }

        TextField(
            value = modelKey,
            onValueChange = {
                modelKey = it
                fetchedSignature = null
                customModels = emptyList()
            },
            modifier = Modifier.fillMaxWidth(),
            label = "API Key",
            useLabelAsPlaceholder = true,
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = { keyVisible = !keyVisible }) {
                    Icon(
                        imageVector = if (keyVisible) MiuixIcons.Normal.Hide else MiuixIcons.Normal.Show,
                        contentDescription = if (keyVisible) "隐藏 API Key" else "显示 API Key",
                        modifier = Modifier.size(24.dp),
                    )
                }
            },
            visualTransformation = if (keyVisible) {
                androidx.compose.ui.text.input.VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
        )

        Spacer(Modifier.height(4.dp))
        Button(
            onClick = ::handleSave,
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading,
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) {
            if (loading) {
                CircularProgressIndicator()
            } else {
                Text(if (provider == Custom && customModels.isEmpty()) "测试并获取模型" else "保存配置")
            }
        }
    }
}

private fun detectHyperProvider(url: String, modelName: String): String {
    val normalizedUrl = url.trim().trimEnd('/').lowercase()
    return HyperProviderPresets.entries.firstOrNull { (_, preset) ->
        modelName in preset.models && preset.endpoint(modelName).trim().trimEnd('/').lowercase() == normalizedUrl
    }?.key ?: Custom
}

private fun normalizeHyperCustomUrl(rawUrl: String): String {
    val trimmed = rawUrl.trim().trimEnd('/')
    if (trimmed.isBlank()) return trimmed
    val lower = trimmed.lowercase()
    if (lower.contains(":generatecontent") || lower.endsWith("/v1/models") || lower.contains("/chat/completions")) return trimmed
    if (lower.contains("googleapis") || lower.contains("generativelanguage") || lower.contains("gemini")) return trimmed
    if (lower.endsWith("/v1")) return "$trimmed/chat/completions"
    if (lower.contains("/v1/")) return trimmed
    return "$trimmed/v1/chat/completions"
}
