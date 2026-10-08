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
import kotlin.coroutines.resume

/** Disposable browser; remote pages receive no application bridge. */
class LinkBrowserSession(private val context: Context, private val manifest: LinkSourceManifest, private val http: LinkHttpClient, private val traceId: String = "") {
    private var view: WebView? = null
    private var failure: LinkAnalysisFailure? = null
    private val blockedRequests = java.util.concurrent.atomic.AtomicInteger()
    suspend fun open(options: JsonObject): JsonObject {
        require(manifest.permissions.browser) { "源没有浏览器权限" }
        val url = options["url"]?.jsonPrimitive?.content ?: error("缺少页面 URL")
        val userAgent = LinkAnalysisPolicy.browserUserAgent(options["userAgent"]?.jsonPrimitive?.content)
        withContext(Dispatchers.IO) { http.checkUrl(url) }
        return withContext(Dispatchers.Main) {
            closeOnMain()
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) throw LinkAnalysisFailure(LinkFailureCode.BROWSER_UNAVAILABLE)
            failure = null; blockedRequests.set(0)
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
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                    try { http.checkUrl(request.url.toString()); null } catch (_: Exception) {
                        blockedRequests.incrementAndGet()
                        WebResourceResponse("text/plain","utf-8",403,"Blocked",emptyMap(),ByteArrayInputStream(ByteArray(0)))
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
                val value = evaluate(expression)
                if (value != JsonNull && value != JsonPrimitive(false) && value != JsonPrimitive("")) return@withTimeoutOrNull value
                delay(Limits.LINK_BROWSER_POLL_MS)
            }
            @Suppress("UNREACHABLE_CODE") JsonNull
        }
        return data ?: throw LinkAnalysisFailure(LinkFailureCode.BROWSER_TIMEOUT)
    }
    suspend fun close() = withContext(NonCancellable + Dispatchers.Main) { closeOnMain() }
    private fun closeOnMain() { view?.apply {
        Log.i("LinkAnalysis", "trace=" + traceId + " stage=BROWSER_CLOSED blocked_requests=" + blockedRequests.get())
 stopLoading(); loadUrl("about:blank"); destroy() }; view = null }
    private fun captureScript() = """
        (() => {
          const max=${Limits.LINK_RESULT_MAX_BYTES}, count=${Limits.LINK_MAX_MEDIA};
          const records=[]; Object.defineProperty(window,'__willdoCapture',{value:records});
          const add=(url,body)=>{if(typeof body==='string' && body.length<=max){records.push({url:String(url),body});while(records.length>count)records.shift();}};
          const fetch0=window.fetch;
          window.fetch=async function(...args){const r=await fetch0.apply(this,args);try{const c=r.clone();const reader=c.body?.getReader();if(reader){let n=0;const parts=[];(async()=>{while(true){const x=await reader.read();if(x.done)break;n+=x.value.byteLength;if(n>max){await reader.cancel();return;}parts.push(x.value);}const bytes=new Uint8Array(n);let i=0;for(const x of parts){bytes.set(x,i);i+=x.length;}add(r.url,new TextDecoder().decode(bytes));})().catch(()=>{});}}catch(e){}return r;};
          const open=XMLHttpRequest.prototype.open;
          XMLHttpRequest.prototype.open=function(method,url,...args){this.addEventListener('load',()=>{try{add(this.responseURL,this.responseText);}catch(e){}});return open.call(this,method,url,...args);};
        })();
    """.trimIndent()
}
