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
import com.antgskds.calendarassistant.service.capsule.CapsuleService
import com.antgskds.calendarassistant.service.notification.NotificationScheduler
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class TextAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var analysisJob: Job? = null
    private var eventObservationJob: Job? = null

    private val NOTIFICATION_ID_PROGRESS = 1001
    private val NOTIFICATION_ID_RESULT = 2002

    private val AGGREGATE_ID_STR = "AGGREGATE_PICKUP"
    private val AGGREGATE_NOTIF_ID = 99999

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
        startObservingEvents()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        eventObservationJob?.cancel()
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

    private fun startObservingEvents() {
        eventObservationJob?.cancel()
        eventObservationJob = serviceScope.launch {
            repository.events.collectLatest { events ->
                delay(200) // 稍加延迟，等待数据库和Settings稳定
                Log.d(TAG, "检测到数据源变更，正在刷新胶囊状态... (Events: ${events.size})")
                refreshAllNotifications()
            }
        }
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

    fun refreshCapsuleState() {
        serviceScope.launch(Dispatchers.Main) {
            refreshAllNotifications()
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
                    showResultNotification("无新增内容", "识别记录均为重复项")
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

        aiEvents.forEach { aiEvent ->
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

                val isDuplicate = currentEvents.any { existing ->
                    val isExpired = existing.endDate.isBefore(LocalDate.now())
                    if (isExpired) return@any false

                    if (finalEventType == "event") {
                        existing.startDate == startDateTime.toLocalDate() &&
                                existing.startTime == startDateTime.format(timeFormatter) &&
                                existing.title.trim().equals(newEventTitle, ignoreCase = true)
                    } else {
                        existing.eventType == "temp" &&
                                existing.description.trim() == aiEvent.description.trim()
                    }
                }

                if (!isDuplicate) {
                    val newEvent = MyEvent(
                        id = UUID.randomUUID().toString(),
                        title = newEventTitle,
                        startDate = startDateTime.toLocalDate(),
                        endDate = endDateTime.toLocalDate(),
                        startTime = startDateTime.format(timeFormatter),
                        endTime = endDateTime.format(timeFormatter),
                        location = aiEvent.location,
                        description = aiEvent.description,
                        color = com.antgskds.calendarassistant.ui.theme.EventColors[currentEvents.size % 8],
                        sourceImagePath = imagePath,
                        eventType = finalEventType
                    )
                    repository.addEvent(newEvent)
                    actuallyAdded.add(newEvent)

                    if (newEvent.eventType == "temp") {
                        NotificationScheduler.scheduleExpiryWarning(this, newEvent)
                    } else {
                        NotificationScheduler.scheduleReminders(this, newEvent)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "保存单个事件失败", e)
            }
        }
        return actuallyAdded
    }

    /**
     * 【完美修复】全量同步通知状态
     * 不再依赖本地 diff，而是告诉 CapsuleService “这些是我现在要显示的，其他的请杀掉”
     */
    private suspend fun refreshAllNotifications() {
        val settings = repository.settings.value

        // 如果开关关闭，发送空列表给 CapsuleService，它会清空所有
        if (!settings.isLiveCapsuleEnabled) {
            sendSyncIntent(emptyList())
            return
        }

        val nowDateTime = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("HH:mm")

        val allEvents = repository.events.value.filter {
            it.endDate.isAfter(LocalDate.now().minusDays(1))
        }

        val activeEvents = allEvents.filter { event ->
            try {
                val endDt = LocalDateTime.of(event.endDate, LocalTime.parse(event.endTime, formatter))
                nowDateTime.isBefore(endDt)
            } catch (e: Exception) { false }
        }

        val (pickupEvents, scheduleEvents) = activeEvents.partition { it.eventType == "temp" }

        // === 1. 计算当前时刻【应该存在】的 ID 白名单 ===
        val currentActiveIds = ArrayList<String>()

        // Schedule: 全部上白名单
        scheduleEvents.forEach { event ->
            val startDt = LocalDateTime.of(event.startDate, LocalTime.parse(event.startTime, formatter))
            val endDt = LocalDateTime.of(event.endDate, LocalTime.parse(event.endTime, formatter))

            if (!nowDateTime.isBefore(startDt)) {
                // 启动/更新
                startCapsule(
                    event = event,
                    type = CapsuleService.TYPE_SCHEDULE,
                    startMillis = startDt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    endMillis = endDt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    contentOverride = null
                )
                // 加入白名单
                currentActiveIds.add(event.id)
            }
        }

        // Pickup: 根据聚合逻辑上白名单
        if (settings.isPickupAggregationEnabled && pickupEvents.size > 1) {
            // >>> 聚合模式 >>>
            val titleText = "${pickupEvents.size} 个待取事项"
            val contentText = pickupEvents.mapIndexed { i, e -> "${i + 1}. ${e.title} ${e.description}" }
                .joinToString("\n")

            val latestStartMillis = pickupEvents.mapNotNull {
                try {
                    LocalDateTime.of(it.startDate, LocalTime.parse(it.startTime, formatter))
                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                } catch(e:Exception) { null }
            }.maxOrNull() ?: System.currentTimeMillis()

            val latestEndMillis = pickupEvents.mapNotNull {
                try {
                    LocalDateTime.of(it.endDate, LocalTime.parse(it.endTime, formatter))
                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                } catch(e:Exception) { null }
            }.maxOrNull() ?: System.currentTimeMillis()

            startServiceIntent(
                idStr = AGGREGATE_ID_STR,
                notifId = AGGREGATE_NOTIF_ID,
                title = titleText,
                content = contentText,
                type = CapsuleService.TYPE_PICKUP,
                startMillis = latestStartMillis,
                endMillis = latestEndMillis
            )
            // 只有聚合 ID 入选白名单，单独的 ID 不入选 -> 它们会被 Sync 杀掉
            currentActiveIds.add(AGGREGATE_ID_STR)

        } else {
            // >>> 单体模式 >>>
            pickupEvents.forEach { event ->
                val startDt = LocalDateTime.of(event.startDate, LocalTime.parse(event.startTime, formatter))
                val endDt = LocalDateTime.of(event.endDate, LocalTime.parse(event.endTime, formatter))

                startCapsule(
                    event = event,
                    type = CapsuleService.TYPE_PICKUP,
                    startMillis = startDt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    endMillis = endDt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    contentOverride = "取件码: ${event.description}"
                )
                currentActiveIds.add(event.id)
            }
            // 聚合 ID 不在白名单 -> 如果之前有聚合，会被 Sync 杀掉
        }

        // === 2. 发送同步指令 ===
        // 这一步是关键：告诉 CapsuleService 只有 list 里的能活，其他的都是僵尸
        sendSyncIntent(currentActiveIds)
    }

    private fun sendSyncIntent(activeIds: List<String>) {
        val intent = Intent(this, CapsuleService::class.java).apply {
            action = CapsuleService.ACTION_SYNC
            putStringArrayListExtra("ACTIVE_IDS", ArrayList(activeIds))
        }
        startService(intent)
    }

    private fun startCapsule(event: MyEvent, type: Int, startMillis: Long, endMillis: Long, contentOverride: String?) {
        startServiceIntent(
            idStr = event.id,
            notifId = event.id.hashCode(),
            title = event.title,
            content = contentOverride ?: "${event.startTime} - ${event.endTime}\n${event.location}",
            type = type,
            startMillis = startMillis,
            endMillis = endMillis
        )
    }

    private fun startServiceIntent(
        idStr: String,
        notifId: Int,
        title: String,
        content: String,
        type: Int,
        startMillis: Long,
        endMillis: Long
    ) {
        val intent = Intent(this, CapsuleService::class.java).apply {
            action = CapsuleService.ACTION_START
            putExtra("EVENT_ID", idStr)
            putExtra("NOTIF_ID", notifId)
            putExtra("EVENT_TITLE", title)
            putExtra("EVENT_CONTENT", content)
            putExtra("TYPE", type)
            putExtra("START_MILLIS", startMillis)
            putExtra("END_MILLIS", endMillis)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
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