package com.antgskds.calendarassistant.data.model

import com.antgskds.calendarassistant.data.model.serializers.LocalDateSerializer
import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * 事件内容指纹
 * 用于判断两个事件是否为"同一个事件"（内容上相同）
 *
 * 指纹组成：title + startDate + endDate + startTime + endTime + location
 */
@Serializable
data class EventFingerprint(
    val title: String,
    @Serializable(with = LocalDateSerializer::class)
    val startDate: LocalDate,
    @Serializable(with = LocalDateSerializer::class)
    val endDate: LocalDate,
    val startTime: String,
    val endTime: String,
    val location: String
)
