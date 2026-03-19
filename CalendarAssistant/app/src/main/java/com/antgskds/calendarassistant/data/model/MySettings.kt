package com.antgskds.calendarassistant.data.model

import kotlinx.serialization.Serializable

@Serializable
data class MySettings(
    // AI 模型配置
    val modelKey: String = "",
    val modelName: String = "gpt-3.5-turbo",
    val modelUrl: String = "",
    val modelProvider: String = "", // 保留旧字段，防止数据丢失
    val useMultimodalAi: Boolean = false,
    val mmModelKey: String = "",
    val mmModelName: String = "",
    val mmModelUrl: String = "",
    val disableThinking: Boolean = false,

    // 功能开关
    val showTomorrowEvents: Boolean = false,
    val isDailySummaryEnabled: Boolean = false,
    val isAdvanceReminderEnabled: Boolean = false, // 日程提前提醒总开关
    val advanceReminderMinutes: Int = 30, // 提前分钟数（30/45/60）

    // 识别设置
    val tempEventsUseRecognitionTime: Boolean = true, // 旧版默认为 true
    val screenshotDelayMs: Long = 1000L,
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
    val timeTableConfigJson: String = "",

    // 主题设置
    val isDarkMode: Boolean = false,

    // 主题模式：1=跟随系统, 2=浅色, 3=深色
    val themeMode: Int = 1,

    // 主题配色方案：DEFAULT/PURPLE/BLUE/GREEN/PINK/ORANGE/TEAL/NEUTRAL=固定配色
    val themeColorScheme: String = "DEFAULT",

    // UI 大小设置：1=小, 2=中(默认), 3=大
    val uiSize: Int = 2,

    // 【实验室】网速胶囊开关
    val isNetworkSpeedCapsuleEnabled: Boolean = false,

    // 智能推荐开关
    val enableSmartRecommendation: Boolean = true,

    // 悬浮窗功能开关
    val isFloatingWindowEnabled: Boolean = false,

    // 侧边栏唤起
    val edgeBarEnabled: Boolean = false,
    val edgeBarSide: String = "RIGHT",
    val edgeBarYPercent: Float = 50f,
    val edgeBarWidthDp: Int = 8,
    val edgeBarHeightDp: Int = 120,
    val edgeBarAlpha: Float = 0.4f,

    // 系统日历重复日程反向同步
    val isRecurringCalendarSyncEnabled: Boolean = false,

    // 捐赠状态
    val hasDonated: Boolean = false
)
