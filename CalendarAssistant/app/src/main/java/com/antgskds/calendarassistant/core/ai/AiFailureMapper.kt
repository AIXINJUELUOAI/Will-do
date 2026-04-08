package com.antgskds.calendarassistant.core.ai

data class AiFailureMessage(
    val title: String,
    val detail: String
)

object AiFailureMapper {
    private const val DEFAULT_TITLE = "分析失败"

    fun map(failure: ApiCallResult.Failure): AiFailureMessage {
        return when (failure.kind) {
            ApiErrorKind.CONFIG -> AiFailureMessage(DEFAULT_TITLE, "AI未配置")
            ApiErrorKind.PARSE -> AiFailureMessage(DEFAULT_TITLE, buildParseDetail(failure))
            ApiErrorKind.NETWORK -> AiFailureMessage(DEFAULT_TITLE, buildNetworkDetail(failure.message))
            ApiErrorKind.HTTP -> AiFailureMessage(DEFAULT_TITLE, buildHttpDetail(failure))
            ApiErrorKind.UNKNOWN -> AiFailureMessage(DEFAULT_TITLE, failure.message.ifBlank { "请求失败" }.take(18))
        }
    }

    private fun buildParseDetail(failure: ApiCallResult.Failure): String {
        val message = failure.message.trim()
        val rawBody = failure.rawBody.orEmpty()
        val lowerBody = rawBody.lowercase()

        return when {
            message.contains("Empty Content", ignoreCase = true) -> "模型无输出"
            message.contains("No Choices", ignoreCase = true) -> "响应缺choices"
            message.contains("Empty Parts", ignoreCase = true) -> "响应缺分片"
            message.contains("No Candidates", ignoreCase = true) -> "响应缺candidates"
            lowerBody.contains("\"content\":null") -> "模型无输出"
            rawBody.isBlank() -> "响应为空"
            else -> "响应格式错"
        }
    }

    private fun buildHttpDetail(failure: ApiCallResult.Failure): String {
        val code = failure.statusCode
        return when (code) {
            400 -> "参数错误(400)"
            401 -> "密钥无效(401)"
            402 -> "额度不足(402)"
            403 -> "无权访问(403)"
            404 -> "接口不存在(404)"
            408 -> "请求超时(408)"
            409 -> "请求冲突(409)"
            413 -> "图片过大(413)"
            415 -> "格式不支持(415)"
            422 -> "参数错误(422)"
            429 -> "请求过多(429)"
            500 -> "服务异常(500)"
            502 -> "网关异常(502)"
            503 -> "服务不可用(503)"
            504 -> "上游超时(504)"
            null -> "请求失败"
            else -> "HTTP $code"
        }
    }

    private fun buildNetworkDetail(message: String): String {
        val lower = message.lowercase()
        val tag = when {
            lower.contains("timeout") || lower.contains("timed out") || lower.contains("sockettimeoutexception") -> "timeout"
            lower.contains("unknownhost") -> "unknown host"
            lower.contains("unreachable") -> "unreachable"
            lower.contains("network") -> "network"
            else -> ""
        }
        return when (tag) {
            "timeout" -> "网络超时"
            "unknown host" -> "地址无效"
            "unreachable" -> "网络不可达"
            "network" -> "网络异常"
            else -> "网络失败"
        }
    }
}
