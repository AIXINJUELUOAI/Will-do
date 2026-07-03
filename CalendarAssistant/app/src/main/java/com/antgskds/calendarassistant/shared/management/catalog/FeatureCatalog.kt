package com.antgskds.calendarassistant.shared.management.catalog

/**
 * 功能模块总览台账（代码内，不暴露给 App 用户）。
 *
 * ## 这是什么
 * 项目所有「功能模块」的总地图。打开本文件即可一眼看全：项目有哪些功能、各属于哪条主链路、
 * 核心入口在哪、干嘛的。让不直接读代码的维护者也能掌握「这个项目到底由哪些功能组成」。
 *
 * ## 怎么登记（agent 开发新功能模块前必须做）
 * 新增一个功能模块（新的业务能力，不是小改动）前，**先在下面 [features] 登记一条 [FeatureEntry]**
 * （写清所属链路、入口、说明），再开始编码。这是「先注册再开发」的最上层——让新功能不会无声出现。
 *
 * ## 边界
 * - 这是人工维护的功能地图，登记元信息，不持有任何业务逻辑。
 * - 不做成给 App 用户看的 UI。registry-check 会把它纳入健康检查（项数、字段非空）。
 */
object FeatureCatalog {

    /** 功能所属的主链路（对应架构 入口→识别→入库→同步→通知，以及横切支撑）。 */
    enum class Chain {
        RECOGNITION,   // 识别
        INGEST,        // 入库
        SYNC,          // 同步
        NOTIFICATION,  // 通知
        SCHEDULE,      // 日程主体（CRUD/展示）
        SUPPORT,       // 横切支撑（备份/诊断/权限/小组件/快捷入口等）
    }

    /**
     * 一个功能模块的登记项。
     * @param name 功能名。
     * @param chain 所属主链路。
     * @param entry 核心入口类（让维护者能定位代码）。
     * @param note 一句话说明这个功能干嘛。
     */
    data class FeatureEntry(
        val name: String,
        val chain: Chain,
        val entry: String,
        val note: String,
    )

    val features: List<FeatureEntry> = listOf(
        // —— 识别 ——
        FeatureEntry("AI 识别", Chain.RECOGNITION, "core/center/RecognitionCenter", "截图/图片/文本/语音 → OCR/多模态/文本解析 → 日程草稿"),
        FeatureEntry("正则日程识别", Chain.RECOGNITION, "data/node/recognition/RecognitionRegexNode", "文本/语音转写先走可配置正则规则生成日程草稿"),
        FeatureEntry("语音输入", Chain.RECOGNITION, "core/service/voice/VoiceCaptureHandleActivity", "长按音量+或悬浮窗入口录音，转写为随口记/识别输入"),
        FeatureEntry("短信取件码", Chain.RECOGNITION, "core/sms/SmsPickupIngestCoordinator", "监听短信、本地解析取件码"),
        FeatureEntry("剪贴板识别", Chain.RECOGNITION, "core/center/ClipboardCodeCenter", "剪贴板取件码识别"),

        // —— 入库 ——
        FeatureEntry("内容入库", Chain.INGEST, "core/center/ContentIngestCenter", "识别结果/短信/导入 → 去重转换 → 写库主线"),
        FeatureEntry("日程导入", Chain.INGEST, "core/center/ImportCenter", "各来源草稿转 Event、本地去重写库"),

        // —— 同步 ——
        FeatureEntry("系统日历同步", Chain.SYNC, "core/center/SyncCenter", "本地日程 ↔ 系统日历双向同步"),

        // —— 通知 ——
        FeatureEntry("通知主链路", Chain.NOTIFICATION, "feature/api/notification/NotificationApi", "普通通知统一发布（NotificationCenter + Publisher）"),
        FeatureEntry("实况胶囊", Chain.NOTIFICATION, "core/capsule/CapsuleStateManager", "胶囊状态计算 + CapsuleDispatcher 发布（原生/魅族/小米超级岛）"),
        FeatureEntry("提醒调度", Chain.NOTIFICATION, "core/center/ReminderCenter", "提醒生命周期、胶囊闹钟、reconcile"),

        // —— 日程主体 ——
        FeatureEntry("日程管理", Chain.SCHEDULE, "core/center/ScheduleCenter", "事件 CRUD、展示模型、重复日程"),
        FeatureEntry("课程表", Chain.SCHEDULE, "core/course", "课程并入事件模型、课表设置"),
        FeatureEntry("快捷备忘", Chain.SCHEDULE, "core/center/QuickMemoCenter", "语音/文字快捷备忘"),
        FeatureEntry("图片随口记", Chain.SCHEDULE, "core/center/QuickMemoCenter", "系统图片/分享图片保存为随口记素材"),
        FeatureEntry("便签笔记", Chain.SCHEDULE, "core/center/NoteCenter", "便签编辑与存储"),

        // —— 横切支撑 ——
        FeatureEntry("天气", Chain.SUPPORT, "core/weather", "天气预警/风险，位置选择"),
        FeatureEntry("背景自定义", Chain.SUPPORT, "feature/appearance/domain/AppBackgroundImageStore", "用户图片背景导入、私有存储与背景取色"),
        FeatureEntry("数据备份", Chain.SUPPORT, "core/center/BackupCenter", "导入导出备份"),
        FeatureEntry("桌面小组件", Chain.SUPPORT, "core/center/WidgetCenter", "日程/课程桌面小组件"),
        FeatureEntry("悬浮窗/EdgeBar", Chain.SUPPORT, "core/center/FloatingCenter", "悬浮窗与侧边栏快捷入口"),
        FeatureEntry("诊断日志", Chain.SUPPORT, "core/center/DiagnosticLogCenter", "异常日志捕获与导出"),
        FeatureEntry("重复事件清理", Chain.SUPPORT, "core/center/DuplicateEventCleanupCenter", "重复事件去重"),
    )

    /** 按链路筛选。 */
    fun byChain(chain: Chain): List<FeatureEntry> = features.filter { it.chain == chain }
}
