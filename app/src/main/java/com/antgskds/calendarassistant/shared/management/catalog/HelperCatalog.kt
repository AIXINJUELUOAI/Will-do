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
        HelperEntry("演示数据生成", Chain.SUPPORT, "feature/settings/developer/application/DemoModeDataFactory", "按当天稳定生成只读日程、大学课表、随口记、账单和完整天气展示数据"),
        HelperEntry("财务消息正则", Chain.RECOGNITION, "feature/accounting/domain/AccountingMessageRules", "按通知包名或短信发件人匹配，命名分组抽取金额、名称、时间和单号，未知静默跳过"),
        HelperEntry("财务规则存储", Chain.RECOGNITION, "feature/accounting/data/AccountingMessageRulePrefs", "开发者编辑与运行时共用本地规则，不覆盖用户禁用和删除"),
        HelperEntry("财务规则编辑", Chain.SUPPORT, "feature/recognition/ui/connector/AccountingRulesEditor", "现有正则页内编辑、增删和测试财务规则，测试不入库"),
        HelperEntry("无障碍诊断快照", Chain.SUPPORT, "platform/accessibility/AccessibilityDiagnosticSnapshot", "有界读取事件源及窗口节点并立即回收，输出不可变 JSON 与现有支付规则判断，遮蔽密码字段；汇总服务配置及仅含状态的健康日志"),
        HelperEntry("支付消息解析", Chain.RECOGNITION, "feature/accounting/domain/PaymentMessageParser", "支付凭证、收款及到账退款本地解析；消息保留时效校验，不调用 AI"),
        HelperEntry("微信结构化支付解析", Chain.RECOGNITION, "feature/accounting/domain/WechatPaymentParser", "解析转账 XML、详情桥接响应及红包领取响应；按本人方向、实际金额与原始交易时间生成账单"),
        HelperEntry("自动记账跨进程接收", Chain.RECOGNITION, "platform/accounting/PaymentCaptureProvider", "Binder 校验支付应用 UID；检查开关、限制数据大小后调用识别契约，拒绝其他应用注入"),
        HelperEntry("微信 Hook 上游适配", Chain.RECOGNITION, "platform/xposed/WechatHookAdaptation", "移植 AutoAccounting 版本与规则缓存；后台适配红包模型，匹配成功后下次正常启动安装，不重启宿主"),
        HelperEntry("上游 DEX 规则扫描", Chain.RECOGNITION, "platform/xposed/upstream/dex/Dex", "AutoAccounting DEX 扫描源码及模型；完整构造函数和回调签名匹配，禁止全局类加载监听"),
        HelperEntry("上游微信红包规则", Chain.RECOGNITION, "platform/xposed/upstream/LuckMoneyModel", "移植 AutoAccounting 包名、构造函数及 onGYNetEnd 签名规则"),
        HelperEntry("支付应用 Hook", Chain.RECOGNITION, "platform/xposed/PaymentCaptureHook", "微信支付/转账数据库、详情桥接、红包领取回调及支付宝同步消息；独立安装、有界后台转发，不改宿主参数"),
        HelperEntry("无障碍支付页面采样", Chain.RECOGNITION, "platform/accessibility/PaymentWindowReader", "有界采集可见根节点/事件源子树并立即回收；单独读取前台包与窗口身份，详情内容就绪后才触发截图，不引入 OCR"),
        HelperEntry("记账结果展示", Chain.NOTIFICATION, "shared/management/resource/notification/display/live/template/AccountingRecognitionDisplay", "按实际入库账单汇总金额与收支，重复和待核对另计；小岛仅金额，展开显示明细汇总"),
        HelperEntry("统一识别结果解析", Chain.RECOGNITION, "feature/recognition/application/ai/RecognitionJsonParser", "分别解析 events 与 bills，坏条目隔离；旧响应兼容，账单缺失字段留待确认"),
        HelperEntry("账单识别转换", Chain.INGEST, "feature/accounting/domain/AccountingRecognitionMapper", "同金额同一分钟独立拦截疑似重复；时间仅接近时结合名称，同类交易号明确不同可放行，支付交易号精确排重兜底"),
        HelperEntry("账单识别待确认列表", Chain.SUPPORT, "feature/accounting/ui/AccountingRecognitionSheet", "展示持久化识别草稿，逐条编辑确认或丢弃，未确认不计入收支"),
        HelperEntry("统一多模态配置", Chain.SUPPORT, "feature/recognition/application/ai/AiModelConfig", "统一图文模型配置；旧设置在 MySettings.migrateToMultimodalConfig 中兼容迁移，不覆盖已有多模态凭据"),
        HelperEntry("账单编辑转换", Chain.INGEST, "feature/accounting/domain/AccountingEntryEditor", "手动账单金额校验和字段转换；编辑保留原始来源、去重标识及待核对状态"),
        HelperEntry("账单编辑面板", Chain.SUPPORT, "feature/accounting/ui/AccountingEditorSheet", "共用 Sheet 新建和编辑账单，复用日期时间滚轮，校验后保存真实数据"),
        HelperEntry("快捷新建账单", Chain.SUPPORT, "feature/accounting/ui/AccountingCreateSheet", "收支分类按钮网格、固定金额输入与保存；默认聚焦数字键盘，更多字段折叠"),
        HelperEntry("账单原图展示", Chain.SUPPORT, "feature/accounting/ui/AccountingSourceImage", "在账单详情和待确认 Sheet 下方按比例展示关联截图，异步采样解码"),
        HelperEntry("侧滑操作图标", Chain.SUPPORT, "shared/ui/material/component/SwipeActionIcon", "日程与账单共用带圆角矩形背景的侧滑动作，保留日程尺寸、颜色和触感"),
        HelperEntry("重复实例时间计算", Chain.SCHEDULE, "feature/schedule/domain/model/RepeatOccurrenceCalculator", "共用重复规则推进和下一次时间计算，供日程展示及随口记提醒使用"),
        // —— 识别 ——
        HelperEntry("AI 失败映射", Chain.RECOGNITION, "feature/recognition/application/ai/AiFailureMapper", "区分接口明确拒绝图片输入与鉴权、额度、网络等失败，保留可读接口原因"),
        HelperEntry("识别失败文案映射", Chain.RECOGNITION, "feature/recognition/application/ai/RecognitionFailureMessageMapper", "识别失败类型与详情 → 现有结果反馈；不支持图片时引导更换模型，不新增提醒入口"),
        HelperEntry("即时码二维码支持", Chain.RECOGNITION, "core/instantcode/InstantCodeQrSupport", "取件/取餐/取票/寄件图片二维码解析与卡片二维码生成"),
        HelperEntry("正则日程解析", Chain.RECOGNITION, "core/rule/RegexScheduleRecognizer", "可配置正则规则 → 日程草稿"),
        HelperEntry("正则规则偏好存储", Chain.RECOGNITION, "core/rule/RegexScheduleRulePrefs", "开发者可编辑正则规则 JSON 读写"),

        // —— 入库 / 日程 ——
        HelperEntry("课程事件映射", Chain.INGEST, "core/course/CourseEventMapper", "课程表数据 → Event"),
        HelperEntry("首页日期共享选中块", Chain.SCHEDULE, "feature/home/ui/render/material/HomeDateSelectionTransition", "共用日历与日程视图选中背景的坐标过渡，圆角与主题色随目标连续变化"),
        HelperEntry("首页日程分组", Chain.SCHEDULE, "feature/home/domain/HomeAgendaMapper", "仅保留有日程或账单的日期；按日期排序、搜索和选中日定位"),
        HelperEntry("日程展示助手", Chain.SCHEDULE, "feature/schedule/domain/ScheduleDisplayHelper", "日程展示字段拼装"),
        HelperEntry("WebDAV V2 编解码", Chain.SYNC, "feature/cloudsync/data/SyncV2Codec", "状态压缩加密、资产加密、HMAC 内容寻址和稳定哈希"),
        HelperEntry("Agent 协议编解码", Chain.SCHEDULE, "shared/api/AgentProtocolJson", "Agent API v2 请求、响应与业务 DTO 的 JSON 转换"),
        HelperEntry("Agent 日志脱敏", Chain.SUPPORT, "feature/settings/diagnostics/application/DiagnosticLogRedactor", "导出给 Agent 前遮盖 API Key、密码、Token 和鉴权请求头"),

        // —— 通知 ——
        HelperEntry("日程实况展示支持", Chain.NOTIFICATION, "shared/management/resource/notification/display/live/template/ScheduleLiveDisplaySupport", "日程胶囊展示字段裁剪/拼接"),
        HelperEntry("天气实况展示支持", Chain.NOTIFICATION, "shared/management/resource/notification/display/live/template/WeatherLiveDisplaySupport", "天气胶囊展示字段裁剪/拼接"),

        // —— 天气 ——
        HelperEntry("按日期天气摘要", Chain.WEATHER, "feature/weather/domain/WeatherDateSummaryMapper", "今日使用实时天气，未来按日期匹配预报；过去、缺失或无效天气不显示"),
        HelperEntry("天气预警图标映射", Chain.WEATHER, "feature/weather/domain/WeatherAlertIconMapper", "预警类型 → 图标"),
        HelperEntry("天气颜色映射", Chain.WEATHER, "feature/weather/domain/WeatherColorMapper", "天气状态 → 颜色"),
        HelperEntry("天气预报图标映射", Chain.WEATHER, "feature/weather/domain/WeatherForecastIconMapper", "预报代码 → 图标"),
        HelperEntry("天气图标映射", Chain.WEATHER, "feature/weather/domain/WeatherIconMapper", "天气代码 → 图标"),

        // —— 横切支撑 ——
        HelperEntry("行内摘要标题", Chain.SUPPORT, "shared/ui/material/component/InlineSummaryHeader", "日期、天气及钱包图标按文字自然换行，仅以空格和间隔点分隔，标题可独立设置字重"),
        HelperEntry("账单备份格式", Chain.SUPPORT, "feature/accounting/domain/AccountingBackupCodec", "版本化 JSON 保存账单原始字段与身份；有界读取、整批校验后通过统一入库入口追加恢复"),
        HelperEntry("账单导出入口", Chain.SUPPORT, "feature/accounting/ui/AccountingExportAction", "记账页与备份页共用文件保存选择器及导出结果反馈"),
        HelperEntry("账单文件解析", Chain.SUPPORT, "feature/accounting/domain/AccountingFileParser", "官方 CSV/XLSX 动态表头、编码与 Excel 日期解析，输出逐行诊断和待核对草稿"),
        HelperEntry("账单导入预览", Chain.SUPPORT, "feature/accounting/ui/AccountingImportSheet", "选择来源、文件与确认，展示错误行及重复导入结果"),
        HelperEntry("账单摘要上下文", Chain.SUPPORT, "feature/accounting/ui/AccountingUiState", "导航装配真实账单，今日与全部页通过相同数据源展示消费摘要"),
        HelperEntry("记账入口展示", Chain.SUPPORT, "feature/accounting/ui/AccountingSummaryHeader", "今日与全部页共用钱包摘要，使用导航注入的真实账单显示日/月支出，向导航层传递日期与月份模式"),
        HelperEntry("记账预览数据", Chain.SUPPORT, "feature/accounting/ui/AccountingPreviewData", "真实账单的日期映射、周期标题与统计展示，不生成演示账单或消费判断"),
        HelperEntry("收支建议映射", Chain.SUPPORT, "feature/accounting/ui/AccountingSuggestionMapper", "按当前周期的已确认人民币账单生成原有十二类提示；同期比较使用相同天数，缺少证据不推断转账或报销"),
        HelperEntry("记账预览图表", Chain.SUPPORT, "feature/accounting/ui/AccountingPreviewCharts", "预览卡片与分析 Sheet 共用折线；结构图按收支最大内容高度布局并交叉淡入淡出，未来日期留空"),
        HelperEntry("公共设置分隔线", Chain.SUPPORT, "shared/ui/material/settings/AppSettingsDivider", "设置卡片内统一 16 dp 水平缩进、0.5 dp 线宽与主题分隔色；开发者相关页面暂不迁移"),
        HelperEntry("公共列表侧滑容器", Chain.SUPPORT, "shared/ui/material/component/AppSwipeReveal", "日程、课程和随口记共用单向拖动、动作区裁切、取消回弹与阈值触感，保留业务内容和既有展开参数"),
        HelperEntry("公共设置行", Chain.SUPPORT, "shared/ui/material/settings/SettingsRowComponents", "设置开关、点击项及滑块等统一展示入口；实验室四项开关复用 SwitchSettingItem"),
        HelperEntry("平板详情工作区", Chain.SUPPORT, "shared/ui/material/component/AppDetailWorkspace", "日程、课程、账单和随口记共用右侧内容与附件分区；保留手机弹层及既有保存入口"),
        HelperEntry("日历像素布局", Chain.SUPPORT, "feature/schedule/ui/render/material/CalendarLayout", "周月日历及课表的表头、网格与事件复用整数像素边界，消除独立取整引起的错位；保留原半透明配色"),
        HelperEntry("统一页面顶部栏", Chain.SUPPORT, "shared/ui/material/component/AppTopBar", "默认标题居中，平板设置页可左对齐；统一顶部安全区及返回按钮，供页面骨架组合使用"),
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
        HelperEntry("公共悬浮按钮", Chain.SUPPORT, "shared/ui/material/component/AppFloatingActionButton", "统一悬浮操作的表面材质与点击入口；玻璃模式使用 onSurface 图标和文字，普通模式保留调用方配色，保持 72 dp 尺寸及 34 dp 图标"),
        HelperEntry("小组件渲染支持", Chain.SUPPORT, "platform/widget/WidgetRenderingSupport", "桌面小组件 RemoteViews 渲染辅助"),
    )
}
