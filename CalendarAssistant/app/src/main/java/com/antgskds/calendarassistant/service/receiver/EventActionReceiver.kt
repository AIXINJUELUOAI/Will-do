package com.antgskds.calendarassistant.service.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.core.ai.convertDraftToEvent
import com.antgskds.calendarassistant.core.capsule.CapsuleStateManager
import com.antgskds.calendarassistant.core.quickmemo.QuickMemoSuggestionCodec
import com.antgskds.calendarassistant.core.quickmemo.QuickMemoSuggestionStatus
import com.antgskds.calendarassistant.service.notification.NotificationIds
import com.antgskds.calendarassistant.data.model.ScheduleDisplayItem.ActionTarget
import com.antgskds.calendarassistant.calendar.models.EventTags
import com.antgskds.calendarassistant.calendar.models.isCompleted
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 事件动作接收器：处理通知上的「完成」「签到」按钮。
 * 统一通过 ActionTarget 路由到 ScheduleCenter 新 API。
 */
class EventActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_COMPLETE = "com.antgskds.calendarassistant.action.COMPLETE"
        const val ACTION_COMPLETE_SCHEDULE = "com.antgskds.calendarassistant.action.COMPLETE_SCHEDULE"
        const val ACTION_CHECKIN = "com.antgskds.calendarassistant.action.CHECKIN"
        const val ACTION_CREATE_QUICK_MEMO_SUGGESTION = "com.antgskds.calendarassistant.action.CREATE_QUICK_MEMO_SUGGESTION"
        const val EXTRA_EVENT_ID = "event_id"
        const val EXTRA_SUGGESTION_ID = "suggestion_id"
        private const val RECURRING_INSTANCE_PREFIX = "rec:"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as App
        val scheduleCenter = app.scheduleCenter
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        when (intent.action) {
            ACTION_CREATE_QUICK_MEMO_SUGGESTION -> {
                val suggestionId = intent.getLongExtra(EXTRA_SUGGESTION_ID, -1L).takeIf { it > 0L } ?: return
                val pendingResult = goAsync()
                scope.launch {
                    try {
                        val suggestion = app.quickMemoCenter.getSuggestion(suggestionId) ?: return@launch
                        if (suggestion.status != QuickMemoSuggestionStatus.PENDING) return@launch
                        val draft = QuickMemoSuggestionCodec.decode(suggestion.candidateJson) ?: return@launch
                        val settings = app.settingsQueryApi.settings.value
                        val event = convertDraftToEvent(
                            draft = draft,
                            defaultDurationMinutes = settings.defaultEventDurationMinutes,
                            forceInstantCodeTimeToNow = settings.forceInstantCodeTimeToNow
                        )
                        val eventId = scheduleCenter.addEvent(event)
                        app.quickMemoCenter.markSuggestionCreated(suggestionId, eventId)
                        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        manager.cancel(NotificationIds.quickMemoSuggestion(suggestionId))
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            ACTION_COMPLETE, ACTION_COMPLETE_SCHEDULE, ACTION_CHECKIN -> {
                val eventIdStr = intent.getStringExtra(EXTRA_EVENT_ID) ?: return
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
                        } else if (eventIdStr.startsWith(RECURRING_INSTANCE_PREFIX)) {
                            val target = parseRecurringTarget(eventIdStr) ?: return@launch
                            when (intent.action) {
                                ACTION_CHECKIN -> scheduleCenter.checkInItem(target)
                                else -> scheduleCenter.completeItem(target)
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

    private fun parseRecurringTarget(eventId: String): ActionTarget.RecurringOccurrence? {
        val parts = eventId.split(':')
        val parentId = parts.getOrNull(1)?.toLongOrNull() ?: return null
        val occurrenceTs = parts.getOrNull(2)?.toLongOrNull() ?: return null
        return ActionTarget.RecurringOccurrence(parentId, occurrenceTs)
    }
}
