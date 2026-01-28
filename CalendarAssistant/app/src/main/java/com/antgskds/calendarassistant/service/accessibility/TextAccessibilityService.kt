package com.antgskds.calendarassistant.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.MainActivity
import com.antgskds.calendarassistant.R
import com.antgskds.calendarassistant.core.ai.RecognitionProcessor
import com.antgskds.calendarassistant.data.model.CalendarEventData
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.service.notification.NotificationScheduler
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class TextAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var analysisJob: Job? = null

    private val NOTIFICATION_ID_PROGRESS = 1001
    private val NOTIFICATION_ID_RESULT = 2002

    private val repository by lazy { (applicationContext as App).repository }

    companion object {
        private const val TAG = "TextAccessibilityService"
        private const val ACTION_CANCEL_ANALYSIS = "ACTION_CANCEL_ANALYSIS"
        @Volatile var instance: TextAccessibilityService? = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "无障碍服务已连接")
        // 胶囊逻辑已移至 CapsuleStateManager，不再需要在此监听事件
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
        return performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
    }

    fun startAnalysis(delayDuration: Duration = 500.milliseconds) {
        analysisJob?.cancel()
        analysisJob = serviceScope.launch {
            delay(delayDuration)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                takeScreenshotAndAnalyze()
            } else {
                showResultNotification("系统版本过低", "截图功能需要 Android 11+")
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

            val eventsList = RecognitionProcessor.analyzeImage(softwareBitmap, settings)
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
                    val isAllPickup = addedEvents.all { it.eventType == "temp" }
                    // 如果全是取件码且开启了实况，则不弹普通通知，直接看实况
                    if (!(settings.isLiveCapsuleEnabled && isAllPickup)) {
                        val count = addedEvents.size
                        val title = if (count == 1) "新事项已添加" else "添加了 $count 个新事项"
                        val content = if (count == 1) {
                            val e = addedEvents.first()
                            if (e.eventType == "temp") "取件码: ${e.description}" else "${e.title} (${e.startTime})"
                        } else {
                            addedEvents.joinToString("，") { it.title }
                        }
                        showResultNotification(title, content)
                    }
                } else {
                    showResultNotification("无新增内容", "所有识别的事件都已存在")
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

        Log.d(TAG, "saveEventsLocally: AI返回了 ${aiEvents.size} 个事件")
        Log.d(TAG, "saveEventsLocally: 当前存储中有 ${currentEvents.size} 个事件")

        aiEvents.forEachIndexed { index, aiEvent ->
            try {
                val now = LocalDateTime.now()
                var startDateTime = try {
                    if (aiEvent.startTime.isNotBlank()) LocalDateTime.parse(aiEvent.startTime, formatter) else now
                } catch (e: Exception) { now }

                var endDateTime = try {
                    if (aiEvent.endTime.isNotBlank()) LocalDateTime.parse(aiEvent.endTime, formatter) else startDateTime.plusHours(1)
                } catch (e: Exception) { startDateTime.plusHours(1) }

                if (aiEvent.type == "pickup") {
                    startDateTime = now
                    endDateTime = now.plusHours(2)
                }

                val finalEventType = if (aiEvent.type == "pickup") "temp" else "event"
                val newEventTitle = aiEvent.title.trim()

                // 修复：使用实时的 repository.events.value 而不是过时的 currentEvents 快照
                // 这确保了即使删除操作刚刚发生，也能获取到最新的数据
                val currentRepositoryEvents = repository.events.value
                Log.d(TAG, "  -> 当前仓库中有 ${currentRepositoryEvents.size} 个事件")

                val isDuplicate = currentRepositoryEvents.any { existing ->
                    val isExpired = existing.endDate.isBefore(LocalDate.now())
                    if (isExpired) return@any false

                    val matches = if (finalEventType == "event") {
                        existing.startDate == startDateTime.toLocalDate() &&
                                existing.startTime == startDateTime.format(timeFormatter) &&
                                existing.title.trim().equals(newEventTitle, ignoreCase = true)
                    } else {
                        // 【修复】对于取件码，使用 title（取件码号码）作为唯一标识
                        // 而不是 description（通常是固定的"快递取件"等文本）
                        existing.eventType == "temp" &&
                                existing.title.trim().equals(newEventTitle, ignoreCase = true)
                    }

                    if (matches && finalEventType == "temp") {
                        Log.d(TAG, "  -> 找到匹配: existing.id=${existing.id}, existing.title='${existing.title}', ai.title='${newEventTitle}'")
                    }
                    matches
                }

                // 添加日志以便调试
                Log.d(TAG, "事件#$index: type=$finalEventType, title=$newEventTitle, desc='${aiEvent.description.trim()}'")
                if (isDuplicate) {
                    Log.d(TAG, "  -> 判定为重复（已存在于仓库中），跳过")
                    return@forEachIndexed
                }

                Log.d(TAG, "  -> 不重复，准备添加新事件")

                // 不重复，创建新事件（先添加到列表，稍后批量提交）
                val newEvent = MyEvent(
                    id = UUID.randomUUID().toString(),
                    title = newEventTitle,
                    startDate = startDateTime.toLocalDate(),
                    endDate = endDateTime.toLocalDate(),
                    startTime = startDateTime.format(timeFormatter),
                    endTime = endDateTime.format(timeFormatter),
                    location = aiEvent.location,
                    description = aiEvent.description,
                    color = com.antgskds.calendarassistant.ui.theme.AppEventColors[currentEvents.size % com.antgskds.calendarassistant.ui.theme.AppEventColors.size],
                    sourceImagePath = imagePath,
                    eventType = finalEventType
                )
                // 【修复】先收集到列表，不立即添加，避免多次触发Flow
                actuallyAdded.add(newEvent)
                Log.d(TAG, "  -> 准备添加新事件: id=${newEvent.id}, title='${newEvent.title}'")

                if (newEvent.eventType == "temp") {
                    NotificationScheduler.scheduleExpiryWarning(this, newEvent)
                } else {
                    NotificationScheduler.scheduleReminders(this, newEvent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "保存单个事件失败", e)
            }
        }

        // 批量添加所有事件（胶囊逻辑已移至 CapsuleStateManager，会自动响应 Flow 变化）
        if (actuallyAdded.isNotEmpty()) {
            Log.d(TAG, "批量添加 ${actuallyAdded.size} 个事件")
            actuallyAdded.forEach { event ->
                repository.addEvent(event)
                // 安排提醒
                if (event.eventType == "temp") {
                    NotificationScheduler.scheduleExpiryWarning(this, event)
                } else {
                    NotificationScheduler.scheduleReminders(this, event)
                }
            }
            Log.d(TAG, "批量添加完成")
        }

        Log.d(TAG, "saveEventsLocally 完成: AI返回${aiEvents.size}个, 成功添加${actuallyAdded.size}个, 跳过${aiEvents.size - actuallyAdded.size}个")
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
            .setSmallIcon(R.drawable.ic_launcher_foreground)
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