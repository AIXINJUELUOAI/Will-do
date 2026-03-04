package com.antgskds.calendarassistant.service.floating

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.antgskds.calendarassistant.core.ai.RecognitionProcessor
import com.antgskds.calendarassistant.data.model.CalendarEventData
import com.antgskds.calendarassistant.data.model.EventTags
import com.antgskds.calendarassistant.data.model.EventType
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.data.repository.AppRepository
import com.antgskds.calendarassistant.service.accessibility.TextAccessibilityService
import com.antgskds.calendarassistant.ui.floating.FloatingScheduleScreen
import com.antgskds.calendarassistant.ui.theme.CalendarAssistantTheme
import com.antgskds.calendarassistant.ui.theme.EventColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class FloatingScheduleService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    companion object {
        private const val TAG = "FloatingScheduleService"
        @Volatile var isShowing: Boolean = false
            private set
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val repository by lazy { AppRepository.getInstance(applicationContext) }

    // 广播接收器
    private val closeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                TextAccessibilityService.ACTION_CLOSE_FLOATING -> {
                    stopSelf()
                }
                Intent.ACTION_CLOSE_SYSTEM_DIALOGS -> {
                    // 监听 Home 键或多任务键，自动关闭悬浮窗
                    val reason = intent.getStringExtra("reason")
                    if (reason == "homekey" || reason == "recentapps") {
                        stopSelf()
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isShowing = true
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

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        initUI()
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
                    stopSelf()
                    return@setOnKeyListener true
                }
                false
            }

            isFocusable = true
            isFocusableInTouchMode = true

            setContent {
                val events by repository.events.collectAsState()

                CalendarAssistantTheme {
                    FloatingScheduleScreen(
                        events = events,
                        onClose = { stopSelf() },
                        onManualInput = { text, onComplete -> 
                            handleManualInput(text, onComplete)
                        },
                        onEventAction = { eventId, actionType ->
                            handleEventAction(eventId, actionType)
                        },
                        onUndo = { eventId ->
                            handleUndo(eventId)
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
        } catch (e: Exception) {
            Log.e(TAG, "UI Init Failed", e)
            stopSelf()
        }
    }

    // ... 下面的业务逻辑部分保持不变 ...

    private fun handleManualInput(text: String, onComplete: () -> Unit = {}) {
        if (text.isBlank()) {
            onComplete()
            return
        }
        serviceScope.launch {
            try {
                val settings = repository.settings.value
                val eventData = withContext(Dispatchers.IO) {
                    RecognitionProcessor.parseUserText(text, settings)
                }
                if (eventData != null) {
                    val event = convertToMyEvent(eventData)
                    repository.addEvent(event)
                }
                // 收起输入法
                hideInputMethod()
                onComplete()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse manual input", e)
                hideInputMethod()
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
                    "checkIn" -> repository.checkInTransport(eventId)
                    "complete" -> repository.completeScheduleEvent(eventId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle event action", e)
            }
        }
    }

    private fun handleUndo(eventId: String) {
        serviceScope.launch {
            try {
                val event = repository.events.value.find { it.id == eventId }
                when {
                    event?.isCheckedIn == true -> repository.undoCheckInTransport(eventId)
                    event?.isCompleted == true -> repository.undoCompleteEvent(eventId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle undo", e)
            }
        }
    }

    private fun convertToMyEvent(eventData: CalendarEventData): MyEvent {
        val now = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        var startDateTime = try {
            if (eventData.startTime.isNotBlank()) LocalDateTime.parse(eventData.startTime, formatter) else now
        } catch (e: Exception) { now }
        var endDateTime = try {
            if (eventData.endTime.isNotBlank()) LocalDateTime.parse(eventData.endTime, formatter) else startDateTime.plusHours(1)
        } catch (e: Exception) { startDateTime.plusHours(1) }
        return MyEvent(
            id = UUID.randomUUID().toString(),
            title = eventData.title.trim(),
            startDate = startDateTime.toLocalDate(),
            endDate = endDateTime.toLocalDate(),
            startTime = startDateTime.format(timeFormatter),
            endTime = endDateTime.format(timeFormatter),
            location = eventData.location,
            description = eventData.description,
            color = EventColors[repository.events.value.size % EventColors.size],
            eventType = EventType.EVENT,
            tag = eventData.tag
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        isShowing = false
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        serviceScope.cancel()
        composeView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) {}
        }
        try {
            unregisterReceiver(closeReceiver)
        } catch (e: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}