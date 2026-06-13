package com.antgskds.calendarassistant.service.floating

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import com.antgskds.calendarassistant.core.query.ScheduleQueryApi
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.content.pm.ServiceInfo
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.MainActivity
import com.antgskds.calendarassistant.R
import com.antgskds.calendarassistant.core.center.ScheduleDisplayHelper
import com.antgskds.calendarassistant.core.ai.AnalysisResult
import com.antgskds.calendarassistant.core.ai.RecognitionFailureMessageMapper
import com.antgskds.calendarassistant.core.ai.isRecognitionConfigReady
import com.antgskds.calendarassistant.core.ai.recognitionConfigMissingMessage
import com.antgskds.calendarassistant.core.center.ScheduleCenter
import com.antgskds.calendarassistant.core.event.DomainEventType
import com.antgskds.calendarassistant.core.event.EventIdentity
import com.antgskds.calendarassistant.core.event.events.IngestFailedEvent
import com.antgskds.calendarassistant.core.event.events.IngestSucceededEvent
import com.antgskds.calendarassistant.core.event.events.RecognitionFailedEvent
import com.antgskds.calendarassistant.core.query.SettingsQueryApi
import com.antgskds.calendarassistant.core.query.WeatherQueryApi
import com.antgskds.calendarassistant.core.quickmemo.audio.QuickMemoAudioRecorder
import com.antgskds.calendarassistant.core.quickmemo.audio.QuickMemoVoiceCaptureState
import com.antgskds.calendarassistant.core.quickmemo.audio.QuickMemoVoiceCaptureStatus
import com.antgskds.calendarassistant.core.weather.hasWeatherConfig
import com.antgskds.calendarassistant.core.service.image.ImagePickHandleActivity
import com.antgskds.calendarassistant.core.util.ImageImportUtils
import com.antgskds.calendarassistant.core.model.RecurringMode
import com.antgskds.calendarassistant.core.model.RecognitionDraft
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.*
import com.antgskds.calendarassistant.data.model.EventPatch
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.data.model.ScheduleDisplayItem
import com.antgskds.calendarassistant.data.model.UiStyle
import com.antgskds.calendarassistant.service.accessibility.TextAccessibilityService
import com.antgskds.calendarassistant.service.notification.NotificationIds
import com.antgskds.calendarassistant.ui.floating.FloatingScheduleScreen
import com.antgskds.calendarassistant.ui.theme.CalendarAssistantStyleTheme
import com.antgskds.calendarassistant.ui.theme.ThemeColorScheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds
import java.io.File
import java.time.LocalDate

class FloatingScheduleService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    companion object {
        private const val TAG = "FloatingScheduleService"
        private const val RECOGNITION_SOURCE_TYPE = "floating"
        private const val RECOGNITION_SOURCE_ID = "floating.manual_input"

        const val ACTION_IMAGE_PICKED = "com.antgskds.calendarassistant.floating.action.IMAGE_PICKED"
        const val ACTION_IMAGE_PICK_CANCELLED = "com.antgskds.calendarassistant.floating.action.IMAGE_PICK_CANCELLED"
        const val ACTION_PREPARE_VOICE_CAPTURE = "com.antgskds.calendarassistant.floating.action.PREPARE_VOICE_CAPTURE"
        const val ACTION_START_VOICE_CAPTURE = "com.antgskds.calendarassistant.floating.action.START_VOICE_CAPTURE"
        const val ACTION_STOP_VOICE_CAPTURE = "com.antgskds.calendarassistant.floating.action.STOP_VOICE_CAPTURE"
        const val ACTION_VOICE_CAPTURE_RECORDING = "com.antgskds.calendarassistant.floating.action.VOICE_CAPTURE_RECORDING"
        const val ACTION_VOICE_CAPTURE_COMPLETED = "com.antgskds.calendarassistant.floating.action.VOICE_CAPTURE_COMPLETED"
        const val ACTION_VOICE_CAPTURE_TOO_SHORT = "com.antgskds.calendarassistant.floating.action.VOICE_CAPTURE_TOO_SHORT"
        const val ACTION_VOICE_CAPTURE_ERROR = "com.antgskds.calendarassistant.floating.action.VOICE_CAPTURE_ERROR"
        const val ACTION_FLOATING_SHOWN = "com.antgskds.calendarassistant.floating.action.SHOWN"
        const val ACTION_FLOATING_HIDDEN = "com.antgskds.calendarassistant.floating.action.HIDDEN"
        const val EXTRA_IMAGE_URI = "extra_image_uri"
        const val EXTRA_VOICE_AUDIO_PATH = "extra_voice_audio_path"
        const val EXTRA_VOICE_DURATION_MS = "extra_voice_duration_ms"
        const val EXTRA_VOICE_ERROR_MESSAGE = "extra_voice_error_message"

        @Volatile var isShowing: Boolean = false
            private set
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null
    private var windowLayoutParams: WindowManager.LayoutParams? = null
    private var isViewAttached: Boolean = false
    private var baseWindowFlags: Int = 0

