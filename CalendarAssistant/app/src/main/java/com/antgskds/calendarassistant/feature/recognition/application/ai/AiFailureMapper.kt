package com.antgskds.calendarassistant.feature.recognition.application.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class AiFailureMessage(
    val title: String,
    val detail: String,
    val errorCode: String? = null,
    val retryable: Boolean = true
)

object AiFailureMapper {
    private const val DEFAULT_TITLE = "分析失败"

    /** 只在图片请求失败后调用，不根据模型名称推断能力。 */
    fun mapImage(failure: ApiCallResult.Failure): AiFailureMessage {
        val upstream = upstreamMessage(failure)
        val canBeCapabilityError =
            (failure.kind == ApiErrorKind.HTTP && failure.statusCode in setOf(400, 404, 415, 422)) ||
                failure.kind == ApiErrorKind.UNKNOWN
        if (canBeCapabilityError && explicitlyRejectsImages(upstream)) {
            return AiFailureMessage(
                title = "识别失败",
                detail = "当前模型不支持图片输入，请更换支持多模态的模型。",
                errorCode = "IMAGE_INPUT_UNSUPPORTED",
                retryable = false
            )
        }

        val mapped = map(failure)
        val detail = if (upstream.isNotBlank() &&
            failure.kind in setOf(ApiErrorKind.HTTP, ApiErrorKind.UNKNOWN)) {
            // 只展示接口的 message/detail，不把整段 JSON 或网关 HTML 塞进通知。
            if (failure.kind == ApiErrorKind.UNKNOWN) upstream
            else "${mapped.detail}：$upstream"
        } else mapped.detail
        return mapped.copy(title = "图片识别失败", detail = detail)
    }

    private fun upstreamMessage(failure: ApiCallResult.Failure): String {
        val raw = failure.rawBody.orEmpty().trim()
        val root = runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
        val error = root?.get("error")
        fun JsonObject.message(): String? =
            (get("message") as? JsonPrimitive)?.takeIf { it.isString }?.content
                ?: (get("detail") as? JsonPrimitive)?.takeIf { it.isString }?.content
        val message = when {
            error is JsonObject -> error.message()
            error is JsonPrimitive && error.isString -> error.content
            else -> root?.message()
        } ?: failure.message.takeIf { it.isNotBlank() }
            ?: raw.takeIf { !it.startsWith("{") && !it.startsWith("[") && !it.startsWith("<") }
            .orEmpty()
        // 通知展示长度限制；不要展示接口回显的整幅图片。
        return message.replace(Regex("data:image/[^\\s\"']+", RegexOption.IGNORE_CASE), "[图片数据]")
            .replace(Regex("\\s+"), " ").trim().take(240)
    }

    private fun explicitlyRejectsImages(message: String): Boolean {
        val text = message.lowercase()
        // 图片格式、大小或分辨率限制不等于模型不具备视觉能力。
        if (Regex("image.{0,30}(?:format|type|size|resolution|dimension|generation|larger|smaller)|type of image|image/(?:png|jpeg|webp)|图片格式|图像格式|图片大小|图片尺寸|图片生成|分辨率|图片.{0,12}(?:超过|大于|小于)")
                .containsMatchIn(text)) return false
        return listOf(
            "(?:model|模型).{0,80}(?:does not support|doesn't support|cannot (?:accept|process)|不支持|不具备).{0,40}(?:images?|vision|multimodal|图片|图像|视觉|多模态)",
            "(?:images?|image_url|vision|multimodal|图片输入|图像输入|多模态).{0,40}(?:not supported|unsupported|不支持).{0,40}(?:model|模型)",
            "(?:image inputs?|vision|multimodal input) (?:is |are )?(?:not supported|unsupported)",
            "image_url.{0,30}only supported by.{0,30}models",
            "model.{0,40}(?:only supports? text|is (?:a )?text-only)",
            "模型.{0,30}(?:仅支持文本|只支持文本|是纯文本模型)"
        ).any { Regex(it).containsMatchIn(text) }
    }

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
            429 -> if (Regex("insufficient[_ ]quota|exceeded.{0,30}quota|insufficient.{0,15}(?:credit|balance)|额度不足|余额不足", RegexOption.IGNORE_CASE)
                    .containsMatchIn(failure.rawBody.orEmpty() + failure.message)) "额度不足(429)" else "请求过多(429)"
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
