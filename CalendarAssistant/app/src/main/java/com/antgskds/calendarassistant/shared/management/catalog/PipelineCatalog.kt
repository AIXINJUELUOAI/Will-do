package com.antgskds.calendarassistant.shared.management.catalog

/**
 * 流程主线台账（代码内，不暴露给 App 用户）。
 *
 * ## 这是什么
 * 项目所有「流程主线」（Pipeline / Orchestrator / Dispatcher 等把多个步骤串成一条主干的编排器）的总地图。
 * 对应架构口诀里的「流程进 pipeline」：同类入口可以很多，但同一种处理流程只能收敛到一条主线。
 * 打开本文件即可一眼看全：项目里有哪些主流程、各属于哪条主链路、入口在哪、干嘛的。
 *
 * ## 怎么登记（agent 新增/改动流程主线前必须做）
 * 新建一个 Pipeline / Orchestrator（或让某个 Dispatcher/Center 承担流程编排职责）前，
 * **先在下面 [pipelines] 登记一条 [PipelineEntry]**，写清它收敛的是哪条流程、入口、说明，再开始编码。
 * 这样能防止「同一种流程被不同入口各复制一套」——这正是本次重构要消灭的乱象。
 *
 * ## 边界
 * - 只登记元信息，不持有业务逻辑。
 * - 「入口可以很多，流程只能一条」：同类流程发现有第二条实现时，应合并而不是再登记一条。
 * - 当前多数主流程仍由过渡期的 Center / Dispatcher 承担，目标是逐步迁到 feature 各自的 application 包下的
 *   正式 Pipeline / Orchestrator（见架构讨论记录）。迁移时更新本台账的 entry 指向。
 */
object PipelineCatalog {

    /** 流程所属的主链路（对应架构 入口→识别→入库→同步→通知，以及横切支撑）。 */
    enum class Chain {
        RECOGNITION,   // 识别
        INGEST,        // 入库
        SYNC,          // 同步
        NOTIFICATION,  // 通知
        SCHEDULE,      // 日程主体
        SUPPORT,       // 横切支撑
    }

    /** 流程主线的成熟度，便于维护者一眼看出哪些还是过渡实现。 */
    enum class Maturity {
        PIPELINE,    // 已是正式 Pipeline/Orchestrator
        TRANSITION,  // 过渡期：由 Center/Dispatcher 兼任流程编排
        PLANNED,     // 讨论记录里规划、尚未落地
    }

    /**
     * 一条流程主线的登记项。
     * @param name 流程名。
     * @param chain 所属主链路。
     * @param entry 当前承担该流程的入口类（让维护者能定位代码）。
     * @param maturity 成熟度。
     * @param note 一句话说明这条流程收敛了什么。
     */
    data class PipelineEntry(
        val name: String,
        val chain: Chain,
        val entry: String,
        val maturity: Maturity,
        val note: String,
    )

    val pipelines: List<PipelineEntry> = listOf(
        // —— 识别 ——
        PipelineEntry(
            "识别主流程", Chain.RECOGNITION, "core/center/RecognitionCenter",
            Maturity.TRANSITION,
            "所有识别入口（截图/图片/文本/语音）统一走 RecognitionApi → 输出 AnalysisResult<RecognitionDraft>",
        ),
        PipelineEntry(
            "正则入库后 AI 复核", Chain.RECOGNITION, "core/rule/RegexAiReviewCoordinator",
            Maturity.TRANSITION,
            "正则先生成并入库单条日程后，后台强制 AI 单条复核并安全修正",
        ),

        // —— 入库 ——
        PipelineEntry(
            "内容入库主流程", Chain.INGEST, "core/center/ContentIngestCenter",
            Maturity.TRANSITION,
            "识别结果/短信/导入经 Actor/Channel 雏形 pipeline 去重转换写库；目标迁为正式 IngestPipeline",
        ),

        // —— 同步 ——
        PipelineEntry(
            "同步主流程", Chain.SYNC, "core/center/SyncCenter",
            Maturity.TRANSITION,
            "本地日程 ↔ 系统日历同步编排；失败不回滚本地入库（重试 Worker 见 WorkerCatalog，规划中）",
        ),

        // —— 通知 ——
        PipelineEntry(
            "通知发布主流程", Chain.NOTIFICATION, "core/center/NotificationCenter",
            Maturity.TRANSITION,
            "NotificationApi 请求 → NotificationCenter 分流 → Publisher 发布；目标迁为 NotificationOrchestrator",
        ),
        PipelineEntry(
            "胶囊发布流程", Chain.NOTIFICATION, "service/capsule/CapsuleDispatcher",
            Maturity.TRANSITION,
            "CapsuleStateManager 只算状态，发布交 CapsuleDispatcher 分流原生/魅族/小米超级岛",
        ),

        // —— 日程主体 ——
        PipelineEntry(
            "本地存储分流", Chain.SCHEDULE, "store/StoreDispatcher",
            Maturity.TRANSITION,
            "日程写入主链路 ScheduleCenter → CalendarCenter → StoreDispatcher → StoreRootNode 的分流节点",
        ),
    )
}
