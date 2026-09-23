package com.antgskds.calendarassistant.shared.management.catalog

import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import com.antgskds.calendarassistant.feature.settings.data.model.FloatingBallGestureAction
import com.antgskds.calendarassistant.feature.settings.data.model.FloatingEntryStyle
import com.antgskds.calendarassistant.feature.settings.data.model.QuickMemoRecordingDisplayMode
import com.antgskds.calendarassistant.feature.settings.data.model.RecognitionMode

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
 * 控件以 Int 为主，开关以 0/1 与 [MySettings] 字段桥接。
 */

/** 配置分组（域）。落地页按出现的域分组；加新域时在此加一个值。 */
enum class ConfigDomain(val label: String) {
    DIAGNOSTICS("日志"),
    APPEARANCE("主题"),
    RECOGNITION("识别"),
    WEATHER("天气"),
    NOTIFICATION("通知"),
    VOICE("语音"),
    SYNC("同步"),
}

/** 暴露级别（§5.5）。编辑页按级别决定是否渲染；SYSTEM_INTERNAL 不渲染。 */
enum class ConfigExposure {
    USER_EDITABLE,    // 普通用户可改（将来可在普通设置页也开入口）
    DEVELOPER_ONLY,   // 仅开发者页可见
    SYSTEM_INTERNAL,  // 系统内部，不在编辑页出现
}

/** Agent 对配置项的访问级别。权限开关和系统内部项不得由 Agent 自行放权。 */
enum class AgentConfigAccess {
    NONE,
    READ_ONLY,
    READ_WRITE,
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
    val getText: (MySettings) -> String = { "" },
    val setText: (MySettings, String) -> MySettings = { s, _ -> s },
    val visible: (MySettings) -> Boolean = { true },
    val agentAccess: AgentConfigAccess = AgentConfigAccess.READ_WRITE,
)

object ConfigCatalog {
    // 平板三栏：导航栏只在右侧仍有足够空间时展开，中间栏始终保持手机内容宽度。
    const val ADAPTIVE_NAVIGATION_EXPAND_MIN_WIDTH_DP = 1116
    const val ADAPTIVE_COMPACT_PANE_WIDTH_DP = 400
    const val ADAPTIVE_CONTENT_PADDING_DP = 24
    const val ADAPTIVE_FORM_MAX_WIDTH_DP = 840
    const val ADAPTIVE_CHART_PAIR_MIN_WIDTH_DP = 800
    const val ADAPTIVE_WEATHER_TABLE_MIN_WIDTH_DP = 640

    // 右侧详情工作区：只按实际可用宽度分栏，无附件时正文限宽居中。
    const val DETAIL_WORKSPACE_SPLIT_MIN_WIDTH_DP = 760
    const val DETAIL_WORKSPACE_FORM_MAX_WIDTH_DP = 560

    // 首页日程视图：无限重复展开窗口、切换动效与末端加载手势阈值。
    const val HOME_AGENDA_PAGE_DAYS = 7
    const val HOME_AGENDA_MOTION_MS = 420
    const val HOME_AGENDA_PULL_DP = 64

    // 通知与短信正则资源上限及成功投递凭据数量。
    const val ACCOUNTING_MESSAGE_MAX_TEXT = 8192
    const val ACCOUNTING_MESSAGE_MAX_RULES = 128
    const val ACCOUNTING_MESSAGE_RECEIPTS = 256

    // 自动记账资源及调用策略；对用户只暴露总开关。
    const val AUTO_ACCOUNTING_DEBOUNCE_MS = 700
    // 支付截图先保留，再给本地通知入库短暂优先期；仅关联本次现场，不抑制历史详情。
    const val AUTO_ACCOUNTING_NOTIFICATION_WAIT_MS = 1_500
    const val AUTO_ACCOUNTING_NOTIFICATION_MATCH_MS = 3_000
    // 微信支付窗口上下文只在短会话内有效；成功事件过期后不再补拍其他页面。
    const val AUTO_ACCOUNTING_WECHAT_SESSION_MS = 120_000
    // 红包确认页等转场稳定后只缓存一次截图，不调用模型；会话沿用微信支付有效期。
    const val AUTO_ACCOUNTING_RED_PACKET_CAPTURE_DELAY_MS = 500
    const val AUTO_ACCOUNTING_SUCCESS_EVENT_MS = 5_000
    // 详情信号只用于当前页面短时间内截图；不保留到稍后的其他页面。
    const val AUTO_ACCOUNTING_DETAIL_SIGNAL_MS = 5_000
    const val AUTO_ACCOUNTING_MIN_INTERVAL_MS = 15_000
    const val AUTO_ACCOUNTING_REPEAT_MS = 300_000
    const val AUTO_ACCOUNTING_MAX_NODES = 512
    const val AUTO_ACCOUNTING_MAX_TEXT = 16_000
    const val AUTO_ACCOUNTING_MAX_PAYLOAD = 64_000
    // 微信混淆模型仅扫描指定包，限制候选类数量与结构遍历深度。
    const val AUTO_ACCOUNTING_MESSAGE_MAX_DEPTH = 16
    const val AUTO_ACCOUNTING_QUEUE_SIZE = 8
    const val AUTO_ACCOUNTING_PROVIDER_TIMEOUT_MS = 8_000
    const val RECOGNITION_SCREENSHOT_TIMEOUT_MS = 5_000
    const val AUTO_ACCOUNTING_MAX_AGE_MS = 600_000
    // 高频无障碍诊断按来源与阶段限流，避免窗口刷新占满每日日志。
    const val AUTO_ACCOUNTING_DIAGNOSTIC_INTERVAL_MS = 5_000
    // 一次性支付诊断的时间和资源上限；不改变正式支付触发条件。
    const val PAYMENT_DIAGNOSTIC_DURATION_MS = 120_000
    // 手动诊断会话的独立健康采样，不受截图耗时/次数上限影响。
    const val PAYMENT_DIAGNOSTIC_HEALTH_INTERVAL_MS = 1_500
    const val PAYMENT_DIAGNOSTIC_TREE_INTERVAL_MS = 300
    const val PAYMENT_DIAGNOSTIC_DELAY_FIRST_MS = 100
    const val PAYMENT_DIAGNOSTIC_DELAY_SECOND_MS = 700
    const val PAYMENT_DIAGNOSTIC_DELAY_LAST_MS = 1_500
    const val PAYMENT_DIAGNOSTIC_SCREENSHOT_INTERVAL_MS = 1_500
    const val PAYMENT_DIAGNOSTIC_MAX_SCREENSHOTS = 80
    const val PAYMENT_DIAGNOSTIC_MAX_IMAGE_BYTES = 64 * 1024 * 1024
    const val PAYMENT_DIAGNOSTIC_MAX_LOG_BYTES = 16 * 1024 * 1024
    const val PAYMENT_DIAGNOSTIC_MAX_EVENTS = 2_000
    const val PAYMENT_DIAGNOSTIC_MAX_TREES = 2_048
    const val PAYMENT_DIAGNOSTIC_MAX_NODES = 128
    const val PAYMENT_DIAGNOSTIC_MAX_WINDOWS = 8
    const val PAYMENT_DIAGNOSTIC_FIELD_CHARS = 512
    const val PAYMENT_DIAGNOSTIC_QUEUE_SIZE = 256
    const val PAYMENT_DIAGNOSTIC_RETAIN_SESSIONS = 3
    // 跨页面账单去重：时间容差及名称相似门槛只用于暂存疑似重复，不直接合并交易。
    const val ACCOUNTING_DUPLICATE_WINDOW_MS = 120_000
    // 账单列表通常只显示到分钟；同一显示时间不因秒数或名称差异放行。
    const val ACCOUNTING_DUPLICATE_TIME_PRECISION_MS = 60_000
    const val ACCOUNTING_NAME_CONTAINMENT_MIN_LENGTH = 3
    const val ACCOUNTING_NAME_FUZZY_MIN_LENGTH = 5
    const val ACCOUNTING_NAME_SIMILARITY_PERCENT = 85
    const val ACCOUNTING_NAME_FUZZY_MAX_LENGTH = 128
    // 文件导入容量与解压/行数上限：限制内存占用，拒绝损坏或异常膨胀的表格。
    const val ACCOUNTING_IMPORT_MAX_BYTES = 16 * 1024 * 1024
    const val ACCOUNTING_IMPORT_EXPANDED_BYTES = 48 * 1024 * 1024
    const val ACCOUNTING_IMPORT_MAX_ROWS = 100_000
    const val ACCOUNTING_IMPORT_MAX_ZIP_ENTRIES = 2048

