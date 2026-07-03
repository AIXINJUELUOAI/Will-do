package com.antgskds.calendarassistant.core.ai

import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.data.model.RecognitionMode

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

fun MySettings.isRecognitionConfigReady(): Boolean {
    return activeAiConfig().isConfigured()
}

fun MySettings.recognitionConfigMissingMessage(): String {
    return activeAiConfig().missingConfigMessage()
}

fun MySettings.isTextRecognitionConfigReady(): Boolean {
    return when (RecognitionMode.normalize(recognitionMode)) {
        RecognitionMode.AI_ONLY -> activeAiConfig().isConfigured()
        RecognitionMode.REGEX_ONLY -> true
        RecognitionMode.REGEX_THEN_AI_ON_EMPTY -> true
        RecognitionMode.REGEX_THEN_AI_REVIEW -> true
        else -> activeAiConfig().isConfigured()
    }
}

fun MySettings.textRecognitionConfigMissingMessage(): String {
    return when (RecognitionMode.normalize(recognitionMode)) {
        RecognitionMode.REGEX_ONLY,
        RecognitionMode.REGEX_THEN_AI_ON_EMPTY,
        RecognitionMode.REGEX_THEN_AI_REVIEW -> "请先检查正则规则配置"
        else -> activeAiConfig().missingConfigMessage()
    }
}
