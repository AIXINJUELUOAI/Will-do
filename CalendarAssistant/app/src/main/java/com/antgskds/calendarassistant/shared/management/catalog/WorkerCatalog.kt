package com.antgskds.calendarassistant.shared.management.catalog

/**
 * 后台任务台账（代码内，不暴露给 App 用户）。
 *
 * ## 这是什么
 * 项目所有「WorkManager Worker / 后台任务」的总地图。后台任务是主链路的「副线」——
 * 同步重试、定时刷新、监听唤醒等可以异步，但每个后台任务都应在此登记，避免后台任务无声增殖。
 * 打开本文件即可一眼看全：项目里有哪些后台任务、各干嘛、归哪条链路、触发方式。
 *
 * ## 怎么登记（agent 新增 Worker 前必须做）
 * 新建一个 Worker 前，**先在下面 [workers] 登记一条 [WorkerEntry]**，写清它做什么、何时触发、归哪条链路，
 * 再开始编码。先看本台账有没有同类后台任务可复用或合并。
 *
 * ## 边界
 * - 只登记元信息。Worker 是副线触发器，主业务链路不能靠隐式后台事件串起来（见架构讨论记录「流程边界」）。
 * - 规划但尚未落地的后台任务（如同步失败重试 Worker）以 PLANNED 登记占位。
 */
object WorkerCatalog {

    /** 任务所属的主链路。 */
    enum class Chain {
        SYNC,          // 同步
        RECOGNITION,   // 识别
        WEATHER,       // 天气
        NOTIFICATION,  // 通知
        SUPPORT,       // 横切支撑
    }

    /** 触发方式。 */
    enum class Trigger {
        PERIODIC,    // 周期性
        ONE_TIME,    // 一次性
        ON_EVENT,    // 事件/开机等唤醒
    }

    /** 成熟度。 */
    enum class Maturity {
        ACTIVE,    // 已落地在用
        PLANNED,   // 规划、尚未落地
    }

    /**
     * 一个后台任务的登记项。
     * @param name 任务名。
     * @param chain 所属主链路。
     * @param entry Worker 类路径（PLANNED 项写规划名）。
     * @param trigger 触发方式。
     * @param maturity 成熟度。
     * @param note 一句话说明它做什么。
     */
    data class WorkerEntry(
        val name: String,
        val chain: Chain,
        val entry: String,
        val trigger: Trigger,
        val maturity: Maturity,
        val note: String,
    )

    val workers: List<WorkerEntry> = listOf(
        // —— 识别 ——
        WorkerEntry(
            "图片分享识别任务", Chain.RECOGNITION, "core/service/image/ImageShareRecognitionWorker",
            Trigger.ONE_TIME, Maturity.ACTIVE,
            "系统图片分享选择识别日程后，在后台完成图片识别并交给入库链路",
        ),

        // —— 天气 ——
        WorkerEntry(
            "天气同步任务", Chain.WEATHER, "feature/weather/domain/WeatherSyncWorker",
            Trigger.PERIODIC, Maturity.ACTIVE,
            "周期拉取天气数据、触发预警分析；开机由 BootReceiver 重排",
        ),

        // —— 规划中（讨论记录：同步失败重试缺口）——
        WorkerEntry(
            "同步重试任务", Chain.SYNC, "SyncRetryWorker（规划）",
            Trigger.ONE_TIME, Maturity.PLANNED,
            "本地日程同步系统日历失败后的后台重试；当前缺口，配合规划中的 SyncRetryPolicy",
        ),
    )
}
