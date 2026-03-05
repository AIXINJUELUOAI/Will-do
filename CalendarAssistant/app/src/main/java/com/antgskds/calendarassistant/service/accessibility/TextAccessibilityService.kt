package com.antgskds.calendarassistant.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.MainActivity
import com.antgskds.calendarassistant.R
import com.antgskds.calendarassistant.core.ai.RecognitionProcessor
import com.antgskds.calendarassistant.data.model.CalendarEventData
import com.antgskds.calendarassistant.data.model.EventTags
import com.antgskds.calendarassistant.data.model.EventType
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.service.notification.NotificationScheduler
import com.antgskds.calendarassistant.service.floating.FloatingScheduleService
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class TextAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var analysisJob: Job? = null

    // 用于处理音量键长按的 Job
    private var volumeLongPressJob: Job? = null
    // 标记是否已经触发了长按事件
    private var isLongPressTriggered = false

    private val NOTIFICATION_ID_PROGRESS = 1001
    private val NOTIFICATION_ID_RESULT = 2002

    private val repository by lazy { (applicationContext as App).repository }
    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    companion object {
        private const val TAG = "TextAccessibilityService"
        private const val ACTION_CANCEL_ANALYSIS = "ACTION_CANCEL_ANALYSIS"
        const val ACTION_CLOSE_FLOATING = "com.antgskds.calendarassistant.ACTION_CLOSE_FLOATING"
        @Volatile var instance: TextAccessibilityService? = null
            private set
        private val isAnalyzing = AtomicBoolean(false)
        private const val LONG_PRESS_THRESHOLD = 500L
    }

    private var launcherPackageName: String? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        launcherPackageName = getLauncherPackageName()
        Log.d(TAG, "无障碍服务已连接")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val currentSettings = try {
            repository.settings.value
        } catch (e: Exception) {
            return super.onKeyEvent(event)
        }

        if (!currentSettings.isFloatingWindowEnabled) {
            return super.onKeyEvent(event)
        }

        // 1. 如果悬浮窗已显示，完全放行所有按键，确保用户可以正常调节音量或进行其他操作
        if (FloatingScheduleService.isShowing) {
            Log.d(TAG, "悬浮窗已显示，放行按键")
            return super.onKeyEvent(event)
        }

        // 2. 监听音量加键 (KEYCODE_VOLUME_UP)
        // 采用“完全拦截 + 手动补偿”策略，解决长按时音量暴增的问题
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    // 如果是重复的 DOWN 事件（物理按住不放时系统会发送多个 DOWN），只处理第一次
                    if (event.repeatCount == 0) {
                        isLongPressTriggered = false
                        volumeLongPressJob?.cancel()

                        // 启动计时协程
                        volumeLongPressJob = serviceScope.launch {
                            delay(LONG_PRESS_THRESHOLD)
                            // 延时结束，说明用户按住超过了阈值，触发长按逻辑
                            isLongPressTriggered = true
                            Log.d(TAG, "长按音量+ 已确认，触发悬浮窗")

                            // 触发轻微震动反馈，告诉用户“功能已激活，可以松手了”
                            performHapticFeedback()

                            startFloatingService()
                        }
                    }
                    // 拦截事件：告诉系统“我处理了”，系统就不会增加音量
                    return true
                }

                KeyEvent.ACTION_UP -> {
                    // 取消长按计时
                    volumeLongPressJob?.cancel()

                    if (isLongPressTriggered) {
                        // 如果之前已经触发了长按逻辑（悬浮窗已打开），这里什么都不做
                        Log.d(TAG, "音量+ 抬起 (长按处理完毕)")
                    } else {
                        // 如果没触发长按，说明这是一次短按
                        // 手动补偿：调用系统 API 增加音量
                        Log.d(TAG, "音量+ 抬起 (短按)，模拟系统音量增加")
                        try {
                            audioManager.adjustStreamVolume(
                                AudioManager.STREAM_MUSIC,
                                AudioManager.ADJUST_RAISE,
                                AudioManager.FLAG_SHOW_UI
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "模拟调节音量失败", e)
                        }
                    }

                    // 重置状态
                    isLongPressTriggered = false
                    // 拦截事件：防止系统处理 UP 事件导致意外行为
                    return true
                }
            }
        }

        // 3. 音量减 (KEYCODE_VOLUME_DOWN) 及其他按键
        // 直接放行 (return super)，恢复系统默认行为
        // 这样可以保留“电源+音量减”截图功能，也可以保留长按音量减快速静音的功能
        return super.onKeyEvent(event)
    }

    private fun performHapticFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val vibrator = vibratorManager.defaultVibrator
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    vibrator.vibrate(50)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "震动反馈失败", e)
        }
    }

    private fun startFloatingService() {
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "悬浮窗权限未授予，无法启动悬浮窗")
            showResultNotification("悬浮窗权限未授予", "请在设置中开启悬浮窗权限")
            return
        }
        serviceScope.launch {
            val intent = Intent(this@TextAccessibilityService, FloatingScheduleService::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startService(intent)
        }
    }

    private fun getLauncherPackageName(): String? {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL_ANALYSIS) {
            cancelCurrentAnalysis()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun cancelCurrentAnalysis() {
        analysisJob?.cancel()
        cancelProgressNotification()
    }

    fun closeNotificationPanel(): Boolean {
        // 第1层：API 12+ 专用 API（针对 Pixel/三星/类原生）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        }

        // 第2层：发送系统广播（针对老版本系统或华为/魅族）
        try {
            sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
        } catch (e: Exception) {
            Log.w(TAG, "发送系统广播失败: ${e.message}")
        }

        // 第3层：模拟返回键 + 150ms 延时（针对 OPPO/vivo/小米 HyperOS 的终极杀招）
        Handler(Looper.getMainLooper()).postDelayed({
            performGlobalAction(GLOBAL_ACTION_BACK)
        }, 150)

        return true
    }

    fun startAnalysis(delayDuration: Duration = 500.milliseconds, fromShortcut: Boolean = false) {
        if (!isAnalyzing.compareAndSet(false, true)) {
            Log.d(TAG, "已有分析任务在执行中，跳过本次请求")
            return
        }
        analysisJob?.cancel()
        analysisJob = serviceScope.launch {
            try {
                delay(delayDuration)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    takeScreenshotAndAnalyze()
                } else {
                    showResultNotification("系统版本过低", "截图功能需要 Android 11+")
                }
            } finally {
                isAnalyzing.set(false)
            }
        }
    }

    private fun takeScreenshotAndAnalyze() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        showProgressNotification("Will do", "正在分析屏幕内容...")
        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshotResult: ScreenshotResult) {
                    analysisJob = serviceScope.launch(Dispatchers.IO) {
                        processScreenshot(screenshotResult)
                    }
                }
                override fun onFailure(errorCode: Int) {
                    cancelProgressNotification()
                    showResultNotification("截图失败", "错误码: $errorCode")
                }
            }
        )
    }

    private suspend fun processScreenshot(result: ScreenshotResult) {
        try {
            val hardwareBuffer = result.hardwareBuffer
            val colorSpace = result.colorSpace
            val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
            if (bitmap == null) {
                cancelProgressNotification()
                return
            }

            val imagesDir = File(filesDir, "event_screenshots")
            if (!imagesDir.exists()) imagesDir.mkdirs()
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val imageFile = File(imagesDir, "IMG_$timestamp.jpg")

            val softwareBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            bitmap.recycle()
            hardwareBuffer.close()

            FileOutputStream(imageFile).use { out ->
                softwareBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }

            val settings = repository.settings.value
            if (settings.modelKey.isBlank()) {
                withContext(Dispatchers.Main) {
                    cancelProgressNotification()
                    showResultNotification("配置缺失", "请先填写 API Key", autoLaunch = true)
                }
                softwareBitmap.recycle()
                return
            }

            val eventsList = RecognitionProcessor.analyzeImage(softwareBitmap, settings, this)
            softwareBitmap.recycle()

            val validEvents = eventsList.filter { it.title.isNotBlank() }
            val addedEvents = if (validEvents.isNotEmpty()) saveEventsLocally(validEvents, imageFile.absolutePath) else emptyList()

            withContext(Dispatchers.Main) {
                cancelProgressNotification()
                if (validEvents.isEmpty()) {
                    showResultNotification("分析完成", "未识别到有效日程")
                    return@withContext
                }
                if (addedEvents.isNotEmpty()) {
                    val count = addedEvents.size
                    val title = if (count == 1) "新事项已添加" else "添加了 $count 个新事项"
                    val content = if (count == 1) {
                        val e = addedEvents.first()
                        "${e.description}(${e.startTime})"
                    } else {
                        addedEvents.joinToString("，") { it.title }
                    }
                    showResultNotification(title, content)
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "处理截图出错", e)
            withContext(Dispatchers.Main) {
                cancelProgressNotification()
                showResultNotification("分析出错", "错误: ${e.message}")
            }
        }
    }

    private suspend fun saveEventsLocally(aiEvents: List<CalendarEventData>, imagePath: String): List<MyEvent> {
        val actuallyAdded = mutableListOf<MyEvent>()
        val currentEvents = repository.events.value
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        aiEvents.forEachIndexed { index, aiEvent ->
            try {
                val now = LocalDateTime.now()
                var startDateTime = try {
                    if (aiEvent.startTime.isNotBlank()) LocalDateTime.parse(aiEvent.startTime, formatter) else now
                } catch (e: Exception) { now }

                var endDateTime = try {
                    if (aiEvent.endTime.isNotBlank()) LocalDateTime.parse(aiEvent.endTime, formatter) else startDateTime.plusHours(1)
                } catch (e: Exception) { startDateTime.plusHours(1) }

                val finalTag = aiEvent.tag.ifBlank { EventTags.GENERAL }
                val newEventTitle = aiEvent.title.trim()

                val currentRepositoryEvents = repository.events.value

                val isDuplicate = currentRepositoryEvents.any { existing ->
                    val isExpired = existing.endDate.isBefore(java.time.LocalDate.now())
                    if (isExpired) return@any false

                    existing.startDate == startDateTime.toLocalDate() &&
                            existing.startTime == startDateTime.format(timeFormatter) &&
                            existing.title.trim().equals(newEventTitle, ignoreCase = true)
                }

                if (isDuplicate) return@forEachIndexed

                val newEvent = MyEvent(
                    id = UUID.randomUUID().toString(),
                    title = newEventTitle,
                    startDate = startDateTime.toLocalDate(),
                    endDate = endDateTime.toLocalDate(),
                    startTime = startDateTime.format(timeFormatter),
                    endTime = endDateTime.format(timeFormatter),
                    location = aiEvent.location,
                    description = aiEvent.description,
                    color = com.antgskds.calendarassistant.ui.theme.EventColors[currentEvents.size % com.antgskds.calendarassistant.ui.theme.EventColors.size],
                    sourceImagePath = imagePath,
                    eventType = EventType.EVENT,
                    tag = finalTag.ifBlank { EventTags.GENERAL }
                )
                actuallyAdded.add(newEvent)

                NotificationScheduler.scheduleReminders(this, newEvent)
            } catch (e: Exception) {
                Log.e(TAG, "保存单个事件失败", e)
            }
        }

        if (actuallyAdded.isNotEmpty()) {
            actuallyAdded.forEach { event ->
                repository.addEvent(event)
                NotificationScheduler.scheduleReminders(this, event)
            }
        }
        return actuallyAdded
    }

    private fun showProgressNotification(title: String, content: String) {
        showBaseNotification(NOTIFICATION_ID_PROGRESS, title, content, isProgress = true, autoLaunch = false)
    }

    private fun cancelProgressNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID_PROGRESS)
    }

    private fun showResultNotification(title: String, content: String, autoLaunch: Boolean = false) {
        showBaseNotification(NOTIFICATION_ID_RESULT, title, content, isProgress = false, autoLaunch = autoLaunch)
    }

    private fun showBaseNotification(id: Int, title: String, content: String, isProgress: Boolean, autoLaunch: Boolean) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, App.CHANNEL_ID_POPUP)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle(title)
            .setContentText(content)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        if (autoLaunch || !isProgress) builder.setContentIntent(pendingIntent)
        if (isProgress) {
            builder.setProgress(0, 0, true)
            builder.setOngoing(true)
        }
        manager.notify(id, builder.build())
    }
}