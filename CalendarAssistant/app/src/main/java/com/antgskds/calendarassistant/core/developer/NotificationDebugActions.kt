package com.antgskds.calendarassistant.core.developer

import com.antgskds.calendarassistant.feature.api.notification.NotificationApi
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationDisplaySnapshot
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationKey
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationKind
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationRequest
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationResult
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationRoute
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationTapTarget
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationTapTargetType
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationTrigger
import com.antgskds.calendarassistant.feature.api.schedule.model.ScheduleInstanceKey

/**
 * Phase 1 开发者调试：通过【新通知链路】预览 / 强制触发一条普通通知。
 *
 * 关键设计：刻意复用真实链路 `create() → trigger(Debug)`，让它完整流经
 * `NotificationApi → NotificationCenter → AndroidNormalNotificationPublisher`，
 * 从而真正验证新发布器是否工作——**不另起任何「测试专用发送逻辑」**，否则等于白测。
 *
 * 注意：本动作不依赖任何真实日程，使用一个固定的虚拟事件 id，不污染用户数据。
 */
object NotificationDebugActions {

    /** 虚拟事件 id，仅用于开发者预览，取负值避免与真实自增主键冲突。 */
    private const val DEBUG_EVENT_ID = -90001L

    suspend fun previewScheduleReminder(api: NotificationApi): NotificationResult {
        val instanceKey = ScheduleInstanceKey.Single(DEBUG_EVENT_ID)
        val key = NotificationKey.scheduleReminder(instanceKey, offsetMinutes = 0)

        val request = NotificationRequest(
            key = key,
            kind = NotificationKind.SCHEDULE_REMINDER,
            display = NotificationDisplaySnapshot(
                shortText = "日程提醒",
                primaryText = "开发者预览：项目评审会",
                secondaryText = "现在开始",
                tertiaryText = "今天 14:00 · 会议室 A203",
                expandedText = "开发者预览（新通知链路 Phase 1）：这条通知经 " +
                    "NotificationApi → NotificationCenter → AndroidNormalNotificationPublisher 真实发布，" +
                    "用于验证新发布器是否正常工作。"
            ),
            route = NotificationRoute.NORMAL,
            scheduleInstanceKey = instanceKey,
            offsetMinutes = 0,
            tapTarget = NotificationTapTarget(type = NotificationTapTargetType.APP_HOME),
            source = "developer_preview"
            // 不设 behavior.triggerAtEpochMillis：保持立即可发布（状态解析为 READY），不注册系统闹钟。
        )

        // 1) 写入 Registry（自洽快照）；triggerAt 为空 → 状态解析为 READY。
        api.create(request)
        // 2) 走 Debug 触发路径——Phase 1 下唯一会真正调用发布器的路径。
        return api.trigger(NotificationTrigger.Debug(key))
    }
}
