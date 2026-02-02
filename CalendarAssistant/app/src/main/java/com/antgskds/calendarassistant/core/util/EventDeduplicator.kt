package com.antgskds.calendarassistant.core.util

import android.util.Log
import com.antgskds.calendarassistant.core.calendar.CalendarManager
import com.antgskds.calendarassistant.data.model.EventFingerprint
import com.antgskds.calendarassistant.data.model.MyEvent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 事件去重工具类
 *
 * 核心策略：内容指纹（Content Fingerprint）
 * - 指纹组成：title + startDate + endDate + startTime + endTime + location
 * - 用于判断两个事件是否为"同一个事件"（内容上相同）
 */
object EventDeduplicator {

    private const val TAG = "EventDeduplicator"

    /**
     * 生成事件的内容指纹
     * 修复：增加空安全处理和统一小写，确保指纹稳定性
     */
    fun generateFingerprint(event: MyEvent): EventFingerprint {
        return EventFingerprint(
            title = event.title.trim().lowercase(), // 统一小写
            startDate = event.startDate,
            endDate = event.endDate,
            startTime = event.startTime,
            endTime = event.endTime,
            // 修复：处理 location 可能为 null 的情况，并统一小写
            location = (event.location ?: "").trim().lowercase()
        )
    }

    /**
     * 生成系统日历事件的内容指纹
     */
    fun generateFingerprintFromSystemEvent(systemEvent: CalendarManager.SystemEventInfo): EventFingerprint {
        val startInstant = Instant.ofEpochMilli(systemEvent.startMillis)
        val endInstant = Instant.ofEpochMilli(systemEvent.endMillis)

        val startDate: LocalDate
        val endDate: LocalDate
        val startTimeStr: String
        val endTimeStr: String

        if (systemEvent.allDay) {
            // 全天事件处理
            val utcZone = ZoneId.of("UTC")
            startDate = startInstant.atZone(utcZone).toLocalDate()
            endDate = endInstant.atZone(utcZone).minusNanos(1).toLocalDate()
            startTimeStr = "00:00"
            endTimeStr = "23:59"
        } else {
            // 普通事件处理
            val systemZone = ZoneId.systemDefault()
            val startDateTime = startInstant.atZone(systemZone).toLocalDateTime()
            val endDateTime = endInstant.atZone(systemZone).toLocalDateTime()

            startDate = startDateTime.toLocalDate()
            endDate = endDateTime.toLocalDate()
            startTimeStr = startDateTime.toLocalTime().toString()
            endTimeStr = endDateTime.toLocalTime().toString()
        }

        return EventFingerprint(
            title = systemEvent.title.trim().lowercase(), // 统一小写
            startDate = startDate,
            endDate = endDate,
            startTime = startTimeStr,
            endTime = endTimeStr,
            location = (systemEvent.location ?: "").trim().lowercase() // 统一处理 null 和小写
        )
    }

    /**
     * 去重结果
     *
     * @property toAdd 需要新增的事件列表
     * @property toSkip 需要跳过的事件列表（重复）
     * @property toUpdateArchiveStatus 需要更新归档状态的事件列表 (event -> shouldBeArchived)
     */
    data class DeduplicationResult(
        val toAdd: List<MyEvent>,
        val toSkip: List<MyEvent>,
        val toUpdateArchiveStatus: List<Pair<MyEvent, Boolean>> // event -> shouldBeArchived
    )

    /**
     * 对导入事件进行去重处理
     *
     * 策略：
     * 1. 课程和临时事件不参与去重，直接跳过
     * 2. 使用内容指纹判断是否重复
     * 3. 重复则跳过，不重复则追加
     * 4. 保留导入文件的 archivedAt 字段状态
     * 5. 检查归档状态冲突（导入归档 vs 现有活跃，导入活跃 vs 现有归档）
     *
     * @param importEvents 要导入的事件列表
     * @param existingActiveEvents 现有的活跃事件列表
     * @param existingArchivedEvents 现有的归档事件列表
     * @param preserveArchivedStatus 是否保留归档状态（默认 true）
     * @return 去重结果
     */
    fun deduplicateForImport(
        importEvents: List<MyEvent>,
        existingActiveEvents: List<MyEvent>,
        existingArchivedEvents: List<MyEvent>,
        preserveArchivedStatus: Boolean = true
    ): DeduplicationResult {
        // 1. 过滤掉课程和临时事件（不参与去重）
        val importRegularEvents = importEvents.filter { it.eventType == "event" }

        // 2. 构建现有事件的指纹集合
        val existingActiveFingerprints = existingActiveEvents
            .filter { it.eventType == "event" }
            .associateBy { generateFingerprint(it) }

        val existingArchivedFingerprints = existingArchivedEvents
            .filter { it.eventType == "event" }
            .associateBy { generateFingerprint(it) }

        val toAdd = mutableListOf<MyEvent>()
        val toSkip = mutableListOf<MyEvent>()
        val toUpdateArchiveStatus = mutableListOf<Pair<MyEvent, Boolean>>()

        for (importEvent in importRegularEvents) {
            val fingerprint = generateFingerprint(importEvent)
            val isImportArchived = importEvent.archivedAt != null

            // 3. 检查是否与现有活跃事件重复
            if (fingerprint in existingActiveFingerprints) {
                val existingEvent = existingActiveFingerprints[fingerprint]!!

                if (preserveArchivedStatus && isImportArchived) {
                    // 场景：导入归档事件 vs 现有活跃事件
                    // 策略：以导入为准，将现有事件标记为需要归档
                    toUpdateArchiveStatus.add(existingEvent to true)
                    Log.d(TAG, "检测到归档状态冲突：现有活跃事件将被归档 - ${importEvent.title}")
                }
                // 重复，跳过
                toSkip.add(importEvent)
                continue
            }

            // 4. 检查是否与现有归档事件重复
            if (fingerprint in existingArchivedFingerprints) {
                val existingEvent = existingArchivedFingerprints[fingerprint]!!

                if (preserveArchivedStatus && !isImportArchived) {
                    // 场景：导入活跃事件 vs 现有归档事件
                    // 策略：以导入为准，将现有归档事件标记为需要还原
                    toUpdateArchiveStatus.add(existingEvent to false)
                    Log.d(TAG, "检测到归档状态冲突：现有归档事件将被还原 - ${importEvent.title}")
                }
                // 重复，跳过
                toSkip.add(importEvent)
                continue
            }

            // 5. 不重复，需要新增
            toAdd.add(importEvent)
        }

        Log.d(TAG, "去重完成: 新增 ${toAdd.size}, 跳过 ${toSkip.size}, 归档状态更新 ${toUpdateArchiveStatus.size}")

        return DeduplicationResult(
            toAdd = toAdd,
            toSkip = toSkip,
            toUpdateArchiveStatus = toUpdateArchiveStatus
        )
    }

    /**
     * 检查系统日历事件是否与现有事件（活跃+归档）内容重复
     *
     * 用于反向同步时防止已归档事件被重新添加
     *
     * @param systemEvent 系统日历事件
     * @param existingEvents 现有事件列表（活跃+归档）
     * @return 是否重复
     */
    fun isContentDuplicate(
        systemEvent: CalendarManager.SystemEventInfo,
        existingEvents: List<MyEvent>
    ): Boolean {
        val systemFingerprint = generateFingerprintFromSystemEvent(systemEvent)

        // 只检查 eventType == "event" 的普通事件
        return existingEvents
            .filter { it.eventType == "event" }
            .any { generateFingerprint(it) == systemFingerprint }
    }
}
