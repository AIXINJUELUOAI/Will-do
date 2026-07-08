package com.antgskds.calendarassistant.shared.management.catalog

import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.data.model.RecognitionMode

/**
 * 配置目录（catalog）—— 可调配置项的唯一声明清单。
 *
 * 设计要点（对应架构纪律「注册要承重」「唯一事实源不动」）：
 * - 值仍存 [MySettings]（SharedPreferences JSON，唯一事实源），这里**只登记元信息**
 *   （域 / 键 / 标签 / 说明 / 暴露级别 / 控件类型）+ 与 MySettings 字段的**绑定**（get/set）。
 * - 加一条配置 = 在 [ConfigCatalog.items] 里 add 一条；配置编辑页自动按域分组渲染、读写走 get/set，
 *   **不需要改任何导航枚举 / 编辑页代码**。
 * - 本文件只放声明（management 纪律：不放流程/发布/网络/DB）；真正写回由编辑页调
 *   settingsOperationApi.updateSettings 完成。
 *
 * 首版只支持 Int 控件（[ConfigControl.IntOptions]），足够天气阈值这条纵切。等出现第二种类型
 * （如某个 Boolean 配置）时，再把下面 get/set 的 Int 泛化为 sealed ConfigValue。
 */

/** 配置分组（域）。落地页按出现的域分组；加新域时在此加一个值。 */
enum class ConfigDomain(val label: String) {
    APPEARANCE("外观"),
    RECOGNITION("识别"),
    WEATHER("天气"),
    NOTIFICATION("通知"),
    VOICE("语音"),
}

/** 暴露级别（§5.5）。编辑页按级别决定是否渲染；SYSTEM_INTERNAL 不渲染。 */
enum class ConfigExposure {
    USER_EDITABLE,    // 普通用户可改（将来可在普通设置页也开入口）
    DEVELOPER_ONLY,   // 仅开发者页可见
    SYSTEM_INTERNAL,  // 系统内部，不在编辑页出现
}

/** 配置层级：用户显式设置 vs 系统底层策略。开发者编辑器先服务 POLICY，用户页将来服务 USER_SETTING。 */
enum class ConfigKind {
    USER_SETTING,   // 用户显式设置（天气开关、刷新间隔…），偏产品功能
    POLICY,         // 系统底层策略（阈值、时长、模板…），偏规则常量；防止散写裸常量
}

/** 控件类型：决定子页怎么渲染、怎么取值/写值。 */
sealed interface ConfigControl {
    /** 离散整数选项（如天气阈值的 1/2/3）。 */
    data class IntOptions(val options: List<Option>) : ConfigControl {
        data class Option(val value: Int, val label: String)
    }
    /** 整数步进输入（带上下限/步长/单位）。用于天气阈值等任意数值。 */
    data class IntInput(val min: Int, val max: Int, val step: Int = 1, val unitLabel: String = "") : ConfigControl
    /** 开关（Boolean）。get/set 以 0/1 与 Int 字段桥接。 */
    object Toggle : ConfigControl
    // 以后扩展：EnumOptions / DoubleInput(降水阈值) …
}

/**
 * 一条配置的登记。get/set 把控件值绑定到具体 MySettings 字段（首版仅 Int）。
 */
class ConfigItem(
    val domain: ConfigDomain,
    val kind: ConfigKind,                     // 用户设置(USER_SETTING) / 系统策略(POLICY)
    val key: String,                          // 稳定标识，如 "weather.location_stability_hits"
    val label: String,
    val description: String,
    val exposure: ConfigExposure,
    val control: ConfigControl,
    val get: (MySettings) -> Int,             // 读当前值
    val set: (MySettings, Int) -> MySettings, // 产出更新后的 settings
)

object ConfigCatalog {

