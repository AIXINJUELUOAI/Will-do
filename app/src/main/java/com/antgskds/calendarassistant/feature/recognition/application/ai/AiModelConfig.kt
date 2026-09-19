package com.antgskds.calendarassistant.feature.recognition.application.ai

import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import com.antgskds.calendarassistant.feature.settings.data.model.migrateToMultimodalConfig
import com.antgskds.calendarassistant.feature.settings.data.model.RecognitionMode

data class AiModelConfig(
    val key: String,
    val name: String,
    val url: String,
    val isMultimodal: Boolean
)

fun MySettings.activeAiConfig(): AiModelConfig {
    val settings = migrateToMultimodalConfig()
    return AiModelConfig(
        key = settings.mmModelKey.trim(),
        name = settings.mmModelName.trim(),
        url = settings.mmModelUrl.trim(),
        isMultimodal = true
    )
}

fun AiModelConfig.isConfigured(): Boolean {
    return key.isNotBlank() && url.isNotBlank() && name.isNotBlank()
}

fun AiModelConfig.missingConfigMessage(): String = "请先配置支持图片输入的多模态模型"

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
