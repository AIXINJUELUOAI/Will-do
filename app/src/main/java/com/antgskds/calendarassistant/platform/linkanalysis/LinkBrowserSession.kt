package com.antgskds.calendarassistant.platform.linkanalysis
import android.content.Context
import android.webkit.*
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.antgskds.calendarassistant.feature.linkanalysis.domain.*
import com.antgskds.calendarassistant.shared.util.AppLogger as Log
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog as Limits
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/** Disposable browser; remote pages receive no application bridge. */
class LinkBrowserSession(private val context: Context, private val manifest: LinkSourceManifest, private val http: LinkHttpClient, private val traceId: String = "") {
    private var view: WebView? = null
    private var failure: LinkAnalysisFailure? = null
    private val blockedRequests = AtomicInteger()
    private val blockedHosts: MutableSet<String> = ConcurrentHashMap.newKeySet()
    // Requests the page wrapper cannot observe (Service Worker/Worker) are captured by URL pattern and replayed by the host.
    private val replayPatterns = manifest.permissions.replay
    private val replayQueue = ConcurrentLinkedQueue<String>()
    private val replaySeen: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private var openedUrl: String = ""
    suspend fun open(options: JsonObject): JsonObject {
        require(manifest.permissions.browser) { "源没有浏览器权限" }
        val url = options["url"]?.jsonPrimitive?.content ?: error("缺少页面 URL")
        val userAgent = LinkAnalysisPolicy.browserUserAgent(options["userAgent"]?.jsonPrimitive?.content)
        withContext(Dispatchers.IO) { http.checkUrl(url) }
        return withContext(Dispatchers.Main) {
            closeOnMain()
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) throw LinkAnalysisFailure(LinkFailureCode.BROWSER_UNAVAILABLE)
            failure = null; blockedRequests.set(0); blockedHosts.clear(); openedUrl = url
            Log.i("LinkAnalysis", "trace=" + traceId + " stage=BROWSER_OPEN custom_ua=" + (userAgent != null))
            val browser = WebView(context); view = browser
            browser.settings.apply {
                javaScriptEnabled = true; domStorageEnabled = true
                if (userAgent != null) userAgentString = userAgent
                allowFileAccess = false; allowContentAccess = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                cacheMode = WebSettings.LOAD_NO_CACHE
                mediaPlaybackRequiresUserGesture = true; setSupportMultipleWindows(false)
            }
            CookieManager.getInstance().setAcceptThirdPartyCookies(browser, false)
            browser.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    !com.antgskds.calendarassistant.feature.linkanalysis.domain.LinkAnalysisPolicy.allows(manifest, request.url.toString())
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    captureReplayable(request)
                    return try { http.checkUrl(request.url.toString()); null } catch (_: Exception) {
                        blockedRequests.incrementAndGet()
                        if (blockedHosts.size < 40) runCatching { LinkSourceProtocol.host(request.url.toString()) }.getOrNull()?.let(blockedHosts::add)
                        WebResourceResponse("text/plain","utf-8",403,"Blocked",emptyMap(),ByteArrayInputStream(ByteArray(0)))
                    }
                }
                override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: android.net.http.SslError) {
                    handler.cancel(); failure = LinkAnalysisFailure(LinkFailureCode.NETWORK_TLS)
                }
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) failure = LinkAnalysisFailure(LinkFailureCode.BROWSER_LOAD)
                }
                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    failure = LinkAnalysisFailure(LinkFailureCode.BROWSER_LOAD); closeOnMain(); return true
                }
            }
            browser.webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
                override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) { callback.invoke(origin, false, false) }
            }
            WebViewCompat.addDocumentStartJavaScript(browser, captureScript(), setOf("*"))
            browser.loadUrl(url)
            buildJsonObject { put("opened", true) }
        }
    }
    suspend fun evaluate(expression: String): JsonElement = withContext(Dispatchers.Main) {
        failure?.let { throw it }; val browser = view ?: error("浏览器未打开")
        require(expression.length <= Limits.LINK_SOURCE_MAX_BYTES)
        suspendCancellableCoroutine { continuation ->
            val script = "(function(){try{const v=($expression);const s=JSON.stringify(v===undefined?null:v);if(s.length>${Limits.LINK_RESULT_MAX_BYTES})return {__error:'页面数据过大'};return JSON.parse(s);}catch(e){return {__error:String(e)}}})()"
            browser.evaluateJavascript(script) { result ->
                if (continuation.isActive) continuation.resume(runCatching { Json.parseToJsonElement(result) }.getOrDefault(JsonNull))
            }
        }.also { value -> if (value is JsonObject && value["__error"] != null) error("页面脚本执行失败") }
    }
    suspend fun waitForData(expression: String, timeoutMs: Long): JsonElement {
        val data = withTimeoutOrNull(timeoutMs.coerceIn(1, Limits.LINK_SCRIPT_TIMEOUT_MS.toLong())) {
            while (true) {
                replayPending()
                val value = evaluate(expression)
                if (value != JsonNull && value != JsonPrimitive(false) && value != JsonPrimitive("")) return@withTimeoutOrNull value
                delay(Limits.LINK_BROWSER_POLL_MS)
            }
            @Suppress("UNREACHABLE_CODE") JsonNull
        }
        if (data == null) logDiagnostics()
        return data ?: throw LinkAnalysisFailure(LinkFailureCode.BROWSER_TIMEOUT)
    }

    /** Privacy-safe page fingerprint; used only when the source data never appears (host/flags, never page text or URL paths). */
    private suspend fun logDiagnostics() {
        val info = withTimeoutOrNull(2000L) { runCatching { evaluate(DIAG_EXPRESSION) }.getOrNull() } as? JsonObject
        fun field(name: String) = info?.get(name)?.jsonPrimitive?.content ?: "?"
        Log.i("LinkAnalysis", "trace=" + traceId + " stage=BROWSER_DIAG host=" + field("h") +
            " title_len=" + field("tl") + " has_state=" + field("st") + " capture=" + field("cap") + " ready=" + field("rs"))
    }

    /** Record GET requests whose URL matches a source-declared replay pattern (bounded, deduplicated). */
    private fun captureReplayable(request: WebResourceRequest) {
        if (replayPatterns.isEmpty() || !request.method.equals("GET", true)) return
        val url = request.url.toString()
        if (replayPatterns.none { url.contains(it) }) return
        if (replaySeen.size >= Limits.LINK_HTTP_MAX_CALLS || url in replaySeen) return
        if (replaySeen.add(url)) replayQueue.add(url)
    }

    /** Replay one captured URL with the WebView cookies and inject the body into window.__willdoCapture for the source. */
    private suspend fun replayPending() {
        val url = replayQueue.poll() ?: return
        try {
            val cookie = withContext(Dispatchers.Main) { runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull() }
            val response = http.request(buildJsonObject {
                put("url", url); put("method", "GET")
                putJsonObject("headers") {
                    if (!cookie.isNullOrBlank()) put("Cookie", cookie)
                    if (openedUrl.isNotBlank()) put("Referer", openedUrl)
                }
            })
            val body = response["body"]?.jsonPrimitive?.content.orEmpty()
            if (body.isBlank() || body.length > Limits.LINK_RESULT_MAX_BYTES) return
            val literal = buildJsonObject { put("url", url); put("body", body) }.toString()
            withContext(Dispatchers.Main) {
                view?.evaluateJavascript("window.__willdoCapture&&window.__willdoCapture.push(" + literal + ")", null)
            }
            Log.i("LinkAnalysis", "trace=" + traceId + " stage=BROWSER_REPLAY ok bytes=" + body.length)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            Log.w("LinkAnalysis", "trace=" + traceId + " stage=BROWSER_REPLAY failed code=" + ((error as? LinkAnalysisFailure)?.code?.name ?: error.javaClass.simpleName))
        }
    }
    suspend fun close() = withContext(NonCancellable + Dispatchers.Main) { closeOnMain() }
    private fun closeOnMain() { view?.apply {
        Log.i("LinkAnalysis", "trace=" + traceId + " stage=BROWSER_CLOSED blocked_requests=" + blockedRequests.get() + " blocked_hosts=" + blockedHosts.joinToString(","))
 stopLoading(); loadUrl("about:blank"); destroy() }; view = null }
    private val DIAG_EXPRESSION = "({h:(typeof location!=='undefined'?location.host:''),tl:(typeof document!=='undefined'&&document.title?document.title.length:0),st:!!window.__INITIAL_STATE__,cap:((window.__willdoCapture||[]).length),rs:(typeof document!=='undefined'?document.readyState:'')})"
    private fun captureScript() = """
        (() => {
          const max=${Limits.LINK_RESULT_MAX_BYTES}, count=${Limits.LINK_BROWSER_CAPTURE_COUNT};
          const records=[]; Object.defineProperty(window,'__willdoCapture',{value:records});
          const add=(url,body)=>{if(typeof body!=='string'||body.length>max)return;const u=String(url);for(let i=0;i<records.length;i++){if(records[i].url===u){records.splice(i,1);break;}}records.push({url:u,body});while(records.length>count)records.shift();};
          const fetch0=window.fetch;
          window.fetch=async function(...args){const r=await fetch0.apply(this,args);try{const c=r.clone();const reader=c.body?.getReader();if(reader){let n=0;const parts=[];(async()=>{while(true){const x=await reader.read();if(x.done)break;n+=x.value.byteLength;if(n>max){await reader.cancel();return;}parts.push(x.value);}const bytes=new Uint8Array(n);let i=0;for(const x of parts){bytes.set(x,i);i+=x.length;}add(r.url,new TextDecoder().decode(bytes));})().catch(()=>{});}}catch(e){}return r;};
          const open=XMLHttpRequest.prototype.open;
          XMLHttpRequest.prototype.open=function(method,url,...args){this.addEventListener('load',()=>{try{add(this.responseURL,this.responseText);}catch(e){}});return open.call(this,method,url,...args);};
        })();
    """.trimIndent()
}