    val items: List<ConfigItem> = listOf(
        ConfigItem(
            domain = ConfigDomain.APPEARANCE,
            kind = ConfigKind.USER_SETTING,
            key = "appearance.background.image_color.enabled",
            label = "背景图片取色",
            description = "在系统动态取色和背景图片取色之间切换主题色来源。",
            exposure = ConfigExposure.USER_EDITABLE,
            control = ConfigControl.Toggle,
            get = { if (it.appBackgroundImageColorEnabled) 1 else 0 },
            set = { s, v -> s.copy(appBackgroundImageColorEnabled = v != 0) },
        ),
        ConfigItem(
            domain = ConfigDomain.APPEARANCE,
            kind = ConfigKind.USER_SETTING,
            key = "appearance.background.miui_blur_test.enabled",
            label = "MIUI 风格测试",
            description = "开发者实验开关：导入背景图片后，将玻璃组件切换为 MIUI 风格模糊态，明暗跟随主题。",
            exposure = ConfigExposure.DEVELOPER_ONLY,
            control = ConfigControl.Toggle,
            get = { if (it.appBackgroundMiuiBlurTestEnabled) 1 else 0 },
            set = { s, v -> s.copy(appBackgroundMiuiBlurTestEnabled = v != 0) },
        ),
        ConfigItem(
            domain = ConfigDomain.APPEARANCE,
            kind = ConfigKind.USER_SETTING,
            key = "appearance.background.wallpaper_blur.enabled",
            label = "壁纸模糊",
            description = "导入背景图片后，单独控制壁纸图片层是否启用模糊。",
            exposure = ConfigExposure.USER_EDITABLE,
            control = ConfigControl.Toggle,
            get = { if (it.appBackgroundWallpaperBlurEnabled) 1 else 0 },
            set = { s, v -> s.copy(appBackgroundWallpaperBlurEnabled = v != 0) },
        ),
        ConfigItem(
            domain = ConfigDomain.APPEARANCE,
            kind = ConfigKind.USER_SETTING,
            key = "appearance.background.card_alpha_percent",
            label = "壁纸卡片透明度",
            description = "导入背景图片后，控制卡片和底栏玻璃表面的透明度百分比。",
            exposure = ConfigExposure.DEVELOPER_ONLY,
            control = ConfigControl.IntInput(
                min = MySettings.APP_BACKGROUND_CARD_ALPHA_MIN_PERCENT,
                max = MySettings.APP_BACKGROUND_CARD_ALPHA_MAX_PERCENT,
                unitLabel = " %"
            ),
            get = { it.appBackgroundCardAlphaPercent },
            set = { s, v -> s.copy(appBackgroundCardAlphaPercent = MySettings.normalizeAppBackgroundCardAlphaPercent(v)) },
        ),
        ConfigItem(
            domain = ConfigDomain.RECOGNITION,
            kind = ConfigKind.USER_SETTING,
            key = "recognition.user.mode",
            label = "识别模式",
            description = "选择文本/语音转写识别时走 AI、正则，或正则未匹配后再走 AI。",
            exposure = ConfigExposure.USER_EDITABLE,
            control = ConfigControl.IntOptions(
                RecognitionMode.ALL.map { mode ->
                    ConfigControl.IntOptions.Option(mode, RecognitionMode.label(mode))
                }
            ),
            get = { it.recognitionMode },
            set = { s, v -> s.copy(recognitionMode = MySettings.normalizeRecognitionMode(v)) },
        ),
        ConfigItem(
            domain = ConfigDomain.WEATHER,
            kind = ConfigKind.POLICY,
            key = "weather.location_stability_hits",
            label = "位置稳定阈值",
            description = "自动定位连续命中同一位置达到阈值后才发送天气预警/风险通知；1 次表示关闭兜底。",
            exposure = ConfigExposure.DEVELOPER_ONLY,
            control = ConfigControl.IntOptions(
                listOf(
                    ConfigControl.IntOptions.Option(1, "1 次（关闭兜底）"),
                    ConfigControl.IntOptions.Option(2, "2 次（默认）"),
                    ConfigControl.IntOptions.Option(3, "3 次（严格）"),
                )
            ),
            get = { it.weatherLocationStabilityRequiredHits },
            set = { s, v -> s.copy(weatherLocationStabilityRequiredHits = v) },
        ),
        ConfigItem(
            domain = ConfigDomain.NOTIFICATION,
            kind = ConfigKind.POLICY,
            key = "notification.result.timeout_ms",
            label = "结果通知停留时长",
            description = "OCR/识别结果类通知自动消失前停留的时长。",
            exposure = ConfigExposure.DEVELOPER_ONLY,
            control = ConfigControl.IntOptions(
                listOf(
                    ConfigControl.IntOptions.Option(5000, "5 秒"),
                    ConfigControl.IntOptions.Option(8000, "8 秒（默认）"),
                    ConfigControl.IntOptions.Option(15000, "15 秒"),
                    ConfigControl.IntOptions.Option(30000, "30 秒"),
                )
            ),
            get = { it.resultNotificationTimeoutMs },
            set = { s, v -> s.copy(resultNotificationTimeoutMs = v) },
        ),
        ConfigItem(
            domain = ConfigDomain.NOTIFICATION,
            kind = ConfigKind.POLICY,
            key = "notification.quick_memo_suggestion.timeout_ms",
            label = "快速备忘建议停留时长",
            description = "快速备忘建议通知自动消失前停留的时长。",
            exposure = ConfigExposure.DEVELOPER_ONLY,
            control = ConfigControl.IntOptions(
                listOf(
                    ConfigControl.IntOptions.Option(30000, "30 秒"),
                    ConfigControl.IntOptions.Option(60000, "60 秒（默认）"),
                    ConfigControl.IntOptions.Option(120000, "120 秒"),
                )
            ),
            get = { it.quickMemoSuggestionTimeoutMs },
            set = { s, v -> s.copy(quickMemoSuggestionTimeoutMs = v) },
        ),
        ConfigItem(
            domain = ConfigDomain.NOTIFICATION,
            kind = ConfigKind.POLICY,
            key = "notification.daily_summary.timeout_ms",
            label = "每日汇总停留时长",
            description = "每日日程汇总通知自动消失前停留的时长。",
            exposure = ConfigExposure.DEVELOPER_ONLY,
            control = ConfigControl.IntOptions(
                listOf(
                    ConfigControl.IntOptions.Option(30000, "30 秒"),
                    ConfigControl.IntOptions.Option(60000, "60 秒（默认）"),
                    ConfigControl.IntOptions.Option(120000, "120 秒"),
                )
            ),
            get = { it.dailySummaryTimeoutMs },
            set = { s, v -> s.copy(dailySummaryTimeoutMs = v) },
        ),
        ConfigItem(
            domain = ConfigDomain.NOTIFICATION,
            kind = ConfigKind.POLICY,
            key = "notification.weather.timeout_ms",
            label = "天气通知停留时长",
            description = "天气预警/风险通知自动消失前停留的时长，普通通知与灵动通知两种形态共用同一值。",
            exposure = ConfigExposure.DEVELOPER_ONLY,
            control = ConfigControl.IntOptions(
                listOf(
                    ConfigControl.IntOptions.Option(60000, "1 分钟"),
                    ConfigControl.IntOptions.Option(180000, "3 分钟（默认）"),
                    ConfigControl.IntOptions.Option(300000, "5 分钟"),
                )
            ),
            get = { it.weatherNotificationTimeoutMs },
            set = { s, v -> s.copy(weatherNotificationTimeoutMs = v) },
        ),
        ConfigItem(
            domain = ConfigDomain.NOTIFICATION,
            kind = ConfigKind.POLICY,
            key = "notification.ocr_progress.timeout_ms",
            label = "识别进度胶囊时长",
            description = "OCR 识别进行中的进度胶囊在自动消失前的最长停留时长（仅灵动形态，无普通对应）。",
            exposure = ConfigExposure.DEVELOPER_ONLY,
            control = ConfigControl.IntOptions(
                listOf(
                    ConfigControl.IntOptions.Option(60000, "1 分钟"),
                    ConfigControl.IntOptions.Option(120000, "2 分钟（默认）"),
                    ConfigControl.IntOptions.Option(300000, "5 分钟"),
                )
            ),
            get = { it.ocrProgressTimeoutMs },
            set = { s, v -> s.copy(ocrProgressTimeoutMs = v) },
        ),
        ConfigItem(
            domain = ConfigDomain.NOTIFICATION,
            kind = ConfigKind.POLICY,
            key = "notification.model_loading.timeout_ms",
            label = "模型加载胶囊时长",
            description = "模型加载中的胶囊在自动消失前的最长停留时长（仅灵动形态，无普通对应）。",
            exposure = ConfigExposure.DEVELOPER_ONLY,
            control = ConfigControl.IntOptions(
                listOf(
                    ConfigControl.IntOptions.Option(300000, "5 分钟"),
                    ConfigControl.IntOptions.Option(600000, "10 分钟（默认）"),
                    ConfigControl.IntOptions.Option(900000, "15 分钟"),
                )
            ),
            get = { it.modelLoadingTimeoutMs },
            set = { s, v -> s.copy(modelLoadingTimeoutMs = v) },
        ),
        ConfigItem(
            domain = ConfigDomain.VOICE,
            kind = ConfigKind.USER_SETTING,
            key = "voice.input.enabled",
            label = "语音输入",
            description = "语音输入总开关；关闭后长按音量+和悬浮窗入口都不能启动语音输入。",
            exposure = ConfigExposure.USER_EDITABLE,
            control = ConfigControl.Toggle,
            get = { if (it.voiceInputEnabled) 1 else 0 },
            set = { s, v -> s.copy(voiceInputEnabled = v != 0) },
        ),
        ConfigItem(
            domain = ConfigDomain.VOICE,
            kind = ConfigKind.USER_SETTING,
            key = "voice.floating_long_press.enabled",
            label = "悬浮窗长按语音输入",
            description = "控制悬浮窗已呼出后再次长按音量+是否进入语音输入；默认开启以保留旧行为。",
            exposure = ConfigExposure.USER_EDITABLE,
            control = ConfigControl.Toggle,
            get = { if (it.floatingVoiceLongPressEnabled) 1 else 0 },
            set = { s, v -> s.copy(floatingVoiceLongPressEnabled = v != 0) },
        ),
        ConfigItem(
            domain = ConfigDomain.VOICE,
            kind = ConfigKind.USER_SETTING,
            key = "voice.floating_text_quick_memo.auto_pin_enabled",
            label = "悬浮窗文本随口记同步挂起",
            description = "悬浮窗随口记模式保存文本后，同步挂到实况通知；需开启实况通知。",
            exposure = ConfigExposure.USER_EDITABLE,
            control = ConfigControl.Toggle,
            get = { if (it.floatingTextQuickMemoAutoPinEnabled) 1 else 0 },
            set = { s, v -> s.copy(floatingTextQuickMemoAutoPinEnabled = v != 0) },
        ),
        ConfigItem(
            domain = ConfigDomain.VOICE,
            kind = ConfigKind.USER_SETTING,
            key = "voice.quick_memo.auto_pin_enabled",
            label = "语音随口记同步挂起",
            description = "语音随口记转写完成后，自动挂到实况通知；需开启实况通知。",
            exposure = ConfigExposure.USER_EDITABLE,
            control = ConfigControl.Toggle,
            get = { if (it.voiceQuickMemoAutoPinEnabled) 1 else 0 },
            set = { s, v -> s.copy(voiceQuickMemoAutoPinEnabled = v != 0) },
        ),
        // —— 天气预测风险阈值（软件自算的 WeatherRiskAlert，非官方 API 预警）——
        ConfigItem(
            domain = ConfigDomain.WEATHER, kind = ConfigKind.POLICY,
            key = "weather.risk.high_temp.trigger_celsius",
            label = "高温触发温度", description = "气温达到此值（℃）触发高温风险提示。",
            exposure = ConfigExposure.DEVELOPER_ONLY,
            control = ConfigControl.IntInput(min = 25, max = 45, unitLabel = " ℃"),
            get = { it.weatherHighTempTriggerCelsius },
            set = { s, v -> s.copy(weatherHighTempTriggerCelsius = v) },
        ),
        ConfigItem(
            domain = ConfigDomain.WEATHER, kind = ConfigKind.POLICY,
            key = "weather.risk.high_temp.high_celsius",
            label = "高温高等级温度", description = "气温达到此值（℃）时高温风险升为高等级。",
            exposure = ConfigExposure.DEVELOPER_ONLY,
            control = ConfigControl.IntInput(min = 30, max = 50, unitLabel = " ℃"),
            get = { it.weatherHighTempHighCelsius },
            set = { s, v -> s.copy(weatherHighTempHighCelsius = v) },
        ),
        ConfigItem(
            domain = ConfigDomain.WEATHER, kind = ConfigKind.POLICY,
            key = "weather.risk.low_temp.trigger_celsius",
            label = "低温触发温度", description = "气温低至此值（℃）触发低温/寒冷风险提示。南方可设 0，北方可调低。",
            exposure = ConfigExposure.DEVELOPER_ONLY,
            control = ConfigControl.IntInput(min = -20, max = 15, unitLabel = " ℃"),
            get = { it.weatherLowTempTriggerCelsius },
            set = { s, v -> s.copy(weatherLowTempTriggerCelsius = v) },
        ),
        ConfigItem(
            domain = ConfigDomain.WEATHER, kind = ConfigKind.POLICY,
            key = "weather.risk.low_temp.high_celsius",
            label = "低温高等级温度", description = "气温低至此值（℃）时低温风险升为高等级。",
            exposure = ConfigExposure.DEVELOPER_ONLY,
            control = ConfigControl.IntInput(min = -30, max = 5, unitLabel = " ℃"),
            get = { it.weatherLowTempHighCelsius },
            set = { s, v -> s.copy(weatherLowTempHighCelsius = v) },
        ),
        ConfigItem(
            domain = ConfigDomain.WEATHER, kind = ConfigKind.POLICY,
            key = "weather.risk.cooling.strong_drop_celsius",
            label = "强降温降幅", description = "回看时段内最高温降幅达到此值（℃）触发强降温提示。",
            exposure = ConfigExposure.DEVELOPER_ONLY,
            control = ConfigControl.IntInput(min = 3, max = 15, unitLabel = " ℃"),
            get = { it.weatherStrongCoolingDropCelsius },
            set = { s, v -> s.copy(weatherStrongCoolingDropCelsius = v) },
        ),
        ConfigItem(
            domain = ConfigDomain.WEATHER, kind = ConfigKind.POLICY,
            key = "weather.risk.cooling.severe_drop_celsius",
            label = "剧烈降温降幅", description = "降幅达到此值（℃）时降温风险升为高等级。",
            exposure = ConfigExposure.DEVELOPER_ONLY,
            control = ConfigControl.IntInput(min = 5, max = 25, unitLabel = " ℃"),
            get = { it.weatherSevereCoolingDropCelsius },
            set = { s, v -> s.copy(weatherSevereCoolingDropCelsius = v) },
        ),
        ConfigItem(
            domain = ConfigDomain.WEATHER, kind = ConfigKind.POLICY,
            key = "weather.risk.cooling.lookback_hours",
            label = "降温回看时长", description = "计算降温幅度时向前回看的小时数。",
            exposure = ConfigExposure.DEVELOPER_ONLY,
            control = ConfigControl.IntInput(min = 1, max = 24, unitLabel = " 小时"),
            get = { it.weatherCoolingLookbackHours },
            set = { s, v -> s.copy(weatherCoolingLookbackHours = v) },
        ),
        ConfigItem(
            domain = ConfigDomain.WEATHER, kind = ConfigKind.POLICY,
            key = "weather.risk.wind.trigger_scale",
            label = "大风触发风力", description = "风力达到此级数触发大风风险提示。",
            exposure = ConfigExposure.DEVELOPER_ONLY,
            control = ConfigControl.IntInput(min = 3, max = 12, unitLabel = " 级"),
            get = { it.weatherWindScaleTrigger },
            set = { s, v -> s.copy(weatherWindScaleTrigger = v) },
        ),
        ConfigItem(
            domain = ConfigDomain.WEATHER, kind = ConfigKind.POLICY,
            key = "weather.risk.wind.medium_scale",
            label = "大风中等级风力", description = "风力达到此级数时大风风险升为中等级。",
            exposure = ConfigExposure.DEVELOPER_ONLY,
            control = ConfigControl.IntInput(min = 4, max = 17, unitLabel = " 级"),
            get = { it.weatherWindScaleMedium },
            set = { s, v -> s.copy(weatherWindScaleMedium = v) },
        ),
        ConfigItem(
            domain = ConfigDomain.WEATHER,
            kind = ConfigKind.POLICY,
            key = "weather.risk.rain.trigger_precip_tenth_mm",
            label = "降雨触发降水量",
            description = "单位时段降水量达到此值触发降雨风险提示（按 0.1mm 计）。",
            exposure = ConfigExposure.DEVELOPER_ONLY,
            control = ConfigControl.IntOptions(
                listOf(
                    ConfigControl.IntOptions.Option(1, "0.1mm（默认）"),
                    ConfigControl.IntOptions.Option(5, "0.5mm"),
                    ConfigControl.IntOptions.Option(10, "1.0mm"),
                )
            ),
            get = { it.weatherRainTriggerPrecipTenthMm },
            set = { s, v -> s.copy(weatherRainTriggerPrecipTenthMm = v) },
        ),
        ConfigItem(
            domain = ConfigDomain.WEATHER,
            kind = ConfigKind.POLICY,
            key = "weather.risk.rain.medium_precip_tenth_mm",
            label = "中雨级降水量",
            description = "降水量达到此值时降雨风险升为中等级（按 0.1mm 计）。",
            exposure = ConfigExposure.DEVELOPER_ONLY,
            control = ConfigControl.IntOptions(
                listOf(
                    ConfigControl.IntOptions.Option(10, "1.0mm（默认）"),
                    ConfigControl.IntOptions.Option(20, "2.0mm"),
                    ConfigControl.IntOptions.Option(50, "5.0mm"),
                )
            ),
            get = { it.weatherRainMediumPrecipTenthMm },
            set = { s, v -> s.copy(weatherRainMediumPrecipTenthMm = v) },
        ),
        ConfigItem(
            domain = ConfigDomain.WEATHER,
            kind = ConfigKind.POLICY,
            key = "weather.risk.rain.high_precip_tenth_mm",
            label = "暴雨级降水量",
            description = "降水量达到此值时降雨风险升为高等级（按 0.1mm 计）。",
            exposure = ConfigExposure.DEVELOPER_ONLY,
            control = ConfigControl.IntOptions(
                listOf(
                    ConfigControl.IntOptions.Option(70, "7.0mm（默认）"),
                    ConfigControl.IntOptions.Option(100, "10.0mm"),
                    ConfigControl.IntOptions.Option(150, "15.0mm"),
                )
            ),
            get = { it.weatherRainHighPrecipTenthMm },
            set = { s, v -> s.copy(weatherRainHighPrecipTenthMm = v) },
        ),
        ConfigItem(
            domain = ConfigDomain.WEATHER,
            kind = ConfigKind.POLICY,
            key = "weather.risk.rain.trigger_pop_percent",
            label = "降雨触发概率",
            description = "降水概率达到此百分比也触发降雨风险提示。",
            exposure = ConfigExposure.DEVELOPER_ONLY,
            control = ConfigControl.IntInput(min = 0, max = 100, unitLabel = " %"),
            get = { it.weatherRainTriggerPopPercent },
            set = { s, v -> s.copy(weatherRainTriggerPopPercent = v) },
        ),
        ConfigItem(
            domain = ConfigDomain.WEATHER,
            kind = ConfigKind.POLICY,
            key = "weather.risk.rain.medium_pop_percent",
            label = "中雨级概率",
            description = "降水概率达到此百分比时降雨风险升为中等级。",
            exposure = ConfigExposure.DEVELOPER_ONLY,
            control = ConfigControl.IntInput(min = 0, max = 100, unitLabel = " %"),
            get = { it.weatherRainMediumPopPercent },
            set = { s, v -> s.copy(weatherRainMediumPopPercent = v) },
        ),
        // —— 天气·用户可调（USER_EDITABLE；将来普通设置页也照此渲染）——
        ConfigItem(
            domain = ConfigDomain.WEATHER,
            kind = ConfigKind.USER_SETTING,
            key = "weather.user.warning_enabled",
            label = "天气预警",
            description = "开启后在命中条件时推送官方天气预警。",
            exposure = ConfigExposure.USER_EDITABLE,
            control = ConfigControl.Toggle,
            get = { if (it.weatherWarningEnabled) 1 else 0 },
            set = { s, v -> s.copy(weatherWarningEnabled = v != 0) },
        ),
        ConfigItem(
            domain = ConfigDomain.WEATHER,
            kind = ConfigKind.USER_SETTING,
            key = "weather.user.risk_warning_enabled",
            label = "天气风险提醒",
            description = "开启后根据小时预报自算降雨/高温等风险并提醒。",
            exposure = ConfigExposure.USER_EDITABLE,
            control = ConfigControl.Toggle,
            get = { if (it.weatherRiskWarningEnabled) 1 else 0 },
            set = { s, v -> s.copy(weatherRiskWarningEnabled = v != 0) },
        ),
        ConfigItem(
            domain = ConfigDomain.WEATHER,
            kind = ConfigKind.USER_SETTING,
            key = "weather.user.show_in_floating",
            label = "悬浮窗显示天气",
            description = "在悬浮窗中显示天气信息。",
            exposure = ConfigExposure.USER_EDITABLE,
            control = ConfigControl.Toggle,
            get = { if (it.showWeatherInFloating) 1 else 0 },
            set = { s, v -> s.copy(showWeatherInFloating = v != 0) },
        ),
        ConfigItem(
            domain = ConfigDomain.WEATHER,
            kind = ConfigKind.USER_SETTING,
            key = "weather.user.warning_lookahead_hours",
            label = "预警前瞻时长",
            description = "向后扫描多少小时的预报来判定风险。",
            exposure = ConfigExposure.USER_EDITABLE,
            control = ConfigControl.IntInput(min = 1, max = 168, unitLabel = " 小时"),
            get = { it.weatherWarningLookaheadHours },
            set = { s, v -> s.copy(weatherWarningLookaheadHours = v) },
        ),
        ConfigItem(
            domain = ConfigDomain.WEATHER,
            kind = ConfigKind.USER_SETTING,
            key = "weather.user.enabled",
            label = "天气功能",
            description = "天气功能总开关；关闭后不获取天气、不推送任何天气预警/风险通知。",
            exposure = ConfigExposure.USER_EDITABLE,
            control = ConfigControl.Toggle,
            get = { if (it.weatherEnabled) 1 else 0 },
            set = { s, v -> s.copy(weatherEnabled = v != 0) },
        ),
        // —— 通知·用户开关（USER_EDITABLE；将来普通设置页也照此渲染）——
        ConfigItem(
            domain = ConfigDomain.NOTIFICATION,
            kind = ConfigKind.USER_SETTING,
            key = "notification.user.daily_summary_enabled",
            label = "每日汇总通知",
            description = "开启后每天定时推送当日（及次日）日程汇总通知。",
            exposure = ConfigExposure.USER_EDITABLE,
            control = ConfigControl.Toggle,
            get = { if (it.isDailySummaryEnabled) 1 else 0 },
            set = { s, v -> s.copy(isDailySummaryEnabled = v != 0) },
        ),
        ConfigItem(
            domain = ConfigDomain.NOTIFICATION,
            kind = ConfigKind.USER_SETTING,
            key = "notification.user.daily_summary_morning_minute_of_day",
            label = "今日提醒时间",
            description = "每天推送今日日程汇总的时间，按当天 00:00 起的分钟数保存。",
            exposure = ConfigExposure.USER_EDITABLE,
            control = ConfigControl.IntInput(
                min = MySettings.DAILY_SUMMARY_MIN_MINUTE_OF_DAY,
                max = MySettings.DAILY_SUMMARY_MAX_MINUTE_OF_DAY,
                unitLabel = " 分钟"
            ),
            get = { it.dailySummaryMorningMinuteOfDay },
            set = { s, v -> s.copy(dailySummaryMorningMinuteOfDay = MySettings.normalizeDailySummaryMinuteOfDay(v)) },
        ),
        ConfigItem(
            domain = ConfigDomain.NOTIFICATION,
            kind = ConfigKind.USER_SETTING,
            key = "notification.user.daily_summary_evening_minute_of_day",
            label = "明日预告时间",
            description = "每天推送明日日程预告的时间，按当天 00:00 起的分钟数保存。",
            exposure = ConfigExposure.USER_EDITABLE,
            control = ConfigControl.IntInput(
                min = MySettings.DAILY_SUMMARY_MIN_MINUTE_OF_DAY,
                max = MySettings.DAILY_SUMMARY_MAX_MINUTE_OF_DAY,
                unitLabel = " 分钟"
            ),
            get = { it.dailySummaryEveningMinuteOfDay },
            set = { s, v -> s.copy(dailySummaryEveningMinuteOfDay = MySettings.normalizeDailySummaryMinuteOfDay(v)) },
        ),
        ConfigItem(
            domain = ConfigDomain.NOTIFICATION,
            kind = ConfigKind.USER_SETTING,
            key = "notification.user.advance_reminder_enabled",
            label = "日程提前提醒",
            description = "日程提前提醒总开关；关闭后不再为日程发送提前提醒通知。",
            exposure = ConfigExposure.USER_EDITABLE,
            control = ConfigControl.Toggle,
            get = { if (it.isAdvanceReminderEnabled) 1 else 0 },
            set = { s, v -> s.copy(isAdvanceReminderEnabled = v != 0) },
        ),
        ConfigItem(
            domain = ConfigDomain.NOTIFICATION,
            kind = ConfigKind.USER_SETTING,
            key = "notification.user.live_capsule_enabled",
            label = "实况胶囊通知",
            description = "开启后识别进度/结果、天气等以实况胶囊（灵动）形态展示。",
            exposure = ConfigExposure.USER_EDITABLE,
            control = ConfigControl.Toggle,
            get = { if (it.isLiveCapsuleEnabled) 1 else 0 },
            set = { s, v -> s.copy(isLiveCapsuleEnabled = v != 0) },
        ),
        ConfigItem(
            domain = ConfigDomain.NOTIFICATION,
            kind = ConfigKind.USER_SETTING,
            key = "notification.user.network_speed_capsule_enabled",
            label = "网速胶囊",
            description = "开启后在实况胶囊中显示实时网速。",
            exposure = ConfigExposure.USER_EDITABLE,
            control = ConfigControl.Toggle,
            get = { if (it.isNetworkSpeedCapsuleEnabled) 1 else 0 },
            set = { s, v -> s.copy(isNetworkSpeedCapsuleEnabled = v != 0) },
        ),
    )

    /** 编辑页可见的配置项（滤掉系统内部项）。 */
    fun editableItems(): List<ConfigItem> =
        items.filter { it.exposure != ConfigExposure.SYSTEM_INTERNAL }

    /** 可见项里出现的域，保持声明顺序、去重。 */
    fun visibleDomains(): List<ConfigDomain> =
        editableItems().map { it.domain }.distinct()

    /** 某个域下的可见配置项。 */
    fun itemsInDomain(domain: ConfigDomain): List<ConfigItem> =
        editableItems().filter { it.domain == domain }
}