    // 收支建议首版门槛；仅对已记录账单作描述，不等同于预算或完整账户流水。
    const val ACCOUNTING_SUGGESTION_MIN_RECORDS = 3
    const val ACCOUNTING_SUGGESTION_SHARE_PERCENT = 40
    const val ACCOUNTING_SUGGESTION_CHANGE_PERCENT = 20
    const val ACCOUNTING_SUGGESTION_STABLE_PERCENT = 10
    const val ACCOUNTING_SUGGESTION_SMALL_MINOR = 2000
    const val ACCOUNTING_SUGGESTION_SMALL_COUNT = 5
    const val ACCOUNTING_SUGGESTION_LARGE_MINOR = 50000

    // 未成功发布时一分钟后重试；胶囊持续时间复用默认日程时长。
    const val QUICK_MEMO_REMINDER_RETRY_MS = 60_000L
    // 重复计算的安全搜索上限及随口记闹钟提前到达时的重排容差。
    const val REPEAT_OCCURRENCE_SEARCH_LIMIT = 100_000
    const val QUICK_MEMO_REMINDER_EARLY_TOLERANCE_MS = 60_000L
    // 自动日志按自然日滚动保留；限制每日文件体积，防止高频日志占满存储。
    const val LOG_RETENTION_DAYS = 3L
    const val LOG_MAX_BYTES = 4 * 1024 * 1024L

