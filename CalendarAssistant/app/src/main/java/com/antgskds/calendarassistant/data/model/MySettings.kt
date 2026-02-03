package com.antgskds.calendarassistant.data.model

import kotlinx.serialization.Serializable

@Serializable
data class MySettings(
    // AI 模型配置
    val modelKey: String = "",
    val modelName: String = "gpt-3.5-turbo",
    val modelUrl: String = "",
    val modelProvider: String = "", // 保留旧字段，防止数据丢失

    // 功能开关
    val showTomorrowEvents: Boolean = false,
    val isDailySummaryEnabled: Boolean = false,
    val isAdvanceReminderEnabled: Boolean = false, // 日程提前提醒总开关
    val advanceReminderMinutes: Int = 20, // 提前分钟数（10/20/30）

    // 识别设置
    val tempEventsUseRecognitionTime: Boolean = true, // 旧版默认为 true
    val screenshotDelayMs: Long = 500L,
    val isLiveCapsuleEnabled: Boolean = false,

    // 【新增】取件码聚合开关 (Beta)
    val isPickupAggregationEnabled: Boolean = false,

    // 【新增】归档配置
    val autoArchiveEnabled: Boolean = false, // 自动归档总开关
    val archiveDaysThreshold: Int = 0, // 归档阈值天数（过期多少天后归档，0=立即归档）

    // 课表设置
    val semesterStartDate: String = "",
    val totalWeeks: Int = 20, // 旧版默认为 20
    val timeTableJson: String = "",

    // 主题设置
    val isDarkMode: Boolean = false,

    // UI 大小设置：1=小, 2=中(默认), 3=大
    val uiSize: Int = 2,

    // 【实验室】网速胶囊开关
    val isNetworkSpeedCapsuleEnabled: Boolean = false
)