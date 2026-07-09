package com.antgskds.calendarassistant.platform.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.core.ai.convertDraftToEvent
import com.antgskds.calendarassistant.core.capsule.CapsuleStateManager
import com.antgskds.calendarassistant.core.quickmemo.QuickMemoSuggestionCodec
import com.antgskds.calendarassistant.core.quickmemo.QuickMemoSuggestionStatus
import com.antgskds.calendarassistant.platform.notification.alarmlegacy.NotificationIds
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
        const val ACTION_CLEAR_TEXT_QUICK_MEMO = "com.antgskds.calendarassistant.action.CLEAR_TEXT_QUICK_MEMO"
        const val ACTION_DEBUG_PRIMARY = "com.antgskds.calendarassistant.action.DEBUG_PRIMARY"
        const val ACTION_DEBUG_SECONDARY = "com.antgskds.calendarassistant.action.DEBUG_SECONDARY"
        const val EXTRA_EVENT_ID = "event_id"
        const val EXTRA_SUGGESTION_ID = "suggestion_id"
        const val EXTRA_QUICK_MEMO_ID = "quick_memo_id"
        private const val RECURRING_INSTANCE_PREFIX = "rec:"
        private const val TAG = "EventActionReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as App
        val scheduleCenter = app.scheduleCenter
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        Log.d(TAG, "receive action=${intent.action} eventId=${intent.getStringExtra(EXTRA_EVENT_ID)}")

        when (intent.action) {
            ACTION_DEBUG_PRIMARY, ACTION_DEBUG_SECONDARY -> {
                Log.d(TAG, "debug notification action clicked action=${intent.action}")
            }
            ACTION_CLEAR_TEXT_QUICK_MEMO -> {
                val memoId = intent.getLongExtra(EXTRA_QUICK_MEMO_ID, -1L).takeIf { it > 0L }
                    ?: run {
                        Log.w(TAG, "ignore quick memo clear action: missing memo id")
                        return
                    }
                val pendingResult = goAsync()
                scope.launch {
                    try {
                        app.quickMemoCenter.clearPinnedTextQuickMemo(memoId)
                        Log.d(TAG, "text quick memo capsule cleared memoId=$memoId")
                    } catch (t: Throwable) {
                        Log.e(TAG, "text quick memo clear failed memoId=$memoId", t)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            ACTION_CREATE_QUICK_MEMO_SUGGESTION -> {
                val suggestionId = intent.getLongExtra(EXTRA_SUGGESTION_ID, -1L).takeIf { it > 0L }
                    ?: run {
                        Log.w(TAG, "ignore quick memo action: missing suggestion id")
                        return
                    }
                val pendingResult = goAsync()
                scope.launch {
                    try {
                        val suggestion = app.quickMemoCenter.getSuggestion(suggestionId) ?: run {
                            Log.w(TAG, "quick memo suggestion not found: $suggestionId")
                            return@launch
                        }
                        if (suggestion.status != QuickMemoSuggestionStatus.PENDING) {
                            Log.d(TAG, "quick memo suggestion ignored: id=$suggestionId status=${suggestion.status}")
                            return@launch
                        }
                        val draft = QuickMemoSuggestionCodec.decode(suggestion.candidateJson) ?: run {
                            Log.w(TAG, "quick memo suggestion decode failed: $suggestionId")
                            return@launch
                        }
                        val settings = app.settingsQueryApi.settings.value
                        val event = convertDraftToEvent(
                            draft = draft,
                            defaultDurationMinutes = settings.defaultEventDurationMinutes,
                            forceInstantCodeTimeToNow = settings.forceInstantCodeTimeToNow,
                            eventColorPaletteHex = settings.eventColorPaletteHex
                        )
                        val eventId = scheduleCenter.addEvent(event)
                        app.quickMemoCenter.markSuggestionCreated(suggestionId, eventId)
                        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        manager.cancel(NotificationIds.quickMemoSuggestion(suggestionId))
                        Log.d(TAG, "quick memo suggestion created eventId=$eventId suggestionId=$suggestionId")
                    } catch (t: Throwable) {
                        Log.e(TAG, "quick memo action failed: suggestionId=$suggestionId", t)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            ACTION_COMPLETE, ACTION_COMPLETE_SCHEDULE, ACTION_CHECKIN -> {
                val eventIdStr = intent.getStringExtra(EXTRA_EVENT_ID) ?: run {
                    Log.w(TAG, "ignore event action: missing event id action=${intent.action}")
                    return
                }
                val pendingResult = goAsync()
                scope.launch {
                    try {
                        if (eventIdStr == CapsuleStateManager.AGGREGATE_PICKUP_ID) {
                            // 聚合取件完成：完成所有活跃的取件事件
                            val pickups = scheduleCenter.events.value.filter {
                                it.tag in setOf(EventTags.PICKUP, EventTags.FOOD, EventTags.TICKET, EventTags.SENDER) && !it.isCompleted
                            }
                            pickups.forEach { event ->
                                val id = event.id ?: return@forEach
                                scheduleCenter.completeItem(ActionTarget.Single(id))
                            }
                            Log.d(TAG, "aggregate pickup action completed count=${pickups.size}")
                        } else if (eventIdStr.startsWith(RECURRING_INSTANCE_PREFIX)) {
                            val target = parseRecurringTarget(eventIdStr) ?: run {
                                Log.w(TAG, "ignore event action: invalid recurring id=$eventIdStr")
                                return@launch
                            }
                            when (intent.action) {
                                ACTION_CHECKIN -> scheduleCenter.checkInItem(target)
                                else -> scheduleCenter.completeItem(target)
                            }
                            Log.d(TAG, "event action applied recurring=$eventIdStr action=${intent.action}")
                        } else {
                            val targetEventId = eventIdStr.toLongOrNull() ?: run {
                                Log.w(TAG, "ignore event action: invalid event id=$eventIdStr")
                                return@launch
                            }
                            val event = scheduleCenter.events.value.find { it.id == targetEventId } ?: run {
                                Log.w(TAG, "ignore event action: event not found id=$targetEventId")
                                return@launch
                            }
                            val target = ActionTarget.Single(targetEventId)

                            when (intent.action) {
                                ACTION_CHECKIN -> scheduleCenter.checkInItem(target)
                                else -> scheduleCenter.completeItem(target)
                            }
                            Log.d(TAG, "event action applied id=$targetEventId title=${event.title} action=${intent.action}")
                        }
                    } catch (t: Throwable) {
                        Log.e(TAG, "event action failed action=${intent.action} eventId=$eventIdStr", t)
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
