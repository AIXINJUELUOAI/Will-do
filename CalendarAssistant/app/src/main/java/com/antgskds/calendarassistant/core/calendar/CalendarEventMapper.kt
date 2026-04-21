package com.antgskds.calendarassistant.core.calendar

import android.util.Log
import com.antgskds.calendarassistant.data.model.EventTags
import com.antgskds.calendarassistant.data.model.MyEvent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 日历事件映射器
 * 负责应用数据模型与系统日历之间的数据转换
 */
object CalendarEventMapper {

    private const val TAG = "CalendarEventMapper"
    private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    // 定义统一的同步日程颜色：青灰色 (索引 6)
    private const val SYNCED_EVENT_COLOR = 0xFFA2B5BB.toInt()

    /**
     * 从系统日历事件信息转换为 MyEvent
     *
     * 🔥 修改：
     * 1. 适配全天事件：解析为 00:00 - 23:59，并使用 UTC 防止时区偏移
     * 2. 颜色处理：统一使用青灰色，清晰标识"外部同步"的日程
     */
    fun mapSystemEventToMyEvent(
        systemEvent: CalendarManager.SystemEventInfo,
        fixedId: String? = null
    ): MyEvent? {
        try {
            val startInstant = Instant.ofEpochMilli(systemEvent.startMillis)
            val endInstant = Instant.ofEpochMilli(systemEvent.endMillis)

            val startDate: LocalDate
            val endDate: LocalDate
            val startTimeStr: String
            val endTimeStr: String

            // 1. 处理全天日程与时区问题
            if (systemEvent.allDay) {
                // === 全天事件处理 ===
                // 系统日历的全天事件存储为 UTC 的 00:00
                // 必须使用 UTC 解析，防止加上时区偏移变成 08:00
                val utcZone = ZoneId.of("UTC")

                startDate = startInstant.atZone(utcZone).toLocalDate()

                // 系统日历的全天结束时间通常是"次日0点"，需要减去1纳秒退回当天
                endDate = endInstant.atZone(utcZone).minusNanos(1).toLocalDate()

                // 强制设置为全天范围 (00:00 - 23:59)
                startTimeStr = "00:00"
                endTimeStr = "23:59"
            } else {
                // === 普通事件处理 ===
                // 使用系统默认时区解析
                val systemZone = ZoneId.systemDefault()
                val startDateTime = startInstant.atZone(systemZone).toLocalDateTime()
                val endDateTime = endInstant.atZone(systemZone).toLocalDateTime()

                startDate = startDateTime.toLocalDate()
                endDate = endDateTime.toLocalDate()
                startTimeStr = startDateTime.toLocalTime().format(TIME_FORMATTER)
                endTimeStr = endDateTime.toLocalTime().format(TIME_FORMATTER)
            }

            // 2. 颜色处理：统一使用青灰色
            // 不再读取 systemEvent.color，直接使用固定颜色
            val colorInt = SYNCED_EVENT_COLOR

            val resolvedTag = when {
                !systemEvent.tag.isNullOrBlank() -> systemEvent.tag.orEmpty()
                systemEvent.description.contains("【列车】") -> EventTags.TRAIN
                systemEvent.description.contains("【用车】") -> EventTags.TAXI
                systemEvent.description.contains("【取件】") || systemEvent.description.contains("【取餐】") -> EventTags.PICKUP
                else -> EventTags.GENERAL
            }

            // 优先使用 fixedId，否则生成新 ID
            val eventId = fixedId
                ?: if (systemEvent.isRecurring && systemEvent.instanceKey != null) {
                    RecurringEventUtils.buildInstanceId(systemEvent.instanceKey)
                } else {
                    systemEvent.appId?.takeIf { it.isNotBlank() }
                        ?: "sync_calendar_${systemEvent.eventId}_${System.currentTimeMillis()}"
                }

            return MyEvent(
                id = eventId,
                title = systemEvent.title,
                startDate = startDate,
                endDate = endDate,
                startTime = startTimeStr,
                endTime = endTimeStr,
                location = systemEvent.location,
                description = systemEvent.description,
                color = androidx.compose.ui.graphics.Color(colorInt),
                isImportant = false,
                tag = resolvedTag,
                lastModified = systemEvent.lastModified ?: System.currentTimeMillis(),
                isRecurring = systemEvent.isRecurring,
                isRecurringParent = false,
                recurringSeriesKey = systemEvent.seriesKey,
                recurringInstanceKey = systemEvent.instanceKey,
                parentRecurringId = systemEvent.seriesKey?.let { RecurringEventUtils.buildParentId(it) },
                nextOccurrenceStartMillis = systemEvent.startMillis,
                skipCalendarSync = systemEvent.isRecurring
            )
        } catch (e: Exception) {
            Log.e(TAG, "转换系统事件失败: eventId=${systemEvent.eventId}", e)
            return null
        }
    }

}
