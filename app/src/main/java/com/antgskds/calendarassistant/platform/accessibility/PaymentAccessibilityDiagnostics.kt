package com.antgskds.calendarassistant.platform.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import com.antgskds.calendarassistant.feature.accounting.domain.AutomaticAccountingPolicy
import com.antgskds.calendarassistant.feature.settings.diagnostics.data.WillDoDownloadLogNode
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog
import com.antgskds.calendarassistant.shared.util.AppLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.resumeWithException

/** 一次性诊断旁路：不依赖支付文字规则，不调用识别/入库 API。所有系统节点在主线程当场读取。 */
class PaymentAccessibilityDiagnostics(
    private val service: TextAccessibilityService,
    private val onFlagsChanged: () -> Unit,
) {
    private class Session(val directory: File) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val started = SystemClock.elapsedRealtime()
        val records = Channel<String>(ConfigCatalog.PAYMENT_DIAGNOSTIC_QUEUE_SIZE)
        var active = true
        var events = 0
        var dropped = 0
        var logLimited = false
        var writerError: String? = null
        var eventLimited = false
        val trees = mutableMapOf<String, String>()
        var treeLimited = false
        var imageBytes = 0L
        var shots = 0
        var saved = 0
        var failures = 0
        var lastTreeAt: Long? = null
        var lastWindowPackage = ""
        var lastWindowClass = ""
        var lastPaymentEventAt = 0L
        var delayed: Job? = null
        lateinit var writer: Job
        lateinit var timer: Job
        lateinit var screenshotLoop: Job
        lateinit var healthLoop: Job
        var finishing: Deferred<String>? = null
    }

    private var session: Session? = null
    val isActive: Boolean get() = session?.active == true

    suspend fun start() {
        check(session?.let { it.active || it.finishing?.isActive == true } != true) { "诊断正在运行或导出，请稍后再试" }
        val stateBeforeConfiguration = service.diagnosticServiceState()
        val directory = withContext(Dispatchers.IO) {
            val root = File(service.filesDir, "payment_diagnostics").apply { mkdirs() }
            // 只清理本诊断器自己生成的目录；公共下载中的已导出 ZIP 不自动删除。
            root.listFiles().orEmpty().filter { it.isDirectory && it.name.startsWith("payment-diagnostic-") }
                .sortedByDescending { it.lastModified() }
                .drop(ConfigCatalog.PAYMENT_DIAGNOSTIC_RETAIN_SESSIONS - 1).forEach { it.deleteRecursively() }
            File(root, "payment-diagnostic-${System.currentTimeMillis()}-${UUID.randomUUID()}").apply {
                check(mkdirs())
                writeHealthHistory(this, "service-health-before.log")
            }
        }
        check(TextAccessibilityService.instance === service) { "无障碍连接已变化，请重新开始诊断" }
        val s = Session(directory)
        session = s
        s.writer = s.scope.launch(Dispatchers.IO) {
            try { File(directory, "events.jsonl").bufferedWriter().use { writer ->
                var bytes = 0L
                for (line in s.records) {
                    val length = line.toByteArray(Charsets.UTF_8).size + 1
                    if (bytes + length > ConfigCatalog.PAYMENT_DIAGNOSTIC_MAX_LOG_BYTES) {
                        s.logLimited = true
                        continue
                    }
                    writer.appendLine(line)
                    writer.flush()
                    bytes += length
                }
            } } catch (e: Exception) {
                s.writerError = e.javaClass.simpleName
                s.records.cancel()
                AppLogger.w(TAG, "diagnostic file write failed error=${e.javaClass.simpleName}")
            }
        }
        record(s, "session_start", JSONObject()
            .put("manufacturer", Build.MANUFACTURER).put("model", Build.MODEL)
            .put("sdk", Build.VERSION.SDK_INT).put("android", Build.VERSION.RELEASE)
            .put("buildFingerprint", Build.FINGERPRINT)
            .put("appVersion", version(service.packageName))
            .put("wechatVersion", version(AutomaticAccountingPolicy.WECHAT))
            .put("alipayVersion", version(AutomaticAccountingPolicy.ALIPAY))
            .put("capturePackages", JSONArray(AutomaticAccountingPolicy.diagnosticPackages.toList()))
            .put("serviceBeforeConfiguration", stateBeforeConfiguration)
            .put("durationMs", ConfigCatalog.PAYMENT_DIAGNOSTIC_DURATION_MS))
        record(s, "service_health", stateBeforeConfiguration.put("phase", "before_diagnostic_configuration"))
        onFlagsChanged() // 先保存原配置，再启用诊断临时标记，避免丢失故障现场。
        s.healthLoop = s.scope.launch {
            while (s.active) {
                sampleHealth(s)
                delay(ConfigCatalog.PAYMENT_DIAGNOSTIC_HEALTH_INTERVAL_MS.toLong())
            }
        }
        s.timer = s.scope.launch {
            delay(ConfigCatalog.PAYMENT_DIAGNOSTIC_DURATION_MS.toLong())
            finish("timeout")
        }
        s.screenshotLoop = s.scope.launch {
            while (s.active) {
                if (s.shots < ConfigCatalog.PAYMENT_DIAGNOSTIC_MAX_SCREENSHOTS && s.imageBytes < ConfigCatalog.PAYMENT_DIAGNOSTIC_MAX_IMAGE_BYTES) {
                    captureScreenshot(s)
                }
                delay(ConfigCatalog.PAYMENT_DIAGNOSTIC_SCREENSHOT_INTERVAL_MS.toLong())
            }
        }
        AppLogger.i(TAG, "session=${directory.name} started durationMs=${ConfigCatalog.PAYMENT_DIAGNOSTIC_DURATION_MS}")
    }

    /** 独立于事件和截图循环；只读取身份/状态，不抓取非支付应用正文。 */
    @Suppress("DEPRECATION")
    private fun sampleHealth(s: Session) {
        val data = attempt { service.diagnosticServiceState() }
        data.put("phase", "periodic")
        data.put("activeWindow", attempt {
            val identity = PaymentWindowReader.readIdentity(service.rootInActiveWindow)
            JSONObject().put("available", identity != null)
                .put("package", identity?.packageName ?: JSONObject.NULL)
                .put("windowId", identity?.windowId ?: JSONObject.NULL)
        })
        data.put("windows", attempt {
            val windows = service.windows
            try {
                JSONObject().put("count", windows.size)
                    .put("truncated", windows.size > ConfigCatalog.PAYMENT_DIAGNOSTIC_MAX_WINDOWS)
                    .put("items", JSONArray(windows.take(ConfigCatalog.PAYMENT_DIAGNOSTIC_MAX_WINDOWS).map {
                        JSONObject().put("id", it.id).put("type", it.type)
                            .put("active", it.isActive).put("focused", it.isFocused)
                    }))
            } finally { windows.forEach { it.recycle() } }
        })
        data.put("systemServiceStatus", attempt {
            val manager = service.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE)
                as android.view.accessibility.AccessibilityManager
            val component = android.content.ComponentName(service, TextAccessibilityService::class.java)
            val listed = manager.getEnabledAccessibilityServiceList(
                android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            ).any { android.content.ComponentName.unflattenFromString(it.id) == component }
            JSONObject().put("accessibilityEnabled", manager.isEnabled).put("serviceListed", listed)
        })
        record(s, "service_health", data)
    }

    private fun writeHealthHistory(directory: File, name: String) {
        // 基础日志读取失败不应阻止本次详细诊断及导出。
        val history = runCatching { AccessibilityDiagnosticSnapshot.healthHistory(AppLogger.readText()) }
            .getOrElse { "health_history_unavailable error=${it.javaClass.simpleName}" }
        File(directory, name).writeText(history)
    }

    /** 必须在 onAccessibilityEvent 当场调用；不把 event/source 放到协程中延迟读取。 */
    fun onEvent(event: AccessibilityEvent) {
        val s = session?.takeIf { it.active } ?: return
        val pkg = event.packageName?.toString().orEmpty()
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            s.lastWindowPackage = pkg
            s.lastWindowClass = event.className?.toString().orEmpty()
        }
        val supported = AutomaticAccountingPolicy.supportsDiagnostics(pkg)
        if (supported) s.lastPaymentEventAt = SystemClock.elapsedRealtime()
        // 其他应用只记录窗口切换元信息，不采集它们的事件原文/源节点。
        if (!supported && event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return
        if (s.events >= ConfigCatalog.PAYMENT_DIAGNOSTIC_MAX_EVENTS) {
            if (!s.eventLimited) record(s, "event_limit", JSONObject())
            s.eventLimited = true
            return
        }
        val eventId = ++s.events
        val data = JSONObject().put("eventId", eventId).put("package", pkg)
            .put("type", event.eventType).put("typeName", AccessibilityEvent.eventTypeToString(event.eventType))
            .put("class", AccessibilityDiagnosticSnapshot.field(event.className))
            .put("windowId", event.windowId).put("eventUptimeMs", event.eventTime)
            .put("receivedUptimeMs", SystemClock.uptimeMillis())
        if (supported) {
            data.put("text", JSONArray(event.text.map { if (event.isPassword) "[PASSWORD]" else AccessibilityDiagnosticSnapshot.field(it) }))
                .put("desc", if (event.isPassword) "[PASSWORD]" else AccessibilityDiagnosticSnapshot.field(event.contentDescription))
            data.put("source", treeReference(s, attempt { AccessibilityDiagnosticSnapshot.tree(event.source) }))
        }
        record(s, "event", data)
        AppLogger.i(TAG, "event=${eventId} package=$pkg type=${event.eventType} class=${data.optString("class")} window=${event.windowId} sourceTextNodes=${data.optJSONObject("source")?.optInt("textNodes", 0)}")
        if (!supported) return
        val now = SystemClock.elapsedRealtime()
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || s.lastTreeAt?.let { now - it >= ConfigCatalog.PAYMENT_DIAGNOSTIC_TREE_INTERVAL_MS } != false) {
            s.lastTreeAt = now
            sampleWindows(s, eventId, "immediate")
        } else record(s, "window_sample_throttled", JSONObject().put("eventId", eventId))
        // 固定时间轴，不被后续事件取消；锚点 ID 能区分延迟采样时是否已换页。
        if (s.delayed?.isActive != true) s.delayed = s.scope.launch {
            var previous = 0
            for (offset in listOf(ConfigCatalog.PAYMENT_DIAGNOSTIC_DELAY_FIRST_MS, ConfigCatalog.PAYMENT_DIAGNOSTIC_DELAY_SECOND_MS, ConfigCatalog.PAYMENT_DIAGNOSTIC_DELAY_LAST_MS)) {
                delay((offset - previous).toLong())
                previous = offset
                if (s.active) sampleWindows(s, eventId, "delay_${offset}ms")
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun sampleWindows(s: Session, eventId: Int, phase: String) {
        val data = JSONObject().put("anchorEventId", eventId).put("phase", phase)
            .put("lastWindowPackage", s.lastWindowPackage).put("lastWindowClass", s.lastWindowClass)
        data.put("activeRoot", treeReference(s, attempt { AccessibilityDiagnosticSnapshot.tree(service.rootInActiveWindow) }))
        val windowsData = JSONArray()
        try {
            val windows = service.windows
            data.put("windowCount", windows.size).put("windowsTruncated", windows.size > ConfigCatalog.PAYMENT_DIAGNOSTIC_MAX_WINDOWS)
            try {
                windows.take(ConfigCatalog.PAYMENT_DIAGNOSTIC_MAX_WINDOWS).forEach { window ->
                    windowsData.put(attempt {
                        JSONObject().put("id", window.id).put("type", window.type).put("layer", window.layer)
                            .put("active", window.isActive).put("focused", window.isFocused)
                            .put("accessibilityFocused", window.isAccessibilityFocused)
                            .put("title", AccessibilityDiagnosticSnapshot.field(window.title))
                            .put("root", treeReference(s, attempt { AccessibilityDiagnosticSnapshot.tree(window.root) }))
                    })
                }
            } finally { windows.forEach { it.recycle() } }
        } catch (e: Exception) { data.put("windowsError", e.javaClass.simpleName) }
        record(s, "windows", data.put("windows", windowsData))
    }

    private fun attempt(block: () -> JSONObject): JSONObject = try { block() }
    catch (e: Exception) { JSONObject().put("error", e.javaClass.simpleName) }

    /** 同一棵树只写一次，其余事件保留引用；避免界面反复刷新先耗尽日志容量。 */
    private fun treeReference(s: Session, tree: JSONObject): JSONObject {
        val key = AutomaticAccountingPolicy.fingerprint(tree.toString())
        var id = s.trees[key]
        if (id == null && s.trees.size < ConfigCatalog.PAYMENT_DIAGNOSTIC_MAX_TREES) {
            val next = "tree-${s.trees.size + 1}"
            if (record(s, "tree_snapshot", JSONObject().put("treeId", next).put("tree", tree))) {
                s.trees[key] = next
                id = next
            }
        }
        if (id == null) s.treeLimited = true
        return JSONObject().put("treeId", id ?: JSONObject.NULL)
            .put("available", tree.optBoolean("available"))
            .put("textNodes", tree.optInt("textNodes"))
            .put("nodeCount", tree.optInt("nodeCount"))
            .put("truncated", tree.optBoolean("truncated"))
            .put("rule", tree.optJSONObject("rule") ?: JSONObject.NULL)
            .put("error", tree.optString("error"))
    }

    @Suppress("DEPRECATION")
    private fun activePackage(): String? {
        val root = service.rootInActiveWindow ?: return null
        return try { root.packageName?.toString() } finally { root.recycle() }
    }

    private suspend fun captureScreenshot(s: Session) {
        val rootPkg = runCatching { activePackage() }.getOrNull()
        val eventFallback = rootPkg == null && AutomaticAccountingPolicy.supportsDiagnostics(s.lastWindowPackage) &&
            SystemClock.elapsedRealtime() - s.lastPaymentEventAt <= ConfigCatalog.PAYMENT_DIAGNOSTIC_SCREENSHOT_INTERVAL_MS * 2L
        val pkg = rootPkg ?: s.lastWindowPackage
        if (!AutomaticAccountingPolicy.supportsDiagnostics(pkg) || (rootPkg == null && !eventFallback)) {
            record(s, "screenshot_skip", JSONObject().put("reason", "no_payment_foreground_evidence")
                .put("activePackage", rootPkg ?: JSONObject.NULL).put("lastWindowPackage", s.lastWindowPackage))
            return
        }
        val number = ++s.shots
        val metadata = JSONObject().put("shot", number).put("package", pkg)
            .put("class", s.lastWindowClass).put("eventFallback", eventFallback)
            .put("anchorEventId", s.events)
        record(s, "screenshot_request", metadata)
        try {
            val result = withTimeout(ConfigCatalog.RECOGNITION_SCREENSHOT_TIMEOUT_MS.toLong()) {
                suspendCancellableCoroutine<AccessibilityService.ScreenshotResult> { continuation ->
                    service.takeScreenshot(Display.DEFAULT_DISPLAY, service.mainExecutor, object : AccessibilityService.TakeScreenshotCallback {
                        override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                            if (!continuation.isActive) { result.hardwareBuffer.close(); return }
                            continuation.resume(result, onCancellation = { _, value, _ -> value.hardwareBuffer.close() })
                        }
                        override fun onFailure(errorCode: Int) {
                            if (continuation.isActive) continuation.resumeWithException(ScreenshotFailure(errorCode))
                        }
                    })
                }
            }
            try {
                metadata.put("afterPackage", runCatching { activePackage() }.getOrNull() ?: JSONObject.NULL)
                withContext(Dispatchers.IO) {
                    val hardware = checkNotNull(Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace))
                    val bitmap = try { checkNotNull(hardware.copy(Bitmap.Config.ARGB_8888, false)) } finally { hardware.recycle() }
                    val file = File(s.directory, "screen-${number.toString().padStart(3, '0')}.png")
                    try {
                        metadata.put("width", bitmap.width).put("height", bitmap.height)
                        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                        ensureActive()
                        if (s.imageBytes + file.length() > ConfigCatalog.PAYMENT_DIAGNOSTIC_MAX_IMAGE_BYTES) {
                            file.delete()
                            metadata.put("status", "image_byte_limit")
                            s.imageBytes = ConfigCatalog.PAYMENT_DIAGNOSTIC_MAX_IMAGE_BYTES.toLong()
                        } else {
                            s.imageBytes += file.length()
                            s.saved++
                            metadata.put("status", "saved").put("file", file.name).put("bytes", file.length())
                        }
                    } catch (e: Exception) { file.delete(); throw e }
                    finally { bitmap.recycle() }
                }
                record(s, "screenshot_result", metadata)
            } finally { result.hardwareBuffer.close() }
        } catch (e: Exception) {
            s.failures++
            metadata.put("status", when (e) { is ScreenshotFailure -> "api_failure"; is TimeoutCancellationException -> "timeout"; is CancellationException -> "cancelled"; else -> "exception" })
                .put("error", e.javaClass.simpleName)
            if (e is ScreenshotFailure) metadata.put("errorCode", e.code).put("errorName", screenshotErrorName(e.code))
            record(s, "screenshot_result", metadata)
            if (e is CancellationException && e !is TimeoutCancellationException) throw e
        }
    }

    private class ScreenshotFailure(val code: Int) : Exception()

    private fun screenshotErrorName(code: Int): String = when (code) {
        AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR -> "INTERNAL_ERROR"
        AccessibilityService.ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS -> "NO_ACCESSIBILITY_ACCESS"
        AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT -> "INTERVAL_TIME_SHORT"
        AccessibilityService.ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY -> "INVALID_DISPLAY"
        AccessibilityService.ERROR_TAKE_SCREENSHOT_INVALID_WINDOW -> "INVALID_WINDOW"
        AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW -> "SECURE_WINDOW"
        else -> "UNKNOWN"
    }

    private fun record(s: Session, kind: String, data: JSONObject): Boolean {
        data.put("kind", kind).put("wallTime", Instant.now().toString())
            .put("elapsedMs", SystemClock.elapsedRealtime() - s.started)
        val sent = s.records.trySend(data.toString()).isSuccess
        if (!sent) s.dropped++
        if (kind.startsWith("screenshot")) AppLogger.i(TAG, "session=${s.directory.name} $data")
        return sent
    }

    fun finish(reason: String): Deferred<String>? {
        val s = session ?: return null
        s.finishing?.let { return it }
        record(s, "service_health", attempt { service.diagnosticServiceState() }.put("phase", "finishing").put("reason", reason))
        s.active = false
        onFlagsChanged()
        return s.scope.async {
            s.timer.cancel()
            s.healthLoop.cancelAndJoin()
            s.delayed?.cancelAndJoin()
            s.screenshotLoop.cancelAndJoin()
            // 生产者已停止，排空队列后再导出，避免生成缺少最后一张图片/日志的 ZIP。
            s.records.close()
            s.writer.join()
            withContext(Dispatchers.IO) {
                writeHealthHistory(s.directory, "service-health-final.log")
                File(s.directory, "summary.json").writeText(JSONObject().put("reason", reason)
                    .put("writerError", s.writerError ?: JSONObject.NULL)
                    .put("events", s.events).put("eventsLimited", s.eventLimited)
                    .put("uniqueTrees", s.trees.size).put("treesLimited", s.treeLimited)
                    .put("queueDropped", s.dropped).put("logLimited", s.logLimited)
                    .put("screenshotAttempts", s.shots).put("imagesSaved", s.saved)
                    .put("screenshotLimitReached", s.shots >= ConfigCatalog.PAYMENT_DIAGNOSTIC_MAX_SCREENSHOTS)
                    .put("screenshotFailures", s.failures).put("imageBytes", s.imageBytes)
                    .put("imageLimitReached", s.imageBytes >= ConfigCatalog.PAYMENT_DIAGNOSTIC_MAX_IMAGE_BYTES)
                    .put("elapsedMs", SystemClock.elapsedRealtime() - s.started).toString(2))
                File(s.directory, "README.txt").writeText("""
                    支付采集诊断（本地实验，无 AI 调用）
                    采集微信、支付宝、拼多多、淘宝和京东的事件正文、source、多窗口与截图，包含购物 App 内嵌支付页。
                    events.jsonl：按行 JSON；event 为当场事件和 source；windows 为活动根及多窗口。
                    service_health 独立定时检查服务状态、实际监听配置、事件间隔与窗口身份，即使事件/截图为零也会记录。
                    session_start.serviceBeforeConfiguration 保存诊断修改标记前的状态；instanceRegistered 仅为应用内引用，不证明系统连接健康。
                    systemServiceStatus.serviceListed 仅表示系统已启用服务，不证明微信正在投递事件。
                    service-health-before/final.log 附带本应用留存的基础状态日志（仅 WillDoAccessHealth），不包含支付正文。
                    自动记录日志关闭或超过既有保留/容量限制时，历史状态可能为空；本会话 service_health 不依赖该开关。
                    节点树以 treeId 引用同文件中的 tree_snapshot，重复的树只写一次；null treeId 表示容量/队列限制。
                    phase=immediate / delay_100ms / delay_700ms / delay_1500ms，通过 anchorEventId 对照。
                    source 只即时读取，延迟样本重新读取根/窗口，不复用过期事件或节点。
                    rule 为样本内对现有规则的判断；truncated=true 或发生节流/丢弃时不能断言内容不存在。
                    购物包的 rule.supported=false 仅表示未开启该包的正式自动识别，不代表诊断未采集。
                    screenshot_request/result 记录图片、对应时间/窗口、错误码；PNG 必须人工查看是否黑屏或区域缺失。
                    定时截图不依赖成功文字，采集范围内 App 的操作过程均可能被保存；无目标前台信号时不截图。
                    summary.json 标明采样及资源上限情况；没有图片不能直接断言系统禁止截图。
                    自动结束和导出后会恢复无障碍配置与原有自动记账；Xposed 链路独立运行。
                """.trimIndent())
                exportDirectory(service, s.directory)
            }.also { path -> android.widget.Toast.makeText(service.applicationContext, "支付诊断已结束，已导出：$path", android.widget.Toast.LENGTH_LONG).show() }
        }.also { task ->
            s.finishing = task
            task.invokeOnCompletion { error ->
                if (error != null) AppLogger.w(TAG, "export failed error=${error.javaClass.simpleName}")
                s.scope.cancel()
            }
        }
    }

    private fun version(pkg: String): String = runCatching { service.packageManager.getPackageInfo(pkg, 0).versionName.orEmpty() }.getOrDefault("unavailable")

    companion object {
        private const val TAG = "WillDoPayDiag"

        suspend fun exportLatest(context: android.content.Context): String = withContext(Dispatchers.IO) {
            val directory = File(context.filesDir, "payment_diagnostics").listFiles().orEmpty()
                .filter { it.isDirectory && it.name.startsWith("payment-diagnostic-") }
                .maxByOrNull { it.lastModified() } ?: error("还没有支付诊断，请先开始一次诊断")
            exportDirectory(context, directory)
        }

        private fun exportDirectory(context: android.content.Context, directory: File): String {
            val name = "${directory.name}.zip"
            val uri = checkNotNull(WillDoDownloadLogNode.getOrCreateDownloadUri(context, WillDoDownloadLogNode.EXPORT_DIR, name))
            checkNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { output ->
                ZipOutputStream(output).use { zip ->
                    directory.listFiles().orEmpty().filter { it.isFile }.sortedBy { it.name }.forEach { file ->
                        zip.putNextEntry(ZipEntry(file.name))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
            val path = WillDoDownloadLogNode.publicPath(WillDoDownloadLogNode.EXPORT_DIR, name)
            AppLogger.i(TAG, "exported path=$path")
            return path
        }
    }
}
