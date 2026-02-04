package com.antgskds.calendarassistant.core.capsule

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.antgskds.calendarassistant.core.course.CourseManager
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.data.repository.AppRepository
import com.antgskds.calendarassistant.data.state.CapsuleUiState
import com.antgskds.calendarassistant.service.capsule.CapsuleService
import com.antgskds.calendarassistant.service.capsule.NetworkSpeedMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow // ✅ 改用 StateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.ui.graphics.toArgb

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

        // ✅ 核心修复 1：改用 MutableStateFlow(0)
        // StateFlow 总是持有最新值，保证 combine 永远不会因为等待信号而卡死或丢状态
        private val forceRefreshTrigger = MutableStateFlow(0)

        // 网速胶囊的动态状态（每次更新都触发状态重新计算）
        private val networkSpeedState = MutableStateFlow<NetworkSpeedMonitor.NetworkSpeed?>(null)
    }

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

    val uiState: StateFlow<CapsuleUiState> = createCapsuleStateFlow()

    init {
        startServiceWakeup()
    }

    private fun startServiceWakeup() {
        appScope.launch {
            uiState.collect { state ->
                when (state) {
                    is CapsuleUiState.Active -> {
                        Log.d(TAG, "主动唤醒：状态变为 Active，启动 CapsuleService")
                        val serviceIntent = Intent(context, CapsuleService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                    }
                    is CapsuleUiState.None -> {
                        Log.d(TAG, "状态变为 None，Service 将自动停止")
                    }
                }
            }
        }
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

        return combine(baseCombine, networkSpeedState) { (events, courses, settings), networkSpeed ->
            Log.d(TAG, "=== computeCapsuleState 被调用 ===")
            computeCapsuleState(events, courses, settings, networkSpeed)
        }.stateIn(
            scope = appScope,
            started = kotlinx.coroutines.flow.SharingStarted.Eagerly,
            initialValue = CapsuleUiState.None
        )
    }

    private fun computeCapsuleState(
        events: List<MyEvent>,
        courses: List<com.antgskds.calendarassistant.data.model.Course>,
        settings: MySettings,
        networkSpeed: NetworkSpeedMonitor.NetworkSpeed?
    ): CapsuleUiState {
        Log.d(TAG, ">>> computeCapsuleState 开始执行")

        // 【实验室】网速胶囊优先：如果开启网速胶囊，覆盖其他所有胶囊
        if (settings.isNetworkSpeedCapsuleEnabled && networkSpeed != null) {
            Log.d(TAG, "网速胶囊模式: ${networkSpeed.formattedSpeed}")
            val capsules = listOf(
                CapsuleUiState.Active.CapsuleItem(
                    id = "network_speed",
                    notifId = 88888,
                    type = CapsuleService.TYPE_NETWORK_SPEED,
                    eventType = "network_speed",
                    title = "↓ ${networkSpeed.formattedSpeed}",
                    content = "下载速度",
                    color = android.graphics.Color.parseColor("#4CAF50"),
                    startMillis = System.currentTimeMillis(),
                    endMillis = System.currentTimeMillis() + 60 * 60 * 1000 // 1小时有效
                )
            )
            return CapsuleUiState.Active(capsules)
        }

        if (!settings.isLiveCapsuleEnabled) {
            return CapsuleUiState.None
        }

        val now = LocalDateTime.now()
        val today = LocalDate.now()

        val todayCourses = CourseManager.getDailyCourses(today, courses, settings)
        val allEvents = (events + todayCourses)

        // 4. 过滤活跃事件
        val activeEvents = allEvents.filter { event ->
            try {
                // ⚠️ 注意：如果你在测试时创建的时间已经过去了（哪怕只过去1秒），
                // 这里的 now.isBefore(endDateTime) 就会返回 false，胶囊就会消失。
                // 建议测试时，将结束时间设置在未来 5-10 分钟。
                val endDateTime = LocalDateTime.of(event.endDate, LocalTime.parse(event.endTime, TIME_FORMATTER))
                val startDateTime = LocalDateTime.of(event.startDate, LocalTime.parse(event.startTime, TIME_FORMATTER))

                val effectiveStartTime = if (settings.isAdvanceReminderEnabled &&
                                               settings.advanceReminderMinutes > 0 &&
                                               event.eventType != "temp") {
                    startDateTime.minusMinutes(settings.advanceReminderMinutes.toLong())
                } else {
                    startDateTime.minusMinutes(1)
                }

                val isActive = if (event.eventType == "temp") {
                    val isStarted = !now.isBefore(effectiveStartTime)
                    val endPlusGrace = endDateTime.plusMinutes(30)
                    val isWithinGracePeriod = now.isBefore(endPlusGrace)
                    isStarted && isWithinGracePeriod
                } else {
                    // 日程胶囊逻辑：必须 未结束 且 已开始
                    now.isBefore(endDateTime) && !now.isBefore(effectiveStartTime)
                }

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
            return CapsuleUiState.None
        }

        // ... 后续构建胶囊逻辑保持不变 ...
        val (pickupEvents, scheduleEvents) = activeEvents.partition { it.eventType == "temp" }
        val capsules = mutableListOf<CapsuleUiState.Active.CapsuleItem>()

        scheduleEvents.forEach { event ->
            capsules.add(CapsuleUiState.Active.CapsuleItem(
                id = event.id,
                notifId = event.id.hashCode(),
                type = CapsuleService.TYPE_SCHEDULE,
                eventType = event.eventType,
                title = event.title,
                content = "${event.startTime} - ${event.endTime}\n${event.location}",
                color = event.color.toArgb(),
                startMillis = toMillis(event, event.startTime),
                endMillis = toMillis(event, event.endTime)
            ))
        }

        val aggregateMode = settings.isPickupAggregationEnabled && pickupEvents.size > 1

        if (aggregateMode) {
            // ==================== 聚合模式：只创建聚合胶囊，不创建独立胶囊 ====================
            Log.d(TAG, "聚合模式: ${pickupEvents.size} 个取件码")
            val contentText = pickupEvents.take(5).mapIndexed { i, e ->
                val line = "${i + 1}. ${e.title}"
                if (i == 4 && pickupEvents.size > 5) "$line ..." else line
            }.joinToString("\n")

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
            val capsuleType = if (isAnyExpired) CapsuleService.TYPE_PICKUP_EXPIRED else CapsuleService.TYPE_PICKUP

            capsules.add(CapsuleUiState.Active.CapsuleItem(
                id = AGGREGATE_PICKUP_ID,
                notifId = AGGREGATE_NOTIF_ID,
                type = capsuleType,
                eventType = "temp",
                title = if (isAnyExpired) "${pickupEvents.size} 个待取 (含过期)" else "${pickupEvents.size} 个待取事项",
                content = contentText,
                color = android.graphics.Color.GREEN,
                startMillis = System.currentTimeMillis(),
                endMillis = latestEndMillis
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

                // 2. 动态生成 Content (打破 StateFlow 去重)
                val dynamicContent = if (isExpired) {
                    "[已过期] 备注: ${event.description}"
                } else {
                    "备注: ${event.description}"
                }

                // 3. ✅ 关键：根据过期状态决定胶囊类型
                // 如果过期，直接传 TYPE_PICKUP_EXPIRED (3)，不让 Provider 瞎猜
                val capsuleType = if (isExpired) {
                    CapsuleService.TYPE_PICKUP_EXPIRED
                } else {
                    CapsuleService.TYPE_PICKUP
                }

                // 4. ✅ 回退：ID 保持稳定，不再 +1
                // 我们改用 CapsuleService 里的暴力刷新策略来解决弹窗问题
                val dynamicNotifId = event.id.hashCode()

                // ✅ 详细日志：输出生成的胶囊信息
                Log.d(TAG, "生成胶囊: id=${event.id}, type=$capsuleType, notifId=$dynamicNotifId")

                capsules.add(CapsuleUiState.Active.CapsuleItem(
                    id = event.id,
                    notifId = dynamicNotifId, // ID 保持不变
                    type = capsuleType,
                    eventType = event.eventType,
                    title = event.title,
                    content = dynamicContent, // 内容变化依然保留
                    color = android.graphics.Color.GREEN,
                    startMillis = toMillis(event, event.startTime),
                    endMillis = toMillis(event, event.endTime)
                ))
            }
        }

        Log.d(TAG, "最终胶囊数量: ${capsules.size}")
        return CapsuleUiState.Active(capsules)
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
