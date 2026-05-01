package com.antgskds.calendarassistant.service.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.core.center.ScheduleDisplayHelper
import com.antgskds.calendarassistant.core.query.AlarmRoute
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.*
import com.antgskds.calendarassistant.data.state.CapsuleUiState
import com.antgskds.calendarassistant.service.capsule.miui.MiuiIslandManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

/**
 * 广播接收器：AlarmReceiver
 *
 * 职责：接收 AlarmManager 的定时广播，并分流处理：
 * 1. 普通提醒 -> 直接发送 Notification
 * 2. 胶囊开始 -> 刷新胶囊状态 (forceRefresh)
 * 3. 胶囊结束 -> 刷新胶囊状态 (forceRefresh)
 *
 * 安全加固：在处理任何操作前，先验证事件是否仍存在于数据源中
 */
class AlarmReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "AlarmReceiver"
        private const val RECURRING_INSTANCE_PREFIX = "rec:"
        private const val EXTRA_EVENT_PARENT_ID = "EVENT_PARENT_ID"
        private const val EXTRA_EVENT_OCCURRENCE_TS = "EVENT_OCCURRENCE_TS"

        @JvmStatic
        internal fun showStandardNotification(context: Context, event: Event, label: String = "日程开始") {
            val app = context.applicationContext as App
            app.notificationCenter.showStandardNotificationForEvent(event = event, label = label)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        // ✅ 1. 获取 PendingResult，告诉系统"我还有异步任务要做，别杀我"
        val pendingResult = goAsync()

        // ✅ 2. 在协程中处理业务
        CoroutineScope(Dispatchers.Main).launch {
            try {
                handleReceiveAsync(context, intent)
            } catch (e: Exception) {
                Log.e(TAG, "AlarmReceiver error", e)
            } finally {
                // ✅ 3. 必须在 finally 中调用 finish()，否则会导致 ANR
                pendingResult.finish()
            }
        }
    }

    /**
     * 将原来的逻辑封装到 suspend 函数中
     */
    private suspend fun handleReceiveAsync(context: Context, intent: Intent) {
        val action = intent.action
        val eventId = intent.getStringExtra("EVENT_ID")
        val app = context.applicationContext as App

        if (eventId == null) {
            Log.w(TAG, "收到广播但 EVENT_ID 为空，忽略处理")
            return
        }

        // 安全加固：第一道防线 - 检查事件是否还存在
        // 如果事件已被删除，直接返回，不进行任何后续操作
        if (!isEventStillValid(context, intent, eventId)) {
            Log.i(TAG, "事件 $eventId 已不存在，跳过通知/服务启动")
            return
        }

        val eventTitle = intent.getStringExtra("EVENT_TITLE") ?: "日程提醒"
        val eventRuleId = intent.getStringExtra("EVENT_RULE_ID") ?: ""

        val routeDecision = app.alarmRoutingQueryApi.resolveRoute(action)
        if (routeDecision.fromUnknownAction) {
            Log.w(TAG, "未知的 action: $action，按普通提醒处理")
        }

        when (routeDecision.route) {
            AlarmRoute.CAPSULE_START -> {
                handleCapsuleStart(context, intent, eventId, eventTitle, eventRuleId)
            }
            AlarmRoute.CAPSULE_END -> {
                handleCapsuleEnd(context, eventId, eventRuleId)
            }
            AlarmRoute.CAPSULE_REFRESH -> {
                handleCapsuleRefresh(context, eventId, eventTitle, eventRuleId)
            }
            AlarmRoute.REMINDER -> {
                if (!app.reminderCenter.isStandardNotificationMode(app.capsuleRoutingQueryApi)) {
                    Log.d(TAG, "胶囊开启，跳过普通提醒: $eventId")
                    return
                }
                val reminderLabel = intent.getStringExtra("REMINDER_LABEL") ?: ""
                val eventLocation = intent.getStringExtra("EVENT_LOCATION") ?: ""
                val eventStartTime = intent.getStringExtra("EVENT_START_TIME") ?: ""
                val eventEndTime = intent.getStringExtra("EVENT_END_TIME") ?: ""
                val eventTag = intent.getStringExtra("EVENT_TAG") ?: ""
                val eventColor = intent.getIntExtra("EVENT_COLOR", 0)
                app.notificationCenter.showStandardNotification(
                    eventId = eventId,
                    title = eventTitle,
                    label = reminderLabel,
                    eventLocation, eventStartTime, eventEndTime, eventTag, eventColor,
                    eventRuleId.ifEmpty { null }
                )
            }
        }
    }

    /**
     * 检查事件是否仍然存在于数据源中
     *
     * 作为安全防线，防止已删除事件的闹钟仍触发通知
     *
     * @param context 上下文
     * @param eventId 事件ID
     * @return true 如果事件仍存在，false 如果事件已被删除
     */
    private suspend fun isEventStillValid(context: Context, intent: Intent, eventId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            if (eventId.startsWith(RECURRING_INSTANCE_PREFIX)) {
                return@withContext isRecurringInstanceStillValid(context, intent, eventId)
            }

            val app = context.applicationContext as App
            val numericId = eventId.toLongOrNull() ?: return@withContext true
            val event = app.calendarCenter.getEvent(numericId)
            val isValid = event != null && event.archivedAt == null && !event.isCompleted
            if (!isValid) Log.w(TAG, "事件验证失败: eventId=$eventId 不存在或已失效")
            isValid
        } catch (e: Exception) {
            Log.e(TAG, "检查事件存在性时出错: ${e.message}", e)
            // 出错时默认返回 true，避免误杀正常通知
            true
        }
    }

    private fun isRecurringInstanceStillValid(context: Context, intent: Intent, eventId: String): Boolean {
        val app = context.applicationContext as App
        val events = app.calendarCenter.getEvents().filter { it.archivedAt == null }
        if (events.isEmpty()) {
            Log.w(TAG, "事件列表为空，重复实例无效: eventId=$eventId")
            return false
        }

        val parentId = intent.getLongExtra(EXTRA_EVENT_PARENT_ID, parseRecurringParentId(eventId))
        val occurrenceTs = intent.getLongExtra(EXTRA_EVENT_OCCURRENCE_TS, parseRecurringOccurrenceTs(eventId))
        if (parentId <= 0L || occurrenceTs <= 0L) return true

        val parentExists = events.any { it.id == parentId && it.archivedAt == null }
        if (!parentExists) return false

        val occurrenceDate = Instant.ofEpochSecond(occurrenceTs)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        return ScheduleDisplayHelper.buildDisplayItems(events, occurrenceDate, occurrenceDate)
            .any { it.stableKey == eventId }
    }

    private fun parseRecurringParentId(eventId: String): Long {
        return eventId.split(':').getOrNull(1)?.toLongOrNull() ?: 0L
    }

    private fun parseRecurringOccurrenceTs(eventId: String): Long {
        return eventId.split(':').getOrNull(2)?.toLongOrNull() ?: 0L
    }

    private fun handleCapsuleStart(context: Context, intent: Intent, eventId: String, title: String, eventRuleId: String) {
        val app = context.applicationContext as App
        app.reminderCenter.routeByCapsuleMode(
            capsuleRoutingQueryApi = app.capsuleRoutingQueryApi,
            onMiuiIsland = {
                Log.d(TAG, "MIUI 岛模式，刷新胶囊状态: $title")
                app.reminderCenter.refreshCapsuleState()
            },
            onLiveCapsule = {
                Log.d(TAG, "启动胶囊: $title (刷新状态)")
                app.reminderCenter.refreshCapsuleState()

                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val hasPermission = notificationManager.areNotificationsEnabled()

                if (hasPermission) {
                    Log.i(TAG, "胶囊已启动，有通知权限 -> 播放提示音")
                    playAlert(context)
                } else {
                    Log.i(TAG, "胶囊已启动，但无通知权限 -> 保持静默")
                }
            },
            onStandardNotification = {
                Log.d(TAG, "跳过实况胶囊 (开关:false)")
                app.notificationCenter.showStandardNotification(
                    eventId = eventId,
                    title = title,
                    label = "日程开始",
                    eventLocation = intent.getStringExtra("EVENT_LOCATION") ?: "",
                    eventStartTime = intent.getStringExtra("EVENT_START_TIME") ?: "",
                    eventEndTime = intent.getStringExtra("EVENT_END_TIME") ?: "",
                    eventTag = intent.getStringExtra("EVENT_TAG") ?: "",
                    eventColor = intent.getIntExtra("EVENT_COLOR", 0),
                    eventRuleId = eventRuleId.ifEmpty { null }
                )
            }
        )
    }

    /**
     * 处理胶囊刷新（准点时刷新文案从"还有x分钟"改为"进行中"）
     * 新架构：Dumb Service 只需要重新启动，会自动重新订阅 uiState 并更新胶囊显示
     */
    private fun handleCapsuleRefresh(context: Context, eventId: String, title: String, eventRuleId: String) {
        val app = context.applicationContext as App
        if (app.reminderCenter.isMiuiIslandMode(app.capsuleRoutingQueryApi)) {
            Log.d(TAG, "MIUI 岛模式，刷新胶囊状态: $title")
            app.reminderCenter.refreshCapsuleState()
            when (val state = app.capsuleCenter.currentState()) {
                is CapsuleUiState.Active -> MiuiIslandManager.update(context, state.capsules)
                is CapsuleUiState.None -> MiuiIslandManager.clear(context)
            }
            return
        }
        Log.d(TAG, "刷新胶囊: $title (准点时刷新文案)")
        app.reminderCenter.refreshCapsuleState()
    }

    private fun handleCapsuleEnd(context: Context, eventId: String, eventRuleId: String) {
        val app = context.applicationContext as App
        if (app.reminderCenter.isMiuiIslandMode(app.capsuleRoutingQueryApi)) {
            Log.d(TAG, "MIUI 岛模式，结束胶囊刷新")
            app.reminderCenter.refreshCapsuleState()
            return
        }
        app.reminderCenter.refreshCapsuleState()
    }

    private fun playAlert(context: Context) {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context, uri)
            ringtone?.play()

            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (vibrator.hasVibrator()) {
                val timing = longArrayOf(0, 200, 100, 200)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(timing, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(timing, -1)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "播放替身提示音失败", e)
        }
    }
}