    private var pendingImagePickCompletion: (() -> Unit)? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val voiceCaptureState = MutableStateFlow(QuickMemoVoiceCaptureState())
    private val audioRecorder by lazy { QuickMemoAudioRecorder(applicationContext) }
    private var voiceConfirmJob: Job? = null
    private var voiceStartJob: Job? = null
    private var voiceStopRequested: Boolean = false
    private var voiceForegroundActive: Boolean = false

    private val app by lazy { applicationContext as App }
    private val scheduleCenter: ScheduleCenter by lazy { app.scheduleCenter }
    private val quickMemoCenter by lazy { app.quickMemoCenter }
    private val audioPlaybackCenter by lazy { app.audioPlaybackCenter }
    private val scheduleQueryApi: ScheduleQueryApi by lazy { app.scheduleQueryApi }
    private val settingsQueryApi: SettingsQueryApi by lazy { app.settingsQueryApi }
    private val weatherQueryApi: WeatherQueryApi by lazy { app.weatherQueryApi }
    private val permissionCenter by lazy { app.permissionCenter }
    private val domainEventBus by lazy { app.domainEventBus }
    private var recognitionFailedSubscriptionJob: Job? = null
    private var ingestSucceededSubscriptionJob: Job? = null
    private var ingestFailedSubscriptionJob: Job? = null
    // 广播接收器
    private val closeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                TextAccessibilityService.ACTION_CLOSE_FLOATING -> {
                    requestClose()
                }
                Intent.ACTION_CLOSE_SYSTEM_DIALOGS -> {
                    // 监听 Home 键或多任务键，自动关闭悬浮窗
                    val reason = intent.getStringExtra("reason")
                    if (reason == "homekey" || reason == "recentapps") {
                        requestClose()
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isShowing = true
        sendBroadcast(Intent(ACTION_FLOATING_SHOWN))
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // 注册广播 (兼容 Android 12+)
        val filter = IntentFilter().apply {
            addAction(TextAccessibilityService.ACTION_CLOSE_FLOATING)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                // Android 12+ 限制了系统广播的接收，通常不需要手动监听 Home 键，系统会处理层级
                addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(closeReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(closeReceiver, filter)
        }

        if (!permissionCenter.canDrawOverlays(this)) {
            requestClose()
            return
        }

        subscribeRecognitionFailedEvents()
        subscribeIngestEvents()
        initUI()
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
                    Toast.makeText(
                        applicationContext,
                        RecognitionFailureMessageMapper.userMessage(payload),
                        Toast.LENGTH_SHORT
                    ).show()
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
                    val message = when {
                        payload.createdCount <= 0 -> "已处理，无新增"
                        payload.createdCount == 1 -> "已添加 1 个事件"
                        else -> "已添加 ${payload.createdCount} 个事件"
                    }
                    Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(
                        applicationContext,
                        payload.message.ifBlank { "保存失败" },
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }

    private fun initUI() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            // Flags 组合修正：
            // 1. FLAG_LAYOUT_NO_LIMITS: 允许突破屏幕边界
            // 2. FLAG_LAYOUT_IN_SCREEN: 确保窗口坐标系使用整个屏幕（包含状态栏）
            // 3. 移除 FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS (关键！防止系统绘制额外背景)
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, // 允许事件穿透到后面，但我们是全屏的，这个其实主要防止焦点独占
            PixelFormat.TRANSLUCENT
        ).apply {
            // 软键盘模式
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE

            // 显式设置对齐方式，防止部分设备默认居中导致偏移
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            x = 0
            y = 0

            // 【核心修复】适配刘海屏/挖孔屏
            // SHORT_EDGES 表示：无论横竖屏，都允许内容延伸到刘海/摄像头区域
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        windowLayoutParams = params
        baseWindowFlags = params.flags

        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingScheduleService)
            setViewTreeSavedStateRegistryOwner(this@FloatingScheduleService)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            // 【修复关闭按钮重叠问题】
            // 在悬浮窗模式下，Compose 有时无法自动获取 statusBarsPadding。
            // 这里强制设置 systemUiVisibility 帮助 Compose 识别全屏状态
            @Suppress("DEPRECATION")
            systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    )

            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    requestClose()
                    return@setOnKeyListener true
                }
                false
            }

            isFocusable = true
            isFocusableInTouchMode = true

            setContent {
                val events by scheduleQueryApi.events.collectAsState()
                val settings by settingsQueryApi.settings.collectAsState()
                val context = LocalContext.current
                val weatherData by weatherQueryApi.weatherData.collectAsState()
                val quickMemos by quickMemoCenter.quickMemos.collectAsState()
                val currentVoiceCaptureState by voiceCaptureState.collectAsState()
                val audioPlaybackState by audioPlaybackCenter.playbackState.collectAsState()
                val undoPending by scheduleCenter.undoManager.currentPending.collectAsState()
                val pendingItemStates by scheduleCenter.pendingItemStates.collectAsState()

                // 根据悬浮窗日程范围设置过滤事件
                val today = LocalDate.now()
                val tomorrow = today.plusDays(1)
                val scheduleEvents = events.filter { it.archivedAt == null }
                val (displayFrom, displayTo) = when (settings.floatingEventRange) {
                    1 -> today to today
                    2 -> today to tomorrow
                    else -> (scheduleEvents.minOfOrNull { it.startDate } ?: today) to today.plusDays(7) // 0 = 过去全部到未来7天
                }
                val scheduleItems = scheduleCenter.applyPendingStateOverrides(
                    ScheduleDisplayHelper.buildDisplayItems(scheduleEvents, displayFrom, displayTo)
                )

                val isDarkTheme = when (settings.themeMode) {
                    1 -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
                    2 -> false
                    3 -> true
                    else -> false
                }
                val themeColorSchemeEnum = ThemeColorScheme.fromName(settings.themeColorScheme)

                CalendarAssistantStyleTheme(
                    uiStyle = UiStyle.fromName(settings.uiStyle),
                    darkTheme = isDarkTheme,
                    dynamicColor = themeColorSchemeEnum == ThemeColorScheme.DEFAULT,
                    themeColorScheme = themeColorSchemeEnum,
                    customThemeColorHex = settings.customThemeColorHex
                ) {
                    val floatingContent: @androidx.compose.runtime.Composable () -> Unit = {
                        when (UiStyle.fromName(settings.uiStyle)) {
                            UiStyle.MIUI -> com.antgskds.calendarassistant.miui.floating.FloatingScheduleScreen(
                                scheduleItems = scheduleItems,
                                weatherData = if (settings.hasWeatherConfig() && settings.showWeatherInFloating) weatherData else null,
                                weatherForecastRange = settings.floatingWeatherForecastRange,
                                expandSide = settings.floatingExpandSide,
                                hapticEnabled = settings.hapticFeedbackEnabled,
                                onClose = { requestClose() },
                                onManualInput = { text, onComplete ->
                                    handleManualInput(text = text, onComplete = onComplete)
                                },
                                onPickImageRequest = { onComplete ->
                                    startScreenshotAnalysisFlow(onComplete)
                                },
                                onUpdateEvent = { updatedEvent, onComplete ->
                                    serviceScope.launch {
                                        try {
                                            scheduleCenter.updateEvent(updatedEvent)
                                            Toast.makeText(applicationContext, "已更新", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Failed to update event", e)
                                            Toast.makeText(applicationContext, "更新失败", Toast.LENGTH_SHORT).show()
                                        } finally {
                                            onComplete()
                                        }
                                    }
                                },
                                onUpdateScheduleItem = { item, patch, onComplete ->
                                    handleUpdateScheduleItem(item, patch, onComplete)
                                },
                                onArchiveScheduleItem = { item ->
                                    serviceScope.launch {
                                        try {
                                            scheduleCenter.archiveItem(item.action)
                                            Toast.makeText(applicationContext, "已归档", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Failed to archive item", e)
                                            Toast.makeText(applicationContext, "归档失败", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onStatusAction = { item ->
                                    scheduleCenter.performPrimaryActionOnItemWithUndo(item)
                                },
                                pendingStatusKeys = pendingItemStates.keys,
                                undoPendingLabel = undoPending?.label,
                                onUndoAction = {
                                    scheduleCenter.undoStatusAction()
                                },
                                onLoadingChange = { _ -> }
                            )
                            UiStyle.MATERIAL3 -> FloatingScheduleScreen(
                        scheduleItems = scheduleItems,
                        quickMemos = quickMemos,
                        voiceCaptureState = currentVoiceCaptureState,
                        audioPlaybackState = audioPlaybackState,
                        weatherData = if (settings.hasWeatherConfig() && settings.showWeatherInFloating) weatherData else null,
                        weatherForecastRange = settings.floatingWeatherForecastRange,
                        expandSide = settings.floatingExpandSide,
                        hapticEnabled = settings.hapticFeedbackEnabled,
                        onClose = { requestClose() },
                        onManualInput = { text, isQuickMemo, onComplete ->
                            handleManualInput(text = text, isQuickMemo = isQuickMemo, onComplete = onComplete)
                        },
                        onPickImageRequest = { onComplete ->
                            startScreenshotAnalysisFlow(onComplete)
                        },
                        onUpdateEvent = { updatedEvent, onComplete ->
                            serviceScope.launch {
                                try {
                                    scheduleCenter.updateEvent(updatedEvent)
                                    Toast.makeText(applicationContext, "已更新", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to update event", e)
                                    Toast.makeText(applicationContext, "更新失败", Toast.LENGTH_SHORT).show()
                                } finally {
                                    onComplete()
                                }
                            }
                        },
                        onUpdateScheduleItem = { item, patch, onComplete ->
                            handleUpdateScheduleItem(item, patch, onComplete)
                        },
                        onArchiveScheduleItem = { item ->
                            serviceScope.launch {
                                try {
                                    scheduleCenter.archiveItem(item.action)
                                    Toast.makeText(applicationContext, "已归档", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to archive item", e)
                                    Toast.makeText(applicationContext, "归档失败", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onStatusAction = { item ->
                            scheduleCenter.performPrimaryActionOnItemWithUndo(item)
                        },
                        pendingStatusKeys = pendingItemStates.keys,
                        undoPendingLabel = undoPending?.label,
                        onUndoAction = {
                            scheduleCenter.undoStatusAction()
                        },
                        onMarkQuickMemoTodo = { memo ->
                            serviceScope.launch { memo.id?.let { quickMemoCenter.markTodoActive(it) } }
                        },
                        onRemoveQuickMemoTodo = { memo ->
                            serviceScope.launch { memo.id?.let { quickMemoCenter.removeTodo(it) } }
                        },
                        onToggleQuickMemoTodo = { memo ->
                            serviceScope.launch { memo.id?.let { quickMemoCenter.toggleTodoCompletion(it) } }
                        },
                        onDeleteQuickMemo = { memo, onComplete ->
                            serviceScope.launch {
                                try {
                                    memo.id?.let { quickMemoCenter.deleteQuickMemo(it) }
                                    Toast.makeText(applicationContext, "已删除", Toast.LENGTH_SHORT).show()
                                } finally {
                                    onComplete()
                                }
                            }
                        },
                        onSaveQuickMemo = { memo, body, onComplete ->
                            serviceScope.launch {
                                try {
                                    memo.id?.let { quickMemoCenter.updateBody(it, body) }
                                } finally {
                                    onComplete()
                                }
                            }
                        },
                        onReorderQuickMemos = { ids ->
                            serviceScope.launch { quickMemoCenter.updateSortRanks(ids) }
                        },
                        onConfirmVoiceCapture = { asTodo ->
                            confirmVoiceCapture(asTodo)
                        },
                        onStartVoiceCapture = {
                            startVoiceCapture()
                        },
                        onStopVoiceCapture = {
                            stopVoiceCapture()
                        },
                        onToggleAudioPlayback = { path ->
                            serviceScope.launch(Dispatchers.IO) {
                                runCatching { audioPlaybackCenter.toggle(path) }
                                    .onFailure { Log.e(TAG, "播放语音随口记失败", it) }
                            }
                        },
                        onLoadingChange = { _ -> }
                    )
                        }
                    }
                    floatingContent()
                }
            }
        }

        try {
            windowManager.addView(composeView, params)
            composeView?.requestFocus()
            isViewAttached = true
        } catch (e: Exception) {
            Log.e(TAG, "UI Init Failed", e)
            isViewAttached = false
            requestClose()
        }
    }

    private fun hideFloatingWindow() {
        val view = composeView ?: return
        val params = windowLayoutParams ?: return
        if (!isViewAttached || !view.isAttachedToWindow) {
            isViewAttached = false
            return
        }

        try {
            params.flags = baseWindowFlags or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            windowManager.updateViewLayout(view, params)
            view.visibility = View.GONE
        } catch (e: Exception) {
            Log.w(TAG, "hideFloatingWindow failed", e)
        }
    }

    private fun detachFloatingWindowForVoice() {
        val view = composeView ?: return
        if (!isViewAttached && !view.isAttachedToWindow) return
        try {
            windowManager.removeView(view)
            isViewAttached = false
        } catch (e: IllegalArgumentException) {
            isViewAttached = false
        } catch (e: Exception) {
            Log.w(TAG, "detachFloatingWindowForVoice failed", e)
        }
    }

    private fun showFloatingWindow() {
        val view = composeView ?: return
        val params = windowLayoutParams ?: return
        if (!permissionCenter.canDrawOverlays(this)) return

        if (!isViewAttached && !view.isAttachedToWindow) {
            try {
                windowManager.addView(view, params)
                isViewAttached = true
            } catch (e: IllegalStateException) {
                Log.w(TAG, "showFloatingWindow addView already attached", e)
                isViewAttached = true
            } catch (e: Exception) {
                Log.e(TAG, "showFloatingWindow addView failed", e)
                isViewAttached = false
                return
            }
        } else {
            isViewAttached = true
        }

        try {
            view.visibility = View.VISIBLE
            view.requestLayout()
            params.flags = baseWindowFlags
            windowManager.updateViewLayout(view, params)
            view.requestFocus()
        } catch (e: Exception) {
            Log.e(TAG, "showFloatingWindow update failed", e)
        }
    }

    private fun finishPendingImagePick() {
        val callback = pendingImagePickCompletion
        pendingImagePickCompletion = null
        callback?.invoke()
    }

    private fun startVoiceCapture() {
        voiceConfirmJob?.cancel()
        voiceStartJob?.cancel()
        voiceStopRequested = false
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            voiceCaptureState.value = QuickMemoVoiceCaptureState(
                status = QuickMemoVoiceCaptureStatus.ERROR,
                message = "需要麦克风权限"
            )
            Toast.makeText(applicationContext, "需要麦克风权限", Toast.LENGTH_SHORT).show()
            requestRecordAudioPermission()
            serviceScope.launch {
                delay(1400)
                voiceCaptureState.value = QuickMemoVoiceCaptureState()
            }
            return
        }
        try {
            startVoiceForeground()
        } catch (e: Exception) {
            Log.e(TAG, "启动随口记前台录音失败", e)
            val message = if (e is SecurityException) "系统限制后台录音，请重试" else "录音失败"
            voiceCaptureState.value = QuickMemoVoiceCaptureState(
                status = QuickMemoVoiceCaptureStatus.ERROR,
                message = message
            )
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
            showFloatingWindow()
            serviceScope.launch {
                delay(1400)
                voiceCaptureState.value = QuickMemoVoiceCaptureState()
            }
            return
        }
        voiceCaptureState.value = QuickMemoVoiceCaptureState(status = QuickMemoVoiceCaptureStatus.RECORDING)
        showFloatingWindow()
        voiceStartJob = serviceScope.launch {
            try {
                withContext(Dispatchers.IO) { audioRecorder.start() }
                if (voiceStopRequested) {
                    voiceStopRequested = false
                    stopVoiceCapture()
                    return@launch
                }
                performServiceHaptic()
                voiceCaptureState.value = QuickMemoVoiceCaptureState(status = QuickMemoVoiceCaptureStatus.RECORDING)
            } catch (e: Exception) {
                Log.e(TAG, "启动随口记录音失败", e)
                stopVoiceForeground()
                withContext(Dispatchers.IO) { audioRecorder.stopAndDiscard() }
                voiceCaptureState.value = QuickMemoVoiceCaptureState(
                    status = QuickMemoVoiceCaptureStatus.ERROR,
                    message = if (e is SecurityException) "系统限制后台录音，请重试" else "录音失败"
                )
                delay(1400)
                voiceCaptureState.value = QuickMemoVoiceCaptureState()
            }
        }
    }

    private fun stopVoiceCapture() {
        if (voiceCaptureState.value.status == QuickMemoVoiceCaptureStatus.RECORDING && !audioRecorder.isRecording) {
            voiceStopRequested = true
            return
        }
        if (voiceCaptureState.value.status != QuickMemoVoiceCaptureStatus.RECORDING && !audioRecorder.isRecording) return
        serviceScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { audioRecorder.stop() }
                stopVoiceForeground()
                performServiceHaptic()
                if (result == null || result.durationMs < QuickMemoAudioRecorder.MIN_RECORDING_MS) {
                    result?.path?.let { path -> withContext(Dispatchers.IO) { File(path).delete() } }
                    voiceCaptureState.value = QuickMemoVoiceCaptureState(status = QuickMemoVoiceCaptureStatus.TOO_SHORT)
                    delay(1100)
                    voiceCaptureState.value = QuickMemoVoiceCaptureState()
                    return@launch
                }
                voiceCaptureState.value = QuickMemoVoiceCaptureState(
                    status = QuickMemoVoiceCaptureStatus.CONFIRMING,
                    tempAudioPath = result.path,
                    durationMs = result.durationMs
                )
                voiceConfirmJob?.cancel()
                voiceConfirmJob = serviceScope.launch {
                    delay(5000)
                    confirmVoiceCapture(asTodo = false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "停止随口记录音失败", e)
                stopVoiceForeground()
                withContext(Dispatchers.IO) { audioRecorder.stopAndDiscard() }
                voiceCaptureState.value = QuickMemoVoiceCaptureState(
                    status = QuickMemoVoiceCaptureStatus.ERROR,
                    message = "录音失败"
                )
                delay(1400)
                voiceCaptureState.value = QuickMemoVoiceCaptureState()
            }
        }
    }

    private fun handleExternalVoiceRecording() {
        voiceConfirmJob?.cancel()
        voiceConfirmJob = null
        voiceStartJob?.cancel()
        voiceStopRequested = false
        voiceCaptureState.value = QuickMemoVoiceCaptureState(status = QuickMemoVoiceCaptureStatus.RECORDING)
    }

    private fun handleExternalVoiceCompleted(path: String?, durationMs: Long) {
        if (path.isNullOrBlank()) {
            handleExternalVoiceError("录音失败")
            return
        }
        voiceConfirmJob?.cancel()
        voiceConfirmJob = null
        voiceStartJob?.cancel()
        voiceStopRequested = false
        voiceCaptureState.value = QuickMemoVoiceCaptureState(
            status = QuickMemoVoiceCaptureStatus.CONFIRMING,
            tempAudioPath = path,
            durationMs = durationMs
        )
        showFloatingWindow()
        voiceConfirmJob = serviceScope.launch {
            delay(5000)
            confirmVoiceCapture(asTodo = false)
        }
    }

    private fun handleExternalVoiceTooShort() {
        voiceConfirmJob?.cancel()
        voiceConfirmJob = null
        voiceStartJob?.cancel()
        voiceCaptureState.value = QuickMemoVoiceCaptureState(status = QuickMemoVoiceCaptureStatus.TOO_SHORT)
        showFloatingWindow()
        serviceScope.launch {
            delay(1100)
            voiceCaptureState.value = QuickMemoVoiceCaptureState()
        }
    }

    private fun handleExternalVoiceError(message: String) {
        voiceConfirmJob?.cancel()
        voiceConfirmJob = null
        voiceStartJob?.cancel()
        voiceCaptureState.value = QuickMemoVoiceCaptureState(
            status = QuickMemoVoiceCaptureStatus.ERROR,
            message = message.ifBlank { "录音失败" }
        )
        showFloatingWindow()
        serviceScope.launch {
            delay(1400)
            voiceCaptureState.value = QuickMemoVoiceCaptureState()
        }
    }

    private fun startVoiceForeground() {
        if (voiceForegroundActive) return
        val notification = buildVoiceCaptureNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NotificationIds.QUICK_MEMO_VOICE_CAPTURE,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NotificationIds.QUICK_MEMO_VOICE_CAPTURE, notification)
        }
        voiceForegroundActive = true
    }

    private fun stopVoiceForeground() {
        if (!voiceForegroundActive) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        voiceForegroundActive = false
    }

    private fun buildVoiceCaptureNotification(): Notification {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            NotificationIds.QUICK_MEMO_VOICE_CAPTURE,
            tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, App.CHANNEL_ID_POPUP)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle("正在录制随口记")
            .setContentText("松开音量+保存录音")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun requestRecordAudioPermission() {
        try {
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_REQUEST_RECORD_AUDIO_PERMISSION, true)
            })
        } catch (e: Exception) {
            Log.w(TAG, "无法打开麦克风权限引导", e)
        }
    }

    private fun confirmVoiceCapture(asTodo: Boolean, closeAfterSave: Boolean = false) {
        val current = voiceCaptureState.value
        if (current.status != QuickMemoVoiceCaptureStatus.CONFIRMING) return
        val path = current.tempAudioPath?.takeIf { it.isNotBlank() } ?: return
        voiceConfirmJob?.cancel()
        voiceConfirmJob = null
        serviceScope.launch {
            try {
                voiceCaptureState.value = current.copy(status = QuickMemoVoiceCaptureStatus.SAVING)
                withContext(Dispatchers.IO) {
                    quickMemoCenter.createVoiceMemo(
                        audioPath = path,
                        durationMs = current.durationMs,
                        asTodo = asTodo
                    )
                }
                voiceCaptureState.value = QuickMemoVoiceCaptureState(status = QuickMemoVoiceCaptureStatus.SAVED)
                Toast.makeText(applicationContext, "已保存到随口记", Toast.LENGTH_SHORT).show()
                if (closeAfterSave) {
                    finishClose()
                } else {
                    delay(1100)
                    voiceCaptureState.value = QuickMemoVoiceCaptureState()
                }
            } catch (e: Exception) {
                Log.e(TAG, "保存语音随口记失败", e)
                withContext(Dispatchers.IO) { File(path).delete() }
                voiceCaptureState.value = QuickMemoVoiceCaptureState(
                    status = QuickMemoVoiceCaptureStatus.ERROR,
                    message = "保存失败"
                )
                delay(1400)
                voiceCaptureState.value = QuickMemoVoiceCaptureState()
                if (closeAfterSave) finishClose()
            }
        }
    }

    private fun performServiceHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(35)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "随口记录音震动反馈失败", e)
        }
    }

    private fun startScreenshotAnalysisFlow(onComplete: () -> Unit) {
        if (pendingImagePickCompletion != null) return
        pendingImagePickCompletion = onComplete
        hideFloatingWindow()

        serviceScope.launch {
            var accessibilityService = TextAccessibilityService.instance
            if (accessibilityService == null) {
                com.antgskds.calendarassistant.core.util.AccessibilityGuardian.restoreIfNeeded(this@FloatingScheduleService)
                accessibilityService = TextAccessibilityService.instance
            }

            if (accessibilityService != null) {
                Log.d(TAG, "无障碍服务可用，开始截屏识屏")
                val delayMs = MySettings.normalizeScreenshotDelayMs(settingsQueryApi.settings.value.screenshotDelayMs)
                accessibilityService.startAnalysis(delayMs.milliseconds)
                finishPendingImagePick()
                requestClose()
                return@launch
            } else {
                Log.w(TAG, "无障碍服务不可用，回退到图片选择")
                Toast.makeText(applicationContext, "未开启无障碍服务，使用图片选择", Toast.LENGTH_SHORT).show()
                try {
                    val intent = Intent(this@FloatingScheduleService, ImagePickHandleActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start image picker", e)
                    finishPendingImagePick()
                }
            }
        }
    }

    private fun handlePickedImage(uri: Uri) {
        serviceScope.launch {
            val settings = settingsQueryApi.settings.value
            if (!settings.isRecognitionConfigReady()) {
                Toast.makeText(applicationContext, settings.recognitionConfigMissingMessage(), Toast.LENGTH_SHORT).show()
                finishPendingImagePick()
                return@launch
            }

            val imageFile = ImageImportUtils.createImportedImageFile(this@FloatingScheduleService)
            val copied = withContext(Dispatchers.IO) {
                ImageImportUtils.copyUriToFile(this@FloatingScheduleService, uri, imageFile)
            }
            if (!copied) {
                Toast.makeText(applicationContext, "图片读取失败", Toast.LENGTH_SHORT).show()
                finishPendingImagePick()
                return@launch
            }

            val bitmap = withContext(Dispatchers.IO) {
                ImageImportUtils.decodeSampledBitmapFromFile(imageFile)
            }
            if (bitmap == null) {
                Toast.makeText(applicationContext, "图片解码失败", Toast.LENGTH_SHORT).show()
                finishPendingImagePick()
                return@launch
            }

            val traceId = EventIdentity.newTraceId("floating_image")
            val result = withContext(Dispatchers.IO) {
                app.recognitionCenter.analyzeImage(
                    bitmap = bitmap,
                    settings = settings,
                    context = applicationContext,
                    sourceType = RECOGNITION_SOURCE_TYPE,
                    sourceId = RECOGNITION_SOURCE_ID,
                    sourceImagePath = imageFile.absolutePath,
                    ingestRequested = true,
                    traceId = traceId
                )
            }
            bitmap.recycle()
            when (result) {
                is AnalysisResult.Success -> Toast.makeText(applicationContext, "识别完成，正在保存...", Toast.LENGTH_SHORT).show()
                is AnalysisResult.Empty -> Unit
                is AnalysisResult.Failure -> Unit
            }
            finishPendingImagePick()
        }
    }

    // ... 下面的业务逻辑部分保持不变 ...

    private fun handleManualInput(
        text: String,
        isQuickMemo: Boolean = false,
        sourceImagePath: String? = null,
        onComplete: () -> Unit = {}
    ) {
        if (text.isBlank()) {
            onComplete()
            return
        }
        serviceScope.launch {
            try {
                val settings = settingsQueryApi.settings.value
                if (isQuickMemo) {
                    val clean = text.trim()
                    withContext(Dispatchers.IO) {
                        quickMemoCenter.createTextMemo(clean)
                    }
                    Toast.makeText(applicationContext, "已保存到随口记", Toast.LENGTH_SHORT).show()
                } else {
                    val traceId = EventIdentity.newTraceId("floating")
                    val result = withContext(Dispatchers.IO) {
                        app.recognitionCenter.parseUserText(
                            text = text,
                            settings = settings,
                            context = applicationContext,
                            sourceType = RECOGNITION_SOURCE_TYPE,
                            sourceId = RECOGNITION_SOURCE_ID,
                            sourceImagePath = sourceImagePath,
                            ingestRequested = true,
                            traceId = traceId
                        )
                    }
                    when (result) {
                        is AnalysisResult.Success -> Toast.makeText(applicationContext, "识别完成，正在保存...", Toast.LENGTH_SHORT).show()
                        is AnalysisResult.Empty -> Unit
                        is AnalysisResult.Failure -> Unit
                    }
                }
                // 收起输入法
                hideInputMethod()
                onComplete()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse manual input", e)
                hideInputMethod()
                Toast.makeText(applicationContext, "分析失败：${e.message ?: "unknown"}", Toast.LENGTH_SHORT).show()
                onComplete()
            }
        }
    }

    private fun hideInputMethod() {
        try {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            composeView?.let { view ->
                imm.hideSoftInputFromWindow(view.windowToken, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to hide input method", e)
        }
    }

    private fun handleUpdateScheduleItem(
        item: ScheduleDisplayItem,
        patch: EventPatch,
        onComplete: () -> Unit
    ) {
        serviceScope.launch {
            try {
                val enrichedPatch = enrichPatchForItem(item, patch)
                when (val target = item.action) {
                    is ScheduleDisplayItem.ActionTarget.Single -> {
                        scheduleCenter.updateSingleFromPatch(target.eventId, enrichedPatch)
                        Toast.makeText(applicationContext, "已更新", Toast.LENGTH_SHORT).show()
                    }
                    is ScheduleDisplayItem.ActionTarget.RecurringOccurrence -> {
                        scheduleCenter.editRecurringFromPatch(
                            parentId = target.parentId,
                            occurrenceTs = target.occurrenceTs,
                            mode = RecurringMode.THIS,
                            patch = enrichedPatch
                        )
                        Toast.makeText(applicationContext, "已更新本次实例", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update schedule item", e)
                Toast.makeText(applicationContext, "更新失败", Toast.LENGTH_SHORT).show()
            } finally {
                onComplete()
            }
        }
    }

    private fun enrichPatchForItem(item: ScheduleDisplayItem, patch: EventPatch): EventPatch {
        val baseEvent = when (val target = item.action) {
            is ScheduleDisplayItem.ActionTarget.Single -> scheduleCenter.events.value.find { it.id == target.eventId }
            is ScheduleDisplayItem.ActionTarget.RecurringOccurrence -> scheduleCenter.events.value.find { it.id == target.parentId }
        }
        return if (baseEvent == null) {
            patch
        } else {
            patch.copy(
                tag = baseEvent.tag,
                color = baseEvent.color,
                rrule = baseEvent.rrule,
                reminder1Minutes = baseEvent.reminder1Minutes,
                reminder2Minutes = baseEvent.reminder2Minutes,
                reminder3Minutes = baseEvent.reminder3Minutes
            )
        }
    }

    private fun convertToEvent(eventData: RecognitionDraft, sourceImagePath: String?): Event {
        val settings = settingsQueryApi.settings.value
        return com.antgskds.calendarassistant.core.ai.convertDraftToEvent(
            eventData,
            sourceImagePath,
            defaultDurationMinutes = settings.defaultEventDurationMinutes,
            forceInstantCodeTimeToNow = settings.forceInstantCodeTimeToNow
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        sendBroadcast(Intent(ACTION_FLOATING_SHOWN))
        when (intent?.action) {
            ACTION_IMAGE_PICKED -> {
                showFloatingWindow()
                val uriStr = intent.getStringExtra(EXTRA_IMAGE_URI)
                if (uriStr.isNullOrBlank()) {
                    finishPendingImagePick()
                    return START_NOT_STICKY
                }
                handlePickedImage(Uri.parse(uriStr))
                return START_NOT_STICKY
            }
            ACTION_IMAGE_PICK_CANCELLED -> {
                showFloatingWindow()
                finishPendingImagePick()
                return START_NOT_STICKY
            }
            ACTION_PREPARE_VOICE_CAPTURE -> {
                detachFloatingWindowForVoice()
                return START_NOT_STICKY
            }
            ACTION_VOICE_CAPTURE_RECORDING -> {
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
                handleExternalVoiceRecording()
                return START_NOT_STICKY
            }
            ACTION_VOICE_CAPTURE_COMPLETED -> {
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
                handleExternalVoiceCompleted(
                    path = intent.getStringExtra(EXTRA_VOICE_AUDIO_PATH),
                    durationMs = intent.getLongExtra(EXTRA_VOICE_DURATION_MS, 0L)
                )
                return START_NOT_STICKY
            }
            ACTION_VOICE_CAPTURE_TOO_SHORT -> {
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
                handleExternalVoiceTooShort()
                return START_NOT_STICKY
            }
            ACTION_VOICE_CAPTURE_ERROR -> {
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
                handleExternalVoiceError(intent.getStringExtra(EXTRA_VOICE_ERROR_MESSAGE).orEmpty())
                return START_NOT_STICKY
            }
            ACTION_START_VOICE_CAPTURE -> {
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
                startVoiceCapture()
                return START_NOT_STICKY
            }
            ACTION_STOP_VOICE_CAPTURE -> {
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
                stopVoiceCapture()
                return START_NOT_STICKY
            }
        }

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        showFloatingWindow()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        isShowing = false
        sendBroadcast(Intent(ACTION_FLOATING_HIDDEN))
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        serviceScope.cancel()
        audioPlaybackCenter.stop()
        audioRecorder.stopAndDiscard()
        voiceConfirmJob?.cancel()
        voiceStartJob?.cancel()
        recognitionFailedSubscriptionJob?.cancel()
        recognitionFailedSubscriptionJob = null
        ingestSucceededSubscriptionJob?.cancel()
        ingestSucceededSubscriptionJob = null
        ingestFailedSubscriptionJob?.cancel()
        ingestFailedSubscriptionJob = null
        composeView?.let { view ->
            if (view.isAttachedToWindow) {
                try { windowManager.removeView(view) } catch (e: Exception) {}
            }
        }
        try {
            unregisterReceiver(closeReceiver)
        } catch (e: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun requestClose() {
        if (voiceCaptureState.value.status == QuickMemoVoiceCaptureStatus.CONFIRMING) {
            confirmVoiceCapture(asTodo = false, closeAfterSave = true)
            return
        }
        if (voiceCaptureState.value.status == QuickMemoVoiceCaptureStatus.RECORDING) {
            audioRecorder.stopAndDiscard()
            voiceCaptureState.value = QuickMemoVoiceCaptureState()
        }
        finishClose()
    }

    private fun finishClose() {
        sendBroadcast(Intent(ACTION_FLOATING_HIDDEN))
        stopSelf()
    }
}
