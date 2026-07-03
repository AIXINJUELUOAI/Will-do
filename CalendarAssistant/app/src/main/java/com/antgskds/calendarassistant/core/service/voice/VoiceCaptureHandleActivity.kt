package com.antgskds.calendarassistant.core.service.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.antgskds.calendarassistant.core.quickmemo.audio.QuickMemoAudioRecorder
import com.antgskds.calendarassistant.platform.floating.FloatingScheduleService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VoiceCaptureHandleActivity : ComponentActivity() {
    private val recorder by lazy { QuickMemoAudioRecorder(this) }
    private var startIssued = false
    private var recording = false
    private var stopRequested = false
    private var stopping = false
    private var completed = false
    private val startRunnable = Runnable { startRecording() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        active = this
        launchPending = false
        showVoiceCaptureUi()
        if (pendingStop) {
            pendingStop = false
            requestStop()
            return
        }
        overridePendingTransition(0, 0)
        prepareFloatingWindowForVoice()
        scheduleVoiceCaptureStart(RESUME_START_DELAY_MS)
    }

    override fun onPostResume() {
        super.onPostResume()
        scheduleVoiceCaptureStart(RESUME_START_DELAY_MS)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) scheduleVoiceCaptureStart(FOCUS_START_DELAY_MS)
    }

    private fun scheduleVoiceCaptureStart(delayMs: Long) {
        if (completed || startIssued || isFinishing) return
        window.decorView.removeCallbacks(startRunnable)
        window.decorView.postDelayed(startRunnable, delayMs)
    }

    private fun prepareFloatingWindowForVoice() {
        if (!FloatingScheduleService.isShowing) return
        try {
            startService(
                Intent(this, FloatingScheduleService::class.java).apply {
                    action = FloatingScheduleService.ACTION_PREPARE_VOICE_CAPTURE
                }
            )
        } catch (e: Exception) {
            Log.w(TAG, "Prepare floating window for voice failed", e)
        }
    }

    private fun startRecording() {
        if (completed || startIssued || isFinishing) return
        if (stopRequested) {
            Log.i(TAG, "Voice capture cancelled before recorder start")
            completed = true
            dispatchVoiceTooShort()
            finishSoon()
            return
        }
        startIssued = true
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            dispatchVoiceError("需要麦克风权限")
            finishSoon()
            return
        }
        lifecycleScope.launch {
            try {
                recorder.start()
                recording = true
                dispatchVoiceRecording()
                performHaptic()
                Toast.makeText(applicationContext, "正在录音，松开音量+保存", Toast.LENGTH_SHORT).show()
                if (stopRequested) stopRecording()
            } catch (e: Exception) {
                Log.e(TAG, "Activity voice recording start failed", e)
                dispatchVoiceError("录音失败")
                finishSoon()
            }
        }
    }

    private fun requestStop() {
        if (completed || stopping || isFinishing) return
        if (!recording) {
            stopRequested = true
            if (!startIssued) {
                window.decorView.removeCallbacks(startRunnable)
                Log.i(TAG, "Voice capture stopped before recorder start")
                completed = true
                dispatchVoiceTooShort()
                finishSoon()
            }
            return
        }
        stopRecording()
    }

    private fun showVoiceCaptureUi() {
        window.statusBarColor = Color.rgb(255, 248, 232)
        window.navigationBarColor = Color.rgb(255, 248, 232)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        val density = resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(28), dp(28), dp(28))
            setBackgroundColor(Color.rgb(255, 248, 232))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(26), dp(28), dp(24))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(28).toFloat()
            }
            elevation = dp(8).toFloat()
        }
        val title = TextView(this).apply {
            text = "正在准备录音"
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(40, 32, 24))
            gravity = Gravity.CENTER
        }
        val message = TextView(this).apply {
            text = "请继续按住音量+\n松开后保存随口记"
            textSize = 16f
            setTextColor(Color.rgb(94, 75, 55))
            gravity = Gravity.CENTER
            setLineSpacing(dp(3).toFloat(), 1.0f)
        }
        val hint = TextView(this).apply {
            text = "这是系统可见的录音页面"
            textSize = 13f
            setTextColor(Color.rgb(143, 116, 82))
            gravity = Gravity.CENTER
        }

        card.addView(title)
        card.addView(message, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(14) })
        card.addView(hint, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(18) })
        root.addView(card, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        setContentView(root)
    }

    private fun stopRecording() {
        if (completed || stopping) return
        stopping = true
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { recorder.stop() }
                recording = false
                completed = true
                performHaptic()
                if (result == null || result.durationMs < QuickMemoAudioRecorder.MIN_RECORDING_MS) {
                    result?.path?.let { path -> withContext(Dispatchers.IO) { runCatching { java.io.File(path).delete() } } }
                    dispatchVoiceTooShort()
                } else {
                    dispatchVoiceCompleted(result.path, result.durationMs)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Activity voice recording stop failed", e)
                dispatchVoiceError("录音失败")
            } finally {
                finishSoon()
            }
        }
    }

    private fun dispatchVoiceRecording() {
        dispatchToFloatingService(
            Intent(this, FloatingScheduleService::class.java).apply {
                action = FloatingScheduleService.ACTION_VOICE_CAPTURE_RECORDING
            }
        )
    }

    private fun dispatchVoiceCompleted(path: String, durationMs: Long) {
        dispatchToFloatingService(
            Intent(this, FloatingScheduleService::class.java).apply {
                action = FloatingScheduleService.ACTION_VOICE_CAPTURE_COMPLETED
                putExtra(FloatingScheduleService.EXTRA_VOICE_AUDIO_PATH, path)
                putExtra(FloatingScheduleService.EXTRA_VOICE_DURATION_MS, durationMs)
            }
        )
    }

    private fun dispatchVoiceTooShort() {
        dispatchToFloatingService(
            Intent(this, FloatingScheduleService::class.java).apply {
                action = FloatingScheduleService.ACTION_VOICE_CAPTURE_TOO_SHORT
            }
        )
    }

    private fun dispatchVoiceError(message: String) {
        dispatchToFloatingService(
            Intent(this, FloatingScheduleService::class.java).apply {
                action = FloatingScheduleService.ACTION_VOICE_CAPTURE_ERROR
                putExtra(FloatingScheduleService.EXTRA_VOICE_ERROR_MESSAGE, message)
            }
        )
    }

    private fun dispatchToFloatingService(intent: Intent) {
        try {
            startService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Dispatch voice state to floating service failed", e)
        }
    }

    private fun performHaptic() {
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
            Log.w(TAG, "Voice capture haptic failed", e)
        }
    }

    private fun finishSoon() {
        window.decorView.postDelayed({
            if (!isFinishing) {
                finish()
                overridePendingTransition(0, 0)
            }
        }, FINISH_DELAY_MS)
    }

    override fun onDestroy() {
        window.decorView.removeCallbacks(startRunnable)
        if (!completed && !stopping && (startIssued || recording)) {
            runCatching { recorder.stopAndDiscard() }
        }
        if (active === this) active = null
        super.onDestroy()
        overridePendingTransition(0, 0)
    }

    companion object {
        private const val TAG = "VoiceCaptureHandleActivity"
        private const val FOCUS_START_DELAY_MS = 240L
        private const val RESUME_START_DELAY_MS = 420L
        private const val FINISH_DELAY_MS = 260L
        @Volatile private var active: VoiceCaptureHandleActivity? = null
        @Volatile private var pendingStop: Boolean = false
        @Volatile private var launchPending: Boolean = false

        fun prepareForLaunch() {
            launchPending = true
            pendingStop = false
        }

        fun clearPendingLaunch() {
            launchPending = false
            pendingStop = false
        }

        fun stopActiveCapture(): Boolean {
            val activity = active
            if (activity == null) {
                if (!launchPending) return false
                pendingStop = true
                return true
            }
            activity.runOnUiThread { activity.requestStop() }
            return true
        }
    }
}
