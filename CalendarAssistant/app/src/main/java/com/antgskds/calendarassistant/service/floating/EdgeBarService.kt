package com.antgskds.calendarassistant.service.floating

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
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.ui.theme.ThemeColorScheme
import com.antgskds.calendarassistant.ui.theme.ThemeColorGenerator
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class EdgeBarService : Service() {

    companion object {
        private const val TAG = "EdgeBarService"
        private const val SIDE_RIGHT = "RIGHT"
        private const val SIDE_LEFT = "LEFT"
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var windowManager: WindowManager
    private var barView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var hiddenByFloating = false
    private val app by lazy { applicationContext as App }
    private val permissionCenter by lazy { app.permissionCenter }
    private val settingsQueryApi by lazy { app.settingsQueryApi }

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
        params.gravity = if (settings.edgeBarSide == SIDE_LEFT) {
            Gravity.TOP or Gravity.START
        } else {
            Gravity.TOP or Gravity.END
        }
        params.x = 0
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
            gravity = if (settings.edgeBarSide == SIDE_LEFT) {
                Gravity.TOP or Gravity.START
            } else {
                Gravity.TOP or Gravity.END
            }
            x = 0
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

        return View.OnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
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

    private fun startFloatingSchedule() {
        hiddenByFloating = true
        updateVisibility()
        val intent = Intent(this, FloatingScheduleService::class.java)
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

    private fun getScreenHeight(): Int {
        val metrics = resources.displayMetrics
        return metrics.heightPixels
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            resources.displayMetrics
        ).toInt()
    }
}
