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
        HelperEntry("重复实例时间计算", Chain.SCHEDULE, "feature/schedule/domain/model/RepeatOccurrenceCalculator", "共用重复规则推进和下一次时间计算，供日程展示及随口记提醒使用"),
        // —— 识别 ——
        HelperEntry("AI 失败映射", Chain.RECOGNITION, "core/ai/AiFailureMapper", "AI 调用失败原因 → 内部失败类型"),
        HelperEntry("识别失败文案映射", Chain.RECOGNITION, "core/ai/RecognitionFailureMessageMapper", "识别失败类型 → 用户可读提示"),
        HelperEntry("即时码二维码支持", Chain.RECOGNITION, "core/instantcode/InstantCodeQrSupport", "取件/取餐/取票/寄件图片二维码解析与卡片二维码生成"),
        HelperEntry("正则日程解析", Chain.RECOGNITION, "core/rule/RegexScheduleRecognizer", "可配置正则规则 → 日程草稿"),
        HelperEntry("正则规则偏好存储", Chain.RECOGNITION, "core/rule/RegexScheduleRulePrefs", "开发者可编辑正则规则 JSON 读写"),

        // —— 入库 / 日程 ——
        HelperEntry("课程事件映射", Chain.INGEST, "core/course/CourseEventMapper", "课程表数据 → Event"),
        HelperEntry("日程展示助手", Chain.SCHEDULE, "feature/schedule/domain/ScheduleDisplayHelper", "日程展示字段拼装"),
        HelperEntry("WebDAV V2 编解码", Chain.SYNC, "feature/cloudsync/data/SyncV2Codec", "状态压缩加密、资产加密、HMAC 内容寻址和稳定哈希"),
        HelperEntry("Agent 协议编解码", Chain.SCHEDULE, "shared/api/AgentProtocolJson", "Agent API v2 请求、响应与业务 DTO 的 JSON 转换"),
        HelperEntry("Agent 日志脱敏", Chain.SUPPORT, "feature/settings/diagnostics/application/DiagnosticLogRedactor", "导出给 Agent 前遮盖 API Key、密码、Token 和鉴权请求头"),

        // —— 通知 ——
        HelperEntry("日程实况展示支持", Chain.NOTIFICATION, "shared/management/resource/notification/display/live/template/ScheduleLiveDisplaySupport", "日程胶囊展示字段裁剪/拼接"),
        HelperEntry("天气实况展示支持", Chain.NOTIFICATION, "shared/management/resource/notification/display/live/template/WeatherLiveDisplaySupport", "天气胶囊展示字段裁剪/拼接"),

        // —— 天气 ——
        HelperEntry("天气预警图标映射", Chain.WEATHER, "feature/weather/domain/WeatherAlertIconMapper", "预警类型 → 图标"),
        HelperEntry("天气颜色映射", Chain.WEATHER, "feature/weather/domain/WeatherColorMapper", "天气状态 → 颜色"),
        HelperEntry("天气预报图标映射", Chain.WEATHER, "feature/weather/domain/WeatherForecastIconMapper", "预报代码 → 图标"),
        HelperEntry("天气图标映射", Chain.WEATHER, "feature/weather/domain/WeatherIconMapper", "天气代码 → 图标"),

        // —— 横切支撑 ——
        HelperEntry("公共设置分隔线", Chain.SUPPORT, "shared/ui/material/settings/AppSettingsDivider", "设置卡片内统一 16 dp 水平缩进、0.5 dp 线宽与主题分隔色；开发者相关页面暂不迁移"),
        HelperEntry("公共列表侧滑容器", Chain.SUPPORT, "shared/ui/material/component/AppSwipeReveal", "日程、课程和随口记共用单向拖动、动作区裁切、取消回弹与阈值触感，保留业务内容和既有展开参数"),
        HelperEntry("公共设置行", Chain.SUPPORT, "shared/ui/material/settings/SettingsRowComponents", "设置开关、点击项及滑块等统一展示入口；实验室四项开关复用 SwitchSettingItem"),
        HelperEntry("统一页面顶部栏", Chain.SUPPORT, "shared/ui/material/component/AppTopBar", "标题固定居中，左右操作可选；统一顶部安全区及返回按钮，供页面骨架组合使用"),
        HelperEntry("统一页面骨架", Chain.SUPPORT, "shared/ui/material/component/AppPageScaffold", "统一背景、系统栏安全区、键盘避让和内容限宽；支持整页滚动及列表自行滚动，供设置、首页、天气、随口记、便签和小组件配置使用"),
        HelperEntry("页面内容安全留白", Chain.SUPPORT, "shared/ui/material/component/AppPageInsets", "骨架向自行滚动的内容提供底部留白，供列表 contentPadding 和浮动操作避让使用，业务无需自行读取导航栏高度"),
        HelperEntry("背景模式样式支持", Chain.SUPPORT, "app/ui/theme/material/background/SettingsBackgroundStyleSupport", "背景壁纸模式下的页面颜色映射"),
        HelperEntry("应用公共 UI 组件", Chain.SUPPORT, "shared/ui/material/component/AppUiComponents", "Material 默认路径下的卡片、弹窗和底部弹层外壳"),
        HelperEntry("应用玻璃表面", Chain.SUPPORT, "shared/ui/material/component/AppGlassSurface", "统一页面与弹层磨砂材质、裁切和无采样源时的背景回退"),
        HelperEntry("跨窗口玻璃背景", Chain.SUPPORT, "shared/ui/material/component/AppWindowBackdrop", "使用屏幕坐标对齐弹层与背景，嵌套弹层合成父场景，避免局部采样导致透明缺口"),
        HelperEntry("公共底部弹层", Chain.SUPPORT, "shared/ui/material/component/AppModalBottomSheet", "统一 Sheet 标题、正文滚动、高度上限与 0 至 3 个操作；SheetMaterialSurface 按外壳坐标绘制背景，隔离预测性返回的正文压缩，统一处理底部安全区"),
        HelperEntry("公共锚点菜单", Chain.SUPPORT, "shared/ui/material/component/AppDropdownMenu", "AppMenuItem 定义菜单项；独立 Popup 隔离背景采样，统一定位、选中态、材质及返回和外部点击关闭，供今日视图切换与宽屏更多操作复用"),
        HelperEntry("公共分段切换", Chain.SUPPORT, "shared/ui/material/component/AppSegmentedControl", "主题色选中项与颜色动画、等宽布局、公共背景材质和单次选择触感，统一主题模式、小组件设置及默认启动页切换"),
        HelperEntry("公共滚轮选择器", Chain.SUPPORT, "shared/ui/material/component/AppWheelPicker", "日期、时间及单列选择共用滚轮和弹窗入口，保留 175 dp 高度与 35 dp 行高"),
        HelperEntry("公共悬浮按钮", Chain.SUPPORT, "shared/ui/material/component/AppFloatingActionButton", "统一悬浮操作的表面材质与点击入口，保留 72 dp 尺寸、34 dp 图标及页面操作语义"),
        HelperEntry("小组件渲染支持", Chain.SUPPORT, "platform/widget/WidgetRenderingSupport", "桌面小组件 RemoteViews 渲染辅助"),
    )
}
