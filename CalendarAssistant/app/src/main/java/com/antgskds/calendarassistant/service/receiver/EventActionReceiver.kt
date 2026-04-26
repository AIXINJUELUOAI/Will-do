package com.antgskds.calendarassistant.service.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.core.capsule.CapsuleStateManager
import com.antgskds.calendarassistant.data.model.ScheduleDisplayItem.ActionTarget
import com.antgskds.calendarassistant.ui.components.UniversalToastUtil
import com.antgskds.calendarassistant.calendar.models.EventTags
import com.antgskds.calendarassistant.calendar.models.isCompleted
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 事件动作接收器：处理通知上的「完成」「签到」按钮。
 * 统一通过 ActionTarget 路由到 ScheduleCenter 新 API。
 */
class EventActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_COMPLETE = "com.antgskds.calendarassistant.action.COMPLETE"
        const val ACTION_COMPLETE_SCHEDULE = "com.antgskds.calendarassistant.action.COMPLETE_SCHEDULE"
        const val ACTION_CHECKIN = "com.antgskds.calendarassistant.action.CHECKIN"
        const val EXTRA_EVENT_ID = "event_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val eventIdStr = intent.getStringExtra(EXTRA_EVENT_ID) ?: return
        val app = context.applicationContext as App
        val scheduleCenter = app.scheduleCenter
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        when (intent.action) {
            ACTION_COMPLETE, ACTION_COMPLETE_SCHEDULE, ACTION_CHECKIN -> {
                val pendingResult = goAsync()
                scope.launch {
                    try {
                        if (eventIdStr == CapsuleStateManager.AGGREGATE_PICKUP_ID) {
                            // 聚合取件完成：完成所有活跃的取件事件
                            val pickups = scheduleCenter.events.value.filter {
                                it.tag == EventTags.PICKUP && !it.isCompleted
                            }
                            pickups.forEach { event ->
                                val id = event.id ?: return@forEach
                                scheduleCenter.completeItem(ActionTarget.Single(id))
                            }
                        } else {
                            val targetEventId = eventIdStr.toLongOrNull() ?: return@launch
                            val event = scheduleCenter.events.value.find { it.id == targetEventId } ?: return@launch
                            val target = ActionTarget.Single(targetEventId)

                            when (intent.action) {
                                ACTION_CHECKIN -> scheduleCenter.checkInItem(target)
                                else -> scheduleCenter.completeItem(target)
                            }
                        }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
