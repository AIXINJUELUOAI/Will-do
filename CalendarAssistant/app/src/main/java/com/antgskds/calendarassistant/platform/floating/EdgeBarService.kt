package com.antgskds.calendarassistant.platform.floating

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
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
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.MainActivity
import com.antgskds.calendarassistant.R
import com.antgskds.calendarassistant.core.quickmemo.audio.QuickMemoAudioRecorder
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.data.model.QuickMemoRecordingDisplayMode
import com.antgskds.calendarassistant.platform.notification.alarmlegacy.NotificationIds
import com.antgskds.calendarassistant.ui.theme.ThemeColorScheme
import com.antgskds.calendarassistant.ui.theme.ThemeColorGenerator
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class EdgeBarService : Service() {

    companion object {
        private const val TAG = "EdgeBarService"
        private const val SIDE_RIGHT = "RIGHT"
        private const val SIDE_LEFT = "LEFT"
        private const val LONG_PRESS_MS = 520L
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var windowManager: WindowManager
    private var barView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var hiddenByFloating = false
    private val app by lazy { applicationContext as App }
    private val permissionCenter by lazy { app.permissionCenter }
    private val settingsQueryApi by lazy { app.settingsQueryApi }
    private val quickMemoCenter by lazy { app.quickMemoCenter }
    private val edgeAudioRecorder by lazy { QuickMemoAudioRecorder(applicationContext) }
    private var edgeVoiceStartJob: Job? = null
    private var edgeVoiceTickerJob: Job? = null
    private var edgeVoiceStopJob: Job? = null
    private var edgeVoiceStartedAt: Long = 0L
    private var edgeVoiceStarting = false
    private var edgeVoiceRecording = false
    private var edgeVoiceStopRequested = false
    private var edgeVoiceUsingFloatingWindow = false

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
                if (!settings.edgeBarEnabled || !settings.isFloatingWindowEnabled) {
                    removeBarView()
                    stopSelf()
                    return@collect
                }
                ensureBarView(settings)
                applySettings(settings)
            }
        }
    }

    override fun onDestroy() {
        edgeVoiceStartJob?.cancel()
        edgeVoiceTickerJob?.cancel()
        edgeVoiceStopJob?.cancel()
        runCatching { edgeAudioRecorder.stopAndDiscard() }
        app.capsuleCommandApi.clearQuickMemoRecording()
        stopEdgeRecordingForeground()
        removeBarView()
        serviceScope.cancel()
        try {
            unregisterReceiver(receiver)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureBarView(settings: MySettings) {
        if (barView != null) return
        hiddenByFloating = FloatingScheduleService.isShowing
        val view = View(this)
        view.setOnTouchListener(createTouchListener(settings))
        barView = view
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
        val view = barView ?: return
        val params = layoutParams ?: return
        val widthPx = dpToPx(settings.edgeBarWidthDp.toFloat())
        val heightPx = dpToPx(settings.edgeBarHeightDp.toFloat())
        params.width = widthPx
        params.height = heightPx
        params.gravity = Gravity.TOP or Gravity.START
        params.x = computeX(settings.edgeBarSide, widthPx)
        params.y = computeY(settings.edgeBarYPercent, heightPx)

        view.background = buildBackground(settings, widthPx, heightPx)

        view.setOnTouchListener(createTouchListener(settings))
        try {
            windowManager.updateViewLayout(view, params)
        } catch (e: Exception) {
            Log.w(TAG, "updateViewLayout failed", e)
        }
        updateGestureExclusion()
    }

    private fun updateGestureExclusion() {
        val view = barView ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        view.post {
            val width = view.width
            val height = view.height
            if (width <= 0 || height <= 0) return@post
            view.systemGestureExclusionRects = listOf(Rect(0, 0, width, height))
        }
    }

    private fun updateVisibility() {
        val view = barView ?: return
        view.visibility = if (hiddenByFloating) View.GONE else View.VISIBLE
    }

    private fun removeBarView() {
        val view = barView ?: return
        try {
            windowManager.removeView(view)
        } catch (_: Exception) {
        }
        barView = null
        layoutParams = null
    }

    private fun createLayoutParams(settings: MySettings): WindowManager.LayoutParams {
        val widthPx = dpToPx(settings.edgeBarWidthDp.toFloat())
        val heightPx = dpToPx(settings.edgeBarHeightDp.toFloat())
        return WindowManager.LayoutParams(
            widthPx,
            heightPx,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = computeX(settings.edgeBarSide, widthPx)
            y = computeY(settings.edgeBarYPercent, heightPx)
        }
    }

    private fun buildBackground(settings: MySettings, widthPx: Int, heightPx: Int): GradientDrawable {
        val baseColor = resolveThemeColor(settings)
        val alpha = (settings.edgeBarAlpha.coerceIn(0f, 1f) * 255).toInt()
        val color = Color.argb(alpha, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dpToPx(4f).toFloat()
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
        val colorScheme = ThemeColorGenerator.generateColorScheme(seed, darkTheme, schemeName)
        return colorScheme.primary.toArgb()
    }

    private fun createTouchListener(settings: MySettings): View.OnTouchListener {
        var downX = 0f
        var downY = 0f
        val triggerDistance = dpToPx(48f)
        val velocityThreshold = dpToPx(800f)
        val touchSlop = dpToPx(12f)
        var longPressJob: Job? = null
        var voiceCaptureTriggered = false

        return View.OnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    voiceCaptureTriggered = false
                    longPressJob?.cancel()
                    longPressJob = serviceScope.launch {
                        delay(LONG_PRESS_MS)
                        voiceCaptureTriggered = startEdgeVoiceCapture(settings)
                        if (voiceCaptureTriggered) performHaptic(HapticFeedbackConstants.LONG_PRESS)
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!voiceCaptureTriggered && kotlin.math.hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                        longPressJob?.cancel()
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    longPressJob?.cancel()
                    if (voiceCaptureTriggered) {
                        stopEdgeVoiceCapture()
                        voiceCaptureTriggered = false
                        return@OnTouchListener true
                    }

                    if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
                        return@OnTouchListener true
                    }

                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    val dt = (event.eventTime - event.downTime).coerceAtLeast(1)
                    val velocity = kotlin.math.abs(dx) / dt * 1000f

                    val shouldTrigger =
                        kotlin.math.abs(dx) > kotlin.math.abs(dy) &&
                            (kotlin.math.abs(dx) >= triggerDistance || velocity >= velocityThreshold)
                    val isRightSide = settings.edgeBarSide != SIDE_LEFT
                    val directionOk = if (isRightSide) dx < 0 else dx > 0
                    if (shouldTrigger && directionOk && !FloatingScheduleService.isShowing) {
                        performHaptic(HapticFeedbackConstants.GESTURE_START)
                        startFloatingSchedule()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun computeY(percent: Float, heightPx: Int): Int {
        val maxY = (getScreenHeight() - heightPx).coerceAtLeast(0)
        return ((percent.coerceIn(0f, 100f) / 100f) * maxY).toInt()
    }

    private fun computeX(side: String, widthPx: Int): Int {
        return if (side == SIDE_LEFT) {
            0
        } else {
            (getScreenWidth() - widthPx).coerceAtLeast(0)
        }
    }

    private fun startFloatingSchedule(inputMode: String = FloatingScheduleService.INPUT_MODE_SCHEDULE) {
        hiddenByFloating = true
        updateVisibility()
        val intent = Intent(this, FloatingScheduleService::class.java).apply {
            putExtra(FloatingScheduleService.EXTRA_INITIAL_INPUT_MODE, inputMode)
        }
        try {
            startService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "start floating failed", e)
            hiddenByFloating = false
            updateVisibility()
            return
        }
        serviceScope.launch {
            kotlinx.coroutines.delay(500)
            if (!FloatingScheduleService.isShowing) {
                hiddenByFloating = false
                updateVisibility()
            }
        }
    }

    private fun startEdgeVoiceCapture(settings: MySettings): Boolean {
        if (edgeVoiceStarting || edgeVoiceRecording) return true
        if (!settings.voiceInputEnabled) {
            Toast.makeText(applicationContext, "请先在实验室中开启随口记", Toast.LENGTH_SHORT).show()
            return false
        }
        if (!permissionCenter.canDrawOverlays(this)) {
            return false
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_REQUEST_RECORD_AUDIO_PERMISSION, true)
            })
            return false
        }
        if (QuickMemoRecordingDisplayMode.normalize(settings.quickMemoRecordingDisplayMode) == QuickMemoRecordingDisplayMode.FLOATING_WINDOW) {
            edgeVoiceUsingFloatingWindow = true
            startFloatingVoiceCapture()
            return true
        }

        edgeVoiceStopRequested = false
        edgeVoiceUsingFloatingWindow = false
        edgeVoiceStarting = true
        edgeVoiceStartedAt = System.currentTimeMillis()

        return try {
            updateEdgeRecordingStatus(0L)
            edgeVoiceStartJob?.cancel()
            edgeVoiceStartJob = serviceScope.launch {
                try {
                    withContext(Dispatchers.IO) { edgeAudioRecorder.start() }
                    edgeVoiceStartedAt = System.currentTimeMillis()
                    edgeVoiceStarting = false
                    edgeVoiceRecording = true
                    updateEdgeRecordingStatus()
                    startEdgeRecordingTicker()
                    if (edgeVoiceStopRequested) {
                        stopEdgeVoiceCapture()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "start edge recorder failed", e)
                    edgeVoiceStarting = false
                    edgeVoiceRecording = false
                    edgeVoiceStopRequested = false
                    runCatching { edgeAudioRecorder.stopAndDiscard() }
                    app.capsuleCommandApi.clearQuickMemoRecording()
                    stopEdgeRecordingForeground()
                    Toast.makeText(applicationContext, "录音失败", Toast.LENGTH_SHORT).show()
                    restoreEdgeVisibilityAfterRecording()
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "start edge voice capture failed", e)
            edgeVoiceStarting = false
            edgeVoiceRecording = false
            edgeVoiceStopRequested = false
            app.capsuleCommandApi.clearQuickMemoRecording()
            stopEdgeRecordingForeground()
            hiddenByFloating = false
            updateVisibility()
            false
        }
    }

    private fun stopEdgeVoiceCapture() {
        if (edgeVoiceUsingFloatingWindow) {
            stopFloatingVoiceCapture()
            edgeVoiceUsingFloatingWindow = false
            return
        }
        if (edgeVoiceStarting && !edgeVoiceRecording) {
            edgeVoiceStopRequested = true
            return
        }
        if (!edgeVoiceRecording) {
            Log.w(TAG, "edge voice capture stop ignored: no active recording")
            return
        }
        edgeVoiceStopRequested = false
        edgeVoiceRecording = false
        edgeVoiceStarting = false
        edgeVoiceTickerJob?.cancel()
        edgeVoiceTickerJob = null
        edgeVoiceStopJob?.cancel()
        edgeVoiceStopJob = serviceScope.launch {
            updateEdgeRecordingNotification("正在保存...", "随口记录音")
            app.capsuleCommandApi.showQuickMemoRecording("正在保存...", "随口记录音")
            try {
                val result = withContext(Dispatchers.IO) { edgeAudioRecorder.stop() }
                if (result == null || result.durationMs < QuickMemoAudioRecorder.MIN_RECORDING_MS) {
                    result?.path?.let { path -> withContext(Dispatchers.IO) { runCatching { File(path).delete() } } }
                    app.capsuleCommandApi.showQuickMemoRecording("录音太短", "请按住更久")
                    Toast.makeText(applicationContext, "录音太短", Toast.LENGTH_SHORT).show()
                    delay(1000)
                    clearEdgeRecordingStatus()
                    return@launch
                }
                withContext(Dispatchers.IO) {
                    val settings = app.settingsQueryApi.settings.value
                    quickMemoCenter.createVoiceMemo(
                        audioPath = result.path,
                        durationMs = result.durationMs,
                        asTodo = false,
                        autoPinOnTranscriptionSuccess = settings.voiceQuickMemoAutoPinEnabled
                    )
                }
                Toast.makeText(applicationContext, "已保存随口记", Toast.LENGTH_SHORT).show()
                clearEdgeRecordingStatus()
            } catch (e: Exception) {
                Log.e(TAG, "stop edge recorder failed", e)
                withContext(Dispatchers.IO) { edgeAudioRecorder.stopAndDiscard() }
                app.capsuleCommandApi.showQuickMemoRecording("录音失败", "请重试")
                Toast.makeText(applicationContext, "录音失败", Toast.LENGTH_SHORT).show()
                delay(1000)
                clearEdgeRecordingStatus()
            }
        }
    }

    private fun startFloatingVoiceCapture() {
        hiddenByFloating = true
        updateVisibility()
        try {
            startService(Intent(this, FloatingScheduleService::class.java).apply {
                action = FloatingScheduleService.ACTION_START_VOICE_CAPTURE
                putExtra(FloatingScheduleService.EXTRA_INITIAL_INPUT_MODE, FloatingScheduleService.INPUT_MODE_NOTE)
            })
        } catch (e: Exception) {
            Log.e(TAG, "start floating voice capture failed", e)
            hiddenByFloating = false
            updateVisibility()
        }
    }

    private fun stopFloatingVoiceCapture() {
        try {
            startService(Intent(this, FloatingScheduleService::class.java).apply {
                action = FloatingScheduleService.ACTION_STOP_VOICE_CAPTURE
            })
        } catch (e: Exception) {
            Log.e(TAG, "stop floating voice capture failed", e)
        }
        restoreEdgeVisibilityAfterRecording()
    }

    private fun startEdgeRecordingTicker() {
        edgeVoiceTickerJob?.cancel()
        edgeVoiceTickerJob = serviceScope.launch {
            while (edgeVoiceRecording) {
                updateEdgeRecordingStatus()
                delay(1000)
            }
        }
    }

    private fun updateEdgeRecordingStatus(elapsedMs: Long = System.currentTimeMillis() - edgeVoiceStartedAt) {
        val title = "录音中：${formatRecordingTime(elapsedMs)}"
        updateEdgeRecordingNotification(title, "松开保存")
        app.capsuleCommandApi.showQuickMemoRecording(title, "松开保存")
    }

    private fun updateEdgeRecordingNotification(title: String, content: String) {
        val notification = buildEdgeRecordingNotification(title, content)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NotificationIds.QUICK_MEMO_VOICE_CAPTURE,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NotificationIds.QUICK_MEMO_VOICE_CAPTURE, notification)
        }
    }

    private fun buildEdgeRecordingNotification(title: String, content: String): Notification {
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
            .setSmallIcon(R.drawable.ic_stat_recording)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun clearEdgeRecordingStatus() {
        edgeVoiceStarting = false
        edgeVoiceRecording = false
        edgeVoiceStopRequested = false
        edgeVoiceStartedAt = 0L
        edgeVoiceTickerJob?.cancel()
        edgeVoiceTickerJob = null
        app.capsuleCommandApi.clearQuickMemoRecording()
        stopEdgeRecordingForeground()
        restoreEdgeVisibilityAfterRecording()
    }

    private fun stopEdgeRecordingForeground() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Exception) {
        }
    }

    private fun restoreEdgeVisibilityAfterRecording() {
        hiddenByFloating = FloatingScheduleService.isShowing
        updateVisibility()
    }

    private fun formatRecordingTime(elapsedMs: Long): String {
        val totalSeconds = (elapsedMs / 1000L).coerceAtLeast(0L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return "%02d:%02d".format(minutes, seconds)
    }

    private fun getScreenHeight(): Int {
        val metrics = resources.displayMetrics
        return metrics.heightPixels
    }

    private fun getScreenWidth(): Int {
        val metrics = resources.displayMetrics
        return metrics.widthPixels
    }

    private fun performHaptic(feedbackConstant: Int) {
        if (!settingsQueryApi.settings.value.hapticFeedbackEnabled) return
        barView?.performHapticFeedback(feedbackConstant)
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            resources.displayMetrics
        ).toInt()
    }
}
