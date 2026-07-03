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

object RecognitionMode {
    const val AI_ONLY = 0
    const val REGEX_ONLY = 1
    const val REGEX_THEN_AI_ON_EMPTY = 2
    const val REGEX_THEN_AI_REVIEW = 3

    val ALL = listOf(AI_ONLY, REGEX_ONLY, REGEX_THEN_AI_ON_EMPTY, REGEX_THEN_AI_REVIEW)

    fun normalize(value: Int): Int {
        return if (value in ALL) value else AI_ONLY
    }

    fun label(value: Int): String {
        return when (normalize(value)) {
            AI_ONLY -> "仅 AI"
            REGEX_ONLY -> "仅正则"
            REGEX_THEN_AI_ON_EMPTY -> "正则优先，失败后 AI"
            REGEX_THEN_AI_REVIEW -> "正则先入库，AI 后台修正"
            else -> "仅 AI"
        }
    }

    fun description(value: Int): String {
        return when (normalize(value)) {
            AI_ONLY -> "沿用当前 AI 识别链路"
            REGEX_ONLY -> "只使用本地正则规则，不消耗 AI 额度"
            REGEX_THEN_AI_ON_EMPTY -> "先用正则，未匹配时再调用 AI"
            REGEX_THEN_AI_REVIEW -> "正则先创建日程，AI 后台复核并修正，会消耗 AI 额度"
            else -> "沿用当前 AI 识别链路"
        }
    }
}

val DEFAULT_EVENT_COLOR_PALETTE_HEX = listOf(
    "#91A3B0",
    "#B4C3A1",
    "#D1B29E",
    "#968D8D",
    "#BCCAD6",
    "#CFD1D3",
    "#A2B5BB",
    "#E2C4C4"
)

private val EVENT_COLOR_HEX_PATTERN = Regex("[0-9A-Fa-f]{6}")

fun normalizeEventColorHex(hex: String): String? {
    val normalized = hex.trim().removePrefix("#")
    if (!normalized.matches(EVENT_COLOR_HEX_PATTERN)) return null
    return "#${normalized.uppercase()}"
}

fun sanitizeEventColorPaletteHex(colors: List<String>): List<String> {
    val normalized = colors.mapNotNull(::normalizeEventColorHex).distinct()
    return normalized.ifEmpty { DEFAULT_EVENT_COLOR_PALETTE_HEX }
}

fun eventColorHexToArgb(hex: String, fallback: Int = 0xFF91A3B0.toInt()): Int {
    val normalized = normalizeEventColorHex(hex) ?: return fallback
    val rgb = normalized.removePrefix("#").toLongOrNull(16) ?: return fallback
    return (0xFF000000 or rgb).toInt()
}

