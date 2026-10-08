package com.antgskds.calendarassistant.platform.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.antgskds.calendarassistant.shared.util.AppLogger as Log
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.feature.recognition.application.ai.convertDraftToEvent
import com.antgskds.calendarassistant.feature.capsule.application.CapsuleStateManager
import com.antgskds.calendarassistant.feature.quickmemo.data.serialization.QuickMemoSuggestionCodec
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoSuggestionStatus
import com.antgskds.calendarassistant.platform.accessibility.TextAccessibilityService
import com.antgskds.calendarassistant.platform.floating.EdgeBarService
import com.antgskds.calendarassistant.platform.floating.FloatingScheduleService
import com.antgskds.calendarassistant.platform.floating.QuickMemoVoiceCaptureService
import com.antgskds.calendarassistant.platform.notification.alarmlegacy.NotificationIds
import com.antgskds.calendarassistant.feature.schedule.presentation.model.ScheduleDisplayItem.ActionTarget
import com.antgskds.calendarassistant.feature.schedule.domain.model.EventTags
import com.antgskds.calendarassistant.feature.schedule.domain.model.isCompleted
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import com.antgskds.calendarassistant.feature.accounting.domain.AccountingDuplicateAction
import com.antgskds.calendarassistant.feature.accounting.application.AccountingDuplicateConfirmation
import com.antgskds.calendarassistant.shared.ui.material.component.UniversalToastUtil

/**
 * 事件动作接收器：处理通知上的「完成」「签到」按钮。
 * 统一通过 ActionTarget 路由到 ScheduleFacade 新 API。
 */
class EventActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_CLEAR_IMAGE_PIN = "com.antgskds.calendarassistant.action.CLEAR_IMAGE_PIN"
        const val ACTION_COMPLETE = "com.antgskds.calendarassistant.action.COMPLETE"
        const val ACTION_COMPLETE_SCHEDULE = "com.antgskds.calendarassistant.action.COMPLETE_SCHEDULE"
        const val ACTION_CHECKIN = "com.antgskds.calendarassistant.action.CHECKIN"
        const val ACTION_CREATE_QUICK_MEMO_SUGGESTION = "com.antgskds.calendarassistant.action.CREATE_QUICK_MEMO_SUGGESTION"
        const val ACTION_CLEAR_TEXT_QUICK_MEMO = "com.antgskds.calendarassistant.action.CLEAR_TEXT_QUICK_MEMO"
        const val ACTION_CLEAR_QUICK_MEMO_REMINDER = "com.antgskds.calendarassistant.action.CLEAR_QUICK_MEMO_REMINDER"
        const val EXTRA_QUICK_MEMO_REMINDER_ID = "quick_memo_reminder_id"
        const val ACTION_STOP_QUICK_MEMO_RECORDING = "com.antgskds.calendarassistant.action.STOP_QUICK_MEMO_RECORDING"
        const val EXTRA_ACCOUNTING_TASK_ID = "accounting_task_id"
        const val ACTION_CANCEL_RECOGNITION = "com.antgskds.calendarassistant.action.CANCEL_RECOGNITION"
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
            com.antgskds.calendarassistant.feature.recognition.ingest.clipboard.ClipboardPromptAction.RECEIVER_ACTION -> {
                val value = intent.getStringExtra(
                    com.antgskds.calendarassistant.feature.recognition.ingest.clipboard.ClipboardPromptAction.EXTRA_KEY
                )?.takeIf(String::isNotBlank) ?: return
                val pending = goAsync()
                scope.launch {
                    try {
                        val message = app.clipboardCodeCenter.acceptPrompt(
                            com.antgskds.calendarassistant.feature.notification.model.NotificationKey(value)
                        )
                        withContext(Dispatchers.Main) { message?.let { UniversalToastUtil.showInfo(context, it) } }
                    } catch (error: Exception) {
                        if (error is kotlinx.coroutines.CancellationException) throw error
                        Log.e(TAG, "clipboard save failed error_type=${error.javaClass.simpleName}")
                        withContext(Dispatchers.Main) { UniversalToastUtil.showError(context, "保存失败，请重试") }
                    } finally { pending.finish() }
                }
            }
            AccountingDuplicateAction.RECEIVER_ACTION -> {
                val ids = AccountingDuplicateAction.draftIds(intent.getStringExtra(AccountingDuplicateAction.EXTRA_DRAFT_IDS))
                if (ids.isEmpty()) return
                val pending = goAsync()
                scope.launch {
                    try {
                        val saved = AccountingDuplicateConfirmation.confirm(ids,
                            readDrafts = { app.accountingApi.drafts.first() },
                            confirmDraft = app.ingestCommandApi::confirmAccountingDraft)
                        withContext(Dispatchers.Main) {
                            if (saved > 0) UniversalToastUtil.showSuccess(context, "已计入 $saved 笔账单")
                            else UniversalToastUtil.showInfo(context, "账单已处理或需要核对，请查看记账页")
                        }
                    } catch (error: Exception) {
                        if (error is kotlinx.coroutines.CancellationException) throw error
                        Log.e(TAG, "accounting duplicate confirmation failed", error)
                        withContext(Dispatchers.Main) {
                            UniversalToastUtil.showError(context, "部分账单未能入库，请在记账页核对")
                        }
                    } finally { pending.finish() }
                }
            }
            ACTION_CLEAR_IMAGE_PIN -> {
                val id = intent.getLongExtra("image_pin_id", -1L).takeIf { it > 0 } ?: return
                val pending = goAsync()
                scope.launch {
                    try { app.imagePinController.clear(id) }
                    catch (error: Exception) { Log.e(TAG, "image pin clear failed", error) }
                    finally { pending.finish() }
                }
            }
            ACTION_CLEAR_QUICK_MEMO_REMINDER -> {
                val reminderId = intent.getLongExtra(EXTRA_QUICK_MEMO_REMINDER_ID, -1L).takeIf { it > 0L } ?: return
                val pendingResult = goAsync()
                scope.launch {
                    try {
                        app.quickMemoCenter.dismissReminderCapsule(reminderId)
                        Log.i(TAG, "reminder capsule dismissed reminder=$reminderId")
                    } catch (error: Exception) {
                        Log.e(TAG, "reminder capsule dismiss failed reminder=$reminderId", error)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            ACTION_DEBUG_PRIMARY, ACTION_DEBUG_SECONDARY -> {
                Log.d(TAG, "debug notification action clicked action=${intent.action}")
            }
            ACTION_STOP_QUICK_MEMO_RECORDING -> {
                val quickMemoHandled = QuickMemoVoiceCaptureService.instance?.stopCaptureFromNotification() == true
                val edgeHandled = EdgeBarService.instance?.stopVoiceCaptureFromNotification() == true
                if (FloatingScheduleService.isShowing) {
                    runCatching {
                        context.startService(Intent(context, FloatingScheduleService::class.java).apply {
                            action = FloatingScheduleService.ACTION_STOP_VOICE_CAPTURE
                        })
                    }.onFailure { t ->
                        Log.w(TAG, "floating voice recording stop dispatch failed", t)
                    }
                }
                if (!quickMemoHandled && !edgeHandled && !FloatingScheduleService.isShowing) {
                    app.capsuleCommandApi.clearQuickMemoRecording()
                    Log.w(TAG, "quick memo recording stop ignored: no active recording service")
                }
            }
            ACTION_CANCEL_RECOGNITION -> {
                val service = TextAccessibilityService.instance
                if (service != null) {
                    val taskId = intent.getStringExtra(EXTRA_ACCOUNTING_TASK_ID)
                    if (taskId == null) service.cancelCurrentAnalysis() else service.cancelAutomaticTask(taskId)
                    Log.d(TAG, "recognition analysis cancelled from live capsule")
                } else {
                    app.capsuleCommandApi.clearOcrCapsule()
                    app.capsuleCommandApi.clearModelLoading()
                    Log.w(TAG, "recognition cancel ignored: accessibility service is not connected")
                }
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
