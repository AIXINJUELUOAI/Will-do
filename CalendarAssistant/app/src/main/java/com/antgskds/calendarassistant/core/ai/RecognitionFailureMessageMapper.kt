package com.antgskds.calendarassistant.core.ai

import com.antgskds.calendarassistant.core.event.events.RecognitionFailedEvent

object RecognitionFailureMessageMapper {
    fun userMessage(payload: RecognitionFailedEvent): String {
        val mapped = userMessage(payload.errorCode)
        if (mapped != null) return mapped

        val message = payload.message.trim()
        return when {
            message.isBlank() -> "识别失败，请稍后重试"
            message.isInternalStatusCode() -> userMessage(message) ?: "识别失败，请稍后重试"
            message == "分析失败" -> "分析失败，请稍后重试"
            else -> message
        }
    }

    fun userMessage(code: String): String? {
        return when (code) {
            "EMPTY_RESULT",
            "EMPTY_EVENTS" -> "未识别到有效日程"
            "INVALID_JSON" -> "模型返回格式异常"
            "INVALID_SCHEMA" -> "模型返回内容不完整"
            "TIMEOUT_LOADING" -> "本地模型加载超时"
            "TIMEOUT_GENERATING" -> "本地模型生成超时"
            "ENGINE_DISCONNECTED" -> "AI 引擎已断开，请重试"
            "ENGINE_KILLED_LOW_MEMORY" -> "系统内存不足，已终止本地模型"
            "ENGINE_NATIVE_CRASH" -> "AI 引擎原生层异常"
            "ENGINE_JAVA_CRASH" -> "AI 引擎服务异常"
            "MODEL_FILE_MISSING" -> "本地模型文件不存在"
            "MODEL_LOAD_FAILED" -> "本地模型加载失败"
            "MODEL_UNSUPPORTED" -> "当前模型不支持此识别模式"
            "USER_CANCELLED" -> "已取消识别"
            "FOREGROUND_START_FAILED" -> "本地模型前台服务启动失败"
            "ANALYSIS_FAILURE" -> "分析失败，请稍后重试"
            "UNKNOWN_ERROR" -> "识别失败，请稍后重试"
            else -> null
        }
    }

    private fun String.isInternalStatusCode(): Boolean {
        return matches(Regex("[A-Z_]+"))
    }
}
