package com.antgskds.calendarassistant.core.ai

import com.antgskds.calendarassistant.data.model.MySettings

data class AiModelConfig(
    val key: String,
    val name: String,
    val url: String,
    val isMultimodal: Boolean
)

fun MySettings.activeAiConfig(): AiModelConfig {
    return if (useMultimodalAi) {
        AiModelConfig(
            key = mmModelKey.trim(),
            name = mmModelName.trim(),
            url = mmModelUrl.trim(),
            isMultimodal = true
        )
    } else {
        AiModelConfig(
            key = modelKey.trim(),
            name = modelName.trim(),
            url = modelUrl.trim(),
            isMultimodal = false
        )
    }
}

fun AiModelConfig.isConfigured(): Boolean {
    return key.isNotBlank() && url.isNotBlank() && name.isNotBlank()
}

fun AiModelConfig.missingConfigMessage(): String {
    return if (isMultimodal) {
        "请先填写多模态AI配置"
    } else {
        "请先填写文本AI配置"
    }
}
