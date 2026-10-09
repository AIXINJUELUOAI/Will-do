package com.antgskds.calendarassistant.feature.linkanalysis.domain

import kotlinx.serialization.SerializationException
import java.net.*
import javax.net.ssl.SSLException

/** Only fixed codes and fixed messages may cross from an imported source to logs/UI. */
enum class LinkFailureCode(val userMessage: String) {
    HTTP_TOO_LARGE("网页响应超过 8 MiB，无法完整提取"),
    HTTP_FAILED("网页请求失败，请重新分享链接后重试"),
    NETWORK_DNS("无法解析网站地址，请检查网络后重试"),
    NETWORK_CONNECT("无法连接网站，请检查网络后重试"),
    NETWORK_TIMEOUT("网络请求超时，可重试"),
    NETWORK_TLS("网站证书验证失败"),
    HOST_REJECTED("解析源访问了未授权或不支持的地址"),
    NO_ARTICLE("未获取正文，链接可能失效或访问受限，请重新分享"),
    LINK_UNAVAILABLE("未获取正文，链接可能失效或访问受限，请重新分享"),
    ACCESS_REQUIRED("页面需要登录或验证，暂无法提取"),
    NO_TARGET("链接未指向可识别的内容，请重新分享"),
    TARGET_MISMATCH("未取得链接对应的目标内容"),
    NO_CONTENT("未取得可用于摘要的正文或素材"),
    NO_AUDIO("未取得完整音轨，请更新解析源后重试"),
    NO_VIDEO("未取得可处理的视频文件"),
    INVALID_PAGE("链接指定的视频分 P 不存在或参数无效"),
    INVALID_RESPONSE("网站未返回可识别的数据"),
    UNSUPPORTED_PLATFORM("此解析源未适配该平台"),
    SOURCE_INVALID("解析源返回的数据不符合协议"),
    BROWSER_UNAVAILABLE("请更新 Android System WebView 后重试"),
    BROWSER_LOAD("网页加载失败，请检查网络或更新解析源"),
    BROWSER_TIMEOUT("网页未在等待时间内提供目标内容，可能需要登录、验证或更新解析源"),
    SCRIPT_MEMORY("解析脚本内存不足，请更新源或减少页面数据"),
    SCRIPT_EXECUTION("解析脚本执行失败，请更新源后重试"),
    EXTRACTION_FAILED("内容提取失败，请检查网络或更新源后重试"),
    MEDIA_DOWNLOAD("素材下载失败，地址可能失效，请重新分析"),
    MEDIA_INVALID("素材处理失败，请检查音频模式和本地模型"),
    AI_CONFIG("请先配置在线 AI 模型"),
    AI_HTTP("AI 请求失败，请确认模型支持所选素材"),
    AI_NETWORK("AI 请求失败，请检查网络后重试"),
    AI_EMPTY("AI 未返回有效摘要"),
    TASK_TIMEOUT("处理超时，可重试"),
    SAVE_FAILED("摘要保存失败，可重试"),
    UNKNOWN("处理失败，可重试");

    companion object {
        fun fromSource(value: String?) = entries.firstOrNull { it.name == value } ?: EXTRACTION_FAILED
    }
}

class LinkAnalysisFailure(
    val code: LinkFailureCode,
    val statusCode: Int? = null,
    cause: Throwable? = null,
) : Exception(code.userMessage, cause) {
    companion object {
        /** Exception messages from scripts/HTTP may contain credentials or extracted content. */
        fun safeDetail(error: Throwable?): String = when (error) {
            is LinkAnalysisFailure -> "code=${error.code.name} http_status=${error.statusCode ?: 0}"
            null -> ""
            else -> "error_type=${error.javaClass.simpleName}"
        }

        fun describe(error: Throwable, stage: String): LinkAnalysisFailure {
            var current: Throwable? = error
            val seen = mutableSetOf<Throwable>()
            while (current != null && seen.add(current)) {
                val item = current
                if (item is LinkAnalysisFailure) return item
                val code = when (item) {
                    is UnknownHostException -> LinkFailureCode.NETWORK_DNS
                    is SocketTimeoutException -> LinkFailureCode.NETWORK_TIMEOUT
                    is SSLException -> LinkFailureCode.NETWORK_TLS
                    is SocketException -> LinkFailureCode.NETWORK_CONNECT
                    is SerializationException -> LinkFailureCode.SOURCE_INVALID
                    else -> null
                }
                if (code != null) return LinkAnalysisFailure(code, cause = error)
                current = item.cause
            }
            val code = when {
                error.javaClass.name.startsWith("com.dokar.quickjs.") &&
                    error.message.orEmpty().contains("out of memory", ignoreCase = true) -> LinkFailureCode.SCRIPT_MEMORY
                error.javaClass.name.startsWith("com.dokar.quickjs.") -> LinkFailureCode.SCRIPT_EXECUTION
                stage == "EXTRACTING" -> LinkFailureCode.EXTRACTION_FAILED
                stage in setOf("PREPARING", "TRANSCRIBING") -> LinkFailureCode.MEDIA_INVALID
                stage == "SUMMARIZING" -> LinkFailureCode.AI_NETWORK
                stage == "SAVING" -> LinkFailureCode.SAVE_FAILED
                else -> LinkFailureCode.UNKNOWN
            }
            return LinkAnalysisFailure(code, cause = error)
        }
    }
}
