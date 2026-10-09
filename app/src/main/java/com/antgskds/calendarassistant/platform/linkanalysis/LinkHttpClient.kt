package com.antgskds.calendarassistant.platform.linkanalysis
import com.antgskds.calendarassistant.feature.linkanalysis.domain.*
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog as Limits
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.io.File
import java.net.*
import java.util.concurrent.atomic.AtomicInteger
import com.antgskds.calendarassistant.shared.util.AppLogger as Log

class LinkHttpClient(private val manifest: LinkSourceManifest, private val traceId: String = "") {
    private val calls = AtomicInteger()
    fun checkUrl(url: String) {
        if (!LinkAnalysisPolicy.allows(manifest, url)) throw LinkAnalysisFailure(LinkFailureCode.HOST_REJECTED)
        val addresses = InetAddress.getAllByName(LinkSourceProtocol.host(url))
        if (addresses.isEmpty() || addresses.any {
            it.isAnyLocalAddress || it.isLoopbackAddress || it.isLinkLocalAddress || it.isSiteLocalAddress || it.isMulticastAddress ||
                (it.address.size == 4 && ((it.address[0].toInt() and 255) == 0 || (it.address[0].toInt() and 255) >= 224 ||
                    (it.address[0].toInt() and 255) == 100 && ((it.address[1].toInt() and 255) in 64..127))) ||
                (it.address.size == 16 && (it.address[0].toInt() and 254) == 252)
        }) throw LinkAnalysisFailure(LinkFailureCode.HOST_REJECTED)
    }
    private fun secure(url: String) = if (url.startsWith("http://", ignoreCase = true)) "https://" + url.substring(7) else url
    private suspend fun connect(url: String, headers: Map<String,String>, method: String, body: String?): HttpURLConnection {
        require(calls.incrementAndGet() <= Limits.LINK_HTTP_MAX_CALLS) { "源请求次数超过上限" }
        var current = url; var verb = method; var payload = body
        val originalHost = LinkSourceProtocol.host(url)
        repeat(Limits.LINK_MAX_REDIRECTS + 1) { redirect ->
            current = secure(current)
            currentCoroutineContext().ensureActive(); checkUrl(current)
            val connection = URL(current).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = Limits.LINK_HTTP_TIMEOUT_MS; connection.readTimeout = Limits.LINK_HTTP_TIMEOUT_MS
            connection.requestMethod = verb
            try {
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/130.0.0.0 Mobile Safari/537.36")
                connection.setRequestProperty("Accept-Encoding", "identity")
                headers.forEach { (key,value) ->
                    require(key.matches(Regex("[a-zA-Z0-9-]+")) && !value.contains('\n') && !value.contains('\r'))
                    require(key.lowercase() !in setOf("host", "connection", "content-length", "transfer-encoding"))
                    if (LinkSourceProtocol.host(current) == originalHost || key.lowercase() !in setOf("cookie", "authorization", "proxy-authorization"))
                        connection.setRequestProperty(key, value)
                }
                if (payload != null) {
                    require(payload!!.length <= Limits.LINK_HTTP_MAX_BYTES)
                    connection.doOutput = true
                    connection.outputStream.use { it.write(payload!!.toByteArray()) }
                }
                if (connection.responseCode !in setOf(301,302,303,307,308)) return connection
                require(redirect < Limits.LINK_MAX_REDIRECTS)
                val next = URL(URL(current), requireNotNull(connection.getHeaderField("Location"))).toString()
                if (connection.responseCode == 303 || connection.responseCode in setOf(301,302) && verb == "POST") { verb = "GET"; payload = null }
                current = next
            } catch (error: Throwable) { connection.disconnect(); throw error }
            connection.disconnect()
        }
        error("重定向次数过多")
    }
    suspend fun request(options: JsonObject): JsonObject = withContext(Dispatchers.IO) {
        val url = requireNotNull(options["url"]?.jsonPrimitive?.content)
        val method = options["method"]?.jsonPrimitive?.content?.uppercase() ?: "GET"
        require(method in setOf("GET","POST","HEAD"))
        val headers = options["headers"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content }.orEmpty()
        Log.i("LinkAnalysis","trace=$traceId stage=HTTP_START method=$method")
        try {
            val connection = connect(url, headers, method, options["body"]?.jsonPrimitive?.content)
            try {
                val status = connection.responseCode
                Log.i("LinkAnalysis","trace=$traceId stage=HTTP_HEADERS http_status=$status declared_bytes=" + connection.contentLengthLong)
                val stream = if (status >= 400) connection.errorStream else connection.inputStream
                val bytes = stream?.use { boundedRead(it, Limits.LINK_HTTP_MAX_BYTES) } ?: ByteArray(0)
                Log.i("LinkAnalysis","trace=$traceId stage=HTTP_DONE http_status=$status bytes=" + bytes.size)
                buildJsonObject {
                    put("url", connection.url.toString()); put("status", status); put("body", bytes.toString(Charsets.UTF_8))
                    putJsonObject("headers") { connection.headerFields.filterKeys { it != null }.forEach { (k,v) -> put(k, v.joinToString(",")) } }
                }
            } finally { connection.disconnect() }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            val failure = LinkAnalysisFailure.describe(error, "EXTRACTING")
            Log.w("LinkAnalysis","trace=$traceId stage=HTTP_FAILED code=" + failure.code.name)
            throw failure
        }
    }
    suspend fun download(url: String, headers: Map<String,String>, target: File): String = withContext(Dispatchers.IO) {
        val connection = connect(url, headers, "GET", null)
        try {
            if (connection.responseCode !in 200..299) {
                Log.w("LinkAnalysis","trace=$traceId stage=MEDIA_BLOCKED host=" + runCatching { LinkSourceProtocol.host(url) }.getOrDefault("?") + " status=" + connection.responseCode)
                throw LinkAnalysisFailure(LinkFailureCode.MEDIA_DOWNLOAD, connection.responseCode)
            }
            Log.i("LinkAnalysis","trace=$traceId stage=MEDIA_HEADERS http_status=" + connection.responseCode + " declared_bytes=" + connection.contentLengthLong)
            require(connection.contentLengthLong <= Limits.LINK_MEDIA_MAX_BYTES) { "素材超过大小上限" }
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(8192); var count = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer); if (read < 0) break
                        count += read; require(count <= Limits.LINK_MEDIA_MAX_BYTES)
                        output.write(buffer,0,read)
                    }
                    require(count > 0) { "素材为空" }
                }
            }
            Log.i("LinkAnalysis","trace=$traceId stage=MEDIA_DONE bytes=" + target.length())
            connection.contentType.orEmpty().substringBefore(';').lowercase()
        } catch (error: Throwable) { target.delete(); throw error }
        finally { connection.disconnect() }
    }
    internal suspend fun boundedRead(input: java.io.InputStream, max: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = input.read(buffer); if (read < 0) break
            if (output.size() + read > max) throw LinkAnalysisFailure(LinkFailureCode.HTTP_TOO_LARGE)
            output.write(buffer,0,read)
        }
        return output.toByteArray()
    }
}
