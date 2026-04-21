package com.antgskds.calendarassistant.service.floating

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.core.ai.AnalysisResult
import com.antgskds.calendarassistant.core.ai.activeAiConfig
import com.antgskds.calendarassistant.core.ai.isConfigured
import com.antgskds.calendarassistant.core.ai.missingConfigMessage
import com.antgskds.calendarassistant.core.center.ScheduleCenter
import com.antgskds.calendarassistant.core.event.DomainEventType
import com.antgskds.calendarassistant.core.event.EventIdentity
import com.antgskds.calendarassistant.core.event.events.IngestFailedEvent
import com.antgskds.calendarassistant.core.event.events.IngestSucceededEvent
import com.antgskds.calendarassistant.core.event.events.RecognitionFailedEvent
import com.antgskds.calendarassistant.core.note.createNoteEvent
import com.antgskds.calendarassistant.core.query.ScheduleQueryApi
import com.antgskds.calendarassistant.core.query.SettingsQueryApi
import com.antgskds.calendarassistant.core.query.WeatherQueryApi
import com.antgskds.calendarassistant.core.weather.hasWeatherConfig
import com.antgskds.calendarassistant.core.service.image.ImagePickHandleActivity
import com.antgskds.calendarassistant.core.util.ImageImportUtils
import com.antgskds.calendarassistant.data.model.CalendarEventData
import com.antgskds.calendarassistant.data.model.EventTags
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.service.accessibility.TextAccessibilityService
import com.antgskds.calendarassistant.ui.floating.FloatingScheduleScreen
import com.antgskds.calendarassistant.ui.theme.CalendarAssistantTheme
import com.antgskds.calendarassistant.ui.theme.EventColors
import com.antgskds.calendarassistant.ui.theme.ThemeColorScheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class FloatingScheduleService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    companion object {
        private const val TAG = "FloatingScheduleService"
        private const val RECOGNITION_SOURCE_TYPE = "floating"
        private const val RECOGNITION_SOURCE_ID = "floating.manual_input"

        const val ACTION_IMAGE_PICKED = "com.antgskds.calendarassistant.floating.action.IMAGE_PICKED"
        const val ACTION_IMAGE_PICK_CANCELLED = "com.antgskds.calendarassistant.floating.action.IMAGE_PICK_CANCELLED"
        const val ACTION_FLOATING_SHOWN = "com.antgskds.calendarassistant.floating.action.SHOWN"
        const val ACTION_FLOATING_HIDDEN = "com.antgskds.calendarassistant.floating.action.HIDDEN"
        const val EXTRA_IMAGE_URI = "extra_image_uri"

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

    private val app by lazy { applicationContext as App }
    private val scheduleCenter: ScheduleCenter by lazy { app.scheduleCenter }
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
                        payload.message.ifBlank { "分析失败" },
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

                // 根据悬浮窗日程范围设置过滤事件
                val today = LocalDate.now()
                val tomorrow = today.plusDays(1)
                val noteEvents = if (settings.noteEnabled) {
                    events.filter { it.tag == EventTags.NOTE && it.archivedAt == null }
                        .sortedWith(compareBy<MyEvent> { it.isCompleted }.thenByDescending { it.lastModified })
                } else {
                    emptyList()
                }
                val filteredEvents = when (settings.floatingEventRange) {
                    1 -> events.filter { it.tag != EventTags.NOTE && (it.startDate == today || it.endDate == today) }
                    2 -> events.filter {
                        it.tag != EventTags.NOTE && (
                            it.startDate == today || it.startDate == tomorrow ||
                                it.endDate == today || it.endDate == tomorrow
                        )
                    }
                    else -> events.filter { it.tag != EventTags.NOTE } // 0 = 全部日程
                }

                val isDarkTheme = when (settings.themeMode) {
                    1 -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
                    2 -> false
                    3 -> true
                    else -> false
                }
                val themeColorSchemeEnum = ThemeColorScheme.fromName(settings.themeColorScheme)

                CalendarAssistantTheme(
                    darkTheme = isDarkTheme,
                    dynamicColor = themeColorSchemeEnum == ThemeColorScheme.DEFAULT,
                    themeColorScheme = themeColorSchemeEnum
                ) {
                    FloatingScheduleScreen(
                        events = filteredEvents,
                        noteEvents = noteEvents,
                        weatherData = if (settings.hasWeatherConfig() && settings.showWeatherInFloating) weatherData else null,
                        noteEnabled = settings.noteEnabled,
                        onClose = { requestClose() },
                        onManualInput = { text, isNote, onComplete ->
                            handleManualInput(text = text, isNote = isNote, sourceImagePath = null, onComplete = onComplete)
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
                        onEventAction = { eventId, actionType ->
                            handleEventAction(eventId, actionType)
                        },
                        onUndo = { eventId, _ ->
                            serviceScope.launch {
                                scheduleCenter.performPrimaryRuleAction(eventId)
                            }
                        },
                        onDeleteNote = { note, onComplete ->
                            serviceScope.launch {
                                try {
                                    scheduleCenter.deleteEvent(note.id)
                                } finally {
                                    onComplete()
                                }
                            }
                        },
                        onRestoreNote = { note, onComplete ->
                            serviceScope.launch {
                                try {
                                    scheduleCenter.addEvent(note)
                                } finally {
                                    onComplete()
                                }
                            }
                        },
                        onLoadingChange = { loading -> 
                            // 状态由 FloatingScheduleScreen 管理，这里可以留空或用于其他同步
                        }
                    )
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

    private fun showFloatingWindow() {
        val view = composeView ?: return
        val params = windowLayoutParams ?: return
        if (!permissionCenter.canDrawOverlays(this)) return

        if (!isViewAttached || !view.isAttachedToWindow) {
            try {
                windowManager.addView(view, params)
                isViewAttached = true
            } catch (e: Exception) {
                Log.e(TAG, "showFloatingWindow addView failed", e)
                isViewAttached = false
                return
            }
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
                accessibilityService.startAnalysis(500.milliseconds)
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
            val config = settings.activeAiConfig()
            if (!config.isConfigured()) {
                Toast.makeText(applicationContext, config.missingConfigMessage(), Toast.LENGTH_SHORT).show()
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

            val ocrText = withContext(Dispatchers.IO) {
                app.recognitionCenter.recognizeText(bitmap)
            }
            bitmap.recycle()

            if (ocrText.isBlank()) {
                Toast.makeText(applicationContext, "OCR 结果为空", Toast.LENGTH_SHORT).show()
                finishPendingImagePick()
                return@launch
            }

            handleManualInput(
                text = ocrText,
                sourceImagePath = imageFile.absolutePath,
                onComplete = ::finishPendingImagePick
            )
        }
    }

    // ... 下面的业务逻辑部分保持不变 ...

    private fun handleManualInput(
        text: String,
        isNote: Boolean = false,
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
                if (isNote) {
                    val note = convertToNote(text)
                    scheduleCenter.addEvent(note)
                    Toast.makeText(applicationContext, "便签已添加", Toast.LENGTH_SHORT).show()
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
                        is AnalysisResult.Success -> {
                            Toast.makeText(applicationContext, "识别完成，正在保存...", Toast.LENGTH_SHORT).show()
                        }
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

    private fun handleEventAction(eventId: String, actionType: String) {
        serviceScope.launch {
            try {
                when (actionType) {
                    "archive" -> {
                        scheduleCenter.archiveEvent(eventId)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(applicationContext, "已归档", Toast.LENGTH_SHORT).show()
                        }
                    }
                    else -> scheduleCenter.performPrimaryRuleAction(eventId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle event action", e)
            }
        }
    }

    private fun convertToMyEvent(eventData: CalendarEventData, sourceImagePath: String?): MyEvent {
        val now = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        var startDateTime = try {
            if (eventData.startTime.isNotBlank()) LocalDateTime.parse(eventData.startTime, formatter) else now
        } catch (e: Exception) { now }
        var endDateTime = try {
            if (eventData.endTime.isNotBlank()) LocalDateTime.parse(eventData.endTime, formatter) else startDateTime.plusHours(1)
        } catch (e: Exception) { startDateTime.plusHours(1) }

        val resolvedTag = when {
            eventData.tag.isNotBlank() && eventData.tag != EventTags.GENERAL -> eventData.tag
            eventData.type == "pickup" -> EventTags.PICKUP
            else -> eventData.tag
        }.ifBlank { EventTags.GENERAL }

        return MyEvent(
            id = UUID.randomUUID().toString(),
            title = eventData.title.trim(),
            startDate = startDateTime.toLocalDate(),
            endDate = endDateTime.toLocalDate(),
            startTime = startDateTime.format(timeFormatter),
            endTime = endDateTime.format(timeFormatter),
            location = eventData.location,
            description = eventData.description,
            color = EventColors[scheduleCenter.events.value.size % EventColors.size],
            sourceImagePath = sourceImagePath,
            tag = resolvedTag
        )
    }

    private fun convertToNote(text: String): MyEvent {
        val clean = text.trim()
        val lines = clean.lines().map { it.trim() }.filter { it.isNotBlank() }
        val titleSeed = lines.firstOrNull().orEmpty().ifBlank { clean }
        val title = titleSeed.take(24).ifBlank { "便签" }
        return createNoteEvent(
            title = title,
            markdown = clean,
            color = EventColors[scheduleCenter.events.value.size % EventColors.size],
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
        sendBroadcast(Intent(ACTION_FLOATING_HIDDEN))
        stopSelf()
    }
}
