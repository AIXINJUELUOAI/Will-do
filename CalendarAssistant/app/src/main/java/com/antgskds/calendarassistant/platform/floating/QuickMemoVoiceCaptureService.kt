package com.antgskds.calendarassistant.platform.floating

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.MainActivity
import com.antgskds.calendarassistant.R
import com.antgskds.calendarassistant.core.quickmemo.audio.QuickMemoAudioRecorder
import com.antgskds.calendarassistant.platform.notification.alarmlegacy.NotificationIds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class QuickMemoVoiceCaptureService : Service() {
    companion object {
        private const val TAG = "QuickMemoVoiceCapture"
        const val ACTION_START = "com.antgskds.calendarassistant.voice_capture.START"
        const val ACTION_STOP = "com.antgskds.calendarassistant.voice_capture.STOP"
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val app by lazy { applicationContext as App }
    private val recorder by lazy { QuickMemoAudioRecorder(applicationContext) }
    private var recording = false
    private var starting = false
    private var stopRequested = false
    private var startedAt = 0L
    private var tickerJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startCapture()
            ACTION_STOP -> stopCapture()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        tickerJob?.cancel()
        runCatching { recorder.stopAndDiscard() }
        app.capsuleCommandApi.clearQuickMemoRecording()
        stopVoiceForeground()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startCapture() {
        if (recording || starting) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(applicationContext, "需要麦克风权限", Toast.LENGTH_SHORT).show()
            requestRecordAudioPermission()
            stopSelf()
            return
        }

        starting = true
        stopRequested = false
        startedAt = System.currentTimeMillis()
        updateRecordingStatus(0L)
        serviceScope.launch {
            try {
                withContext(Dispatchers.IO) { recorder.start() }
                startedAt = System.currentTimeMillis()
                starting = false
                recording = true
                updateRecordingStatus()
                startTicker()
                if (stopRequested) stopCapture()
            } catch (e: Exception) {
                Log.e(TAG, "start failed", e)
                clearRecordingStatus("录音失败")
            }
        }
    }

    private fun stopCapture() {
        if (starting && !recording) {
            stopRequested = true
            return
        }
        if (!recording) return
        recording = false
        starting = false
        tickerJob?.cancel()
        tickerJob = null
        serviceScope.launch {
            updateRecordingNotification("正在保存...", "随口记录音")
            app.capsuleCommandApi.showQuickMemoRecording("正在保存...", "随口记录音")
            try {
                val result = withContext(Dispatchers.IO) { recorder.stop() }
                if (result == null || result.durationMs < QuickMemoAudioRecorder.MIN_RECORDING_MS) {
                    result?.path?.let { path -> withContext(Dispatchers.IO) { runCatching { File(path).delete() } } }
                    clearRecordingStatus("录音太短")
                    return@launch
                }
                withContext(Dispatchers.IO) {
                    val settings = app.settingsQueryApi.settings.value
                    app.quickMemoCenter.createVoiceMemo(
                        audioPath = result.path,
                        durationMs = result.durationMs,
                        asTodo = false,
                        autoPinOnTranscriptionSuccess = settings.voiceQuickMemoAutoPinEnabled
                    )
                }
                clearRecordingStatus("已保存随口记")
            } catch (e: Exception) {
                Log.e(TAG, "stop failed", e)
                withContext(Dispatchers.IO) { recorder.stopAndDiscard() }
                clearRecordingStatus("录音失败")
            }
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            while (recording) {
                updateRecordingStatus()
                delay(1000)
            }
        }
    }

    private fun updateRecordingStatus(elapsedMs: Long = System.currentTimeMillis() - startedAt) {
        val title = "录音中：${formatRecordingTime(elapsedMs)}"
        updateRecordingNotification(title, "松开保存")
        app.capsuleCommandApi.showQuickMemoRecording(title, "松开保存")
    }

    private fun updateRecordingNotification(title: String, content: String) {
        val notification = buildRecordingNotification(title, content)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NotificationIds.QUICK_MEMO_VOICE_CAPTURE, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NotificationIds.QUICK_MEMO_VOICE_CAPTURE, notification)
        }
    }

    private fun buildRecordingNotification(title: String, content: String): Notification {
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

    private fun clearRecordingStatus(toast: String) {
        starting = false
        recording = false
        stopRequested = false
        startedAt = 0L
        tickerJob?.cancel()
        tickerJob = null
        app.capsuleCommandApi.clearQuickMemoRecording()
        stopVoiceForeground()
        Toast.makeText(applicationContext, toast, Toast.LENGTH_SHORT).show()
        stopSelf()
    }

    private fun stopVoiceForeground() {
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

    private fun requestRecordAudioPermission() {
        try {
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_REQUEST_RECORD_AUDIO_PERMISSION, true)
            })
        } catch (e: Exception) {
            Log.w(TAG, "cannot open permission guide", e)
        }
    }

    private fun formatRecordingTime(elapsedMs: Long): String {
        val totalSeconds = (elapsedMs / 1000L).coerceAtLeast(0L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return "%02d:%02d".format(minutes, seconds)
    }
}
