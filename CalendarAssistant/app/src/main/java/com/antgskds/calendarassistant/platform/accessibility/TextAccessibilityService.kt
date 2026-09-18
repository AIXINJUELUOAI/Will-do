package com.antgskds.calendarassistant.platform.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.antgskds.calendarassistant.shared.util.AppLogger as Log
import android.view.Display
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.feature.recognition.application.ai.AnalysisResult
import com.antgskds.calendarassistant.feature.recognition.application.ai.RecognitionFailureMessageMapper
import com.antgskds.calendarassistant.feature.recognition.application.ai.isRecognitionConfigReady
import com.antgskds.calendarassistant.feature.recognition.application.ai.recognitionConfigMissingMessage
import com.antgskds.calendarassistant.shared.event.DomainEventType
import com.antgskds.calendarassistant.shared.event.EventIdentity
import com.antgskds.calendarassistant.shared.event.events.IngestFailedEvent
import com.antgskds.calendarassistant.shared.event.events.IngestSucceededEvent
import com.antgskds.calendarassistant.shared.event.events.RecognitionFailedEvent
import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import com.antgskds.calendarassistant.feature.capsule.domain.CapsuleActionSpec
import com.antgskds.calendarassistant.platform.floating.FloatingScheduleService
import com.antgskds.calendarassistant.platform.receiver.EventActionReceiver
import com.antgskds.calendarassistant.shared.management.resource.notification.display.normal.NormalNotificationContent
import com.antgskds.calendarassistant.shared.management.resource.notification.display.normal.RecognitionNormalDisplay
import com.antgskds.calendarassistant.shared.management.resource.notification.display.normal.SystemNormalDisplay
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import com.antgskds.calendarassistant.feature.accounting.domain.AutomaticAccountingPolicy
import com.antgskds.calendarassistant.feature.accounting.domain.WechatPaymentSessionPolicy
import com.antgskds.calendarassistant.feature.accounting.domain.PaymentDetailPolicy
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class TextAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var analysisJob: Job? = null
    private var automaticTriggerJob: Job? = null
    private var wechatTriggerJob: Job? = null
    private var detailTriggerJob: Job? = null
    private var automaticRequest: AutomaticRequest? = null
    private val automaticPolicy = AutomaticAccountingPolicy()
    private val wechatSession = WechatPaymentSessionPolicy()
    private val detailPolicy = PaymentDetailPolicy()
    private sealed interface AutomaticRequest {
        val packageName: String
        data class Screen(val snapshot: PaymentWindowReader.Snapshot) : AutomaticRequest {
            override val packageName get() = snapshot.packageName
        }
        data class WechatSuccess(val candidate: WechatPaymentSessionPolicy.Candidate) : AutomaticRequest {
            override val packageName get() = AutomaticAccountingPolicy.WECHAT
        }
        data class Detail(val candidate: PaymentDetailPolicy.Candidate) : AutomaticRequest {
            override val packageName get() = candidate.packageName
        }
    }
    private val diagnosticTimes = mutableMapOf<String, Long>()
    private val paymentDiagnostics by lazy { PaymentAccessibilityDiagnostics(this) { refreshKeyEventFiltering() } }

    suspend fun startPaymentDiagnostics() {
        check(!isAnalyzing.get()) { "请等待当前识别结束，再开始诊断" }
        automaticTriggerJob?.cancel()
        wechatTriggerJob?.cancel()
        wechatSession.reset()
        detailTriggerJob?.cancel()
        detailPolicy.reset()
        paymentDiagnostics.start()
    }

    suspend fun exportPaymentDiagnostics(): String =
        paymentDiagnostics.finish("user_export")?.await()
            ?: PaymentAccessibilityDiagnostics.exportLatest(applicationContext)

    /** 高频阶段按固定阶段/来源键限流；内容只有判断结果，不带 UI 原文。主线程调用。 */
    private fun logAutomatic(stage: String, detail: String, source: String = "", throttle: Boolean = false) {
        if (throttle) {
            val key = "$stage:$source"
            val now = android.os.SystemClock.elapsedRealtime()
            if (diagnosticTimes[key]?.let { now - it < ConfigCatalog.AUTO_ACCOUNTING_DIAGNOSTIC_INTERVAL_MS } == true) return
            diagnosticTimes[key] = now
        }
        Log.i("WillDoAccounting", "accessibility stage=$stage source=$source $detail")
    }

    // 用于处理音量键长按的 Job
    private var volumeLongPressJob: Job? = null
    // 标记是否已经触发了长按事件
    private var isLongPressTriggered = false
    private var isVoiceCaptureTriggered = false
    private var isVolumeUpPressed = false

    private val NOTIFICATION_ID_PROGRESS = 1001
    private val NOTIFICATION_ID_RESULT = 2002

    private val app by lazy { applicationContext as App }
    private val capsuleCenter by lazy { app.capsuleCenter }
    private val floatingCenter by lazy { app.floatingCenter }
    private val domainEventBus by lazy { app.domainEventBus }
    private val settingsQueryApi by lazy { app.settingsQueryApi }
    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private var recognitionFailedSubscriptionJob: Job? = null
    private var ingestSucceededSubscriptionJob: Job? = null
    private var ingestFailedSubscriptionJob: Job? = null
    private var keyFilterSettingsJob: Job? = null
    private var baseAccessibilityFlags: Int? = null

    companion object {
        private const val TAG = "TextAccessibilityService"
        private const val RECOGNITION_LOG_TAG = "WillDoRecognition"
        private const val RECOGNITION_SOURCE_TYPE = "accessibility"
        private const val RECOGNITION_SOURCE_ID = "accessibility.screenshot"
        const val ACTION_CANCEL_ANALYSIS = "ACTION_CANCEL_ANALYSIS"
        const val ACTION_CLOSE_FLOATING = "com.antgskds.calendarassistant.ACTION_CLOSE_FLOATING"
        @Volatile var instance: TextAccessibilityService? = null
            private set
        @Volatile private var lastConnectedAt: Long = 0L
        @Volatile private var lastDisconnectedAt: Long = 0L
        private val isAnalyzing = AtomicBoolean(false)
        private const val LONG_PRESS_THRESHOLD = 400L
        private const val ACTION_VOLUME_LONG_PRESS_NONE = 0
        private const val ACTION_VOLUME_LONG_PRESS_SCREENSHOT = 1
        private const val ACTION_VOLUME_LONG_PRESS_FLOATING = 2
        private const val ACTION_VOLUME_LONG_PRESS_VOICE = 3

        fun isConnected(): Boolean = instance != null

        fun lastConnectedAt(): Long = lastConnectedAt

        fun lastDisconnectedAt(): Long = lastDisconnectedAt
    }

    private var launcherPackageName: String? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        runCatching { paymentDiagnostics.onEvent(event) }
            .onFailure { Log.w("WillDoPayDiag", "event capture error=${it.javaClass.simpleName}") }
        if (paymentDiagnostics.isActive) return
        val source = event.packageName?.toString().orEmpty()
        val enabled = AutomaticAccountingPolicy.enabled(settingsQueryApi.settings.value)
        if (!enabled) {
            detailTriggerJob?.cancel()
            detailPolicy.reset()
            wechatSession.reset()
            wechatTriggerJob?.cancel()
            automaticTriggerJob?.cancel()
            return
        }
        val now = android.os.SystemClock.elapsedRealtime()
        val supportedSource = AutomaticAccountingPolicy.supports(source)
        val foreground = if (supportedSource || event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            runCatching { PaymentWindowReader.readIdentity(rootInActiveWindow) }.getOrNull()
        } else null
        foreground?.let { detailPolicy.observeForeground(it.packageName, it.windowId) }
        val eventIsForeground = foreground != null && foreground.packageName == source && foreground.windowId == event.windowId
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && eventIsForeground) {
            wechatSession.observeWindow(source, event.className?.toString().orEmpty(), event.windowId, now)
            detailPolicy.observeWindow(source, event.className?.toString().orEmpty(), event.windowId,
                foregroundPackage = foreground.packageName, foregroundWindow = foreground.windowId)
        }
        // 不持有 event/source；事件文字与节点树是独立信息源，空树不代表没有成功信号。
        if (supportedSource && eventIsForeground && event.eventType in setOf(
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                AccessibilityEvent.TYPE_VIEW_HOVER_ENTER, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED,
                AccessibilityEvent.TYPE_ANNOUNCEMENT)) {
            // source 只能当场读取；700ms 后再取 root 会丢掉微信本次刷新暴露的子树。
            val eventSnapshot = runCatching { PaymentWindowReader.read(event.source) }
                .onFailure { logAutomatic("detail_source", "reason=read_error", source, throttle = true) }.getOrNull()
            if (eventSnapshot?.packageName == source && eventSnapshot.windowId == event.windowId) {
                val ready = PaymentDetailPolicy.inspectReadiness(eventSnapshot.texts, eventSnapshot.editable)
                if (ready.detail || ready.loading) logAutomatic("detail_content",
                    "detail=${ready.detail} amount=${ready.amount} time=${ready.time} loading=${ready.loading} ready=${ready.ready}", source, throttle = true)
                detailPolicy.claimTree(source, eventSnapshot.windowId, eventSnapshot.texts, eventSnapshot.editable,
                    now, evidence = "event_source")?.let { candidate ->
                    scheduleDetailRecognition(candidate)
                    return
                }
            }
            val texts = event.text.take(ConfigCatalog.AUTO_ACCOUNTING_MAX_NODES)
                .map { it?.toString().orEmpty().take(ConfigCatalog.AUTO_ACCOUNTING_MAX_TEXT) } +
                event.contentDescription?.toString().orEmpty().take(ConfigCatalog.AUTO_ACCOUNTING_MAX_TEXT)
            detailPolicy.claimEvent(source, event.windowId, texts, now)?.let { candidate ->
                scheduleDetailRecognition(candidate)
                return
            }
            if (!detailPolicy.hasDetailContext(source)) wechatSession.claimSuccess(source, event.windowId, texts, now)?.let { candidate ->
                logAutomatic("success_event", "type=${event.eventType} window=${candidate.windowId} session=${candidate.sessionId}", source)
                automaticTriggerJob?.cancel()
                wechatTriggerJob?.cancel()
                // 独立等待，不被后续空树刷新或系统岛事件取消、无限重置。
                wechatTriggerJob = serviceScope.launch {
                    delay(ConfigCatalog.AUTO_ACCOUNTING_DEBOUNCE_MS.toLong())
                    val request = AutomaticRequest.WechatSuccess(candidate)
                    if (isAnalyzing.get() || !settingsQueryApi.settings.value.isRecognitionConfigReady()) {
                        logAutomatic("gate", "reason=success_event_busy_or_config_missing", source)
                        return@launch
                    }
                    if (!automaticPageStillValid(request, "success_event_ready")) return@launch
                    val reservation = automaticPolicy.reserveWithReason(source, "wechat_session:${candidate.sessionId}", android.os.SystemClock.elapsedRealtime())
                    logAutomatic("reservation", "route=success_event result=$reservation", source)
                    if (reservation == AutomaticAccountingPolicy.Reservation.ACCEPTED) startRecognition(0.milliseconds, request)
                }
            }
        }
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return
        val supported = AutomaticAccountingPolicy.supports(source)
        // 只有支付应用事件能重置支付防抖；其他窗口变化交给截图前的前台校验处理。
        if (!supported) return
        if (automaticTriggerJob?.isActive == true) {
            logAutomatic("debounce_cancel", "reason=${if (supported) "payment_window_event" else "other_window_event"}", throttle = true)
        }
        automaticTriggerJob?.cancel()
        if (supported) logAutomatic("event", "type=${event.eventType} enabled=$enabled busy=${isAnalyzing.get()}", source, throttle = true)
        if (!enabled) return
        automaticTriggerJob = serviceScope.launch {
            delay(ConfigCatalog.AUTO_ACCOUNTING_DEBOUNCE_MS.toLong())
            val ready = settingsQueryApi.settings.value.isRecognitionConfigReady()
            if (isAnalyzing.get() || !ready) {
                logAutomatic("gate", "busy=${isAnalyzing.get()} configReady=$ready", source, throttle = true)
                return@launch
            }
            val snapshot = readAutomaticWindow("screen", source) ?: return@launch
            detailPolicy.claimTree(snapshot.packageName, snapshot.windowId, snapshot.texts, snapshot.editable,
                android.os.SystemClock.elapsedRealtime())?.let { candidate ->
                scheduleDetailRecognition(candidate, waitForPage = false)
                return@launch
            }
            if (detailPolicy.isBlocked(snapshot.packageName, snapshot.windowId, snapshot.texts, snapshot.editable) ||
                detailPolicy.hasDetailContext(snapshot.packageName) ||
                (snapshot.packageName == AutomaticAccountingPolicy.WECHAT && wechatSession.hasClaimedSuccess())) return@launch
            val check = AutomaticAccountingPolicy.inspectScreen(snapshot.packageName, snapshot.texts, snapshot.editable)
            logAutomatic("screen", "activeSource=${snapshot.packageName} textCount=${snapshot.texts.size} eligible=${check.eligible} " +
                "editable=${check.editable} excluded=${check.excludedMarker ?: "none"} success=${check.success} amount=${check.amount}", source, throttle = true)
            if (!check.eligible) return@launch
            if (!AutomaticAccountingPolicy.enabled(settingsQueryApi.settings.value)) {
                logAutomatic("gate", "reason=disabled", source, throttle = true)
                return@launch
            }
            val reservation = automaticPolicy.reserveWithReason(snapshot.packageName, snapshot.texts.joinToString("\n"), android.os.SystemClock.elapsedRealtime())
            logAutomatic("reservation", "result=$reservation", source, throttle = reservation != AutomaticAccountingPolicy.Reservation.ACCEPTED)
            if (reservation != AutomaticAccountingPolicy.Reservation.ACCEPTED) return@launch
            startRecognition(0.milliseconds, AutomaticRequest.Screen(snapshot))
        }
    }

    private fun scheduleDetailRecognition(candidate: PaymentDetailPolicy.Candidate, waitForPage: Boolean = true) {
        // 与成功事件共用模型入口，详情信号优先，确保不会把历史账单按当前时间入库。
        wechatTriggerJob?.cancel()
        detailTriggerJob?.cancel()
        logAutomatic("detail_signal", "evidence=${candidate.evidence} window=${candidate.windowId} visit=${candidate.visit}", candidate.packageName)
        detailTriggerJob = serviceScope.launch {
            if (waitForPage) delay(ConfigCatalog.AUTO_ACCOUNTING_DEBOUNCE_MS.toLong())
            if (isAnalyzing.get() || !settingsQueryApi.settings.value.isRecognitionConfigReady()) {
                logAutomatic("gate", "reason=detail_busy_or_config_missing", candidate.packageName)
                return@launch
            }
            val request = AutomaticRequest.Detail(candidate)
            if (!automaticPageStillValid(request, "detail_ready")) return@launch
            // root 仍可能为空，使用就绪时的内容摘要排重；不退回窗口号，避免吞掉同窗另一笔交易。
            val identity = "detail_content:${candidate.contentFingerprint}"
            val reservation = automaticPolicy.reserveWithReason(candidate.packageName, identity, android.os.SystemClock.elapsedRealtime())
            logAutomatic("reservation", "route=detail result=$reservation", candidate.packageName)
            if (reservation == AutomaticAccountingPolicy.Reservation.ACCEPTED) startRecognition(0.milliseconds, request)
        }
    }

    override fun onInterrupt() {
        paymentDiagnostics.finish("service_interrupted")
        logAutomatic("service", "state=interrupted")
        detailTriggerJob?.cancel()
        detailPolicy.reset()
        wechatTriggerJob?.cancel()
        wechatSession.reset()
        automaticTriggerJob?.cancel(); cancelCurrentAnalysis()
    }

    private fun readAutomaticWindow(stage: String, source: String): PaymentWindowReader.Snapshot? {
        return try {
            val root = rootInActiveWindow
            if (root == null) {
                logAutomatic(stage, "reason=no_active_root", source, throttle = true)
                null
            } else {
                PaymentWindowReader.read(root).also {
                    if (it == null) logAutomatic(stage, "reason=active_app_not_supported", source, throttle = true)
                }
            }
        } catch (e: Exception) {
            logAutomatic(stage, "reason=read_error error=${e.javaClass.simpleName}", source, throttle = true)
            null
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        logAutomatic("service", "state=connected")
        lastConnectedAt = System.currentTimeMillis()
        launcherPackageName = getLauncherPackageName()
        baseAccessibilityFlags = serviceInfo?.flags?.and(AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS.inv())
        refreshKeyEventFiltering()
        subscribeKeyFilterSettings()
        subscribeRecognitionFailedEvents()
        subscribeIngestEvents()
        app.runtimeCenter.startEdgeBarIfNeeded()
        Log.d(TAG, "无障碍服务已连接")
    }

    private fun subscribeKeyFilterSettings() {
        keyFilterSettingsJob?.cancel()
        keyFilterSettingsJob = serviceScope.launch {
            var previousAutomaticEnabled: Boolean? = null
            settingsQueryApi.settings.collect {
                refreshKeyEventFiltering()
                if (previousAutomaticEnabled != it.automaticAccountingEnabled) {
                    previousAutomaticEnabled = it.automaticAccountingEnabled
                    logAutomatic("settings", "enabled=${it.automaticAccountingEnabled} configReady=${it.isRecognitionConfigReady()}")
                }
                if (!AutomaticAccountingPolicy.enabled(it)) {
                    detailTriggerJob?.cancel()
                    detailPolicy.reset()
                    automaticTriggerJob?.cancel()
                    wechatTriggerJob?.cancel()
                    wechatSession.reset()
                    if (automaticRequest != null) cancelCurrentAnalysis()
                }
            }
        }
    }

    private fun refreshKeyEventFiltering() {
        val info = serviceInfo ?: return
        val baseFlags = baseAccessibilityFlags ?: info.flags.and(AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS.inv())
        val shouldFilterKeys = shouldFilterVolumeUpKeys()
        val keyFlags = if (shouldFilterKeys) {
            baseFlags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        } else {
            volumeLongPressJob?.cancel()
            isLongPressTriggered = false
            isVoiceCaptureTriggered = false
            isVolumeUpPressed = false
            baseFlags
        }
        val nextFlags = if (paymentDiagnostics.isActive) keyFlags or
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        else keyFlags

        if (info.flags != nextFlags) {
            info.flags = nextFlags
            setServiceInfo(info)
            Log.d(TAG, "按键过滤状态已更新: enabled=$shouldFilterKeys")
        }
    }

    private fun shouldFilterVolumeUpKeys(): Boolean {
        val settings = settingsQueryApi.settings.value
        if (!settings.volumeUpLongPressEnabled) return false
        return when (settings.volumeUpLongPressAction.coerceIn(1, 3)) {
            ACTION_VOLUME_LONG_PRESS_SCREENSHOT -> true
            ACTION_VOLUME_LONG_PRESS_FLOATING -> settings.isFloatingWindowEnabled || settings.voiceInputEnabled
            ACTION_VOLUME_LONG_PRESS_VOICE -> settings.voiceInputEnabled
            else -> false
        }
    }

    private fun subscribeRecognitionFailedEvents() {
        recognitionFailedSubscriptionJob?.cancel()
        recognitionFailedSubscriptionJob = serviceScope.launch {
            domainEventBus
                .eventsOfType<RecognitionFailedEvent>(DomainEventType.RECOGNITION_FAILED)
                .collect { event ->
                    val payload = event.payload
                    if (payload.sourceType != RECOGNITION_SOURCE_TYPE || payload.sourceId != RECOGNITION_SOURCE_ID) {
                        return@collect
                    }
                    cancelProgressNotification()
                    app.notificationCenter.showRecognitionFailureResultNotification(
                        RecognitionFailureMessageMapper.display(payload)
                    )
                    Handler(Looper.getMainLooper()).postDelayed({
                        cancelResultNotification()
                    }, 8000)
                }
        }
    }

    private fun subscribeIngestEvents() {
        ingestSucceededSubscriptionJob?.cancel()
        ingestSucceededSubscriptionJob = serviceScope.launch {
            domainEventBus
                .eventsOfType<IngestSucceededEvent>(DomainEventType.INGEST_SUCCEEDED)
                .collect { event ->
                    val payload = event.payload
                    if (payload.sourceType != RECOGNITION_SOURCE_TYPE || payload.sourceId != RECOGNITION_SOURCE_ID) {
                        return@collect
                    }
                    cancelProgressNotification()
                    if (payload.createdCount <= 0) {
                        showResultNotification(RecognitionNormalDisplay.completedNoNewEvents(), useOcrCapsule = true, durationMs = 8000L)
                        Handler(Looper.getMainLooper()).postDelayed({
                            cancelResultNotification()
                        }, 8000)
                    }
                }
        }

        ingestFailedSubscriptionJob?.cancel()
        ingestFailedSubscriptionJob = serviceScope.launch {
            domainEventBus
                .eventsOfType<IngestFailedEvent>(DomainEventType.INGEST_FAILED)
                .collect { event ->
                    val payload = event.payload
                    if (payload.sourceType != RECOGNITION_SOURCE_TYPE || payload.sourceId != RECOGNITION_SOURCE_ID) {
                        return@collect
                    }
                    cancelProgressNotification()
                    showResultNotification(RecognitionNormalDisplay.saveFailed(payload.message), useOcrCapsule = true, durationMs = 8000L)
                    Handler(Looper.getMainLooper()).postDelayed({
                        cancelResultNotification()
                    }, 8000)
                }
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val currentSettings = try {
            settingsQueryApi.settings.value
        } catch (e: Exception) {
            return super.onKeyEvent(event)
        }

        val longPressAction = currentSettings.volumeUpLongPressAction.coerceIn(1, 3)
        val shouldHandleLongPress = shouldFilterVolumeUpKeys()

        if (!shouldHandleLongPress) {
            volumeLongPressJob?.cancel()
            isLongPressTriggered = false
            isVoiceCaptureTriggered = false
            isVolumeUpPressed = false
            return false
        }

        if (FloatingScheduleService.isShowing && longPressAction == ACTION_VOLUME_LONG_PRESS_SCREENSHOT) {
            Log.d(TAG, "悬浮窗已显示，识屏长按放行")
            return false
        }

        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount > 0) {
                        return true
                    }

                    isVolumeUpPressed = true
                    isLongPressTriggered = false
                    isVoiceCaptureTriggered = false
                    volumeLongPressJob?.cancel()

                    volumeLongPressJob = serviceScope.launch {
                        delay(LONG_PRESS_THRESHOLD)
                        isLongPressTriggered = true
                        val actionLabel = when (longPressAction) {
                            ACTION_VOLUME_LONG_PRESS_SCREENSHOT -> "识屏"
                            ACTION_VOLUME_LONG_PRESS_FLOATING -> "悬浮窗"
                            ACTION_VOLUME_LONG_PRESS_VOICE -> "语音"
                            else -> "无操作"
                        }
                        Log.d(TAG, "长按音量+ 已确认，触发 $actionLabel")

                        performHapticFeedback()

                        when (longPressAction) {
                            ACTION_VOLUME_LONG_PRESS_SCREENSHOT -> {
                                startAnalysis(MySettings.normalizeScreenshotDelayMs(currentSettings.screenshotDelayMs).milliseconds)
                            }
                            ACTION_VOLUME_LONG_PRESS_FLOATING -> {
                                if (FloatingScheduleService.isShowing) {
                                    if (currentSettings.voiceInputEnabled && currentSettings.floatingVoiceLongPressEnabled) {
                                        isVoiceCaptureTriggered = true
                                        startVoiceCaptureService()
                                    }
                                } else {
                                    startFloatingService()
                                    serviceScope.launch {
                                        delay(240)
                                        if (
                                            isVolumeUpPressed &&
                                            currentSettings.voiceInputEnabled &&
                                            currentSettings.floatingVoiceLongPressEnabled
                                        ) {
                                            isVoiceCaptureTriggered = true
                                            startVoiceCaptureService()
                                        }
                                    }
                                }
                            }
                            ACTION_VOLUME_LONG_PRESS_VOICE -> {
                                if (FloatingScheduleService.isShowing && !currentSettings.floatingVoiceLongPressEnabled) {
                                    Log.d(TAG, "悬浮窗长按随口记已关闭，忽略音量+语音触发")
                                    return@launch
                                }
                                isVoiceCaptureTriggered = true
                                startVoiceCaptureService()
                            }
                        }
                    }

                    return true
                }

                KeyEvent.ACTION_UP -> {
                    isVolumeUpPressed = false
                    volumeLongPressJob?.cancel()

                    if (isLongPressTriggered) {
                        Log.d(TAG, "音量+ 抬起 (长按处理完毕)")
                        if (isVoiceCaptureTriggered) {
                            stopVoiceCaptureService()
                        }
                    } else {
                        Log.d(TAG, "音量+ 抬起 (短按)，模拟系统音量增加")
                        try {
                            audioManager.adjustSuggestedStreamVolume(
                                AudioManager.ADJUST_RAISE,
                                AudioManager.USE_DEFAULT_STREAM_TYPE,
                                AudioManager.FLAG_SHOW_UI
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "模拟调节音量失败", e)
                        }
                    }

                    isLongPressTriggered = false
                    isVoiceCaptureTriggered = false
                    return true
                }

                else -> {
                    isVolumeUpPressed = false
                    volumeLongPressJob?.cancel()
                    if (isVoiceCaptureTriggered) {
                        stopVoiceCaptureService()
                    }
                    isLongPressTriggered = false
                    isVoiceCaptureTriggered = false
                    return true
                }
            }
        }

        return super.onKeyEvent(event)
    }

    private fun performHapticFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val vibrator = vibratorManager.defaultVibrator
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    vibrator.vibrate(50)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "震动反馈失败", e)
        }
    }

    private fun startFloatingService() {
        if (!floatingCenter.canDrawOverlays(this)) {
            Log.w(TAG, "悬浮窗权限未授予，无法启动悬浮窗")
            app.notificationCenter.showFloatingPermissionDeniedNotification(NOTIFICATION_ID_RESULT)
            return
        }
        serviceScope.launch {
            floatingCenter.startFloatingService()
        }
    }

    private fun startVoiceCaptureService() {
        if (!settingsQueryApi.settings.value.voiceInputEnabled) {
            Log.w(TAG, "随口记未开启，无法启动随口记录音")
            showResultNotification("随口记未开启", "请先在实验室中开启随口记")
            return
        }
        if (!floatingCenter.canDrawOverlays(this)) {
            Log.w(TAG, "悬浮窗权限未授予，无法启动随口记录音")
            app.notificationCenter.showFloatingPermissionDeniedNotification(NOTIFICATION_ID_RESULT)
            return
        }
        serviceScope.launch {
            floatingCenter.startVoiceCaptureService()
        }
    }

    private fun stopVoiceCaptureService() {
        if (!floatingCenter.canDrawOverlays(this)) return
        serviceScope.launch {
            floatingCenter.stopVoiceCaptureService()
        }
    }

    private fun getLauncherPackageName(): String? {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName
    }

    override fun onUnbind(intent: Intent?): Boolean {
        paymentDiagnostics.finish("service_unbound")
        logAutomatic("service", "state=unbound")
        detailTriggerJob?.cancel()
        detailPolicy.reset()
        automaticTriggerJob?.cancel()
        wechatTriggerJob?.cancel()
        wechatSession.reset()
        cancelCurrentAnalysis()
        instance = null
        lastDisconnectedAt = System.currentTimeMillis()
        recognitionFailedSubscriptionJob?.cancel()
        recognitionFailedSubscriptionJob = null
        ingestSucceededSubscriptionJob?.cancel()
        ingestSucceededSubscriptionJob = null
        ingestFailedSubscriptionJob?.cancel()
        ingestFailedSubscriptionJob = null
        keyFilterSettingsJob?.cancel()
        keyFilterSettingsJob = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        paymentDiagnostics.finish("service_destroyed")
        instance = null
        lastDisconnectedAt = System.currentTimeMillis()
        recognitionFailedSubscriptionJob?.cancel()
        recognitionFailedSubscriptionJob = null
        ingestSucceededSubscriptionJob?.cancel()
        ingestSucceededSubscriptionJob = null
        ingestFailedSubscriptionJob?.cancel()
        ingestFailedSubscriptionJob = null
        keyFilterSettingsJob?.cancel()
        keyFilterSettingsJob = null
        cancelCurrentAnalysis()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL_ANALYSIS) {
            cancelCurrentAnalysis()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    fun cancelCurrentAnalysis() {
        if (automaticRequest != null) logAutomatic("cancel", "reason=cancel_requested")
        analysisJob?.cancel()
        // 由任务 finally 释放互斥；取消旧任务后不能提前放行新截图。
        cancelProgressNotification()
    }

    /**
     * 终极适配版：解决国产系统控制中心收起与三星/类原生回退冲突
     */
    fun closeNotificationPanel(): Boolean {
        var syncSuccess = false
        val tag = "PanelFixV3"

        // --- 层级 1：标准 Android 12+ API ---
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                // 无障碍服务自带执行权限，无需 WRITE_SECURE_SETTINGS
                syncSuccess = performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
            } catch (e: Exception) {
                Log.w(tag, "API 12+ 指令执行异常", e)
            }
        }

        // --- 层级 2：传统广播 (仅在层级 1 明确失败或版本不支持时触发) ---
        if (!syncSuccess) {
            try {
                @Suppress("DEPRECATION")
                sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
                // 注意：广播发出不代表成功收起，仅标记指令已送达
            } catch (e: Exception) {
                Log.w(tag, "系统广播发送失败", e)
            }
        }

        // --- 层级 3：智能动态"补刀"逻辑 ---
        Handler(Looper.getMainLooper()).postDelayed({
            val rootNode = rootInActiveWindow
            val currentPackage = rootNode?.packageName?.toString() ?: ""
            
            // 扩展国产 ROM 包名库，支持可配置扩展
            val systemUiPackages = mutableSetOf(
                "com.android.systemui",    // 通用/原生/MIUI/OneUI
                "com.coloros.systemui",   // OPPO/ColorOS
                "com.oppo.systemui",      // OPPO 旧版
                "com.vivo.systemui",      // vivo/OriginOS
                "com.huawei.systemui",    // 华为/EMUI
                "com.hihonor.systemui",   // 荣耀/MagicUI
                "com.meizu.systemui"      // 魅族/Flyme
            )

            // 检查当前是否仍处于系统 UI 界面（排除桌面和 App）
            val isStillOnSystemUi = systemUiPackages.any { currentPackage.contains(it) }

            if (isStillOnSystemUi) {
                Log.d(tag, "检测到面板钉子户: $currentPackage，执行 Back 补刀")
                // GLOBAL_ACTION_BACK 是 Android CDD 规定的交互兜底
                performGlobalAction(GLOBAL_ACTION_BACK)
            } else {
                // 面板已消失，不执行任何操作，保护三星/原生用户不回退
                Log.d(tag, "面板已安全避让，当前包名: $currentPackage")
            }
            
            rootNode?.recycle()
        }, 180) // 微调至 180ms，避开部分设备 150ms 时的动画临界态

        return syncSuccess
    }

    fun startAnalysis(delayDuration: Duration = 500.milliseconds, fromShortcut: Boolean = false) {
        Log.i(
            RECOGNITION_LOG_TAG,
            "accessibility start requested delayMs=${delayDuration.inWholeMilliseconds} " +
                "shortcut=$fromShortcut connected=${isConnected()} busy=${isAnalyzing.get()}"
        )
        startRecognition(delayDuration, null)
    }

    private fun startRecognition(delayDuration: Duration, automatic: AutomaticRequest?) {
        if (paymentDiagnostics.isActive) {
            Log.i("WillDoPayDiag", "recognition skipped: diagnostic session active")
            return
        }
        if (!isAnalyzing.compareAndSet(false, true)) {
            if (automatic != null) logAutomatic("gate", "reason=busy_before_start", automatic.packageName)
            Log.d(TAG, "已有分析任务在执行中，跳过本次请求")
            return
        }
        automaticRequest = automatic
        if (automatic != null) logAutomatic("start", "accepted=true", automatic.packageName)
        analysisJob = serviceScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                delay(delayDuration)
                if (automatic != null && !automaticPageStillValid(automatic, "before_screenshot")) return@launch
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    takeScreenshotAndAnalyze(automatic)
                } else {
                    showResultNotification(SystemNormalDisplay.androidVersionTooLow(), useOcrCapsule = true)
                }
            } catch (e: TimeoutCancellationException) {
                if (automatic != null) logAutomatic("finish", "reason=timeout", automatic.packageName)
                cancelProgressNotification()
                if (automatic == null) showResultNotification(RecognitionNormalDisplay.screenshotFailed("截图超时，请重试"), useOcrCapsule = true)
            } catch (e: Exception) {
                if (automatic != null) logAutomatic("finish", "reason=${if (e is CancellationException) "cancelled" else "error"} error=${e.javaClass.simpleName}", automatic.packageName)
                cancelProgressNotification()
                if (e is CancellationException) throw e
                Log.w(RECOGNITION_LOG_TAG, "截图任务失败: ${e.javaClass.simpleName}")
                if (automatic == null) showResultNotification(RecognitionNormalDisplay.screenshotFailed("截图失败，请重试"), useOcrCapsule = true)
            } finally {
                if (automatic != null) logAutomatic("task_end", "busyReleased=true", automatic.packageName)
                automaticRequest = null
                isAnalyzing.set(false)
            }
        }
    }

    /**
     * 截图并分析屏幕内容
     *
     * ⚠️ 注意：takeScreenshot() 必须在主线程调用（系统要求）
     * 但分析工作 (processScreenshot) 会在后台线程执行，避免阻塞主线程
     */
    private fun automaticPageStillValid(expected: AutomaticRequest, phase: String): Boolean {
        if (!AutomaticAccountingPolicy.enabled(settingsQueryApi.settings.value)) {
            logAutomatic(phase, "reason=disabled", expected.packageName)
            return false
        }
        val current = readAutomaticWindow(phase, expected.packageName) ?: return false
        if (expected is AutomaticRequest.Detail) {
            val valid = detailPolicy.isValid(expected.candidate, current.packageName, current.windowId, current.texts,
                current.editable, android.os.SystemClock.elapsedRealtime())
            logAutomatic(phase, "route=detail window=${current.windowId} valid=$valid", expected.packageName)
            return valid
        }
        // 原成功入口不能在截图时变成详情页，否则其缺失时间回填规则会污染历史账单。
        if (detailPolicy.hasDetailContext(current.packageName) || PaymentDetailPolicy.hasDetailMarker(current.texts) ||
            detailPolicy.isBlocked(current.packageName, current.windowId, current.texts, current.editable)) return false
        if (expected is AutomaticRequest.WechatSuccess) {
            val valid = wechatSession.isValid(expected.candidate, current.packageName, current.windowId, android.os.SystemClock.elapsedRealtime())
            logAutomatic(phase, "route=success_event window=${current.windowId} valid=$valid", expected.packageName)
            return valid
        }
        val unchanged = current.fingerprint == (expected as AutomaticRequest.Screen).snapshot.fingerprint
        val valid = current.eligible && unchanged
        logAutomatic(phase, "eligible=${current.eligible} unchanged=$unchanged valid=$valid", expected.packageName)
        return valid
    }

    private suspend fun takeScreenshotAndAnalyze(automatic: AutomaticRequest?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (automatic != null) logAutomatic("screenshot", "state=requested", automatic.packageName)
        val screenshot = withTimeout(ConfigCatalog.RECOGNITION_SCREENSHOT_TIMEOUT_MS.toLong()) {
            suspendCancellableCoroutine<ScreenshotResult> { continuation ->
                takeScreenshot(
            Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshotResult: ScreenshotResult) {
                    if (automatic != null) logAutomatic("screenshot", "state=success active=${continuation.isActive}", automatic.packageName)
                    if (!continuation.isActive) { screenshotResult.hardwareBuffer.close(); return }
                    continuation.resume(screenshotResult, onCancellation = { _, result, _ -> result.hardwareBuffer.close() })
                }
                override fun onFailure(errorCode: Int) {
                    if (automatic != null) logAutomatic("screenshot", "state=failed errorCode=$errorCode", automatic.packageName)
                    if (continuation.isActive) continuation.resumeWithException(IllegalStateException("screenshot:$errorCode"))
                }
            }
        )
            }
        }
        try {
            if (automatic != null && !automaticPageStillValid(automatic, "after_screenshot")) return
            showProgressNotification(
                if (automatic != null) RecognitionNormalDisplay.analyzingAccounting()
                else RecognitionNormalDisplay.analyzing()
            )
            withContext(Dispatchers.IO) { processScreenshot(screenshot, automatic) }
        } finally { screenshot.hardwareBuffer.close() }
    }

    private suspend fun processScreenshot(result: ScreenshotResult, automatic: AutomaticRequest?) {
        var ownedBitmap: Bitmap? = null
        try {
            val hardwareBuffer = result.hardwareBuffer
            val colorSpace = result.colorSpace
            val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
            if (bitmap == null) {
                Log.e(RECOGNITION_LOG_TAG, "accessibility hardware bitmap wrap failed")
                withContext(Dispatchers.Main) {
                    cancelProgressNotification()
                    showResultNotification(RecognitionNormalDisplay.screenshotProcessFailed(), useOcrCapsule = true, durationMs = 8000L)
                }
                return
            }

            val imagesDir = File(filesDir, "event_screenshots")
            if (!imagesDir.exists()) imagesDir.mkdirs()
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val imageFile = File(imagesDir, "IMG_$timestamp.jpg")

            val softwareBitmap = try { bitmap.copy(Bitmap.Config.ARGB_8888, true) } finally { bitmap.recycle() }
            ownedBitmap = softwareBitmap

            if (automatic == null) FileOutputStream(imageFile).use { out ->
                softwareBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }

            val settings = settingsQueryApi.settings.value
            if (!settings.isRecognitionConfigReady()) {
                Log.w(
                    RECOGNITION_LOG_TAG,
                    "accessibility image rejected configReady=false multimodal=true"
                )
                withContext(Dispatchers.Main) {
                    cancelProgressNotification()
                    showResultNotification(RecognitionNormalDisplay.configMissing(settings.recognitionConfigMissingMessage()), autoLaunch = true, useOcrCapsule = true, durationMs = 12000L)
                }
                softwareBitmap.recycle()
                return
            }

            val traceId = EventIdentity.newTraceId("accessibility")
            Log.i(
                RECOGNITION_LOG_TAG,
                "accessibility dispatching recognition trace=$traceId " +
                    "size=${softwareBitmap.width}x${softwareBitmap.height} " +
                    "multimodal=true"
            )
            val analysisResult = if (automatic != null) app.recognitionApi.analyzeAutomaticAccountingImage(
                softwareBitmap, settings, applicationContext, automatic.packageName, traceId,
                isDetailPage = automatic is AutomaticRequest.Detail
            ) else app.recognitionApi.analyzeImage(
                bitmap = softwareBitmap,
                settings = settings,
                context = applicationContext,
                sourceType = RECOGNITION_SOURCE_TYPE,
                sourceId = RECOGNITION_SOURCE_ID,
                sourceImagePath = imageFile.absolutePath,
                ingestRequested = true,
                traceId = traceId
            )
            softwareBitmap.recycle()
            if (automatic != null) {
                Log.i("WillDoAccounting", "accessibility stage=result source=${automatic.packageName} trace=$traceId result=${analysisResult.javaClass.simpleName}")
                withContext(Dispatchers.Main) {
                    // 先结束进度，再展示失败；任务 finally 不能清掉刚发布的结果胶囊。
                    cancelProgressNotification()
                    if (analysisResult is AnalysisResult.Failure) {
                        showResultNotification("自动记账失败", analysisResult.failure.fullMessage(), useOcrCapsule = true)
                    }
                }
                return
            }

            withContext(Dispatchers.Main) {
                when (analysisResult) {
                    is AnalysisResult.Success -> {
                        if (analysisResult.accountingResult != null) {
                            // 记账结果已独立展示；只清理识别进度，避免普通结果覆盖金额。
                            cancelProgressNotification()
                            return@withContext
                        }
                        if (analysisResult.bills.isNotEmpty() || analysisResult.billIssues.isNotEmpty()) {
                            cancelProgressNotification()
                            showResultNotification("识别完成", analysisResult.feedback(), useOcrCapsule = true, durationMs = 8000L)
                            return@withContext
                        }
                        val validEvents = analysisResult.data.filter { it.title.isNotBlank() }
                        if (validEvents.isEmpty()) {
                            cancelProgressNotification()
                            showResultNotification(RecognitionNormalDisplay.analysisCompletedNoValidSchedule(), useOcrCapsule = true, durationMs = 5000L)
                            Handler(Looper.getMainLooper()).postDelayed({
                                cancelResultNotification()
                            }, 5000)
                            return@withContext
                        }
                    }
                    is AnalysisResult.Empty -> Unit
                    is AnalysisResult.Failure -> {
                        cancelProgressNotification()
                        showResultNotification(
                            analysisResult.failure.title,
                            analysisResult.failure.detail,
                            useOcrCapsule = true,
                            durationMs = 8000L
                        )
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "处理截图出错", e)
            withContext(Dispatchers.Main) {
                cancelProgressNotification()
                showResultNotification(RecognitionNormalDisplay.analysisError(e.message), useOcrCapsule = true, durationMs = 8000L)
            }
        } finally {
            ownedBitmap?.let { if (!it.isRecycled) it.recycle() }
        }
    }

    private fun showProgressNotification(content: NormalNotificationContent) {
        if (shouldUseOcrCapsule()) {
            capsuleCenter.showOcrProgress(
                title = content.title,
                content = content.contentText,
                actions = listOf(
                    CapsuleActionSpec(
                        label = "取消",
                        receiverAction = EventActionReceiver.ACTION_CANCEL_RECOGNITION
                    )
                )
            )
        } else {
            app.notificationCenter.showRecognitionStatusNotification(
                notificationId = NOTIFICATION_ID_PROGRESS,
                content = content,
                isProgress = true,
                autoLaunch = false
            )
        }
    }

    private fun cancelProgressNotification() {
        if (shouldUseOcrCapsule()) {
            capsuleCenter.clearOcrCapsule()
            capsuleCenter.clearModelLoading()
            return
        }
        app.notificationCenter.cancelNotification(NOTIFICATION_ID_PROGRESS)
    }

    private fun cancelResultNotification() {
        if (shouldUseOcrCapsule()) {
            capsuleCenter.clearOcrCapsule()
            capsuleCenter.clearModelLoading()
            return
        }
        app.notificationCenter.cancelNotification(NOTIFICATION_ID_RESULT)
    }

    private fun showResultNotification(
        title: String,
        content: String,
        autoLaunch: Boolean = false,
        useOcrCapsule: Boolean = false,
        durationMs: Long = 8000L
    ) {
        if (useOcrCapsule && shouldUseOcrCapsule()) {
            capsuleCenter.showOcrResult(title, content, durationMs)
        } else {
            app.notificationCenter.showRecognitionStatusNotification(
                notificationId = NOTIFICATION_ID_RESULT,
                content = NormalNotificationContent(title = title, contentText = content),
                isProgress = false,
                autoLaunch = autoLaunch,
                durationMs = durationMs
            )
        }
    }

    private fun showResultNotification(
        content: NormalNotificationContent,
        autoLaunch: Boolean = false,
        useOcrCapsule: Boolean = false,
        durationMs: Long = 8000L
    ) {
        showResultNotification(
            title = content.title,
            content = content.contentText,
            autoLaunch = autoLaunch,
            useOcrCapsule = useOcrCapsule,
            durationMs = durationMs
        )
    }

    private fun buildScreenshotFailureContent(errorCode: Int): String {
        return RecognitionNormalDisplay.screenshotFailureContent(errorCode)
    }

    private fun shouldUseOcrCapsule(): Boolean {
        return settingsQueryApi.settings.value.isLiveCapsuleEnabled
    }

}
