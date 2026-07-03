package com.antgskds.calendarassistant.shared.management.catalog

/**
 * 策略台账（代码内，不暴露给 App 用户）。
 *
 * ## 这是什么
 * 项目所有「Policy 策略」的总地图。架构口诀里反复强调：**用户开关只进 Policy，不散落在各入口里判断**。
 * Policy 负责「根据用户设置/输入类型/能力可用性，决定走哪条路」，把分支决策集中到一处。
 * 打开本文件即可一眼看全：项目里有哪些策略、各管哪类决策、归哪条链路。
 *
 * ## 怎么登记（agent 新增 Policy 前必须做）
 * 新建一个策略前，**先在下面 [policies] 登记一条 [PolicyEntry]**，写清它管哪类决策、归哪条链路，
 * 再开始编码。新增「根据开关选择行为」的逻辑时，应优先放进对应 Policy，而不是在入口里写 if/else。
 *
 * ## 边界
 * - 只登记元信息。Policy 只做「选择」决策，不执行副作用（不写库、不发通知、不同步）。
 * - 讨论记录规划但尚未落地的策略（如 RecognitionModePolicy / SyncRetryPolicy）以 PLANNED 登记占位。
 */
object PolicyCatalog {

    /** 策略所属的主链路。 */
    enum class Chain {
        RECOGNITION,   // 识别
        INGEST,        // 入库
        SYNC,          // 同步
        NOTIFICATION,  // 通知
        SCHEDULE,      // 日程主体
        SUPPORT,       // 横切支撑
    }

    /** 策略成熟度。 */
    enum class Maturity {
        ACTIVE,    // 已落地在用
        PLANNED,   // 讨论记录规划、尚未落地
    }

    /**
     * 一个策略的登记项。
     * @param name 策略名。
     * @param chain 所属主链路。
     * @param entry 策略类路径（PLANNED 项写规划名）。
     * @param maturity 成熟度。
     * @param note 一句话说明它管哪类决策。
     */
    data class PolicyEntry(
        val name: String,
        val chain: Chain,
        val entry: String,
        val maturity: Maturity,
        val note: String,
    )

    val policies: List<PolicyEntry> = listOf(
        // —— 通知 / 提醒 ——
        PolicyEntry(
            "提醒策略", Chain.NOTIFICATION, "store/reminder/ReminderPolicy",
            Maturity.ACTIVE,
            "根据胶囊开关/事件类型决定排哪些提醒闹钟",
        ),

        PolicyEntry(
            "识别模式策略", Chain.RECOGNITION, "core/rule/RecognitionModePolicy",
            Maturity.ACTIVE,
            "按用户设置在 AI、正则、正则优先失败后 AI、正则入库后 AI 修正之间选择文本识别路径",
        ),

        // —— 规划中（讨论记录）——
        PolicyEntry(
            "同步重试策略", Chain.SYNC, "SyncRetryPolicy（规划）",
            Maturity.PLANNED,
            "同步失败后多久重试、最多几次、哪些类型需同步；配合规划中的同步重试 Worker",
        ),
    )
}