    val items: List<ConfigItem> = listOf(
        ConfigItem(ConfigDomain.APPEARANCE, ConfigKind.USER_SETTING, "course.module_enabled", "启用课表功能",
            "课表总开关：隐藏课程入口和展示，暂停课程提醒；保留数据和下滑偏好。", ConfigExposure.DEVELOPER_ONLY,
            ConfigControl.Toggle, { if (it.courseModuleEnabled) 1 else 0 },
            { s, v -> s.copy(courseModuleEnabled = v != 0) }, agentAccess = AgentConfigAccess.NONE),
        ConfigItem(ConfigDomain.APPEARANCE, ConfigKind.USER_SETTING, "home.agenda_only_scheduled", "日程视图显示模式",
            "默认仅日程；完整模式在已加载范围内补齐空日期，不影响其他日历视图。", ConfigExposure.DEVELOPER_ONLY,
            ConfigControl.IntOptions(listOf(ConfigControl.IntOptions.Option(0, "完整"), ConfigControl.IntOptions.Option(1, "仅日程"))),
            { if (it.homeAgendaOnlyScheduled) 1 else 0 }, { s, v -> s.copy(homeAgendaOnlyScheduled = v != 0) }),
        // courseRemindersResumeAtMillis 是总开关恢复时记录的运行状态，不是可调参数。

        ConfigItem(
            domain = ConfigDomain.DIAGNOSTICS,
            kind = ConfigKind.USER_SETTING,
            key = "developer.demo_mode.enabled",
            label = "界面演示模式",
            description = "使用本地只读示例数据预览主要页面，不写入数据库、不参与同步和通知。",
            exposure = ConfigExposure.DEVELOPER_ONLY,
            control = ConfigControl.Toggle,
            get = { if (it.developerDemoModeEnabled) 1 else 0 },
            set = { s, v -> s.copy(developerDemoModeEnabled = v != 0) },
            agentAccess = AgentConfigAccess.NONE,
        ),
        *listOf(
            Triple("navigation_expand_min_width_dp", ADAPTIVE_NAVIGATION_EXPAND_MIN_WIDTH_DP, "允许一级导航栏展开的最小窗口宽度。"),
            Triple("compact_pane_width_dp", ADAPTIVE_COMPACT_PANE_WIDTH_DP, "平板三栏中承载手机页面的固定中间栏宽度。"),
            Triple("content_padding_dp", ADAPTIVE_CONTENT_PADDING_DP, "平板右侧内容与左对齐标题的水平留白。"),
            Triple("form_max_width_dp", ADAPTIVE_FORM_MAX_WIDTH_DP, "平板设置表单最大阅读宽度。"),
            Triple("chart_pair_min_width_dp", ADAPTIVE_CHART_PAIR_MIN_WIDTH_DP, "右侧分析内容可用宽度达到此值时并排展示图表。"),
            Triple("weather_table_min_width_dp", ADAPTIVE_WEATHER_TABLE_MIN_WIDTH_DP, "平板预报列表展开降水、风力列所需的最小内容宽度。"),
        ).map { (key, value, description) -> ConfigItem(
            ConfigDomain.APPEARANCE,
            ConfigKind.POLICY,
            "adaptive.$key",
            "平板布局 $key",
            description,
            ConfigExposure.SYSTEM_INTERNAL,
            ConfigControl.IntInput(value, value),
            { value },
            { s, _ -> s },
            agentAccess = AgentConfigAccess.NONE,
        ) }.toTypedArray(),
        ConfigItem(ConfigDomain.APPEARANCE, ConfigKind.USER_SETTING, "home.agenda_reverse", "日程视图排列方向",
            "只反转日期组；同日仍按时间先后。", ConfigExposure.DEVELOPER_ONLY,
            ConfigControl.IntOptions(listOf(ConfigControl.IntOptions.Option(0, "向下排列"), ConfigControl.IntOptions.Option(1, "向上排列"))),
            { if (it.homeAgendaReverseOrder) 1 else 0 }, { s, v -> s.copy(homeAgendaReverseOrder = v != 0) }),
        ConfigItem(ConfigDomain.APPEARANCE, ConfigKind.POLICY, "home.agenda_page_days", "日程展开天数", "首次及每次追加的未来天数。",
            ConfigExposure.SYSTEM_INTERNAL, ConfigControl.IntInput(HOME_AGENDA_PAGE_DAYS, HOME_AGENDA_PAGE_DAYS),
            { HOME_AGENDA_PAGE_DAYS }, { s, _ -> s }, agentAccess = AgentConfigAccess.NONE),
        ConfigItem(ConfigDomain.APPEARANCE, ConfigKind.POLICY, "home.agenda_motion_ms", "日程视图过渡时长", "日期与日程视图使用一致的过渡时长。",
            ConfigExposure.SYSTEM_INTERNAL, ConfigControl.IntInput(HOME_AGENDA_MOTION_MS, HOME_AGENDA_MOTION_MS),
            { HOME_AGENDA_MOTION_MS }, { s, _ -> s }, agentAccess = AgentConfigAccess.NONE),
        ConfigItem(ConfigDomain.APPEARANCE, ConfigKind.POLICY, "home.agenda_pull_dp", "日程末端加载距离", "仅手动越过末端并松手时加载。",
            ConfigExposure.SYSTEM_INTERNAL, ConfigControl.IntInput(HOME_AGENDA_PULL_DP, HOME_AGENDA_PULL_DP),
            { HOME_AGENDA_PULL_DP }, { s, _ -> s }, agentAccess = AgentConfigAccess.NONE),

        ConfigItem(ConfigDomain.RECOGNITION, ConfigKind.USER_SETTING, "accounting.messages_enabled", "通知与短信记账", "通过本地正则自动捕获通知和短信中的账单", ConfigExposure.USER_EDITABLE, ConfigControl.Toggle, { if (it.accountingMessagesEnabled) 1 else 0 }, { s, v -> s.copy(accountingMessagesEnabled = v != 0) }, agentAccess = AgentConfigAccess.READ_ONLY),
        *listOf("max_text" to ACCOUNTING_MESSAGE_MAX_TEXT, "max_rules" to ACCOUNTING_MESSAGE_MAX_RULES, "receipts" to ACCOUNTING_MESSAGE_RECEIPTS).map { (key, value) ->
            ConfigItem(ConfigDomain.RECOGNITION, ConfigKind.POLICY, "accounting.message_$key", "财务消息 $key", "限制本地正则输入、规则数量和重复投递凭据。", ConfigExposure.SYSTEM_INTERNAL, ConfigControl.IntInput(value, value), { value }, { s, _ -> s }, agentAccess = AgentConfigAccess.NONE)
        }.toTypedArray(),
        *listOf(
            Triple("min_records", ACCOUNTING_SUGGESTION_MIN_RECORDS, "频繁消费、同人转账及同期比较至少三笔"),
            Triple("share_percent", ACCOUNTING_SUGGESTION_SHARE_PERCENT, "购物或餐饮达到本期已记录支出的百分比"),
            Triple("change_percent", ACCOUNTING_SUGGESTION_CHANGE_PERCENT, "同期增减达到此百分比才提醒"),
            Triple("stable_percent", ACCOUNTING_SUGGESTION_STABLE_PERCENT, "同期变化在此百分比以内视为平稳"),
            Triple("small_minor", ACCOUNTING_SUGGESTION_SMALL_MINOR, "小额消费单笔上限，单位分"),
            Triple("small_count", ACCOUNTING_SUGGESTION_SMALL_COUNT, "小额消费累计笔数门槛"),
            Triple("large_minor", ACCOUNTING_SUGGESTION_LARGE_MINOR, "大额支出单笔门槛，单位分"),
        ).map { (key, value, note) -> ConfigItem(ConfigDomain.RECOGNITION, ConfigKind.POLICY,
            "accounting.suggestion_$key", "收支建议 $key", note, ConfigExposure.SYSTEM_INTERNAL,
            ConfigControl.IntInput(value, value), { value }, { s, _ -> s }, agentAccess = AgentConfigAccess.NONE)
        }.toTypedArray(),
        *listOf(
            "duration_ms" to PAYMENT_DIAGNOSTIC_DURATION_MS,
            "health_interval_ms" to PAYMENT_DIAGNOSTIC_HEALTH_INTERVAL_MS,
            "tree_interval_ms" to PAYMENT_DIAGNOSTIC_TREE_INTERVAL_MS,
            "delay_first_ms" to PAYMENT_DIAGNOSTIC_DELAY_FIRST_MS,
            "delay_second_ms" to PAYMENT_DIAGNOSTIC_DELAY_SECOND_MS,
            "delay_last_ms" to PAYMENT_DIAGNOSTIC_DELAY_LAST_MS,
            "screenshot_interval_ms" to PAYMENT_DIAGNOSTIC_SCREENSHOT_INTERVAL_MS,
            "max_screenshots" to PAYMENT_DIAGNOSTIC_MAX_SCREENSHOTS,
            "max_image_bytes" to PAYMENT_DIAGNOSTIC_MAX_IMAGE_BYTES,
            "max_log_bytes" to PAYMENT_DIAGNOSTIC_MAX_LOG_BYTES,
            "max_events" to PAYMENT_DIAGNOSTIC_MAX_EVENTS,
            "max_trees" to PAYMENT_DIAGNOSTIC_MAX_TREES,
            "max_nodes" to PAYMENT_DIAGNOSTIC_MAX_NODES,
            "max_windows" to PAYMENT_DIAGNOSTIC_MAX_WINDOWS,
            "field_chars" to PAYMENT_DIAGNOSTIC_FIELD_CHARS,
            "queue_size" to PAYMENT_DIAGNOSTIC_QUEUE_SIZE,
            "retain_sessions" to PAYMENT_DIAGNOSTIC_RETAIN_SESSIONS,
        ).map { (key, value) -> ConfigItem(ConfigDomain.RECOGNITION, ConfigKind.POLICY, "diagnostics.payment_$key", "支付诊断 $key", "限制一次性支付诊断的时长、读取量和本地文件体积。", ConfigExposure.SYSTEM_INTERNAL, ConfigControl.IntInput(value, value), { value }, { s, _ -> s }, agentAccess = AgentConfigAccess.NONE) }.toTypedArray(),
        ConfigItem(ConfigDomain.RECOGNITION, ConfigKind.USER_SETTING, "accounting.automatic_enabled", "自动记账（Beta）", "自动捕获支付信息", ConfigExposure.USER_EDITABLE, ConfigControl.Toggle, { if (it.automaticAccountingEnabled) 1 else 0 }, { s, v -> s.copy(automaticAccountingEnabled = v != 0) }, agentAccess = AgentConfigAccess.READ_ONLY),
        *listOf(
            Triple("notification_wait_ms", "支付通知优先等待", AUTO_ACCOUNTING_NOTIFICATION_WAIT_MS),
            Triple("notification_match_ms", "支付通知关联时间窗", AUTO_ACCOUNTING_NOTIFICATION_MATCH_MS),
            Triple("debounce_ms", "支付页面稳定等待", AUTO_ACCOUNTING_DEBOUNCE_MS),
            Triple("red_packet_capture_delay_ms", "红包确认页截图等待", AUTO_ACCOUNTING_RED_PACKET_CAPTURE_DELAY_MS),
            Triple("wechat_session_ms", "微信支付上下文有效期", AUTO_ACCOUNTING_WECHAT_SESSION_MS),
            Triple("success_event_ms", "支付成功事件截图有效期", AUTO_ACCOUNTING_SUCCESS_EVENT_MS),
            Triple("detail_signal_ms", "账单详情信号截图有效期", AUTO_ACCOUNTING_DETAIL_SIGNAL_MS),
            Triple("min_interval_ms", "自动截图最短间隔", AUTO_ACCOUNTING_MIN_INTERVAL_MS),
            Triple("repeat_ms", "自动采集重复抑制", AUTO_ACCOUNTING_REPEAT_MS),
            Triple("max_nodes", "支付页面节点上限", AUTO_ACCOUNTING_MAX_NODES),
            Triple("max_text", "支付页面文本上限", AUTO_ACCOUNTING_MAX_TEXT),
            Triple("max_payload", "Hook 消息长度上限", AUTO_ACCOUNTING_MAX_PAYLOAD),
            Triple("message_max_depth", "支付消息结构深度上限", AUTO_ACCOUNTING_MESSAGE_MAX_DEPTH),
            Triple("queue_size", "Hook 转发队列上限", AUTO_ACCOUNTING_QUEUE_SIZE),
            Triple("provider_timeout_ms", "支付消息处理超时", AUTO_ACCOUNTING_PROVIDER_TIMEOUT_MS),
            Triple("screenshot_timeout_ms", "截图回调等待超时", RECOGNITION_SCREENSHOT_TIMEOUT_MS),
            Triple("max_age_ms", "实时支付消息时效", AUTO_ACCOUNTING_MAX_AGE_MS),
            Triple("diagnostic_interval_ms", "自动记账诊断日志最短间隔", AUTO_ACCOUNTING_DIAGNOSTIC_INTERVAL_MS),
        ).map { (key, label, value) -> ConfigItem(ConfigDomain.RECOGNITION, ConfigKind.POLICY, "accounting.auto_$key", label, "限制自动采集资源和重复请求，避免历史消息回放入账。", ConfigExposure.SYSTEM_INTERNAL, ConfigControl.IntInput(value, value), { value }, { s, _ -> s }, agentAccess = AgentConfigAccess.NONE) }.toTypedArray(),
        ConfigItem(ConfigDomain.RECOGNITION, ConfigKind.POLICY, "accounting.duplicate_time_precision_ms", "账单相同时间精度", "同金额、同收支方向且同一分钟时独立判为疑似重复，不要求名称相似。", ConfigExposure.SYSTEM_INTERNAL, ConfigControl.IntInput(ACCOUNTING_DUPLICATE_TIME_PRECISION_MS, ACCOUNTING_DUPLICATE_TIME_PRECISION_MS), { ACCOUNTING_DUPLICATE_TIME_PRECISION_MS }, { s, _ -> s }, agentAccess = AgentConfigAccess.NONE),
        ConfigItem(ConfigDomain.RECOGNITION, ConfigKind.POLICY, "accounting.duplicate_window_ms", "账单疑似重复时间范围", "同金额账单按交易时间前后两分钟筛选。", ConfigExposure.SYSTEM_INTERNAL, ConfigControl.IntInput(ACCOUNTING_DUPLICATE_WINDOW_MS, ACCOUNTING_DUPLICATE_WINDOW_MS), { ACCOUNTING_DUPLICATE_WINDOW_MS }, { s, _ -> s }, agentAccess = AgentConfigAccess.NONE),
        ConfigItem(ConfigDomain.RECOGNITION, ConfigKind.POLICY, "accounting.name_containment_min_length", "账单简称最小长度", "限制短名称包含匹配，避免单字产生误判。", ConfigExposure.SYSTEM_INTERNAL, ConfigControl.IntInput(ACCOUNTING_NAME_CONTAINMENT_MIN_LENGTH, ACCOUNTING_NAME_CONTAINMENT_MIN_LENGTH), { ACCOUNTING_NAME_CONTAINMENT_MIN_LENGTH }, { s, _ -> s }, agentAccess = AgentConfigAccess.NONE),
        ConfigItem(ConfigDomain.RECOGNITION, ConfigKind.POLICY, "accounting.name_fuzzy_min_length", "账单近似名称最小长度", "较短名称不进行编辑距离模糊匹配。", ConfigExposure.SYSTEM_INTERNAL, ConfigControl.IntInput(ACCOUNTING_NAME_FUZZY_MIN_LENGTH, ACCOUNTING_NAME_FUZZY_MIN_LENGTH), { ACCOUNTING_NAME_FUZZY_MIN_LENGTH }, { s, _ -> s }, agentAccess = AgentConfigAccess.NONE),
        ConfigItem(ConfigDomain.RECOGNITION, ConfigKind.POLICY, "accounting.name_similarity_percent", "账单名称相似度", "达到门槛仅判为疑似重复并待核对。", ConfigExposure.SYSTEM_INTERNAL, ConfigControl.IntInput(ACCOUNTING_NAME_SIMILARITY_PERCENT, ACCOUNTING_NAME_SIMILARITY_PERCENT), { ACCOUNTING_NAME_SIMILARITY_PERCENT }, { s, _ -> s }, agentAccess = AgentConfigAccess.NONE),
        ConfigItem(ConfigDomain.RECOGNITION, ConfigKind.POLICY, "accounting.name_fuzzy_max_length", "账单模糊匹配长度上限", "限制异常长名称的编辑距离计算开销。", ConfigExposure.SYSTEM_INTERNAL, ConfigControl.IntInput(ACCOUNTING_NAME_FUZZY_MAX_LENGTH, ACCOUNTING_NAME_FUZZY_MAX_LENGTH), { ACCOUNTING_NAME_FUZZY_MAX_LENGTH }, { s, _ -> s }, agentAccess = AgentConfigAccess.NONE),
        ConfigItem(ConfigDomain.RECOGNITION, ConfigKind.POLICY, "accounting.import_max_zip_entries", "账单压缩条目上限", "限制账单导入资源占用。", ConfigExposure.SYSTEM_INTERNAL, ConfigControl.IntInput(ACCOUNTING_IMPORT_MAX_ZIP_ENTRIES, ACCOUNTING_IMPORT_MAX_ZIP_ENTRIES), { ACCOUNTING_IMPORT_MAX_ZIP_ENTRIES }, { s, _ -> s }, agentAccess = AgentConfigAccess.NONE),
        ConfigItem(ConfigDomain.RECOGNITION, ConfigKind.POLICY, "accounting.import_max_rows", "账单行数上限", "限制账单导入资源占用。", ConfigExposure.SYSTEM_INTERNAL, ConfigControl.IntInput(ACCOUNTING_IMPORT_MAX_ROWS, ACCOUNTING_IMPORT_MAX_ROWS), { ACCOUNTING_IMPORT_MAX_ROWS }, { s, _ -> s }, agentAccess = AgentConfigAccess.NONE),
        ConfigItem(ConfigDomain.RECOGNITION, ConfigKind.POLICY, "accounting.import_expanded_bytes", "账单解压容量", "限制账单导入资源占用。", ConfigExposure.SYSTEM_INTERNAL, ConfigControl.IntInput(ACCOUNTING_IMPORT_EXPANDED_BYTES, ACCOUNTING_IMPORT_EXPANDED_BYTES), { ACCOUNTING_IMPORT_EXPANDED_BYTES }, { s, _ -> s }, agentAccess = AgentConfigAccess.NONE),
        ConfigItem(ConfigDomain.RECOGNITION, ConfigKind.POLICY, "accounting.import_max_bytes", "账单文件容量", "限制账单导入资源占用。", ConfigExposure.SYSTEM_INTERNAL, ConfigControl.IntInput(ACCOUNTING_IMPORT_MAX_BYTES, ACCOUNTING_IMPORT_MAX_BYTES), { ACCOUNTING_IMPORT_MAX_BYTES }, { s, _ -> s }, agentAccess = AgentConfigAccess.NONE),
        ConfigItem(ConfigDomain.NOTIFICATION, ConfigKind.POLICY, "notification.quick_memo_retry_ms", "随口记提醒失败重试间隔", "发送失败保留记录，稍后重新尝试发布。", ConfigExposure.SYSTEM_INTERNAL, ConfigControl.IntInput(60000, 60000), { QUICK_MEMO_REMINDER_RETRY_MS.toInt() }, { s, _ -> s }, agentAccess = AgentConfigAccess.NONE),
        ConfigItem(
            domain = ConfigDomain.NOTIFICATION,
            kind = ConfigKind.POLICY,
            key = "notification.repeat_occurrence_search_limit",
            label = "重复实例搜索上限",
            description = "限制计算下一次重复实例的推进次数，避免异常规则无限循环。",
            exposure = ConfigExposure.SYSTEM_INTERNAL,
            control = ConfigControl.IntInput(REPEAT_OCCURRENCE_SEARCH_LIMIT, REPEAT_OCCURRENCE_SEARCH_LIMIT),
            get = { REPEAT_OCCURRENCE_SEARCH_LIMIT },
            set = { s, _ -> s },
            agentAccess = AgentConfigAccess.NONE,
        ),
        ConfigItem(
            domain = ConfigDomain.NOTIFICATION,
            kind = ConfigKind.POLICY,
            key = "notification.quick_memo_early_tolerance_ms",
            label = "随口记提醒提前容差",
            description = "触发比目标时间提前超过此容差时重新排程，避免旧闹钟误发。",
            exposure = ConfigExposure.SYSTEM_INTERNAL,
            control = ConfigControl.IntInput(QUICK_MEMO_REMINDER_EARLY_TOLERANCE_MS.toInt(), QUICK_MEMO_REMINDER_EARLY_TOLERANCE_MS.toInt(), unitLabel = "毫秒"),
            get = { QUICK_MEMO_REMINDER_EARLY_TOLERANCE_MS.toInt() },
            set = { s, _ -> s },
            agentAccess = AgentConfigAccess.NONE,
        ),
        ConfigItem(
            domain = ConfigDomain.DIAGNOSTICS,
            kind = ConfigKind.POLICY,
            key = "diagnostics.retention_days",
            label = "日志保留天数",
            description = "保留今天及前两天，启动、写入和导出时清理更早的自动日志。",
            exposure = ConfigExposure.SYSTEM_INTERNAL,
            control = ConfigControl.IntInput(3, 3, unitLabel = "天"),
            get = { LOG_RETENTION_DAYS.toInt() },
            set = { s, _ -> s },
            agentAccess = AgentConfigAccess.NONE,
        ),
        ConfigItem(
            domain = ConfigDomain.DIAGNOSTICS,
            kind = ConfigKind.POLICY,
            key = "diagnostics.daily_max_bytes",
            label = "单日日志容量上限",
            description = "每天最多 4 MB，达到上限后停止当天追加，保留已有记录。",
            exposure = ConfigExposure.SYSTEM_INTERNAL,
            control = ConfigControl.IntInput(LOG_MAX_BYTES.toInt(), LOG_MAX_BYTES.toInt(), unitLabel = "字节"),
            get = { LOG_MAX_BYTES.toInt() },
            set = { s, _ -> s },
            agentAccess = AgentConfigAccess.NONE,
        ),
        ConfigItem(
            domain = ConfigDomain.DIAGNOSTICS,
            kind = ConfigKind.USER_SETTING,
            key = "diagnostics.auto_record",
            label = "自动记录日志",
            description = "将运行、异常和卡顿日志写入私有目录，按天保留最近三天；关闭后仍可导出已有记录。",
            exposure = ConfigExposure.DEVELOPER_ONLY,
            control = ConfigControl.Toggle,
            get = { if (it.autoRecordLogs) 1 else 0 },
            set = { s, v -> s.copy(autoRecordLogs = v != 0) },
            agentAccess = AgentConfigAccess.READ_ONLY,
        ),
        ConfigItem(
            domain = ConfigDomain.SYNC,
            kind = ConfigKind.USER_SETTING,
            key = "sync.webdav.enabled",
            label = "多设备同步",
            description = "同步日程、随口记及其附件。",
            exposure = ConfigExposure.USER_EDITABLE,
            control = ConfigControl.Toggle,
            get = { if (it.webDavSyncEnabled) 1 else 0 },
            set = { s, v -> s.copy(webDavSyncEnabled = v != 0) },
        ),
        ConfigItem(
            domain = ConfigDomain.SYNC,
            kind = ConfigKind.USER_SETTING,
            key = "sync.webdav.wifi_only",
            label = "仅在 Wi-Fi 下同步",
            description = "移动网络下暂停自动同步。",
            exposure = ConfigExposure.USER_EDITABLE,
            control = ConfigControl.Toggle,
            get = { if (it.webDavWifiOnly) 1 else 0 },
            set = { s, v -> s.copy(webDavWifiOnly = v != 0) },
        ),
        ConfigItem(
            domain = ConfigDomain.SYNC,
            kind = ConfigKind.POLICY,
            key = "sync.webdav.foreground_interval_seconds",
            label = "前台同步间隔",
            description = "应用处于前台时轮询其他设备状态的间隔。",
            exposure = ConfigExposure.DEVELOPER_ONLY,
            control = ConfigControl.IntInput(min = 1, max = 300, unitLabel = " 秒"),
            get = { it.webDavForegroundSyncIntervalSeconds },
            set = { s, v -> s.copy(webDavForegroundSyncIntervalSeconds = v.coerceIn(1, 300)) },
        ),
        ConfigItem(
            domain = ConfigDomain.SYNC,
            kind = ConfigKind.POLICY,
            key = "sync.webdav.remote_root_override",
            label = "WebDAV 测试根目录覆盖",
            description = "仅由开发者页专用输入框维护，系统始终追加 sync/v2。",
            exposure = ConfigExposure.SYSTEM_INTERNAL,
            control = ConfigControl.IntInput(min = 0, max = 0),
            get = { 0 },
            set = { s, _ -> s },
            getText = { it.webDavRemotePathOverride },
            setText = { s, v -> s.copy(webDavRemotePathOverride = v) },
        ),
        ConfigItem(
            domain = ConfigDomain.SYNC,
            kind = ConfigKind.USER_SETTING,
            key = "agent.api.enabled",
            label = "允许 Agent 访问",
            description = "开启后，官方 Agent 可以访问 WillDo 提供的日程、课程、随口记、设置和诊断能力。",
            exposure = ConfigExposure.SYSTEM_INTERNAL,
            control = ConfigControl.Toggle,
            get = { if (it.agentApiEnabled) 1 else 0 },
            set = { s, v -> s.copy(agentApiEnabled = v != 0) },
            agentAccess = AgentConfigAccess.NONE,
        ),
        ConfigItem(
            domain = ConfigDomain.SYNC,
            kind = ConfigKind.USER_SETTING,
            key = "agent.third_party_access.enabled",
            label = "允许第三方 Agent 访问",
            description = "允许第三方 Agent 通过公开接口访问、操作 WillDo 数据；不包含附件和文件。",
            exposure = ConfigExposure.SYSTEM_INTERNAL,
            control = ConfigControl.Toggle,
            get = { if (it.agentThirdPartyAccessEnabled) 1 else 0 },
            set = { s, v -> s.copy(agentThirdPartyAccessEnabled = v != 0) },
            agentAccess = AgentConfigAccess.NONE,
        ),
        ConfigItem(
            domain = ConfigDomain.SYNC,
            kind = ConfigKind.USER_SETTING,
            key = "agent.connection_management.enabled",
            label = "允许 Agent 管理连接配置",
            description = "允许 Agent 管理模型、天气和 WebDAV 连接；已有密钥和密码不会以明文返回。",
            exposure = ConfigExposure.SYSTEM_INTERNAL,
            control = ConfigControl.Toggle,
            get = { if (it.agentConnectionManagementEnabled) 1 else 0 },
            set = { s, v -> s.copy(agentConnectionManagementEnabled = v != 0) },
            agentAccess = AgentConfigAccess.NONE,
        ),
        ConfigItem(
            domain = ConfigDomain.SYNC,
            kind = ConfigKind.POLICY,
            key = "agent.database_operations.enabled",
            label = "允许 Agent 操作数据库（高风险）",
            description = "允许 Agent 查询、修改和删除数据库中的业务数据；仅用于开发和数据清理。",
            exposure = ConfigExposure.DEVELOPER_ONLY,
            control = ConfigControl.Toggle,
            get = { if (it.agentDatabaseOperationsEnabled) 1 else 0 },
            set = { s, v -> s.copy(agentDatabaseOperationsEnabled = v != 0) },
            agentAccess = AgentConfigAccess.NONE,
        ),
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
            label = "MIUI 模糊材质",
            description = "导入背景图片后，在标准卡片材质和 MIUI 模糊材质之间切换；模糊材质不受卡片透明度影响。",
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
            description = "控制背景层图片模糊",
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
            description = "标准卡片材质下，控制卡片和底栏表面的透明度；MIUI 模糊材质下不参与渲染。",
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
            domain = ConfigDomain.RECOGNITION,
            kind = ConfigKind.POLICY,
            key = "recognition.schedule_ingest.dedup_enabled",
            label = "日程入库去重",
            description = "开启后相同日程只入库一次（短信取件码与识别结果统一去重）；关闭后不检查已有日程。",
            exposure = ConfigExposure.DEVELOPER_ONLY,
            control = ConfigControl.Toggle,
            get = { if (it.scheduleIngestDedupEnabled) 1 else 0 },
            set = { s, v -> s.copy(scheduleIngestDedupEnabled = v != 0) },
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
            description = "AI 识别结果类通知自动消失前停留的时长。",
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
            description = "AI 识别进行中的进度胶囊在自动消失前的最长停留时长（仅灵动形态，无普通对应）。",
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
            label = "随口记",
            description = "随口记功能总开关",
            exposure = ConfigExposure.USER_EDITABLE,
            control = ConfigControl.Toggle,
            get = { if (it.voiceInputEnabled) 1 else 0 },
            set = { s, v -> s.copy(voiceInputEnabled = v != 0) },
        ),
        ConfigItem(
            domain = ConfigDomain.VOICE,
            kind = ConfigKind.USER_SETTING,
            key = "voice.floating_long_press.enabled",
            label = "悬浮窗长按随口记",
            description = "呼出悬浮窗后，再次长按音量+开始随口记录音",
            exposure = ConfigExposure.USER_EDITABLE,
            control = ConfigControl.Toggle,
            get = { if (it.floatingVoiceLongPressEnabled) 1 else 0 },
            set = { s, v -> s.copy(floatingVoiceLongPressEnabled = v != 0) },
        ),
        ConfigItem(
            domain = ConfigDomain.VOICE,
            kind = ConfigKind.USER_SETTING,
            key = "voice.floating_text_quick_memo.auto_pin_enabled",
            label = "文本随口记同步挂起",
            description = "随口记文本保存后，同步挂起到实况通知",
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
            description = "随口记语音转写后，同步挂起到实况通知",
            exposure = ConfigExposure.USER_EDITABLE,
            control = ConfigControl.Toggle,
            get = { if (it.voiceQuickMemoAutoPinEnabled) 1 else 0 },
            set = { s, v -> s.copy(voiceQuickMemoAutoPinEnabled = v != 0) },
        ),
        ConfigItem(
            domain = ConfigDomain.VOICE,
            kind = ConfigKind.USER_SETTING,
            key = "voice.quick_memo.recording_display_mode",
            label = "随口记录音展示",
            description = "控制所有随口记录音入口在录音时使用实况通知还是悬浮窗。",
            exposure = ConfigExposure.USER_EDITABLE,
            control = ConfigControl.IntOptions(
                listOf(
                    ConfigControl.IntOptions.Option(QuickMemoRecordingDisplayMode.LIVE_CAPSULE, "实况通知"),
                    ConfigControl.IntOptions.Option(QuickMemoRecordingDisplayMode.FLOATING_WINDOW, "悬浮窗"),
                )
            ),
            get = { it.quickMemoRecordingDisplayMode },
            set = { s, v -> s.copy(quickMemoRecordingDisplayMode = QuickMemoRecordingDisplayMode.normalize(v)) },
        ),
        ConfigItem(
            domain = ConfigDomain.VOICE,
            kind = ConfigKind.POLICY,
            key = "developer.quick_memo.pinned_fixed_title_enabled",
            label = "随口记挂起固定标题",
            description = "开发者专用：开启后，随口记挂起胶囊标题固定为“随口记”，内容仍显示正文；关闭后保持标题和内容都使用正文。",
            exposure = ConfigExposure.DEVELOPER_ONLY,
            control = ConfigControl.Toggle,
            get = { if (it.quickMemoPinnedFixedTitleEnabled) 1 else 0 },
            set = { s, v -> s.copy(quickMemoPinnedFixedTitleEnabled = v != 0) },
        ),
        ConfigItem(
            domain = ConfigDomain.VOICE,
            kind = ConfigKind.USER_SETTING,
            key = "voice.floating.entry_style",
            label = "悬浮入口样式",
            description = "旧兼容字段；当前侧边栏和悬浮球使用独立开关。",
            exposure = ConfigExposure.USER_EDITABLE,
            control = ConfigControl.IntOptions(
                listOf(
                    ConfigControl.IntOptions.Option(FloatingEntryStyle.EDGE_BAR, "侧边栏"),
                    ConfigControl.IntOptions.Option(FloatingEntryStyle.FLOATING_BALL, "悬浮球"),
                )
            ),
            get = { it.floatingEntryStyle },
            set = { s, v -> s.copy(floatingEntryStyle = FloatingEntryStyle.normalize(v)) },
        ),
        ConfigItem(
            domain = ConfigDomain.VOICE,
            kind = ConfigKind.USER_SETTING,
            key = "voice.floating_ball.enabled",
            label = "悬浮球入口",
            description = "在屏幕中显示悬浮球",
            exposure = ConfigExposure.USER_EDITABLE,
            control = ConfigControl.Toggle,
            get = { if (it.floatingBallEnabled) 1 else 0 },
            set = { s, v -> s.copy(floatingBallEnabled = v != 0) },
        ),
        ConfigItem(
            domain = ConfigDomain.VOICE,
            kind = ConfigKind.USER_SETTING,
            key = "voice.floating_ball.single_tap_action",
            label = "悬浮球单击动作",
            description = "配置悬浮球单击触发的操作。",
            exposure = ConfigExposure.USER_EDITABLE,
            control = floatingGestureActionControl(),
            get = { it.floatingBallSingleTapAction },
            set = { s, v -> s.copy(floatingBallSingleTapAction = FloatingBallGestureAction.normalize(v)) },
        ),
        ConfigItem(
            domain = ConfigDomain.VOICE,
            kind = ConfigKind.USER_SETTING,
            key = "voice.floating_ball.double_tap_action",
            label = "悬浮球双击动作",
            description = "配置悬浮球双击触发的操作。",
            exposure = ConfigExposure.USER_EDITABLE,
            control = floatingGestureActionControl(),
            get = { it.floatingBallDoubleTapAction },
            set = { s, v -> s.copy(floatingBallDoubleTapAction = FloatingBallGestureAction.normalize(v)) },
        ),
        ConfigItem(
            domain = ConfigDomain.VOICE,
            kind = ConfigKind.USER_SETTING,
            key = "voice.floating_ball.long_press_action",
            label = "悬浮球长按动作",
            description = "配置悬浮球长按触发的操作。",
            exposure = ConfigExposure.USER_EDITABLE,
            control = floatingGestureActionControl(),
            get = { it.floatingBallLongPressAction },
            set = { s, v -> s.copy(floatingBallLongPressAction = FloatingBallGestureAction.normalize(v)) },
        ),
        ConfigItem(
            domain = ConfigDomain.VOICE,
            kind = ConfigKind.USER_SETTING,
            key = "voice.edge_bar.single_tap_action",
            label = "侧边栏单击动作",
            description = "配置侧边栏单击触发的操作；滑动呼出日程仍保留。",
            exposure = ConfigExposure.USER_EDITABLE,
            control = floatingGestureActionControl(),
            get = { it.edgeBarSingleTapAction },
            set = { s, v -> s.copy(edgeBarSingleTapAction = FloatingBallGestureAction.normalize(v)) },
        ),
        ConfigItem(
            domain = ConfigDomain.VOICE,
            kind = ConfigKind.USER_SETTING,
            key = "voice.edge_bar.double_tap_action",
            label = "侧边栏双击动作",
            description = "配置侧边栏双击触发的操作；滑动呼出日程仍保留。",
            exposure = ConfigExposure.USER_EDITABLE,
            control = floatingGestureActionControl(),
            get = { it.edgeBarDoubleTapAction },
            set = { s, v -> s.copy(edgeBarDoubleTapAction = FloatingBallGestureAction.normalize(v)) },
        ),
        ConfigItem(
            domain = ConfigDomain.VOICE,
            kind = ConfigKind.USER_SETTING,
            key = "voice.edge_bar.long_press_action",
            label = "侧边栏长按动作",
            description = "配置侧边栏长按触发的操作；滑动呼出日程仍保留。",
            exposure = ConfigExposure.USER_EDITABLE,
            control = floatingGestureActionControl(),
            get = { it.edgeBarLongPressAction },
            set = { s, v -> s.copy(edgeBarLongPressAction = FloatingBallGestureAction.normalize(v)) },
        ),
        ConfigItem(
            domain = ConfigDomain.VOICE,
            kind = ConfigKind.USER_SETTING,
            key = "voice.floating_drag_text.include_title",
            label = "拖拽文本包含标题",
            description = "从悬浮窗拖拽日程到输入框时，输出第一行标题。",
            exposure = ConfigExposure.DEVELOPER_ONLY,
            control = ConfigControl.Toggle,
            get = { if (it.floatingDragTextIncludeTitle) 1 else 0 },
            set = { s, v -> s.copy(floatingDragTextIncludeTitle = v != 0) },
        ),
        ConfigItem(
            domain = ConfigDomain.VOICE,
            kind = ConfigKind.USER_SETTING,
            key = "voice.floating_drag_text.include_time",
            label = "拖拽文本包含时间",
            description = "从悬浮窗拖拽日程到输入框时，附加开始和结束时间。",
            exposure = ConfigExposure.DEVELOPER_ONLY,
            control = ConfigControl.Toggle,
            get = { if (it.floatingDragTextIncludeTime) 1 else 0 },
            set = { s, v -> s.copy(floatingDragTextIncludeTime = v != 0) },
        ),
        ConfigItem(
            domain = ConfigDomain.VOICE,
            kind = ConfigKind.USER_SETTING,
            key = "voice.floating_drag_text.include_location",
            label = "拖拽文本包含地点",
            description = "从悬浮窗拖拽日程到输入框时，附加地点。",
            exposure = ConfigExposure.DEVELOPER_ONLY,
            control = ConfigControl.Toggle,
            get = { if (it.floatingDragTextIncludeLocation) 1 else 0 },
            set = { s, v -> s.copy(floatingDragTextIncludeLocation = v != 0) },
        ),
        ConfigItem(
            domain = ConfigDomain.VOICE,
            kind = ConfigKind.USER_SETTING,
            key = "voice.floating_drag_text.include_description",
            label = "拖拽文本包含详情",
            description = "从悬浮窗拖拽日程到输入框时，附加备注或结构化详情。",
            exposure = ConfigExposure.DEVELOPER_ONLY,
            control = ConfigControl.Toggle,
            get = { if (it.floatingDragTextIncludeDescription) 1 else 0 },
            set = { s, v -> s.copy(floatingDragTextIncludeDescription = v != 0) },
        ),
        ConfigItem(
            domain = ConfigDomain.VOICE,
            kind = ConfigKind.POLICY,
            key = "voice.floating_drag.hot_zone_percent",
            label = "拖拽热区范围",
            description = "控制悬浮卡片拖出和拖回取消使用的呼出侧热区宽度百分比。",
            exposure = ConfigExposure.DEVELOPER_ONLY,
            control = ConfigControl.IntInput(
                min = MySettings.FLOATING_DRAG_HOT_ZONE_MIN_PERCENT,
                max = MySettings.FLOATING_DRAG_HOT_ZONE_MAX_PERCENT,
                step = 5,
                unitLabel = " %"
            ),
            get = { it.floatingDragHotZonePercent },
            set = { s, v -> s.copy(floatingDragHotZonePercent = MySettings.normalizeFloatingDragHotZonePercent(v)) },
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
            description = "根据未来 x 小时预报推断风险并提醒",
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
            label = "实况通知",
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

    private fun floatingGestureActionControl(): ConfigControl.IntOptions {
        return ConfigControl.IntOptions(
            FloatingBallGestureAction.ALL.map { action ->
                ConfigControl.IntOptions.Option(action, FloatingBallGestureAction.label(action))
            }
        )
    }
}
