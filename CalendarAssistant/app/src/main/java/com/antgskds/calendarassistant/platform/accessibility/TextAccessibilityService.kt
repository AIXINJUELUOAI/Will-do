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
import android.util.Log
import android.view.Display
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.core.ai.AnalysisResult
import com.antgskds.calendarassistant.core.ai.RecognitionFailureMessageMapper
import com.antgskds.calendarassistant.core.ai.isRecognitionConfigReady
import com.antgskds.calendarassistant.core.ai.recognitionConfigMissingMessage
import com.antgskds.calendarassistant.core.event.DomainEventType
import com.antgskds.calendarassistant.core.event.EventIdentity
import com.antgskds.calendarassistant.core.event.events.IngestFailedEvent
import com.antgskds.calendarassistant.core.event.events.IngestSucceededEvent
import com.antgskds.calendarassistant.core.event.events.RecognitionFailedEvent
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.platform.floating.FloatingScheduleService
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class TextAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var analysisJob: Job? = null

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
        private const val RECOGNITION_SOURCE_TYPE = "accessibility"
        private const val RECOGNITION_SOURCE_ID = "accessibility.screenshot"
        private const val ACTION_CANCEL_ANALYSIS = "ACTION_CANCEL_ANALYSIS"
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

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        lastConnectedAt = System.currentTimeMillis()
        launcherPackageName = getLauncherPackageName()
        baseAccessibilityFlags = serviceInfo?.flags?.and(AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS.inv())
        refreshKeyEventFiltering()
        subscribeKeyFilterSettings()
        subscribeRecognitionFailedEvents()
        subscribeIngestEvents()
        Log.d(TAG, "无障碍服务已连接")
    }

    private fun subscribeKeyFilterSettings() {
        keyFilterSettingsJob?.cancel()
        keyFilterSettingsJob = serviceScope.launch {
            settingsQueryApi.settings.collect {
                refreshKeyEventFiltering()
            }
        }
    }

    private fun refreshKeyEventFiltering() {
        val info = serviceInfo ?: return
        val baseFlags = baseAccessibilityFlags ?: info.flags.and(AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS.inv())
        val shouldFilterKeys = shouldFilterVolumeUpKeys()
        val nextFlags = if (shouldFilterKeys) {
            baseFlags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        } else {
            volumeLongPressJob?.cancel()
            isLongPressTriggered = false
            isVoiceCaptureTriggered = false
            isVolumeUpPressed = false
            baseFlags
        }

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
            ACTION_VOLUME_LONG_PRESS_FLOATING -> settings.isFloatingWindowEnabled
            ACTION_VOLUME_LONG_PRESS_VOICE -> settings.voiceInputEnabled && settings.isFloatingWindowEnabled
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
            Log.w(TAG, "语音输入未开启，无法启动语音输入")
            showResultNotification("语音输入未开启", "请先在实验室中开启语音输入")
            return
        }
        if (!floatingCenter.canDrawOverlays(this)) {
            Log.w(TAG, "悬浮窗权限未授予，无法启动语音输入")
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
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL_ANALYSIS) {
            cancelCurrentAnalysis()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun cancelCurrentAnalysis() {
        analysisJob?.cancel()
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
        if (!isAnalyzing.compareAndSet(false, true)) {
            Log.d(TAG, "已有分析任务在执行中，跳过本次请求")
            return
        }
        analysisJob?.cancel()
        analysisJob = serviceScope.launch {
            try {
                delay(delayDuration)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    takeScreenshotAndAnalyze()
                } else {
                    showResultNotification(SystemNormalDisplay.androidVersionTooLow(), useOcrCapsule = true)
                }
            } finally {
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
    private fun takeScreenshotAndAnalyze() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return

        // ✅ 主线程调用 takeScreenshot（系统要求）
        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshotResult: ScreenshotResult) {
                    showProgressNotification(RecognitionNormalDisplay.analyzing())
                    // ✅ 将耗时的分析工作移到后台线程
                    analysisJob = serviceScope.launch(Dispatchers.IO) {
                        processScreenshot(screenshotResult)
                    }
                }
                override fun onFailure(errorCode: Int) {
                    Log.w(TAG, "takeScreenshot 失败: code=$errorCode")
                    showResultNotification(RecognitionNormalDisplay.screenshotFailed(buildScreenshotFailureContent(errorCode)), useOcrCapsule = true)
                }
            }
        )
    }

    private suspend fun processScreenshot(result: ScreenshotResult) {
        try {
            val hardwareBuffer = result.hardwareBuffer
            val colorSpace = result.colorSpace
            val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
            if (bitmap == null) {
                hardwareBuffer.close()
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

            val softwareBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            bitmap.recycle()
            hardwareBuffer.close()

            FileOutputStream(imageFile).use { out ->
                softwareBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }

            val settings = settingsQueryApi.settings.value
            if (!settings.isRecognitionConfigReady()) {
                withContext(Dispatchers.Main) {
                    cancelProgressNotification()
                    showResultNotification(RecognitionNormalDisplay.configMissing(settings.recognitionConfigMissingMessage()), autoLaunch = true, useOcrCapsule = true, durationMs = 12000L)
                }
                softwareBitmap.recycle()
                return
            }

            val traceId = EventIdentity.newTraceId("accessibility")
            val analysisResult = app.recognitionCenter.analyzeImage(
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

            withContext(Dispatchers.Main) {
                when (analysisResult) {
                    is AnalysisResult.Success -> {
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
        }
    }

    private fun showProgressNotification(content: NormalNotificationContent) {
        if (shouldUseOcrCapsule()) {
            capsuleCenter.showOcrProgress(content.title, content.contentText)
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
