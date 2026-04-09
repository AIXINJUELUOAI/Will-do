package com.antgskds.calendarassistant.core.capsule

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.compose.ui.graphics.toArgb
import com.antgskds.calendarassistant.core.course.CourseManager
import com.antgskds.calendarassistant.core.rule.RuleMatchingEngine
import com.antgskds.calendarassistant.core.util.FlymeUtils
import com.antgskds.calendarassistant.core.util.OsUtils
import com.antgskds.calendarassistant.data.model.EventType
import com.antgskds.calendarassistant.data.model.EventTags
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.data.repository.AppRepository
import com.antgskds.calendarassistant.data.state.CapsuleUiState
import com.antgskds.calendarassistant.service.capsule.CapsuleDisplayModel
import com.antgskds.calendarassistant.service.capsule.CapsuleMessageComposer
import com.antgskds.calendarassistant.service.capsule.IconUtils
import com.antgskds.calendarassistant.service.capsule.NetworkSpeedMonitor
import com.antgskds.calendarassistant.service.capsule.provider.FlymeCapsuleProvider
import com.antgskds.calendarassistant.service.capsule.provider.ICapsuleProvider
import com.antgskds.calendarassistant.service.capsule.provider.NativeCapsuleProvider
import com.antgskds.calendarassistant.service.capsule.miui.MiuiIslandManager
import com.antgskds.calendarassistant.xposed.XposedModuleStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow // ✅ 改用 StateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

/**
 * 胶囊状态管理器 - 主动唤醒模式
 */
