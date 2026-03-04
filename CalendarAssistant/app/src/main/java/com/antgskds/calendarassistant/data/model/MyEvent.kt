package com.antgskds.calendarassistant.data.model

import androidx.compose.ui.graphics.Color
import com.antgskds.calendarassistant.data.model.serializers.ColorSerializer
import com.antgskds.calendarassistant.data.model.serializers.LocalDateSerializer
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.util.UUID

object EventType {
    const val EVENT = "event"   // 普通日程（含取件码、火车、打车）
    const val COURSE = "course" // 课程
}

object EventTags {
    const val GENERAL = "general"  // 普通日程、会议、约会
    const val PICKUP = "pickup"  // 取件、取餐、核销码
    const val TRAIN = "train"     // 火车、高铁、飞机
    const val TAXI = "taxi"       // 网约车、出租车
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
    val eventType: String = EventType.EVENT,
    val tag: String = EventTags.GENERAL,
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    @Serializable(with = LocalDateSerializer::class)
    val originalEndDate: LocalDate? = null,
    val originalEndTime: String? = null,
    val isCheckedIn: Boolean = false,
    val archivedAt: Long? = null,
    val lastModified: Long = System.currentTimeMillis()  // 最后修改时间戳
)