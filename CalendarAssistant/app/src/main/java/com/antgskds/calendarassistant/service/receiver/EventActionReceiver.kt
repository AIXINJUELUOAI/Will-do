package com.antgskds.calendarassistant.service.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.core.center.ScheduleCenter
import com.antgskds.calendarassistant.core.capsule.CapsuleStateManager
import com.antgskds.calendarassistant.ui.components.UniversalToastUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 事件动作接收器
 * 处理取件码的"已取"和"延长"操作
 */
class EventActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_COMPLETE = "com.antgskds.calendarassistant.action.COMPLETE"
        const val ACTION_COMPLETE_SCHEDULE = "com.antgskds.calendarassistant.action.COMPLETE_SCHEDULE"
        const val ACTION_CHECKIN = "com.antgskds.calendarassistant.action.CHECKIN"
        const val EXTRA_EVENT_ID = "event_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID)
        val app = context.applicationContext as App
        val scheduleCenter = app.scheduleCenter
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        when (intent.action) {
            ACTION_COMPLETE, ACTION_COMPLETE_SCHEDULE, ACTION_CHECKIN -> {
                val pendingResult = goAsync()
                scope.launch {
                    try {
                        if (eventId == CapsuleStateManager.AGGREGATE_PICKUP_ID) {
                            completeAllActivePickups(scheduleCenter = scheduleCenter, app = app, context = context)
                        } else {
                            val targetEventId = eventId ?: return@launch
                            scheduleCenter.performPrimaryRuleAction(targetEventId)
                        }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    /**
     * 批量完成所有活跃的取件码（聚合胶囊使用）
     * 获取所有未过期的取件码并批量删除
     */
    private suspend fun completeAllActivePickups(
        scheduleCenter: ScheduleCenter,
        app: App,
        context: Context
    ) {
        val completedCount = scheduleCenter.completeAllActivePickups()

        // 取消聚合胶囊的通知
        val nm = NotificationManagerCompat.from(context)
        nm.cancel(CapsuleStateManager.AGGREGATE_NOTIF_ID)

        // 显示删除数量
        if (completedCount > 0) {
            withContext(Dispatchers.Main) {
                UniversalToastUtil.showSuccess(context, "已完成 $completedCount 个取件码")
            }
        }

        // 主动触发胶囊状态刷新
        app.capsuleCommandApi.forceRefresh()
    }
}
