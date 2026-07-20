package com.antgskds.calendarassistant.feature.quickmemo.application

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.MainActivity
import com.antgskds.calendarassistant.R
import com.antgskds.calendarassistant.feature.quickmemo.data.QuickMemoRepository
import com.antgskds.calendarassistant.feature.quickmemo.data.asr.SherpaParaformerTranscriber
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoTranscriptionStatus
import com.antgskds.calendarassistant.feature.quickmemo.domain.transcription.TranscriptionResult
import com.antgskds.calendarassistant.feature.schedule.data.db.EventsDatabase
import com.antgskds.calendarassistant.platform.notification.alarmlegacy.NotificationIds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger

class QuickMemoTranscriptionService : Service() {
    companion object {
        private const val TAG = "QuickMemoTranscriptionSvc"
        private const val ACTION_TRANSCRIBE = "com.antgskds.calendarassistant.quickmemo.action.TRANSCRIBE"
        const val ACTION_TRANSCRIPTION_SUCCEEDED = "com.antgskds.calendarassistant.quickmemo.action.TRANSCRIPTION_SUCCEEDED"
        const val ACTION_TRANSCRIPTION_FAILED = "com.antgskds.calendarassistant.quickmemo.action.TRANSCRIPTION_FAILED"
        const val EXTRA_MEMO_ID = "extra_memo_id"
        const val EXTRA_AUDIO_PATH = "extra_audio_path"
        const val EXTRA_AUTO_PIN = "extra_auto_pin"
        const val EXTRA_TEXT = "extra_text"
        private const val TRANSCRIPTION_TIMEOUT_MS = 120_000L

        fun enqueue(context: Context, memoId: Long, audioPath: String, autoPin: Boolean) {
            val intent = Intent(context, QuickMemoTranscriptionService::class.java).apply {
                action = ACTION_TRANSCRIBE
                putExtra(EXTRA_MEMO_ID, memoId)
                putExtra(EXTRA_AUDIO_PATH, audioPath)
                putExtra(EXTRA_AUTO_PIN, autoPin)
            }
            ContextCompat.startForegroundService(context.applicationContext, intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeJobs = AtomicInteger(0)
    private val repository: QuickMemoRepository by lazy {
        QuickMemoRepository(EventsDatabase.getInstance(applicationContext).quickMemoDao())
    }
    private val transcriber by lazy { SherpaParaformerTranscriber(applicationContext) }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_TRANSCRIBE) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val memoId = intent.getLongExtra(EXTRA_MEMO_ID, -1L)
        val audioPath = intent.getStringExtra(EXTRA_AUDIO_PATH).orEmpty()
        val autoPin = intent.getBooleanExtra(EXTRA_AUTO_PIN, false)
        Log.i(TAG, "onStartCommand memoId=$memoId audioPath=$audioPath autoPin=$autoPin startId=$startId")
        if (memoId <= 0L || audioPath.isBlank()) {
            Log.w(TAG, "ignore invalid transcription request memoId=$memoId audioBlank=${audioPath.isBlank()}")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        activeJobs.incrementAndGet()
        startTranscriptionForeground()
        serviceScope.launch {
            try {
                transcribeMemo(memoId, audioPath, autoPin)
            } finally {
                if (activeJobs.decrementAndGet() <= 0) {
                    stopTranscriptionForeground()
                    stopSelf(startId)
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun transcribeMemo(memoId: Long, audioPath: String, autoPin: Boolean) {
        try {
            Log.i(TAG, "transcribeMemo start memoId=$memoId audioPath=$audioPath")
            val memo = repository.getQuickMemo(memoId)
            if (memo == null || memo.audioPath != audioPath) {
                Log.w(TAG, "memo invalid before transcription memoId=$memoId exists=${memo != null} currentAudio=${memo?.audioPath}")
                notifyFailed(memoId)
                return
            }
            repository.updateTranscriptionStatus(memoId, QuickMemoTranscriptionStatus.PROCESSING)
            when (val result = withTimeout(TRANSCRIPTION_TIMEOUT_MS) { transcriber.transcribe(audioPath) }) {
                is TranscriptionResult.Success -> {
                    if (repository.getQuickMemo(memoId)?.audioPath != audioPath) {
                        Log.w(TAG, "memo audio changed after transcription memoId=$memoId")
                        notifyFailed(memoId)
                        return
                    }
                    val text = result.text.trim()
                    if (text.isBlank()) {
                        Log.w(TAG, "transcription blank memoId=$memoId")
                        repository.updateTranscriptionStatus(memoId, QuickMemoTranscriptionStatus.FAILED)
                        notifyFailed(memoId)
                    } else {
                        Log.i(TAG, "transcription succeeded memoId=$memoId textLen=${text.length}")
                        repository.updateTranscriptionStatus(memoId, QuickMemoTranscriptionStatus.SUCCESS, text)
                        notifySucceeded(memoId, text, autoPin)
                    }
                }
                is TranscriptionResult.Failure -> {
                    Log.w(TAG, "transcription failed memoId=$memoId retryable=${result.retryable}: ${result.message}")
                    repository.updateTranscriptionStatus(memoId, QuickMemoTranscriptionStatus.FAILED)
                    notifyFailed(memoId)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "transcribe memo failed", e)
            runCatching { repository.updateTranscriptionStatus(memoId, QuickMemoTranscriptionStatus.FAILED) }
            notifyFailed(memoId)
        }
    }

    private fun notifySucceeded(memoId: Long, text: String, autoPin: Boolean) {
        Log.i(TAG, "notify transcription succeeded memoId=$memoId textLen=${text.length} autoPin=$autoPin")
        sendBroadcast(Intent(ACTION_TRANSCRIPTION_SUCCEEDED).apply {
            setClass(this@QuickMemoTranscriptionService, QuickMemoTranscriptionResultReceiver::class.java)
            putExtra(EXTRA_MEMO_ID, memoId)
            putExtra(EXTRA_TEXT, text)
            putExtra(EXTRA_AUTO_PIN, autoPin)
        })
    }

    private fun notifyFailed(memoId: Long) {
        Log.w(TAG, "notify transcription failed memoId=$memoId")
        sendBroadcast(Intent(ACTION_TRANSCRIPTION_FAILED).apply {
            setClass(this@QuickMemoTranscriptionService, QuickMemoTranscriptionResultReceiver::class.java)
            putExtra(EXTRA_MEMO_ID, memoId)
        })
    }

    private fun startTranscriptionForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NotificationIds.QUICK_MEMO_TRANSCRIPTION_SERVICE,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NotificationIds.QUICK_MEMO_TRANSCRIPTION_SERVICE, notification)
        }
    }

    private fun stopTranscriptionForeground() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        }
    }

    private fun buildNotification(): Notification {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            NotificationIds.QUICK_MEMO_TRANSCRIPTION_SERVICE,
            tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, App.CHANNEL_ID_POPUP)
            .setSmallIcon(R.drawable.ic_stat_quickmemo)
            .setContentTitle("转写中")
            .setContentText("随口记")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
