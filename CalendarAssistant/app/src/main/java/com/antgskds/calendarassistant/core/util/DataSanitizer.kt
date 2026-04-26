package com.antgskds.calendarassistant.core.util

import android.util.Log
import com.antgskds.calendarassistant.calendar.models.Event

object DataSanitizer {
    private const val TAG = "DataSanitizer"

    data class SanitizeResult<T>(
        val data: List<T>,
        val removedTitles: List<String>,
        val isFromBackup: Boolean = false
    )

    fun sanitizeEvents(events: List<Event>): SanitizeResult<Event> {
        if (events.isEmpty()) {
            return SanitizeResult(emptyList(), emptyList())
        }

        val removedTitles = mutableListOf<String>()

        val sanitized = events
            .mapNotNull { event ->
                var removeReason: String? = null

                when {
                    event.title.isBlank() -> removeReason = "空标题"
                    event.startTS <= 0L -> removeReason = "无效开始时间"
                    event.endTS <= 0L -> removeReason = "无效结束时间"
                }

                if (removeReason != null) {
                    removedTitles.add("${event.title}($removeReason)")
                    null
                } else {
                    event
                }
            }

        val uniqueById = sanitized
            .filter { it.id != null }
            .groupBy { it.id }
            .map { (_, group) ->
                if (group.size > 1) {
                    removedTitles.addAll(group.drop(1).map { "${it.title}(重复ID)" })
                }
                group.first()
            }

        if (removedTitles.isNotEmpty()) {
            Log.w(TAG, "数据自愈: 清理了 ${removedTitles.size} 条异常日程: $removedTitles")
        }

        return SanitizeResult(uniqueById, removedTitles)
    }

    fun buildCleanupSummary(eventRemoved: List<String>): String {
        if (eventRemoved.isEmpty()) return ""
        val titles = eventRemoved.take(5).joinToString("、")
        val suffix = if (eventRemoved.size > 5) "等${eventRemoved.size}个" else ""
        return "日程: $titles$suffix"
    }
}
