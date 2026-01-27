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
import kotlinx.coroutines.CoroutineScope
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
 *
 * 职责：
 * 1. 统一计算胶囊状态（合并日程、课程、取件码）
 * 2. 监听时间变化（每分钟）实现过期检测
 * 3. 主动唤醒：当计算出 Active 状态时，启动 CapsuleService
 *
 * 依赖：
 * - AppRepository: 获取 events、courses、settings
 * - CoroutineScope: App Scope
 * - Context: 启动 Service
 */
class CapsuleStateManager(
    private val repository: AppRepository,
    private val appScope: CoroutineScope,
    private val context: Context
) {
    companion object {
        private const val TAG = "CapsuleStateManager"
        private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")

        // 聚合胶囊常量
        private const val AGGREGATE_PICKUP_ID = "AGGREGATE_PICKUP"
        private const val AGGREGATE_NOTIF_ID = 99999
    }

    /**
     * 胶囊UI状态 - StateFlow
     * 自动合并 events、settings 和时间ticker
     */
    val uiState: StateFlow<CapsuleUiState> = createCapsuleStateFlow()

    init {
        // ✅ 主动唤醒机制：监听 uiState，当变为 Active 时启动 Service
        startServiceWakeup()
    }

    /**
     * 主动唤醒 Service
     * 当状态变为 Active 时，确保 CapsuleService 正在运行
     */
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

    /**
     * 创建胶囊状态流
     * 合并：events + settings + ticker(每分钟)
     */
    private fun createCapsuleStateFlow(): StateFlow<CapsuleUiState> {
        // 时间ticker流 - 每分钟发射一次
        val tickerFlow = kotlinx.coroutines.flow.flow {
            emit(System.currentTimeMillis()) // ✅ 立即发射第一个值，确保初始化时计算一次
            while (true) {
                kotlinx.coroutines.delay(60_000) // 每分钟
                emit(System.currentTimeMillis())
                Log.d(TAG, "Ticker fired: 检查过期状态")
            }
        }

        return combine(
            repository.events,
            repository.courses,
            repository.settings,
            tickerFlow
        ) { events, courses, settings, _ ->
            computeCapsuleState(events, courses, settings)
        }.stateIn(
            scope = appScope,
            started = kotlinx.coroutines.flow.SharingStarted.Eagerly,
            initialValue = CapsuleUiState.None
        )
    }

    /**
     * 核心计算逻辑：根据当前状态计算应该显示的胶囊
     *
     * 关键约束：
     * 1. 必须包含虚拟课程（CourseManager.getDailyCourses）
     * 2. 必须包含分钟级ticker（否则过期不会自动刷新）
     * 3. 严禁修改 MyEvent 或 Course 数据结构
     */
    private fun computeCapsuleState(
        events: List<MyEvent>,
        courses: List<com.antgskds.calendarassistant.data.model.Course>,
        settings: MySettings
    ): CapsuleUiState {
        // 1. 检查实况胶囊开关
        if (!settings.isLiveCapsuleEnabled) {
            Log.d(TAG, "实况胶囊未开启")
            return CapsuleUiState.None
        }

        val now = LocalDateTime.now()
        val today = LocalDate.now()

        // 2. 获取今日虚拟课程（关键！不能只读events.json）
        val todayCourses = CourseManager.getDailyCourses(today, courses, settings)
        Log.d(TAG, "今日课程数量: ${todayCourses.size}")

        // 3. 合并日程和课程
        val allEvents = (events + todayCourses)
        Log.d(TAG, "合并后事件总数: ${allEvents.size}")

        // 4. 过滤活跃事件（未过期且已开始）
        val activeEvents = allEvents.filter { event ->
            try {
                val endDateTime = LocalDateTime.of(event.endDate, LocalTime.parse(event.endTime, TIME_FORMATTER))
                val startDateTime = LocalDateTime.of(event.startDate, LocalTime.parse(event.startTime, TIME_FORMATTER))

                // 检查：未过期 且 已开始（✅ 给 startDateTime 1分钟宽容度，防止秒级误差）
                val isActive = now.isBefore(endDateTime) && !now.isBefore(startDateTime.minusMinutes(1))

                if (isActive) {
                    Log.d(TAG, "活跃事件: ${event.title} (${event.eventType}) ${event.startTime}-${event.endTime}")
                }

                isActive
            } catch (e: Exception) {
                Log.e(TAG, "解析事件时间失败: ${event.title}", e)
                false
            }
        }

        if (activeEvents.isEmpty()) {
            Log.d(TAG, "无活跃事件")
            return CapsuleUiState.None
        }

        // 5. 分区：日程 vs 取件码
        val (pickupEvents, scheduleEvents) = activeEvents.partition { it.eventType == "temp" }
        Log.d(TAG, "取件码: ${pickupEvents.size}, 日程: ${scheduleEvents.size}")

        // 6. 构建胶囊列表
        val capsules = mutableListOf<CapsuleUiState.Active.CapsuleItem>()

        // 6a. 添加日程胶囊
        scheduleEvents.forEach { event ->
            val startMillis = toMillis(event, event.startTime)
            val endMillis = toMillis(event, event.endTime)

            capsules.add(CapsuleUiState.Active.CapsuleItem(
                id = event.id,
                notifId = event.id.hashCode(),
                type = CapsuleService.TYPE_SCHEDULE,
                title = event.title,
                content = "${event.startTime} - ${event.endTime}\n${event.location}",
                color = event.color.toArgb(), // ✅ 修复：使用 toArgb() 正确转换 Compose Color
                startMillis = startMillis,
                endMillis = endMillis
            ))
        }

        // 6b. 处理取件码（聚合 vs 单体）
        val aggregateMode = settings.isPickupAggregationEnabled && pickupEvents.size > 1

        if (aggregateMode) {
            // 聚合模式
            Log.d(TAG, "聚合模式: ${pickupEvents.size} 个取件码")
            val contentText = pickupEvents.take(5).mapIndexed { i, e ->
                val line = "${i + 1}. ${e.title}"
                if (i == 4 && pickupEvents.size > 5) "$line ..." else line
            }.joinToString("\n")

            // 取最晚的结束时间
            val latestEndMillis = pickupEvents.mapNotNull {
                try {
                    LocalDateTime.of(it.endDate, LocalTime.parse(it.endTime, TIME_FORMATTER))
                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                } catch (e: Exception) { null }
            }.maxOrNull() ?: (System.currentTimeMillis() + 2 * 60 * 60 * 1000) // 默认2小时

            capsules.add(CapsuleUiState.Active.CapsuleItem(
                id = AGGREGATE_PICKUP_ID,
                notifId = AGGREGATE_NOTIF_ID,
                type = CapsuleService.TYPE_PICKUP,
                title = "${pickupEvents.size} 个待取事项",
                content = contentText,
                color = android.graphics.Color.GREEN,
                startMillis = System.currentTimeMillis(),
                endMillis = latestEndMillis
            ))
        } else {
            // 单体模式
            Log.d(TAG, "单体模式: ${pickupEvents.size} 个取件码")
            pickupEvents.forEach { event ->
                capsules.add(CapsuleUiState.Active.CapsuleItem(
                    id = event.id,
                    notifId = event.id.hashCode(),
                    type = CapsuleService.TYPE_PICKUP,
                    title = event.title,
                    content = "取件码: ${event.description}",
                    color = android.graphics.Color.GREEN,
                    startMillis = System.currentTimeMillis(),
                    endMillis = toMillis(event, event.endTime)
                ))
            }
        }

        Log.d(TAG, "最终胶囊数量: ${capsules.size}")
        return CapsuleUiState.Active(capsules)
    }

    /**
     * 辅助函数：将事件的时间字符串转换为毫秒时间戳
     */
    private fun toMillis(event: MyEvent, timeStr: String): Long {
        return try {
            val localDateTime = LocalDateTime.of(event.startDate, LocalTime.parse(timeStr, TIME_FORMATTER))
            localDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (e: Exception) {
            Log.e(TAG, "时间转换失败: $timeStr", e)
            System.currentTimeMillis()
        }
    }
}
