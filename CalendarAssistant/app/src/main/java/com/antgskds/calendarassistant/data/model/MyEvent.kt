package com.antgskds.calendarassistant.data.model

import androidx.compose.ui.graphics.Color
import com.antgskds.calendarassistant.data.model.serializers.ColorSerializer
import com.antgskds.calendarassistant.data.model.serializers.LocalDateSerializer
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.util.UUID

object EventTags {
    const val GENERAL = "general"  // 普通日程、会议、约会
    const val COURSE = "course"    // 课程
    const val PICKUP = "pickup"  // 取件、核销码
    const val FOOD = "food"      // 取餐、外卖
    const val TRAIN = "train"     // 火车、高铁
    const val TAXI = "taxi"       // 网约车、出租车
    const val FLIGHT = "flight"   // 航班、飞机
    const val TICKET = "ticket"   // 取票
    const val SENDER = "sender"   // 寄件
    const val NOTE = "note"       // 便签
}

@Serializable
data class MyEvent(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    @Serializable(with = LocalDateSerializer::class)
    val startDate: LocalDate,
    @Serializable(with = LocalDateSerializer::class)
    val endDate: LocalDate,
    val startTime: String, // HH:mm
    val endTime: String,   // HH:mm
    val location: String,
    val description: String,
    @Serializable(with = ColorSerializer::class)
    val color: Color,
    val isImportant: Boolean = false,
    val sourceImagePath: String? = null,
    val reminders: List<Int> = emptyList(),
    val tag: String = EventTags.GENERAL,
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    @Serializable(with = LocalDateSerializer::class)
    val originalEndDate: LocalDate? = null,
    val originalEndTime: String? = null,
    val isCheckedIn: Boolean = false,
    val archivedAt: Long? = null,
    val lastModified: Long = System.currentTimeMillis(),  // 最后修改时间戳
    val isRecurring: Boolean = false,
    val isRecurringParent: Boolean = false,
    val recurringSeriesKey: String? = null,
    val recurringInstanceKey: String? = null,
    val parentRecurringId: String? = null,
    val excludedRecurringInstances: List<String> = emptyList(),
    val nextOccurrenceStartMillis: Long? = null,
    val skipCalendarSync: Boolean = false
)
