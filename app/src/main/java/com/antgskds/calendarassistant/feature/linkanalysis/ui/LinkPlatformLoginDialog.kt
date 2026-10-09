package com.antgskds.calendarassistant.feature.linkanalysis.ui

import android.annotation.SuppressLint
import android.webkit.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.antgskds.calendarassistant.feature.linkanalysis.domain.LinkAnalysisPolicy
import com.antgskds.calendarassistant.feature.linkanalysis.domain.LinkSourceLoginEntry
import com.antgskds.calendarassistant.feature.linkanalysis.domain.LinkSourceManifest
import com.antgskds.calendarassistant.platform.linkanalysis.LinkHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

/** Source-declared login page; no extraction script or Android bridge is injected. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LinkPlatformLoginDialog(manifest: LinkSourceManifest, entry: LinkSourceLoginEntry, onDismiss: () -> Unit) {
    if (!LinkAnalysisPolicy.allowsLogin(manifest, entry)) return
    val ref = remember { arrayOfNulls<WebView>(1) }
    val http = remember(manifest) { LinkHttpClient(manifest) }
    var loading by remember { mutableStateOf(true) }
    var loadAttempt by remember { mutableIntStateOf(0) }
    var failure by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.name.trim() + "登录", style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = { loadAttempt++ }) { Text("刷新") }
                    TextButton(onClick = {
                        CookieManager.getInstance().removeAllCookies {
                            CookieManager.getInstance().flush()
                            loadAttempt++
                        }
                    }) { Text("清除登录") }
                    TextButton(onClick = onDismiss) { Text("完成") }
                }
                AndroidView(
                    factory = { context -> WebView(context).also { web ->
                        ref[0] = web
                        web.settings.apply {
                            javaScriptEnabled = true; domStorageEnabled = true
                            entry.userAgent?.let { userAgentString = it }
                            allowFileAccess = false; allowContentAccess = false
                            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            mediaPlaybackRequiresUserGesture = true; setSupportMultipleWindows(false)
                        }
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
                        web.webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val blocked = !LinkAnalysisPolicy.allowsLoginUrl(manifest, request.url.toString())
                                if (blocked && request.isForMainFrame) {
                                    loading = false; failure = "网页地址不在解析源允许的范围内"
                                }
                                return blocked
                            }
                            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                                try {
                                    require(LinkAnalysisPolicy.allowsLoginUrl(manifest, request.url.toString()))
                                    http.checkUrl(request.url.toString())
                                    null
                                } catch (_: Exception) {
                                    WebResourceResponse("text/plain", "utf-8", 403, "Blocked", emptyMap(),
                                        ByteArrayInputStream(ByteArray(0)))
                                }
                            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) { loading = true }
                            override fun onPageFinished(view: WebView, url: String?) { loading = false }
                            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: android.net.http.SslError) {
                                handler.cancel(); loading = false; failure = "无法验证网页安全连接"
                            }
                            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                                if (request.isForMainFrame) {
                                    loading = false; failure = "页面加载失败，请检查网络或解析源配置"
                                }
                            }
                        }
                        web.webChromeClient = object : WebChromeClient() {
                            override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
                            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                                callback.invoke(origin, false, false)
                            }
                        }
                    } },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
                failure?.let { Text(it, modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
                if (loading && failure == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }
    LaunchedEffect(manifest, entry, loadAttempt) {
        loading = true; failure = null
        val allowed = withContext(Dispatchers.IO) { runCatching { http.checkUrl(entry.url) }.isSuccess }
        if (allowed) ref[0]?.loadUrl(entry.url)
        else { loading = false; failure = "登录地址不可访问，请检查网络或解析源配置" }
    }
    DisposableEffect(Unit) { onDispose { ref[0]?.destroy(); ref[0] = null } }
}
