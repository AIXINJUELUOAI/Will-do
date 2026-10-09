package com.antgskds.calendarassistant.feature.linkanalysis.domain

import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog as Limits
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.IDN
import java.net.URI

object LinkSourceProtocol {
    const val VERSION = 1
    val json = Json { ignoreUnknownKeys = false; encodeDefaults = true }

    fun host(url: String): String {
        val uri = URI(url)
        require(uri.scheme?.lowercase() in setOf("http", "https") && uri.rawUserInfo == null)
        require(uri.port in setOf(-1, 80, 443))
        return IDN.toASCII(requireNotNull(uri.host)).lowercase().trimEnd('.')
    }
    fun validHostRule(rule: String): Boolean {
        val host = rule.removePrefix("*.")
        return runCatching {
            host == IDN.toASCII(host).lowercase() && host.contains('.') &&
                host.split('.').all { it.isNotEmpty() && it.matches(Regex("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?")) } &&
                !host.substringAfterLast('.').all(Char::isDigit)
        }.getOrDefault(false)
    }
    fun matchesHost(rule: String, host: String): Boolean =
        if (rule.startsWith("*.")) host.endsWith("." + rule.removePrefix("*."))
        else rule == host

    fun validate(manifest: LinkSourceManifest) {
        require(manifest.protocolVersion == VERSION) { "不支持的源协议版本" }
        require(manifest.id.matches(Regex("[a-z][a-z0-9_.-]{2,63}"))) { "无效的源 ID" }
        require(manifest.name.isNotBlank() && manifest.name.length <= 80 && manifest.version.isNotBlank() && manifest.version.length <= 80)
        require(portablePath(manifest.entry) && manifest.entry.endsWith(".js")) { "无效的入口路径" }
        require(manifest.matches.isNotEmpty() && manifest.matches.size <= Limits.LINK_SOURCE_MAX_FILES)
        require(manifest.permissions.networkHosts.isNotEmpty() && manifest.permissions.networkHosts.size <= Limits.LINK_SOURCE_MAX_FILES)
        require(manifest.permissions.networkHosts.all(::validHostRule)) { "网络权限必须使用完整域名或 *.域名" }
        require(manifest.permissions.replay.size <= Limits.LINK_HTTP_MAX_CALLS &&
            manifest.permissions.replay.all { it.length in 1..200 && !it.contains('\r') && !it.contains('\n') }) { "回放地址模式无效" }
        require(manifest.loginEntries.size <= Limits.LINK_LOGIN_MAX_ENTRIES &&
            manifest.loginEntries.all { LinkAnalysisPolicy.allowsLogin(manifest, it) } &&
            manifest.loginEntries.map { it.url }.distinct().size == manifest.loginEntries.size) {
            "登录入口须有有效名称、HTTPS 网址、浏览器权限及对应域名权限，且不能重复"
        }
        require(manifest.matches.all { validHostRule(it.host) && it.pathPrefix.startsWith("/") &&
            manifest.permissions.networkHosts.any { permission -> permission == it.host || matchesHost(permission, it.host.removePrefix("*.")) } })
    }
    fun portablePath(path: String): Boolean = path.isNotBlank() && !path.startsWith("/") &&
        !path.contains('\\') && path.split('/').all { it.isNotBlank() && it !in setOf(".", "..") && it.matches(Regex("[a-zA-Z0-9_.-]+")) }

    fun validate(result: LinkExtractionResult, requestId: String) {
        require(result.protocolVersion == VERSION && result.requestId == requestId) { "源返回了不匹配的任务结果" }
        require(result.status in setOf("ok", "partial", "error"))
        if (result.status == "error") return
        require(result.source != null && runCatching { host(result.source.url) }.isSuccess)
        require(result.contentType in setOf("article", "image_post", "video", "mixed", "unknown"))
        require(result.body.kind in setOf("full", "excerpt", "description", "none") && result.body.format == "plain")
        require(result.body.text.length <= Limits.LINK_TEXT_MAX_CHARS)
        require(result.title.length <= 512 && result.author.length <= 512)
        require(result.media.size <= Limits.LINK_MAX_MEDIA && result.media.map { it.order }.distinct().size == result.media.size)
        require(result.warnings.size <= Limits.LINK_MAX_MEDIA && result.warnings.all { it.length <= 512 })
        require(result.media.all { it.type in setOf("image", "audio", "video") &&
            it.role in setOf("content_image", "cover", "speech_audio", "background_music", "video") &&
            it.urls.isNotEmpty() && it.urls.size <= Limits.LINK_MAX_REDIRECTS && it.urls.all { url -> runCatching { host(url) }.isSuccess } &&
            it.order >= 0 && it.group.length <= 80 && it.headers.size <= Limits.LINK_MAX_MEDIA &&
            it.headers.all { (k,v) -> k.matches(Regex("[a-zA-Z0-9-]+")) && !v.contains('\r') && !v.contains('\n') && v.length <= Limits.LINK_RESULT_MAX_BYTES } })
    }
}
@Serializable data class LinkSourceManifest(
    val protocolVersion: Int, val id: String, val name: String, val version: String,
    val entry: String = "main.js", val matches: List<LinkSourceMatch>, val permissions: LinkSourcePermissions,
    /** 可选交互式网页登录入口；旧源默认没有入口，不从平台域名推导。 */
    val loginEntries: List<LinkSourceLoginEntry> = emptyList(),
)
@Serializable data class LinkSourceLoginEntry(val name: String, val url: String, val userAgent: String? = null)
@Serializable data class LinkSourceMatch(val host: String, val pathPrefix: String = "/")
@Serializable data class LinkSourcePermissions(
    val networkHosts: List<String>, val browser: Boolean = false,
    /** 网页请求 URL 子串；命中的请求由宿主带 Cookie 重放并把响应注入 window.__willdoCapture（用于 Service Worker/Worker 发出的接口）。 */
    val replay: List<String> = emptyList(),
)
@Serializable data class LinkSourcePackage(val manifest: LinkSourceManifest, val files: Map<String,String>, val digest: String)
@Serializable data class LinkSourceInput(val protocolVersion: Int = 1, val requestId: String, val url: String, val shareText: String = "")
@Serializable data class LinkResultSource(val url: String, val canonicalUrl: String = "", val contentId: String = "")
@Serializable data class LinkResultBody(val kind: String = "none", val format: String = "plain", val text: String = "")
@Serializable data class LinkResultMedia(
    val type: String, val role: String, val urls: List<String>, val headers: Map<String,String> = emptyMap(),
    val mimeType: String = "", val durationMs: Long? = null, val order: Int, val group: String = "",
)
@Serializable data class LinkExtractionResult(
    val protocolVersion: Int = 1, val requestId: String, val status: String,
    val source: LinkResultSource? = null, val title: String = "", val author: String = "",
    val contentType: String = "unknown", val body: LinkResultBody = LinkResultBody(),
    val media: List<LinkResultMedia> = emptyList(), val warnings: List<String> = emptyList(),
    val error: LinkSourceError? = null,
)
@Serializable data class LinkSourceError(val code: String, val message: String)
@Serializable data class LinkSummaryData(
    val title: String = "", val author: String = "", val contentType: String = "",
    val extractedText: String = "", val summary: String = "", val warnings: List<String> = emptyList(),
    val sourceId: String = "", val sourceVersion: String = "", val completedAt: Long = 0,
    // title 保留解析源标题；自动标题仅用于随口记标题保护，不影响旧备份反序列化。
    val aiTitle: String = "", val autoTitle: String = "",
    val transcript: String = "", val bodyTranscript: String = "",
)
