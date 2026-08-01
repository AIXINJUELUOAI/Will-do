package com.antgskds.calendarassistant.feature.cloudsync.data

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal class WebDavUrlResolver(
    baseUrl: String,
    remotePath: String,
) {
    private val baseUri = parseBaseUri(baseUrl)
    private val remoteSegments = parseRemoteSegments(remotePath)

    val normalizedBaseUrl: String = baseUri.toASCIIString().trimEnd('/') + "/"
    val normalizedRemotePath: String = remoteSegments.joinToString("/", prefix = "/")

    fun remoteDirectoryUrls(): List<String> = remoteSegments.indices.map { index ->
        buildUrl(remoteSegments.take(index + 1))
    }

    fun remoteRootUrl(): String = buildUrl(remoteSegments)

    fun childUrl(fileName: String): String {
        require(fileName.isNotBlank()) { "文件名不能为空" }
        require('/' !in fileName && '\\' !in fileName) { "文件名不能包含路径分隔符" }
        require(fileName != "." && fileName != "..") { "文件名不合法" }
        return buildUrl(remoteSegments + fileName)
    }

    fun descendantUrl(relativePath: String): String = buildUrl(remoteSegments + parseRelativeSegments(relativePath))

    fun descendantDirectoryUrls(relativeDirectory: String): List<String> {
        val segments = parseRelativeSegments(relativeDirectory)
        return segments.indices.map { index -> buildUrl(remoteSegments + segments.take(index + 1)) }
    }

    private fun buildUrl(segments: List<String>): String {
        val basePath = baseUri.rawPath.orEmpty().trimEnd('/')
        val suffix = segments.joinToString("/") { encodePathSegment(it) }
        val path = when {
            basePath.isBlank() && suffix.isBlank() -> "/"
            suffix.isBlank() -> if (basePath.startsWith('/')) basePath else "/$basePath"
            basePath.isBlank() -> "/$suffix"
            else -> "${if (basePath.startsWith('/')) basePath else "/$basePath"}/$suffix"
        }
        return "${baseUri.scheme}://${baseUri.rawAuthority}$path"
    }

    private fun parseBaseUri(raw: String): URI {
        val value = raw.trim()
        require(value.isNotEmpty()) { "WebDAV 地址不能为空" }
        val uri = runCatching { URI(value) }.getOrElse { error("WebDAV 地址格式不正确") }
        require(uri.scheme.equals("https", true) || uri.scheme.equals("http", true)) {
            "WebDAV 地址仅支持 HTTP 或 HTTPS"
        }
        require(!uri.rawAuthority.isNullOrBlank()) { "WebDAV 地址缺少服务器域名" }
        require(uri.rawQuery == null && uri.rawFragment == null) { "WebDAV 地址不能包含查询参数或片段" }
        return uri
    }

    private fun parseRemoteSegments(raw: String): List<String> {
        val segments = raw.trim().replace('\\', '/').split('/').filter { it.isNotBlank() }
        require(segments.isNotEmpty()) { "远程目录不能为空" }
        require(segments.none { it == "." || it == ".." }) { "远程目录不能包含 . 或 .." }
        return segments
    }

    private fun parseRelativeSegments(raw: String): List<String> {
        val value = raw.trim().replace('\\', '/')
        require(value.isNotEmpty() && !value.startsWith('/')) { "相对路径不能为空或以 / 开头" }
        val segments = value.split('/').filter { it.isNotBlank() }
        require(segments.isNotEmpty()) { "相对路径不能为空" }
        require(segments.none { it == "." || it == ".." }) { "相对路径不能包含 . 或 .." }
        return segments
    }

    private fun encodePathSegment(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
}
