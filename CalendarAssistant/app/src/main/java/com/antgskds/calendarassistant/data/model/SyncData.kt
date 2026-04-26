package com.antgskds.calendarassistant.data.model

import kotlinx.serialization.Serializable

/**
 * 同步数据存储模型。第一阶段简化，仅保留基本结构。
 */
@Serializable
data class SyncData(
    val lastSyncTimestamp: Long = 0L,
    val syncedCalendarIds: List<String> = emptyList()
)