class CapsuleStateManager(
    private val repository: AppRepository,
    private val appScope: CoroutineScope,
    private val context: Context
) {
    companion object {
        private const val TAG = "CapsuleStateManager"
        private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")

        const val AGGREGATE_PICKUP_ID = "AGGREGATE_PICKUP"
        const val AGGREGATE_NOTIF_ID = 99999

        // 胶囊类型常量（原 TYPE_*）
        const val TYPE_SCHEDULE = 1
        const val TYPE_PICKUP = 2
        const val TYPE_PICKUP_EXPIRED = 3
        const val TYPE_NETWORK_SPEED = 4
        const val TYPE_OCR_PROGRESS = 5
        const val TYPE_OCR_RESULT = 6

        private const val OCR_PROGRESS_ID = "OCR_PROGRESS"
        private const val OCR_RESULT_ID = "OCR_RESULT"
        private const val OCR_NOTIF_ID = 88886
        private const val OCR_PROGRESS_TIMEOUT_MS = 2 * 60 * 1000L
        private const val OCR_RESULT_TIMEOUT_MS = 8000L
        private const val OCR_UPDATE_THROTTLE_MS = 600L
        private const val EVENT_TYPE_OCR_PROGRESS = "ocr_progress"
        private const val EVENT_TYPE_OCR_RESULT = "ocr_result"

        // ✅ 核心修复 1：改用 MutableStateFlow(0)
        // StateFlow 总是持有最新值，保证 combine 永远不会因为等待信号而卡死或丢状态
        private val forceRefreshTrigger = MutableStateFlow(0)

        // 网速胶囊的动态状态（每次更新都触发状态重新计算）
        private val networkSpeedState = MutableStateFlow<NetworkSpeedMonitor.NetworkSpeed?>(null)
    }

    private data class OcrCapsuleState(
        val id: String,
        val notifId: Int,
        val type: Int,
        val eventType: String,
        val title: String,
        val content: String,
        val description: String,
        val color: Int,
        val startMillis: Long,
        val endMillis: Long,
        val display: CapsuleDisplayModel,
        val expiresAt: Long?
    )

    private val ocrCapsuleState = MutableStateFlow<OcrCapsuleState?>(null)
    private var ocrAutoClearJob: Job? = null
    private var lastOcrUpdateAt = 0L

    // 通知管理
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val provider: ICapsuleProvider = if (FlymeUtils.isFlyme()) FlymeCapsuleProvider() else NativeCapsuleProvider()
    private val activeNotifIds = ConcurrentHashMap.newKeySet<Int>()
    private var monitorJob: Job? = null
    private var isAggregateMode = false

    /**
     * 【修复问题3】强制刷新胶囊状态
     * ✅ 改为同步执行，确保调用后立即生效
     */
    fun forceRefresh() {
        // ✅ 直接在调用线程更新值，不使用协程
        val newValue = forceRefreshTrigger.value + 1
        forceRefreshTrigger.value = newValue
        Log.d(TAG, "forceRefresh: 主动触发胶囊状态刷新 (Counter: $newValue)")
    }

    fun updateNetworkSpeed(speed: NetworkSpeedMonitor.NetworkSpeed?) {
        networkSpeedState.value = speed
    }

    fun showOcrProgress(title: String, content: String) {
        val now = System.currentTimeMillis()
        val display = CapsuleMessageComposer.composeOcrProgress(title, content)
        updateOcrCapsule(
            OcrCapsuleState(
                id = OCR_PROGRESS_ID,
                notifId = OCR_NOTIF_ID,
                type = TYPE_OCR_PROGRESS,
                eventType = EVENT_TYPE_OCR_PROGRESS,
                title = display.shortText,
                content = display.secondaryText ?: content,
                description = "",
                color = android.graphics.Color.parseColor("#2979FF"),
                startMillis = now,
                endMillis = now + OCR_PROGRESS_TIMEOUT_MS,
                display = display,
                expiresAt = now + OCR_PROGRESS_TIMEOUT_MS
            ),
            OCR_PROGRESS_TIMEOUT_MS
        )
    }

    fun showOcrResult(title: String, content: String, durationMs: Long = OCR_RESULT_TIMEOUT_MS) {
        val now = System.currentTimeMillis()
        val display = CapsuleMessageComposer.composeOcrResult(title, content)
        updateOcrCapsule(
            OcrCapsuleState(
                id = OCR_RESULT_ID,
                notifId = OCR_NOTIF_ID,
                type = TYPE_OCR_RESULT,
                eventType = EVENT_TYPE_OCR_RESULT,
                title = display.shortText,
                content = display.secondaryText ?: content,
                description = "",
                color = android.graphics.Color.parseColor("#4CAF50"),
                startMillis = now,
                endMillis = now + durationMs,
                display = display,
                expiresAt = now + durationMs
            ),
            durationMs
        )
    }

    fun clearOcrCapsule() {
        ocrAutoClearJob?.cancel()
        ocrAutoClearJob = null
        ocrCapsuleState.value = null
    }

    private fun updateOcrCapsule(state: OcrCapsuleState, autoClearMs: Long?) {
        val now = System.currentTimeMillis()
        val current = ocrCapsuleState.value
        if (current != null && current.type == state.type && current.display == state.display) {
            if (now - lastOcrUpdateAt < OCR_UPDATE_THROTTLE_MS) {
                return
            }
        }
        lastOcrUpdateAt = now
        ocrCapsuleState.value = state
        if (autoClearMs != null) {
            scheduleOcrAutoClear(autoClearMs)
        }
    }

    private fun scheduleOcrAutoClear(delayMs: Long) {
        ocrAutoClearJob?.cancel()
        ocrAutoClearJob = appScope.launch {
            kotlinx.coroutines.delay(delayMs)
            clearOcrCapsule()
        }
    }

    val uiState: StateFlow<CapsuleUiState> = createCapsuleStateFlow()

    init {
        startNotificationManager()
    }

    /**
     * 启动通知管理机制
     * 当胶囊状态变化时自动发布/更新/取消通知
     */
    private fun startNotificationManager() {
        appScope.launch {
            uiState.collect { state ->
                val settings = repository.settings.value
                val useMiuiIsland = isMiuiIslandMode(settings)
                when (state) {
                    is CapsuleUiState.Active -> {
                        if (useMiuiIsland) {
                            MiuiIslandManager.update(context, state.capsules)
                            cancelAllCapsuleNotifications()
                        } else {
                            updateCapsules(state.capsules)
                        }
                    }
                    is CapsuleUiState.None -> {
                        monitorJob?.cancel()
                        isAggregateMode = false
                        MiuiIslandManager.clear(context)
                        cancelAllCapsuleNotifications()
                    }
                }
            }
        }
    }

    private fun isMiuiIslandMode(settings: MySettings): Boolean {
        return settings.isLiveCapsuleEnabled && OsUtils.isHyperOS() && XposedModuleStatus.isActive()
    }

    private fun updateCapsules(newCapsules: List<CapsuleUiState.Active.CapsuleItem>) {
        val validIds = newCapsules.map { it.notifId }.toSet()

        val newAggregateMode = newCapsules.any { it.id == AGGREGATE_PICKUP_ID }
        if (newAggregateMode && !isAggregateMode) {
            isAggregateMode = true
            startMonitoring()
        } else if (!newAggregateMode && isAggregateMode) {
            isAggregateMode = false
            monitorJob?.cancel()
        }

        newCapsules.forEach { item ->
            val iconResId = IconUtils.getSmallIconForCapsule(context, item)
            val notification = provider.buildNotification(context, item, iconResId)
            notificationManager.notify(item.notifId, notification)
            activeNotifIds.add(item.notifId)
        }

        // 清理不再需要的通知
        val staleIds = activeNotifIds.toMutableSet()
        staleIds.removeAll(validIds)
        staleIds.forEach { id ->
            notificationManager.cancel(id)
            activeNotifIds.remove(id)
        }
    }

    private fun startMonitoring() {
        monitorJob?.cancel()
        monitorJob = appScope.launch {
            while (true) {
                kotlinx.coroutines.delay(3000)
                if (isAggregateMode) {
                    cleanupStaleNotifications()
                }
            }
        }
    }

    private fun cleanupStaleNotifications() {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
            val activeNotifications = notificationManager.activeNotifications
            activeNotifications.forEach { sb ->
                val notificationId = sb.id
                if (notificationId !in activeNotifIds) return@forEach
                val channelId = sb.notification.channelId
                val channelMatch = channelId != null && channelId.contains("live", ignoreCase = true)
                if (channelMatch) {
                    // 检查对应的胶囊是否仍然有效
                    val state = uiState.value
                    if (state is CapsuleUiState.Active) {
                        val stillValid = state.capsules.any { it.notifId == notificationId }
                        if (!stillValid) {
                            notificationManager.cancel(notificationId)
                            activeNotifIds.remove(notificationId)
                            Log.d(TAG, "清除过期胶囊通知: id=$notificationId")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "清理过期通知失败", e)
        }
    }

    private fun cancelAllCapsuleNotifications() {
        activeNotifIds.forEach { id ->
            notificationManager.cancel(id)
        }
        activeNotifIds.clear()
    }

    private fun createCapsuleStateFlow(): StateFlow<CapsuleUiState> {
        // ✅ 改用 MutableStateFlow，确保立即有值且 combine 能正常工作
        val tickerTrigger = MutableStateFlow(System.currentTimeMillis())

        // 启动定时器，每 10 秒更新一次（快速检测过期）
        appScope.launch {
            while (true) {
                kotlinx.coroutines.delay(10_000)
                tickerTrigger.value = System.currentTimeMillis()
                Log.d(TAG, "Ticker fired: 检查过期状态")
            }
        }

        // combine 只支持最多 5 个流，需要嵌套 combine
        val baseCombine = combine(
            repository.events,
            repository.courses,
            repository.settings,
            tickerTrigger,
            forceRefreshTrigger
        ) { events, courses, settings, _, _ ->
            Triple(events, courses, settings)
        }

        return combine(baseCombine, networkSpeedState, ocrCapsuleState) { (events, courses, settings), networkSpeed, ocrCapsule ->
            Log.d(TAG, "=== computeCapsuleState 被调用 ===")
            computeCapsuleState(events, courses, settings, networkSpeed, ocrCapsule)
        }.flowOn(Dispatchers.Default)  // ✅ 将胶囊计算移到后台线程，避免主线程 ANR
        .stateIn(
            scope = appScope,
            started = kotlinx.coroutines.flow.SharingStarted.Eagerly,
            initialValue = CapsuleUiState.None
        )
    }

    private fun computeCapsuleState(
        events: List<MyEvent>,
        courses: List<com.antgskds.calendarassistant.data.model.Course>,
        settings: MySettings,
        networkSpeed: NetworkSpeedMonitor.NetworkSpeed?,
        ocrCapsule: OcrCapsuleState?
    ): CapsuleUiState {
        Log.d(TAG, ">>> computeCapsuleState 开始执行")

        val nowMillis = System.currentTimeMillis()
        val activeOcrCapsule = ocrCapsule?.let { state ->
            if (state.expiresAt != null && nowMillis >= state.expiresAt) {
                clearOcrCapsule()
                null
            } else {
                state
            }
        }

        if (activeOcrCapsule != null && settings.isLiveCapsuleEnabled) {
            val ocrItem = createCapsuleItem(
                id = activeOcrCapsule.id,
                notifId = activeOcrCapsule.notifId,
                type = activeOcrCapsule.type,
                eventType = activeOcrCapsule.eventType,
                description = activeOcrCapsule.description,
                color = activeOcrCapsule.color,
                startMillis = activeOcrCapsule.startMillis,
                endMillis = activeOcrCapsule.endMillis,
                display = activeOcrCapsule.display
            )
            // OCR 胶囊不阻断后续日程计算，与日程胶囊共存
            val scheduleCapsules = computeScheduleCapsules(events, courses, settings)
            return CapsuleUiState.Active(listOf(ocrItem) + scheduleCapsules)
        }

        // 【实验室】网速胶囊：若未触发 OCR 胶囊则覆盖其他胶囊
        if (settings.isNetworkSpeedCapsuleEnabled && networkSpeed != null) {
            Log.d(TAG, "网速胶囊模式: ${networkSpeed.formattedSpeed}")
            val display = CapsuleMessageComposer.composeNetworkSpeed(networkSpeed)
            val capsules = listOf(
                createCapsuleItem(
                    id = "network_speed",
                    notifId = 88888,
                    type = TYPE_NETWORK_SPEED,
                    eventType = "network_speed",
                    description = "",
                    color = android.graphics.Color.parseColor("#4CAF50"),
                    startMillis = System.currentTimeMillis(),
                    endMillis = System.currentTimeMillis() + 60 * 60 * 1000, // 1小时有效
                    display = display
                )
            )
            return CapsuleUiState.Active(capsules)
        }

        if (!settings.isLiveCapsuleEnabled) {
            return CapsuleUiState.None
        }

        val capsules = computeScheduleCapsules(events, courses, settings)
        return if (capsules.isEmpty()) CapsuleUiState.None else CapsuleUiState.Active(capsules)
    }

    private fun computeScheduleCapsules(
        events: List<MyEvent>,
        courses: List<com.antgskds.calendarassistant.data.model.Course>,
        settings: MySettings
    ): List<CapsuleUiState.Active.CapsuleItem> {

        val now = LocalDateTime.now()
        val today = LocalDate.now()

        val todayCourses = CourseManager.getDailyCourses(today, courses, settings)
        val allEvents = (events + todayCourses)

        // 4. 过滤活跃事件
        val activeEvents = allEvents.filter { event ->
            try {
                if (event.isRecurringParent) {
                    return@filter false
                }
                if (event.tag == EventTags.NOTE) {
                    return@filter false
                }

                // ⚠️ 注意：如果你在测试时创建的时间已经过去了（哪怕只过去1秒），
                // 这里的 now.isBefore(endDateTime) 就会返回 false，胶囊就会消失。
                // 建议测试时，将结束时间设置在未来 5-10 分钟。
                val endDateTime = LocalDateTime.of(event.endDate, LocalTime.parse(event.endTime, TIME_FORMATTER))
                val startDateTime = LocalDateTime.of(event.startDate, LocalTime.parse(event.startTime, TIME_FORMATTER))

                val effectiveStartTime = if (settings.isAdvanceReminderEnabled &&
                                               settings.advanceReminderMinutes > 0) {
                    startDateTime.minusMinutes(settings.advanceReminderMinutes.toLong())
                } else {
                    startDateTime.minusMinutes(1)
                }

                val isActive = !event.isCompleted && now.isBefore(endDateTime) && !now.isBefore(effectiveStartTime)

                // 调试日志：如果胶囊消失，请检查 Logcat 中这一行的 isActive 是 true 还是 false
                // Log.d(TAG, "Event: ${event.title}, End: $endDateTime, Now: $now, IsActive: $isActive")

                isActive
            } catch (e: Exception) {
                Log.e(TAG, "解析事件时间失败: ${event.title}", e)
                false
            }
        }

        if (activeEvents.isEmpty()) {
            Log.d(TAG, "无活跃事件 (Active list empty)")
            return emptyList()
        }

        // ... 后续构建胶囊逻辑保持不变 ...
        val (pickupEvents, scheduleEvents) = activeEvents.partition { isPickupRule(it) }
        val capsules = mutableListOf<CapsuleUiState.Active.CapsuleItem>()

        scheduleEvents.forEach { event ->
            val endDateTime = LocalDateTime.of(event.endDate, LocalTime.parse(event.endTime, TIME_FORMATTER))
            val isExpired = now.isAfter(endDateTime)
            val display = CapsuleMessageComposer.composeSchedule(context, event, isExpired)

            capsules.add(createCapsuleItem(
                id = event.id,
                notifId = event.id.hashCode(),
                type = TYPE_SCHEDULE,
                eventType = resolveCapsuleEventType(event),
                description = event.description,
                color = event.color.toArgb(),
                startMillis = toMillis(event, event.startTime),
                endMillis = toMillis(event, event.endTime),
                display = display
            ))
        }

        val aggregateMode = settings.isPickupAggregationEnabled && pickupEvents.size > 1

        if (aggregateMode) {
            // ==================== 聚合模式：只创建聚合胶囊，不创建独立胶囊 ====================
            Log.d(TAG, "聚合模式: ${pickupEvents.size} 个取件码")
            val latestEndMillis = pickupEvents.mapNotNull {
                try {
                    LocalDateTime.of(it.endDate, LocalTime.parse(it.endTime, TIME_FORMATTER))
                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                } catch (e: Exception) { null }
            }.maxOrNull() ?: (System.currentTimeMillis() + 2 * 60 * 60 * 1000)

            // ✅ 修复：根据过期状态决定胶囊类型
            val isAnyExpired = pickupEvents.any { event ->
                val endDateTime = LocalDateTime.of(event.endDate, LocalTime.parse(event.endTime, TIME_FORMATTER))
                now.isAfter(endDateTime)
            }
            val capsuleType = if (isAnyExpired) TYPE_PICKUP_EXPIRED else TYPE_PICKUP
            val display = CapsuleMessageComposer.composeAggregatePickup(context, pickupEvents)

            capsules.add(createCapsuleItem(
                id = AGGREGATE_PICKUP_ID,
                notifId = AGGREGATE_NOTIF_ID,
                type = capsuleType,
                eventType = RuleMatchingEngine.RULE_PICKUP,
                description = pickupEvents.firstOrNull()?.description ?: "",
                color = android.graphics.Color.GREEN,
                startMillis = System.currentTimeMillis(),
                endMillis = latestEndMillis,
                display = display
            ))
        } else {
            // ==================== 非聚合模式：创建独立胶囊 ====================
            Log.d(TAG, "非聚合模式: ${pickupEvents.size} 个取件码")
            pickupEvents.forEach { event ->
                // 1. 计算过期状态
                val endDateTime = LocalDateTime.of(event.endDate, LocalTime.parse(event.endTime, TIME_FORMATTER))
                val isExpired = now.isAfter(endDateTime)

                // ✅ 详细日志：输出每个取件码的状态
                Log.d(TAG, "取件码: ${event.title}, 结束时间: $endDateTime, 当前: $now, 过期: $isExpired")

                // 3. ✅ 关键：根据过期状态决定胶囊类型
                // 如果过期，直接传 TYPE_PICKUP_EXPIRED (3)，不让 Provider 瞎猜
                val capsuleType = if (isExpired) {
                    TYPE_PICKUP_EXPIRED
                } else {
                    TYPE_PICKUP
                }

                // 4. ID 保持稳定，不再 +1
                val dynamicNotifId = event.id.hashCode()
                val display = CapsuleMessageComposer.composePickup(context, event, isExpired)

                // ✅ 详细日志：输出生成的胶囊信息
                Log.d(TAG, "生成胶囊: id=${event.id}, type=$capsuleType, notifId=$dynamicNotifId, title=${display.shortText}")

                capsules.add(createCapsuleItem(
                    id = event.id,
                    notifId = dynamicNotifId, // ID 保持不变
                    type = capsuleType,
                    eventType = RuleMatchingEngine.RULE_PICKUP,
                    description = event.description,
                    color = android.graphics.Color.GREEN,
                    startMillis = toMillis(event, event.startTime),
                    endMillis = toMillis(event, event.endTime),
                    display = display
                ))
            }
        }

        Log.d(TAG, "最终胶囊数量: ${capsules.size}")
        return capsules
    }

    private fun createCapsuleItem(
        id: String,
        notifId: Int,
        type: Int,
        eventType: String,
        description: String,
        color: Int,
        startMillis: Long,
        endMillis: Long,
        display: CapsuleDisplayModel
    ): CapsuleUiState.Active.CapsuleItem {
        return CapsuleUiState.Active.CapsuleItem(
            id = id,
            notifId = notifId,
            type = type,
            eventType = eventType,
            title = display.shortText,
            content = display.expandedText
                ?: listOfNotNull(display.secondaryText, display.tertiaryText).joinToString("\n"),
            description = description,
            color = color,
            startMillis = startMillis,
            endMillis = endMillis,
            display = display
        )
    }

    private fun resolveCapsuleEventType(event: MyEvent): String {
        return if (event.eventType == EventType.COURSE) {
            EventType.COURSE
        } else {
            resolveRuleId(event)
        }
    }

    private fun resolveRuleId(event: MyEvent): String {
        val parsedRuleId = RuleMatchingEngine.resolvePayload(event)?.ruleId
        if (!parsedRuleId.isNullOrBlank()) {
            return parsedRuleId
        }
        return when (event.tag) {
            EventTags.PICKUP -> RuleMatchingEngine.RULE_PICKUP
            EventTags.TRAIN -> RuleMatchingEngine.RULE_TRAIN
            EventTags.TAXI -> RuleMatchingEngine.RULE_TAXI
            EventTags.GENERAL -> RuleMatchingEngine.RULE_GENERAL
            else -> if (event.tag.isNotBlank()) event.tag else RuleMatchingEngine.RULE_GENERAL
        }
    }

    private fun isPickupRule(event: MyEvent): Boolean {
        return resolveRuleId(event) == RuleMatchingEngine.RULE_PICKUP
    }

    private fun toMillis(event: MyEvent, timeStr: String): Long {
        return try {
            // 修复：时间必须对应正确的日期
            // startTime 对应 startDate，endTime 对应 endDate
            val date = if (timeStr == event.startTime) event.startDate else event.endDate
            val localDateTime = LocalDateTime.of(date, LocalTime.parse(timeStr, TIME_FORMATTER))
            localDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (e: Exception) {
            Log.e(TAG, "时间转换失败: $timeStr", e)
            System.currentTimeMillis()
        }
    }
}
