package com.antgskds.calendarassistant.core.ai

import com.antgskds.calendarassistant.core.event.events.RecognitionFailedEvent

data class RecognitionFailureDisplay(
    val reason: String,
    val suggestion: String
)

object RecognitionFailureMessageMapper {
    fun display(payload: RecognitionFailedEvent): RecognitionFailureDisplay {
        val code = payload.errorCode.takeIf { it.isNotBlank() }
            ?: payload.message.trim().takeIf { it.isInternalStatusCode() }
        val reason = code?.let(::userMessage) ?: userMessage(payload)
        val normalizedReason = reason
            .removeSuffix("，请稍后重试")
            .removeSuffix("，请重试")
            .takeIf { it != "识别失败" }
            ?: "未知错误"
        return RecognitionFailureDisplay(
            reason = normalizedReason,
            suggestion = suggestionFor(code, payload.retryable)
        )
    }

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

    private fun suggestionFor(code: String?, retryable: Boolean): String {
        return when (code) {
            "EMPTY_RESULT",
            "EMPTY_EVENTS" -> "请换一张更清晰的截图，或手动补充时间地点"
            "INVALID_JSON",
            "INVALID_SCHEMA" -> "请重试，或切换到更稳定的模型"
            "TIMEOUT_LOADING" -> "请稍后重试，或重新选择本地模型"
            "TIMEOUT_GENERATING" -> "请稍后重试，或换用云端/更小模型"
            "ENGINE_DISCONNECTED" -> "请重新启动识别服务后再试"
            "ENGINE_KILLED_LOW_MEMORY" -> "请关闭后台应用，或换用更小的本地模型"
            "ENGINE_NATIVE_CRASH",
            "ENGINE_JAVA_CRASH" -> "请重启本地模型服务，或切换模型"
            "MODEL_FILE_MISSING" -> "请重新导入本地模型文件"
            "MODEL_LOAD_FAILED" -> "请确认模型文件完整，或重新导入模型"
            "MODEL_UNSUPPORTED" -> "请切换支持当前识别模式的模型"
            "USER_CANCELLED" -> "如需继续，请重新发起识别"
            "FOREGROUND_START_FAILED" -> "请检查通知权限和后台限制后重试"
            "ANALYSIS_FAILURE",
            "UNKNOWN_ERROR" -> "请稍后重试，或切换模型后再试"
            else -> if (retryable) "请稍后重试，或切换模型后再试" else "请检查识别内容和模型配置"
        }
    }

    private fun String.isInternalStatusCode(): Boolean {
        return matches(Regex("[A-Z_]+"))
    }
}
