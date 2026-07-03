package com.antgskds.calendarassistant.shared.management.catalog

/**
 * 辅助工具台账（代码内，不暴露给 App 用户）。
 *
 * ## 这是什么
 * 项目所有「Helper / Mapper / Support 类纯工具」的总地图——它们不持有业务状态、不发起流程，
 * 只做无副作用的转换/拼装/映射（如通知文案裁剪、图标映射、颜色映射、展示字段拼接）。
 * 打开本文件即可一眼看全：项目里有哪些可复用工具、各干嘛的、归哪条链路。
 *
 * ## 怎么登记（agent 新增 Helper/Mapper/Support 前必须做）
 * 新建一个工具类前，**先在下面 [helpers] 登记一条 [HelperEntry]**，写清它做什么转换、归哪条链路，
 * 再开始编码。先看本台账有没有已存在的同类工具可复用，避免到处重复造 Mapper/Helper。
 *
 * ## 边界
 * - 只登记元信息。Helper 本身必须是无副作用的纯转换：不读写数据库、不发网络、不构建通知、不持久状态。
 * - 有状态/有副作用/串流程的，属于 Pipeline 或 Center，登记到 [PipelineCatalog]，不在这里。
 */
object HelperCatalog {

    /** 工具所属的主链路（对应架构 入口→识别→入库→同步→通知，以及横切支撑）。 */
    enum class Chain {
        RECOGNITION,   // 识别
        INGEST,        // 入库
        SYNC,          // 同步
        NOTIFICATION,  // 通知
        SCHEDULE,      // 日程主体
        WEATHER,       // 天气
        SUPPORT,       // 横切支撑
    }

    /**
     * 一个工具类的登记项。
     * @param name 工具名。
     * @param chain 所属主链路。
     * @param entry 工具类路径（让维护者能定位代码）。
     * @param note 一句话说明它做什么转换。
     */
    data class HelperEntry(
        val name: String,
        val chain: Chain,
        val entry: String,
        val note: String,
    )

    val helpers: List<HelperEntry> = listOf(
        // —— 识别 ——
        HelperEntry("AI 失败映射", Chain.RECOGNITION, "core/ai/AiFailureMapper", "AI 调用失败原因 → 内部失败类型"),
        HelperEntry("识别失败文案映射", Chain.RECOGNITION, "core/ai/RecognitionFailureMessageMapper", "识别失败类型 → 用户可读提示"),
        HelperEntry("正则日程解析", Chain.RECOGNITION, "core/rule/RegexScheduleRecognizer", "可配置正则规则 → 日程草稿"),
        HelperEntry("正则规则偏好存储", Chain.RECOGNITION, "core/rule/RegexScheduleRulePrefs", "开发者可编辑正则规则 JSON 读写"),

        // —— 入库 / 日程 ——
        HelperEntry("课程事件映射", Chain.INGEST, "core/course/CourseEventMapper", "课程表数据 → Event"),
        HelperEntry("日程展示助手", Chain.SCHEDULE, "core/center/ScheduleDisplayHelper", "日程展示字段拼装"),

        // —— 通知 ——
        HelperEntry("日程实况展示支持", Chain.NOTIFICATION, "shared/management/resource/notification/display/live/template/ScheduleLiveDisplaySupport", "日程胶囊展示字段裁剪/拼接"),
        HelperEntry("天气实况展示支持", Chain.NOTIFICATION, "shared/management/resource/notification/display/live/template/WeatherLiveDisplaySupport", "天气胶囊展示字段裁剪/拼接"),

        // —— 天气 ——
        HelperEntry("天气预警图标映射", Chain.WEATHER, "feature/weather/domain/WeatherAlertIconMapper", "预警类型 → 图标"),
        HelperEntry("天气颜色映射", Chain.WEATHER, "feature/weather/domain/WeatherColorMapper", "天气状态 → 颜色"),
        HelperEntry("天气预报图标映射", Chain.WEATHER, "feature/weather/domain/WeatherForecastIconMapper", "预报代码 → 图标"),
        HelperEntry("天气图标映射", Chain.WEATHER, "feature/weather/domain/WeatherIconMapper", "天气代码 → 图标"),

        // —— 横切支撑 ——
        HelperEntry("背景模式样式支持", Chain.SUPPORT, "ui/page_display/settings/SettingsBackgroundStyleSupport", "背景壁纸模式下的页面颜色映射"),
        HelperEntry("小组件渲染支持", Chain.SUPPORT, "platform/widget/WidgetRenderingSupport", "桌面小组件 RemoteViews 渲染辅助"),
    )
}