fun eventColorPaletteToArgb(colors: List<String>): List<Int> =
    sanitizeEventColorPaletteHex(colors).map(::eventColorHexToArgb)

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
    val dailySummaryMorningMinuteOfDay: Int = DAILY_SUMMARY_DEFAULT_MORNING_MINUTE_OF_DAY, // 今日提醒时间，默认 06:00
    val dailySummaryEveningMinuteOfDay: Int = DAILY_SUMMARY_DEFAULT_EVENING_MINUTE_OF_DAY, // 明日预告时间，默认 22:00
    val isAdvanceReminderEnabled: Boolean = false, // 日程提前提醒总开关
    val advanceReminderMinutes: Int = 30, // 提前分钟数（30/45/60）
    val hapticFeedbackEnabled: Boolean = true,

    // 列表排序方向（true=倒序，false=正序）。默认值 = 各列表当前行为，保证零行为变化。
    val homeListReverseOrder: Boolean = false,        // 首页今日/明日，现状正序
    val allEventsListReverseOrder: Boolean = false,   // 全部日程页，现状正序
    val floatingListReverseOrder: Boolean = true,     // 悬浮窗，现状倒序
    val archivesListReverseOrder: Boolean = true,     // 归档页，现状倒序


    // 识别设置
    val tempEventsUseRecognitionTime: Boolean = true, // 旧版默认为 true
    val recognitionMode: Int = RecognitionMode.AI_ONLY,
    val defaultEventDurationMinutes: Int = 60,
    val eventColorPaletteHex: List<String> = DEFAULT_EVENT_COLOR_PALETTE_HEX,
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

    // 【实验室】语音输入总开关
    val voiceInputEnabled: Boolean = false,

    // 【实验室】悬浮窗已呼出后，长按音量+是否允许进入语音输入；默认保留旧行为
    val floatingVoiceLongPressEnabled: Boolean = true,

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

    // 自定义背景：图片保存到 App 私有目录，图片取色结果复用自定义主题色链路
    val appBackgroundEnabled: Boolean = false,
    val appBackgroundImagePath: String = "",
    val appBackgroundSeedColorHex: String = "",
    val appBackgroundImageColorEnabled: Boolean = false,
    val appBackgroundMiuiBlurTestEnabled: Boolean = false,
    val appBackgroundWallpaperBlurEnabled: Boolean = false,
    val appBackgroundScrimAlphaPercent: Int = 0, // 旧设置兼容字段；主界面背景不再叠加蒙层

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
    // —— 通知策略（Policy Config：ConfigCatalog 登记、业务读取；默认 = 原硬编码常量）——
    val resultNotificationTimeoutMs: Int = 8000,
    val quickMemoSuggestionTimeoutMs: Int = 60000,
    val dailySummaryTimeoutMs: Int = 60000,
    val weatherNotificationTimeoutMs: Int = 180000,
    val ocrProgressTimeoutMs: Int = 120000,
    val modelLoadingTimeoutMs: Int = 600000,
    // —— 天气预测风险阈值（Policy Config；默认 = WeatherRiskAnalyzer 原硬编码值；可按地区/用户覆盖）——
    val weatherHighTempTriggerCelsius: Int = 35,
    val weatherHighTempHighCelsius: Int = 38,
    val weatherLowTempTriggerCelsius: Int = 0,
    val weatherLowTempHighCelsius: Int = -5,
    val weatherStrongCoolingDropCelsius: Int = 6,
    val weatherSevereCoolingDropCelsius: Int = 10,
    val weatherCoolingLookbackHours: Int = 6,
    val weatherWindScaleTrigger: Int = 5,
    val weatherWindScaleMedium: Int = 6,
    val weatherRainTriggerPrecipTenthMm: Int = 1,
    val weatherRainMediumPrecipTenthMm: Int = 10,
    val weatherRainHighPrecipTenthMm: Int = 70,
    val weatherRainTriggerPopPercent: Int = 60,
    val weatherRainMediumPopPercent: Int = 80,

    // 长按音量+动作
    val volumeUpLongPressEnabled: Boolean = false,
    val volumeUpLongPressAction: Int = 1, // 1=识屏, 2=悬浮窗, 3=语音输入

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
        const val DAILY_SUMMARY_MIN_MINUTE_OF_DAY = 0
        const val DAILY_SUMMARY_MAX_MINUTE_OF_DAY = 1439
        const val DAILY_SUMMARY_DEFAULT_MORNING_MINUTE_OF_DAY = 6 * 60
        const val DAILY_SUMMARY_DEFAULT_EVENING_MINUTE_OF_DAY = 22 * 60

        fun normalizeScreenshotDelayMs(delayMs: Long): Long {
            return delayMs.coerceIn(SCREENSHOT_DELAY_MIN_MS, SCREENSHOT_DELAY_MAX_MS)
        }

        fun normalizeDailySummaryMinuteOfDay(minuteOfDay: Int): Int {
            return minuteOfDay.coerceIn(DAILY_SUMMARY_MIN_MINUTE_OF_DAY, DAILY_SUMMARY_MAX_MINUTE_OF_DAY)
        }

        fun normalizeRecognitionMode(mode: Int): Int {
            return RecognitionMode.normalize(mode)
        }
    }
}
