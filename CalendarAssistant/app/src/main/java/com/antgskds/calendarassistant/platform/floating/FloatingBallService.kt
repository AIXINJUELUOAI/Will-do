package com.antgskds.calendarassistant.platform.floating

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.ui.graphics.toArgb
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.MainActivity
import com.antgskds.calendarassistant.core.service.shortcut.ShortcutHandleActivity
import com.antgskds.calendarassistant.data.model.FloatingBallGestureAction
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.ui.theme.ThemeColorGenerator
import com.antgskds.calendarassistant.ui.theme.ThemeColorScheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class FloatingBallService : Service() {
    companion object {
        private const val TAG = "FloatingBallService"
        private const val LONG_PRESS_MS = 520L
        private const val TAP_WINDOW_MS = 320L
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val app by lazy { applicationContext as App }
    private val permissionCenter by lazy { app.permissionCenter }
    private val settingsQueryApi by lazy { app.settingsQueryApi }
    private lateinit var windowManager: WindowManager
    private var ballView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var hiddenByFloating = false
    private var tapCount = 0
    private var tapJob: Job? = null
    private var longPressVoiceActive = false
    private var toggleVoiceActive = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                FloatingScheduleService.ACTION_FLOATING_SHOWN -> {
                    hiddenByFloating = true
                    updateVisibility()
                }
                FloatingScheduleService.ACTION_FLOATING_HIDDEN -> {
                    hiddenByFloating = false
                    updateVisibility()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (!permissionCenter.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val filter = IntentFilter().apply {
            addAction(FloatingScheduleService.ACTION_FLOATING_SHOWN)
            addAction(FloatingScheduleService.ACTION_FLOATING_HIDDEN)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
        serviceScope.launch {
            settingsQueryApi.settings.collect { settings ->
                if (!settings.isFloatingWindowEnabled || !settings.floatingBallEnabled) {
                    removeBallView()
                    stopSelf()
                    return@collect
                }
                ensureBallView(settings)
                applySettings(settings)
            }
        }
    }

    override fun onDestroy() {
        tapJob?.cancel()
        if (longPressVoiceActive || toggleVoiceActive) {
            app.floatingCenter.stopVoiceCaptureService()
        }
        removeBallView()
        serviceScope.cancel()
        try {
            unregisterReceiver(receiver)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureBallView(settings: MySettings) {
        if (ballView != null) return
        hiddenByFloating = FloatingScheduleService.isShowing
        val view = View(this)
        view.setOnTouchListener(createTouchListener())
        ballView = view
        val params = createLayoutParams(settings)
        layoutParams = params
        try {
            windowManager.addView(view, params)
        } catch (e: Exception) {
            Log.e(TAG, "addView failed", e)
        }
        updateGestureExclusion()
        updateVisibility()
    }

    private fun applySettings(settings: MySettings) {
        val view = ballView ?: return
        val params = layoutParams ?: return
        val sizePx = dpToPx(settings.floatingBallSizeDp.toFloat())
        params.width = sizePx
        params.height = sizePx
        params.gravity = Gravity.TOP or Gravity.START
        params.x = percentToX(settings.floatingBallXPercent, sizePx)
        params.y = percentToY(settings.floatingBallYPercent, sizePx)
        view.background = buildBackground(settings, sizePx)
        view.setOnTouchListener(createTouchListener())
        try {
            windowManager.updateViewLayout(view, params)
        } catch (e: Exception) {
            Log.w(TAG, "updateViewLayout failed", e)
        }
        updateGestureExclusion()
    }

    private fun createLayoutParams(settings: MySettings): WindowManager.LayoutParams {
        val sizePx = dpToPx(settings.floatingBallSizeDp.toFloat())
        return WindowManager.LayoutParams(
            sizePx,
            sizePx,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = percentToX(settings.floatingBallXPercent, sizePx)
            y = percentToY(settings.floatingBallYPercent, sizePx)
        }
    }

    private fun createTouchListener(): View.OnTouchListener {
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var dragged = false
        var longTriggered = false
        var longPressJob: Job? = null
        val touchSlop = dpToPx(8f)

        return View.OnTouchListener { _, event ->
            val params = layoutParams ?: return@OnTouchListener true
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragged = false
                    longTriggered = false
                    longPressJob?.cancel()
                    longPressJob = serviceScope.launch {
                        delay(LONG_PRESS_MS)
                        if (!dragged) {
                            longTriggered = true
                            performHaptic(HapticFeedbackConstants.LONG_PRESS)
                            performGestureAction(settingsQueryApi.settings.value.floatingBallLongPressAction, fromLongPress = true)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!longTriggered && kotlin.math.hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                        dragged = true
                        longPressJob?.cancel()
                        params.x = (startX + dx.toInt()).coerceIn(0, (getScreenWidth() - params.width).coerceAtLeast(0))
                        params.y = (startY + dy.toInt()).coerceIn(0, (getScreenHeight() - params.height).coerceAtLeast(0))
                        runCatching { windowManager.updateViewLayout(ballView, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    longPressJob?.cancel()
                    when {
                        longTriggered -> {
                            if (longPressVoiceActive) {
                                app.floatingCenter.stopVoiceCaptureService()
                                longPressVoiceActive = false
                            }
                        }
                        dragged -> persistPosition(params)
                        event.actionMasked != MotionEvent.ACTION_CANCEL -> handleTap()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun handleTap() {
        tapCount += 1
        tapJob?.cancel()
        tapJob = serviceScope.launch {
            delay(TAP_WINDOW_MS)
            val settings = settingsQueryApi.settings.value
            val action = if (tapCount >= 2) settings.floatingBallDoubleTapAction else settings.floatingBallSingleTapAction
            tapCount = 0
            if (FloatingBallGestureAction.normalize(action) != FloatingBallGestureAction.NONE) {
                performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
            }
            performGestureAction(action, fromLongPress = false)
        }
    }

    private fun performGestureAction(action: Int, fromLongPress: Boolean) {
        when (FloatingBallGestureAction.normalize(action)) {
            FloatingBallGestureAction.OPEN_FLOATING_SCHEDULE -> app.floatingCenter.startFloatingService(FloatingScheduleService.INPUT_MODE_SCHEDULE)
            FloatingBallGestureAction.OPEN_QUICK_MEMO -> app.floatingCenter.startFloatingService(FloatingScheduleService.INPUT_MODE_NOTE)
            FloatingBallGestureAction.QUICK_RECOGNITION -> startQuickRecognition()
            FloatingBallGestureAction.QUICK_MEMO_RECORDING -> startOrToggleVoiceCapture(fromLongPress)
            FloatingBallGestureAction.OPEN_APP_HOME -> openAppHome()
            else -> Unit
        }
    }

    private fun startOrToggleVoiceCapture(fromLongPress: Boolean) {
        if (!settingsQueryApi.settings.value.voiceInputEnabled) {
            Toast.makeText(applicationContext, "请先开启随口记", Toast.LENGTH_SHORT).show()
            return
        }
        if (fromLongPress) {
            longPressVoiceActive = app.floatingCenter.startVoiceCaptureService()
            return
        }
        if (toggleVoiceActive) {
            app.floatingCenter.stopVoiceCaptureService()
            toggleVoiceActive = false
        } else {
            toggleVoiceActive = app.floatingCenter.startVoiceCaptureService()
        }
    }

    private fun startQuickRecognition() {
        try {
            startActivity(Intent(this, ShortcutHandleActivity::class.java).apply {
                action = ShortcutHandleActivity.ACTION_QUICK_CAPTURE
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            })
        } catch (e: Exception) {
            Log.e(TAG, "start quick recognition failed", e)
        }
    }

    private fun openAppHome() {
        try {
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            })
        } catch (e: Exception) {
            Log.e(TAG, "open home failed", e)
        }
    }

    private fun persistPosition(params: WindowManager.LayoutParams) {
        val maxX = (getScreenWidth() - params.width).coerceAtLeast(1)
        val maxY = (getScreenHeight() - params.height).coerceAtLeast(1)
        val xPercent = params.x.toFloat() / maxX * 100f
        val yPercent = params.y.toFloat() / maxY * 100f
        val current = settingsQueryApi.settings.value
        app.settingsOperationApi.updateSettings(
            current.copy(
                floatingBallXPercent = xPercent.coerceIn(0f, 100f),
                floatingBallYPercent = yPercent.coerceIn(0f, 100f)
            )
        )
    }

    private fun buildBackground(settings: MySettings, sizePx: Int): GradientDrawable {
        val baseColor = resolveThemeColor(settings)
        val alpha = (settings.floatingBallAlpha.coerceIn(0.2f, 1f) * 255).toInt()
        val color = Color.argb(alpha, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke((sizePx * 0.04f).toInt().coerceAtLeast(1), Color.argb(80, 255, 255, 255))
        }
    }

    private fun resolveThemeColor(settings: MySettings): Int {
        val schemeName = settings.themeColorScheme
        if (schemeName == ThemeColorScheme.DEFAULT.name && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return getColor(android.R.color.system_accent1_500)
        }
        val darkTheme = when (settings.themeMode) {
            1 -> resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
            2 -> false
            3 -> true
            else -> false
        }
        val seed = ThemeColorScheme.fromName(schemeName).primaryColor
        return ThemeColorGenerator.generateColorScheme(seed, darkTheme, schemeName).primary.toArgb()
    }

    private fun updateGestureExclusion() {
        val view = ballView ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        view.post {
            val width = view.width
            val height = view.height
            if (width > 0 && height > 0) view.systemGestureExclusionRects = listOf(Rect(0, 0, width, height))
        }
    }

    private fun updateVisibility() {
        ballView?.visibility = if (hiddenByFloating) View.GONE else View.VISIBLE
    }

    private fun removeBallView() {
        val view = ballView ?: return
        try {
            windowManager.removeView(view)
        } catch (_: Exception) {
        }
        ballView = null
        layoutParams = null
    }

    private fun percentToX(percent: Float, widthPx: Int): Int {
        val maxX = (getScreenWidth() - widthPx).coerceAtLeast(0)
        return ((percent.coerceIn(0f, 100f) / 100f) * maxX).toInt()
    }

    private fun percentToY(percent: Float, heightPx: Int): Int {
        val maxY = (getScreenHeight() - heightPx).coerceAtLeast(0)
        return ((percent.coerceIn(0f, 100f) / 100f) * maxY).toInt()
    }

    private fun getScreenHeight(): Int = resources.displayMetrics.heightPixels
    private fun getScreenWidth(): Int = resources.displayMetrics.widthPixels

    private fun performHaptic(feedbackConstant: Int) {
        if (!settingsQueryApi.settings.value.hapticFeedbackEnabled) return
        ballView?.performHapticFeedback(feedbackConstant)
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics).toInt()
    }
}
