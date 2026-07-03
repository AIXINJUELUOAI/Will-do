package com.antgskds.calendarassistant.shared.management.catalog

import com.antgskds.calendarassistant.feature.api.notification.model.NotificationKind

/**
 * 通知类型登记清单（代码内台账，不暴露给 App 用户）。
 *
 * ## 这是什么
 * 项目所有「通知类型」（[NotificationKind]）的唯一总台账。打开本文件即可一眼看全：
 * 项目会发哪些种类的通知、各是干嘛的、典型走普通通知还是实况胶囊。
 *
 * ## 怎么登记（agent 加新通知类型必须做）
 * 在 [NotificationKind] 枚举里加一个值时，**必须同时在下面 [kinds] 登记一条 [KindEntry]**
 * （带 note 说明），否则架构守卫 KIND_NOT_REGISTERED 会让 `checkArchitectureGuardrails` 失败。
 *
 * ## 边界
 * - 只登记元信息（标签/说明/典型路由倾向），不持有发布逻辑。真正发布走 NotificationCenter。
 * - 不要做成给 App 用户看的 UI——是代码内管理台账。
 */
object NotificationKindCatalog {

    /** 典型展示倾向（仅说明用，不是硬规则；实际路由由 NotificationCenter/Request 决定）。 */
    enum class TypicalRoute {
        /** 一般走普通通知。 */
        NORMAL,
        /** 一般走实况胶囊。 */
        LIVE,
        /** 普通或实况都可能。 */
        EITHER,
    }

    /**
     * 一个通知类型的登记项。
     * @param kind 通知类型枚举值。
     * @param label 人类可读标签。
     * @param typicalRoute 典型展示倾向。
     * @param note 一句话说明这类通知是干嘛的。
     */
    data class KindEntry(
        val kind: NotificationKind,
        val label: String,
        val typicalRoute: TypicalRoute,
        val note: String,
    )

    val kinds: List<KindEntry> = listOf(
        KindEntry(NotificationKind.SCHEDULE_REMINDER, "日程提醒", TypicalRoute.EITHER, "日程到点/提前提醒；普通提醒走普通通知，开启胶囊时走实况胶囊"),
        KindEntry(NotificationKind.RECOGNITION_STATUS, "识别状态", TypicalRoute.EITHER, "OCR/AI 识别进行中、成功、失败的反馈"),
        KindEntry(NotificationKind.WEATHER_ALERT, "天气预警", TypicalRoute.EITHER, "天气预警/风险提醒，带超时自动消失"),
        KindEntry(NotificationKind.SYSTEM_STATUS, "系统状态", TypicalRoute.NORMAL, "服务未开启、权限缺失、前台服务等系统类通知"),
        KindEntry(NotificationKind.DEBUG, "调试", TypicalRoute.NORMAL, "开发者/ADB 调试触发的通知"),
        KindEntry(NotificationKind.GENERIC, "通用", TypicalRoute.NORMAL, "未归类的通用通知兜底"),
    )

    /** 按枚举值查登记项。 */
    fun find(kind: NotificationKind): KindEntry? = kinds.firstOrNull { it.kind == kind }
}
