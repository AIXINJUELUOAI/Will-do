package com.antgskds.calendarassistant.feature.cloudsync.data

import com.antgskds.calendarassistant.feature.cloudsync.domain.WebDavCapabilities
import com.antgskds.calendarassistant.feature.cloudsync.domain.WebDavConnectionConfig
import com.antgskds.calendarassistant.feature.cloudsync.domain.WebDavConnectionTestResult
import com.antgskds.calendarassistant.feature.cloudsync.domain.WebDavCredentials
import com.antgskds.calendarassistant.feature.cloudsync.domain.WebDavRemoteStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.util.Base64
import java.util.UUID

class KtorWebDavRemoteStore(
    private val client: HttpClient,
    private val testTokenFactory: () -> String = { UUID.randomUUID().toString() },
) : WebDavRemoteStore {

    override suspend fun testConnection(
        config: WebDavConnectionConfig,
        credentials: WebDavCredentials,
    ): WebDavConnectionTestResult {
        return runCatching {
            val resolver = WebDavUrlResolver(config.baseUrl, config.remotePath)
            val normalizedCredentials = credentials.copy(username = credentials.username.trim())
            ensureRemoteDirectories(resolver, normalizedCredentials)

            val testId = testTokenFactory()
            val temporaryUrl = resolver.childUrl(".willdo-connection-$testId.tmp")
            val finalUrl = resolver.childUrl(".willdo-connection-$testId.ready")
            val payload = "WillDo WebDAV connection test $testId"
            var moveSupported = false
            try {
                requireSuccess(
                    response = execute(HttpMethod.Put, temporaryUrl, normalizedCredentials, payload),
                    operation = "写入测试文件",
                ).bodyAsText()
                verifyFile(temporaryUrl, normalizedCredentials, payload)

                val moveResponse = execute(
                    method = MOVE,
                    url = temporaryUrl,
                    credentials = normalizedCredentials,
                    extraHeaders = mapOf(
                        DESTINATION to finalUrl,
                        OVERWRITE to "F",
                    ),
                )
                moveSupported = moveResponse.status.isSuccessCode()
                moveResponse.bodyAsText()
                if (moveSupported) {
                    verifyFile(finalUrl, normalizedCredentials, payload)
                } else {
                    deleteQuietly(temporaryUrl, normalizedCredentials)
                    requireSuccess(
                        response = execute(HttpMethod.Put, finalUrl, normalizedCredentials, payload),
                        operation = "写入兼容测试文件",
                    ).bodyAsText()
                    verifyFile(finalUrl, normalizedCredentials, payload)
                }
            } finally {
                deleteQuietly(temporaryUrl, normalizedCredentials)
                deleteQuietly(finalUrl, normalizedCredentials)
            }

            val options = execute(HttpMethod.Options, resolver.remoteRootUrl(), normalizedCredentials)
            val davHeader = options.headers["DAV"].orEmpty()
            options.bodyAsText()
            WebDavConnectionTestResult(
                success = true,
                message = if (moveSupported) "连接成功，支持原子重命名" else "连接成功，将使用兼容上传模式",
                capabilities = WebDavCapabilities(moveSupported = moveSupported, davHeader = davHeader),
            )
        }.getOrElse { error ->
            WebDavConnectionTestResult(
                success = false,
                message = error.message?.takeIf { it.isNotBlank() } ?: "WebDAV 连接失败",
            )
        }
    }

    private suspend fun ensureRemoteDirectories(
        resolver: WebDavUrlResolver,
        credentials: WebDavCredentials,
    ) {
        resolver.remoteDirectoryUrls().forEach { url ->
            val probe = execute(
                method = PROPFIND,
                url = url,
                credentials = credentials,
                body = PROPFIND_BODY,
                extraHeaders = mapOf(DEPTH to "0"),
                contentType = ContentType.Application.Xml,
            )
            when {
                probe.status == HttpStatusCode.NotFound -> {
                    val create = execute(MKCOL, url, credentials)
                    if (create.status != HttpStatusCode.MethodNotAllowed && !create.status.isSuccessCode()) {
                        throw WebDavException("创建远程目录失败：HTTP ${create.status.value}")
                    }
                    create.bodyAsText()
                }
                probe.status == HttpStatusCode.Unauthorized -> throw WebDavException("WebDAV 账号或密码错误")
                probe.status == HttpStatusCode.Forbidden -> throw WebDavException("WebDAV 目录没有访问权限")
                probe.status != MULTI_STATUS && !probe.status.isSuccessCode() -> {
                    throw WebDavException("检查远程目录失败：HTTP ${probe.status.value}")
                }
            }
            probe.bodyAsText()
        }
    }

    private suspend fun verifyFile(
        url: String,
        credentials: WebDavCredentials,
        expected: String,
    ) {
        val response = execute(HttpMethod.Get, url, credentials)
        requireSuccess(response, "读取测试文件")
        if (response.bodyAsText() != expected) {
            throw WebDavException("WebDAV 文件校验失败")
        }
    }

    private suspend fun deleteQuietly(url: String, credentials: WebDavCredentials) {
        runCatching { execute(HttpMethod.Delete, url, credentials).bodyAsText() }
    }

    private suspend fun execute(
        method: HttpMethod,
        url: String,
        credentials: WebDavCredentials,
        body: String? = null,
        extraHeaders: Map<String, String> = emptyMap(),
        contentType: ContentType? = null,
    ) = client.request(url) {
        this.method = method
        if (credentials.username.isNotBlank() || credentials.password.isNotBlank()) {
            val token = Base64.getEncoder().encodeToString(
                "${credentials.username}:${credentials.password}".toByteArray(Charsets.UTF_8)
            )
            header(HttpHeaders.Authorization, "Basic $token")
        }
        extraHeaders.forEach { (name, value) -> header(name, value) }
        if (contentType != null) contentType(contentType)
        if (body != null) setBody(body)
    }

    private fun requireSuccess(
        response: io.ktor.client.statement.HttpResponse,
        operation: String,
    ): io.ktor.client.statement.HttpResponse {
        when (response.status) {
            HttpStatusCode.Unauthorized -> throw WebDavException("WebDAV 账号或密码错误")
            HttpStatusCode.Forbidden -> throw WebDavException("WebDAV 目录没有写入权限")
            else -> if (!response.status.isSuccessCode()) {
                throw WebDavException("$operation 失败：HTTP ${response.status.value}")
            }
        }
        return response
    }

    companion object {
        private val PROPFIND = HttpMethod("PROPFIND")
        private val MKCOL = HttpMethod("MKCOL")
        private val MOVE = HttpMethod("MOVE")
        private val MULTI_STATUS = HttpStatusCode(207, "Multi-Status")
        private const val DEPTH = "Depth"
        private const val DESTINATION = "Destination"
        private const val OVERWRITE = "Overwrite"
        private const val PROPFIND_BODY =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                "<d:propfind xmlns:d=\"DAV:\"><d:prop><d:resourcetype/></d:prop></d:propfind>"

        fun createAndroid(): KtorWebDavRemoteStore = KtorWebDavRemoteStore(
            HttpClient(Android) {
                install(HttpTimeout) {
                    connectTimeoutMillis = 15_000
                    requestTimeoutMillis = 30_000
                    socketTimeoutMillis = 30_000
                }
            }
        )
    }
}

private class WebDavException(message: String) : IllegalStateException(message)

private fun HttpStatusCode.isSuccessCode(): Boolean = value in 200..299
