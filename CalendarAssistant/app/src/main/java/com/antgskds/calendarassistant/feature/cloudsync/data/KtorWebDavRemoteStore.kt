package com.antgskds.calendarassistant.feature.cloudsync.data

import com.antgskds.calendarassistant.feature.cloudsync.domain.WebDavCapabilities
import com.antgskds.calendarassistant.feature.cloudsync.domain.WebDavConnectionConfig
import com.antgskds.calendarassistant.feature.cloudsync.domain.WebDavConnectionTestResult
import com.antgskds.calendarassistant.feature.cloudsync.domain.WebDavCredentials
import com.antgskds.calendarassistant.feature.cloudsync.domain.WebDavRemoteStore
import com.antgskds.calendarassistant.feature.cloudsync.domain.WebDavResource
import com.antgskds.calendarassistant.feature.cloudsync.domain.WebDavWriteResult
import io.ktor.client.call.body
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
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
import java.io.StringReader
import java.util.Base64
import java.util.UUID
import java.net.URI
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

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

    override suspend fun writeFileAtomically(
        config: WebDavConnectionConfig,
        credentials: WebDavCredentials,
        relativePath: String,
        content: ByteArray,
        contentType: String,
    ): WebDavWriteResult {
        val resolver = WebDavUrlResolver(config.baseUrl, config.remotePath)
        val normalizedCredentials = credentials.copy(username = credentials.username.trim())
        ensureRemoteDirectories(resolver, normalizedCredentials)
        val parentPath = relativePath.substringBeforeLast('/', missingDelimiterValue = "")
        if (parentPath.isNotEmpty()) {
            ensureDirectories(resolver.descendantDirectoryUrls(parentPath), normalizedCredentials)
        }

        val finalUrl = resolver.descendantUrl(relativePath)
        val temporaryUrl = resolver.descendantUrl("$relativePath.tmp")
        requireSuccess(
            response = execute(
                method = HttpMethod.Put,
                url = temporaryUrl,
                credentials = normalizedCredentials,
                body = content,
                contentType = ContentType.parse(contentType),
            ),
            operation = "上传同步临时文件",
        ).bodyAsText()

        val moveResponse = execute(
            method = MOVE,
            url = temporaryUrl,
            credentials = normalizedCredentials,
            extraHeaders = mapOf(DESTINATION to finalUrl, OVERWRITE to "T"),
        )
        val moveSupported = moveResponse.status.isSuccessCode()
        moveResponse.bodyAsText()
        if (!moveSupported) {
            deleteQuietly(temporaryUrl, normalizedCredentials)
            requireSuccess(
                response = execute(
                    method = HttpMethod.Put,
                    url = finalUrl,
                    credentials = normalizedCredentials,
                    body = content,
                    contentType = ContentType.parse(contentType),
                ),
                operation = "上传同步文件",
            ).bodyAsText()
        }
        verifyFile(finalUrl, normalizedCredentials, content)
        return WebDavWriteResult(
            moveSupported = moveSupported,
        )
    }

    override suspend fun readFile(
        config: WebDavConnectionConfig,
        credentials: WebDavCredentials,
        relativePath: String,
    ): ByteArray? {
        val resolver = WebDavUrlResolver(config.baseUrl, config.remotePath)
        val response = execute(HttpMethod.Get, resolver.descendantUrl(relativePath), credentials.normalized())
        if (response.status == HttpStatusCode.NotFound) {
            response.bodyAsText()
            return null
        }
        requireSuccess(response, "读取同步文件")
        return response.body()
    }

    override suspend fun listDirectory(
        config: WebDavConnectionConfig,
        credentials: WebDavCredentials,
        relativeDirectory: String,
    ): List<WebDavResource> {
        val resolver = WebDavUrlResolver(config.baseUrl, config.remotePath)
        ensureRemoteDirectories(resolver, credentials.normalized())
        if (relativeDirectory.isNotBlank()) {
            ensureDirectories(resolver.descendantDirectoryUrls(relativeDirectory), credentials.normalized())
        }
        val directoryUrl = if (relativeDirectory.isBlank()) {
            resolver.remoteRootUrl()
        } else {
            resolver.descendantUrl(relativeDirectory)
        }
        val response = execute(
            method = PROPFIND,
            url = directoryUrl,
            credentials = credentials.normalized(),
            body = PROPFIND_LIST_BODY,
            extraHeaders = mapOf(DEPTH to "1"),
            contentType = ContentType.Application.Xml,
        )
        if (response.status == HttpStatusCode.NotFound) {
            response.bodyAsText()
            return emptyList()
        }
        if (response.status != MULTI_STATUS && !response.status.isSuccessCode()) {
            requireSuccess(response, "列出同步目录")
        }
        return parseDirectoryListing(response.bodyAsText(), directoryUrl, relativeDirectory)
    }

    override suspend fun deleteFile(
        config: WebDavConnectionConfig,
        credentials: WebDavCredentials,
        relativePath: String,
    ): Boolean {
        val resolver = WebDavUrlResolver(config.baseUrl, config.remotePath)
        val response = execute(HttpMethod.Delete, resolver.descendantUrl(relativePath), credentials.normalized())
        val deleted = response.status.isSuccessCode() || response.status == HttpStatusCode.NotFound
        response.bodyAsText()
        if (!deleted) requireSuccess(response, "删除同步文件")
        return deleted
    }

    override suspend fun createFileIfAbsent(
        config: WebDavConnectionConfig,
        credentials: WebDavCredentials,
        relativePath: String,
        content: ByteArray,
        contentType: String,
    ): Boolean {
        val resolver = WebDavUrlResolver(config.baseUrl, config.remotePath)
        val normalizedCredentials = credentials.normalized()
        ensureRemoteDirectories(resolver, normalizedCredentials)
        val parentPath = relativePath.substringBeforeLast('/', missingDelimiterValue = "")
        if (parentPath.isNotEmpty()) ensureDirectories(resolver.descendantDirectoryUrls(parentPath), normalizedCredentials)
        val response = execute(
            method = HttpMethod.Put,
            url = resolver.descendantUrl(relativePath),
            credentials = normalizedCredentials,
            body = content,
            extraHeaders = mapOf(HttpHeaders.IfNoneMatch to "*"),
            contentType = ContentType.parse(contentType),
        )
        val created = response.status.isSuccessCode()
        val alreadyExists = response.status == HttpStatusCode.PreconditionFailed || response.status == HttpStatusCode.Conflict
        response.bodyAsText()
        if (!created && !alreadyExists) requireSuccess(response, "创建同步文件")
        return created
    }

    override suspend fun writeFileIfAbsentAtomically(
        config: WebDavConnectionConfig,
        credentials: WebDavCredentials,
        relativePath: String,
        content: ByteArray,
        contentType: String,
    ): Boolean {
        val resolver = WebDavUrlResolver(config.baseUrl, config.remotePath)
        val normalizedCredentials = credentials.normalized()
        ensureRemoteDirectories(resolver, normalizedCredentials)
        val parentPath = relativePath.substringBeforeLast('/', missingDelimiterValue = "")
        if (parentPath.isNotEmpty()) ensureDirectories(resolver.descendantDirectoryUrls(parentPath), normalizedCredentials)
        val finalUrl = resolver.descendantUrl(relativePath)
        val temporaryPath = "$relativePath.${UUID.randomUUID()}.tmp"
        val temporaryUrl = resolver.descendantUrl(temporaryPath)
        requireSuccess(
            response = execute(
                method = HttpMethod.Put,
                url = temporaryUrl,
                credentials = normalizedCredentials,
                body = content,
                contentType = ContentType.parse(contentType),
            ),
            operation = "上传不可变资源临时文件",
        ).bodyAsText()
        try {
            val moveResponse = execute(
                method = MOVE,
                url = temporaryUrl,
                credentials = normalizedCredentials,
                extraHeaders = mapOf(DESTINATION to finalUrl, OVERWRITE to "F"),
            )
            val status = moveResponse.status
            moveResponse.bodyAsText()
            when {
                status.isSuccessCode() -> {
                    verifyFile(finalUrl, normalizedCredentials, content)
                    return true
                }
                status == HttpStatusCode.PreconditionFailed || status == HttpStatusCode.Conflict -> {
                    val existing = readFile(config, normalizedCredentials, relativePath)
                        ?: throw WebDavException("不可变资源并发发布后不存在")
                    if (!existing.contentEquals(content)) {
                        // AES-GCM nonce may differ for the same plaintext. The caller performs semantic verification.
                    }
                    return false
                }
                status == HttpStatusCode.MethodNotAllowed || status == HttpStatusCode.NotImplemented -> {
                    val created = createFileIfAbsent(config, normalizedCredentials, relativePath, content, contentType)
                    return created
                }
                else -> throw WebDavException("发布不可变资源失败：HTTP ${status.value}")
            }
        } finally {
            deleteQuietly(temporaryUrl, normalizedCredentials)
        }
    }

    private suspend fun ensureRemoteDirectories(
        resolver: WebDavUrlResolver,
        credentials: WebDavCredentials,
    ) {
        ensureDirectories(resolver.remoteDirectoryUrls(), credentials)
    }

    private suspend fun ensureDirectories(
        urls: List<String>,
        credentials: WebDavCredentials,
    ) {
        urls.forEach { url ->
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

    private suspend fun verifyFile(
        url: String,
        credentials: WebDavCredentials,
        expected: ByteArray,
    ) {
        val response = execute(HttpMethod.Get, url, credentials)
        requireSuccess(response, "读取同步文件")
        if (!response.body<ByteArray>().contentEquals(expected)) {
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
        body: Any? = null,
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
        private const val PROPFIND_LIST_BODY =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                "<d:propfind xmlns:d=\"DAV:\"><d:prop>" +
                "<d:resourcetype/><d:getetag/><d:getcontentlength/><d:getlastmodified/>" +
                "</d:prop></d:propfind>"

        fun createAndroid(): KtorWebDavRemoteStore = KtorWebDavRemoteStore(
            HttpClient(CIO) {
                install(HttpTimeout) {
                    connectTimeoutMillis = 15_000
                    requestTimeoutMillis = 30_000
                    socketTimeoutMillis = 30_000
                }
            }
        )
    }

    private fun parseDirectoryListing(xml: String, directoryUrl: String, relativeDirectory: String): List<WebDavResource> {
        require(!xml.contains("<!DOCTYPE", ignoreCase = true) && !xml.contains("<!ENTITY", ignoreCase = true)) {
            "WebDAV 目录响应包含不安全的 XML 声明"
        }
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching { isXIncludeAware = false }
            runCatching { isExpandEntityReferences = false }
            runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
            runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "") }
            runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "") }
        }
        val document = factory.newDocumentBuilder().apply {
            setEntityResolver { _, _ -> InputSource(StringReader("")) }
        }.parse(InputSource(StringReader(xml)))
        val directoryPath = URI(directoryUrl).path.trimEnd('/')
        val responses = document.getElementsByTagNameNS("DAV:", "response")
        return buildList {
            for (index in 0 until responses.length) {
                val response = responses.item(index)
                val href = response.childNodes.asSequence()
                    .firstOrNull { it.localName == "href" }
                    ?.textContent
                    ?.trim()
                    .orEmpty()
                if (href.isBlank()) continue
                val path = URI("${directoryUrl.trimEnd('/')}/").resolve(href).path.trimEnd('/')
                if (path == directoryPath) continue
                val name = path.substringAfterLast('/')
                if (name.isBlank()) continue
                val resourceType = response.childNodes.deepFirst("resourcetype")
                val isDirectory = resourceType?.childNodes?.asSequence()?.any { it.localName == "collection" } == true
                val relativePath = listOf(relativeDirectory.trim('/'), name).filter { it.isNotBlank() }.joinToString("/")
                add(
                    WebDavResource(
                        name = name,
                        relativePath = relativePath,
                        isDirectory = isDirectory,
                        etag = response.childNodes.deepText("getetag"),
                        size = response.childNodes.deepText("getcontentlength").toLongOrNull() ?: 0L,
                        lastModified = response.childNodes.deepText("getlastmodified"),
                    )
                )
            }
        }
    }
}

private class WebDavException(message: String) : IllegalStateException(message)

private fun HttpStatusCode.isSuccessCode(): Boolean = value in 200..299

private fun WebDavCredentials.normalized(): WebDavCredentials = copy(username = username.trim())

private fun org.w3c.dom.NodeList.asSequence(): Sequence<org.w3c.dom.Node> = sequence {
    for (index in 0 until length) yield(item(index))
}

private fun org.w3c.dom.NodeList.deepFirst(localName: String): org.w3c.dom.Node? {
    asSequence().forEach { node ->
        if (node.localName == localName) return node
        node.childNodes.deepFirst(localName)?.let { return it }
    }
    return null
}

private fun org.w3c.dom.NodeList.deepText(localName: String): String = deepFirst(localName)?.textContent?.trim().orEmpty()
