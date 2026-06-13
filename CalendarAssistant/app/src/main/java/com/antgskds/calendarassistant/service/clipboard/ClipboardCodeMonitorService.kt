package com.antgskds.calendarassistant.service.clipboard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.MainActivity
import com.antgskds.calendarassistant.R
import com.antgskds.calendarassistant.core.util.PrivilegeManager
import com.antgskds.calendarassistant.service.notification.NotificationIds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ClipboardCodeMonitorService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var monitorJob: Job? = null
    private var processHandle: PrivilegeManager.ProcessHandle? = null

    private val app: App get() = applicationContext as App

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!app.settingsQueryApi.settings.value.clipboardCodeRecognitionEnabled) {
            stopSelf()
            return START_NOT_STICKY
        }
        PrivilegeManager.refreshPrivilege()
        if (!PrivilegeManager.hasPrivilege) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NotificationIds.CLIPBOARD_CODE_MONITOR, buildNotification())
        app.clipboardCodeCenter.setPrivilegedMonitorActive(true)
        startMonitor()
        return START_STICKY
    }

    override fun onDestroy() {
        app.clipboardCodeCenter.setPrivilegedMonitorActive(false)
        monitorJob?.cancel()
        processHandle?.destroy()
        processHandle = null
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startMonitor() {
        if (monitorJob?.isActive == true) return
        monitorJob = serviceScope.launch {
            while (app.settingsQueryApi.settings.value.clipboardCodeRecognitionEnabled) {
                PrivilegeManager.refreshPrivilege()
                if (!PrivilegeManager.hasPrivilege) break
                runCatching { tryAutoIngestCurrentClipboard("clipboard_monitor_start") }
                    .onFailure { Log.w(TAG, "Clipboard initial check failed", it) }
                runCatching { monitorLogcatOnce() }
                    .onFailure { Log.w(TAG, "Clipboard log monitor failed", it) }
                delay(RESTART_DELAY_MS)
            }
            app.clipboardCodeCenter.setPrivilegedMonitorActive(false)
            stopSelf()
        }
    }

    private suspend fun monitorLogcatOnce() {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val command = arrayOf("logcat", "-T", timestamp, "ClipboardService:E", "*:S")
        val handle = PrivilegeManager.startPrivilegedProcess(command) ?: return
        processHandle = handle
        try {
            BufferedReader(InputStreamReader(handle.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val current = line ?: continue
                    Log.d(TAG, current)
                    onClipboardChangedLog(current)
                }
            }
        } finally {
            handle.destroy()
            if (processHandle === handle) processHandle = null
        }
    }

    private suspend fun onClipboardChangedLog(line: String) {
        if (line.contains(packageName)) return
        tryAutoIngestCurrentClipboard("clipboard_background")
    }

    private suspend fun tryAutoIngestCurrentClipboard(source: String) {
        val ingested = app.clipboardCodeCenter.autoIngestCurrentClipboard(source)
        if (ingested) {
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(applicationContext, "已创建剪贴板码类日程", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "剪贴板码类识别",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Shizuku/Root 后台监听剪贴板码类内容"
            setShowBadge(false)
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_qs_recognition)
            .setContentTitle("剪贴板码类识别运行中")
            .setContentText("复制取件码、取餐码、取票码、寄件码后将自动入库")
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    companion object {
        private const val TAG = "ClipboardCodeMonitor"
        private const val CHANNEL_ID = "calendar_assistant_clipboard_code_monitor_v1"
        private const val RESTART_DELAY_MS = 3000L

        fun startIfNeeded(context: Context) {
            val appContext = context.applicationContext
            val app = appContext as? App ?: return
            if (!app.settingsQueryApi.settings.value.clipboardCodeRecognitionEnabled) {
                stop(appContext)
                return
            }
            PrivilegeManager.refreshPrivilege()
            if (!PrivilegeManager.hasPrivilege) {
                stop(appContext)
                return
            }
            val intent = Intent(appContext, ClipboardCodeMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.applicationContext.stopService(Intent(context.applicationContext, ClipboardCodeMonitorService::class.java))
        }
    }
}
