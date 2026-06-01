package com.antgskds.calendarassistant.core.util

import android.util.Log
import com.antgskds.calendarassistant.core.util.EventFingerprint
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.*

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
     * 归一化时间格式，只保留 HH:mm，去掉秒和毫秒
     * "14:30:45.123456" -> "14:30"
     * "14:30" -> "14:30"
     */
    private fun normalizeTimeFormat(timeStr: String): String {
        return try {
            if (timeStr.contains(".")) {
                // 包含毫秒，先去掉
                timeStr.substringBefore(".")
            } else {
                timeStr
            }.let { trimmed ->
                val parts = trimmed.split(":")
                if (parts.size >= 2) {
                    parts[0].padStart(2, '0') + ":" + parts[1].padStart(2, '0')
                } else {
                    trimmed
                }
            }
        } catch (e: Exception) {
            timeStr
        }
    }

    /**
     * 生成事件的内容指纹
     * 修复：增加空安全处理和统一小写，确保指纹稳定性
     */
    fun generateFingerprint(event: Event): EventFingerprint {
        return EventFingerprint(
            title = event.title.trim().lowercase(),
            startTS = event.startTS,
            endTS = event.endTS,
            location = event.location.trim().lowercase()
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
        val toAdd: List<Event>,
        val toSkip: List<Event>,
        val toUpdateArchiveStatus: List<Pair<Event, Boolean>> // event -> shouldBeArchived
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
        importEvents: List<Event>,
        existingActiveEvents: List<Event>,
        existingArchivedEvents: List<Event>,
        preserveArchivedStatus: Boolean = true
    ): DeduplicationResult {
        // 1. 过滤掉课程（不参与去重）
        val importRegularEvents = importEvents.filter { it.tag != "__removed_course__" }

        // 2. 构建现有事件的指纹集合
        val existingActiveFingerprints = existingActiveEvents
            .filter { it.tag != "__removed_course__" }
            .associateBy { generateFingerprint(it) }

        val existingArchivedFingerprints = existingArchivedEvents
            .filter { it.tag != "__removed_course__" }
            .associateBy { generateFingerprint(it) }

        val toAdd = mutableListOf<Event>()
        val toSkip = mutableListOf<Event>()
        val toUpdateArchiveStatus = mutableListOf<Pair<Event, Boolean>>()

        for (importEvent in importRegularEvents) {
            val fingerprint = generateFingerprint(importEvent)
            val isImportArchived = importEvent.archivedAtMillis != null

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
}
