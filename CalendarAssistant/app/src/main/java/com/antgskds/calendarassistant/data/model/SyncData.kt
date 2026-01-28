package com.antgskds.calendarassistant.data.model

import kotlinx.serialization.Serializable

/**
 * 日历同步数据模型
 * 独立于 MySettings，存储在 sync_data.json 中
 */
@Serializable
data class SyncData(
    /**
     * 是否启用日历同步
     */
    val isSyncEnabled: Boolean = false,

    /**
     * 目标系统日历的 ID
     * -1 表示未设置，将使用默认日历
     */
    val targetCalendarId: Long = -1L,

    /**
     * 应用内事件 ID 到系统日历事件 ID 的映射
     * Key: 应用内事件 ID (MyEvent.id 或 Course.id)
     * Value: 系统日历事件 ID (Long 转字符串)
     */
    val mapping: Map<String, String> = emptyMap(),

    /**
     * 上次同步时间戳（毫秒）
     */
    val lastSyncTime: Long = 0L,

    /**
     * 上次完整同步课程时的学期标识
     * 用于判断是否需要重新生成课程事件
     * 格式: "{startWeek}-{endWeek}" 或基于开学日期的哈希
     */
    val lastSemesterHash: String = ""
)
