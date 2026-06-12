package com.antgskds.calendarassistant.data.model

import kotlinx.serialization.Serializable

object LiveNotificationTemplateMode {
    const val AUTO = "AUTO"
    const val FULL = "FULL"
    const val COMPACT = "COMPACT"

    val ALL = listOf(AUTO, FULL, COMPACT)

    fun normalize(value: String): String {
        return if (value in ALL) value else AUTO
    }
}

@Serializable
data class MySettings(
    // AI 模型配置
    val modelKey: String = "",
    val modelName: String = "",
    val modelUrl: String = "",
    val modelProvider: String = "", // 保留旧字段，防止数据丢失
    val useMultimodalAi: Boolean = false,
    val mmModelKey: String = "",
    val mmModelName: String = "",
    val mmModelUrl: String = "",
    val disableThinking: Boolean = false,
    val isLocalSemanticEnabled: Boolean = false,
    val selectedLocalModelId: String = "",

    // 功能开关
    val showTomorrowEvents: Boolean = false,
    val isDailySummaryEnabled: Boolean = false,
    val isAdvanceReminderEnabled: Boolean = false, // 日程提前提醒总开关
    val advanceReminderMinutes: Int = 30, // 提前分钟数（30/45/60）
    val hapticFeedbackEnabled: Boolean = true,

    // 识别设置
    val tempEventsUseRecognitionTime: Boolean = true, // 旧版默认为 true
    val defaultEventDurationMinutes: Int = 60,
    val screenshotDelayMs: Long = 1000L,
    val isLiveCapsuleEnabled: Boolean = false,
    val liveNotificationTemplateMode: String = LiveNotificationTemplateMode.AUTO,

    // 【新增】取件码聚合开关 (Beta)
    val isPickupAggregationEnabled: Boolean = false,

    // 短信自动解析取件码
    val isSmsMonitoringEnabled: Boolean = false,

    // 【实验室】码类事件时间兜底：取件/取餐/取票/寄件忽略 AI 返回时间，入库时使用当前时间
    val forceInstantCodeTimeToNow: Boolean = false,

    // 【实验室】预测性返回手势
    val predictiveBackEnabled: Boolean = true,

    // 【实验室】剪贴板码类识别
    val clipboardCodeRecognitionEnabled: Boolean = false,

    // 首页入口配置（第 2~4 位，第一位固定侧边栏）
    val homeBottomItems: List<String> = listOf(HomeEntryKey.TODAY, HomeEntryKey.ALL, HomeEntryKey.NOTE),
    val homeStartPageKey: String = HomeEntryKey.TODAY,

    // 【新增】归档配置
    val autoArchiveEnabled: Boolean = false, // 自动归档总开关
    val archiveDaysThreshold: Int = 0, // 归档阈值天数（过期多少天后归档，0=立即归档）

    // 课表设置
    val courseFeatureEnabled: Boolean = true,
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
    val customThemeColorHex: String = "#6750A4",

    // UI 大小设置：1=小, 2=中(默认), 3=大
    val uiSize: Int = 2,
    val uiStyle: String = UiStyle.MATERIAL3.name,

    // 桌面小组件：0=跟随软件, 1=浅色, 2=深色
    val widgetThemeMode: Int = WidgetThemeMode.FOLLOW_APP,
    val widgetBackgroundAlpha: Float = 0.9f,

    // 【实验室】网速胶囊开关
    val isNetworkSpeedCapsuleEnabled: Boolean = false,

    // 悬浮窗功能开关
    val isFloatingWindowEnabled: Boolean = false,
    val floatingEventRange: Int = 1, // 悬浮窗日程范围：0=全部, 1=今日, 2=今日+明日
    val floatingExpandSide: String = "RIGHT", // 悬浮窗展开方向：LEFT/RIGHT

    // 天气配置
    val weatherEnabled: Boolean = false,
    val weatherApiUrl: String = "",
    val weatherApiKey: String = "",
    val weatherCity: String = "",
    val weatherLocationMode: String = "auto_fallback_manual",
    val weatherManualLocationId: String = "",
    val weatherManualLocationName: String = "",
    val weatherManualAdm1: String = "",
    val weatherManualAdm2: String = "",
    val weatherManualCountry: String = "",
    val weatherManualLat: Double = 0.0,
    val weatherManualLon: Double = 0.0,
    val weatherWarningEnabled: Boolean = true,
    val weatherRiskWarningEnabled: Boolean = true,
    val weatherWarningLookaheadHours: Int = 24,
    val weatherLocationStabilityRequiredHits: Int = 2,
    val weatherProvider: String = "qweather",
    val weatherRefreshInterval: Int = 30,
    val showWeatherInFloating: Boolean = true,
    val floatingWeatherForecastRange: Int = 0,

    // 长按音量+动作
    val volumeUpLongPressEnabled: Boolean = false,
    val volumeUpLongPressAction: Int = 0, // 0=无操作, 1=识屏, 2=悬浮窗, 3=语音输入

    // 侧边栏唤起
    val edgeBarEnabled: Boolean = false,
    val edgeBarSide: String = "RIGHT",
    val edgeBarYPercent: Float = 50f,
    val edgeBarWidthDp: Int = 8,
    val edgeBarHeightDp: Int = 120,
    val edgeBarAlpha: Float = 0.4f,

    // 捐赠状态
    val hasDonated: Boolean = false,

    // 开发者选项
    val developerOptionsUnlocked: Boolean = false,
    val developerOptionsEnabled: Boolean = false,
    val developerOptionsDisabledAtMillis: Long = 0L
) {
    companion object {
        const val SCREENSHOT_DELAY_MIN_MS = 500L
        const val SCREENSHOT_DELAY_MAX_MS = 2500L

        fun normalizeScreenshotDelayMs(delayMs: Long): Long {
            return delayMs.coerceIn(SCREENSHOT_DELAY_MIN_MS, SCREENSHOT_DELAY_MAX_MS)
        }
    }
}
