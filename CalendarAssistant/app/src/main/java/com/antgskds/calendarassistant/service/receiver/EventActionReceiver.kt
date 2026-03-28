package com.antgskds.calendarassistant.service.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.core.capsule.CapsuleStateManager
import com.antgskds.calendarassistant.core.rule.RuleMatchingEngine
import com.antgskds.calendarassistant.core.util.OsUtils
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.ui.components.UniversalToastUtil
import com.antgskds.calendarassistant.xposed.XposedModuleStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.format.DateTimeFormatter

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
        private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
    }

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID)
        val repository = (context.applicationContext as App).repository
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        when (intent.action) {
            ACTION_COMPLETE, ACTION_COMPLETE_SCHEDULE, ACTION_CHECKIN -> {
                val pendingResult = goAsync()
                scope.launch {
                    try {
                        if (eventId == CapsuleStateManager.AGGREGATE_PICKUP_ID) {
                            completeAllActivePickups(repository, context)
                        } else {
                            val targetEventId = eventId ?: return@launch
                            repository.performPrimaryRuleAction(targetEventId)
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
        repository: com.antgskds.calendarassistant.data.repository.AppRepository,
        context: Context
    ) {
        val now = java.time.LocalDateTime.now()
        val settings = repository.settings.value

        // 获取所有取件码类型的活跃事件
        val activePickups = repository.events.value.filter { event ->
            isAggregateActivePickup(event, settings, now)
        }

        // 批量完成所有活跃取件码
        activePickups.forEach { event ->
            repository.completeScheduleEvent(event.id)
        }

        // 取消聚合胶囊的通知
        val nm = NotificationManagerCompat.from(context)
        nm.cancel(CapsuleStateManager.AGGREGATE_NOTIF_ID)

        // 显示删除数量
        if (activePickups.isNotEmpty()) {
            withContext(Dispatchers.Main) {
                UniversalToastUtil.showSuccess(context, "已完成 ${activePickups.size} 个取件码")
            }
        }

        // 主动触发胶囊状态刷新
        repository.capsuleStateManager.forceRefresh()
    }

    private fun isAggregateActivePickup(
        event: MyEvent,
        settings: MySettings,
        now: java.time.LocalDateTime
    ): Boolean {
        val ruleId = RuleMatchingEngine.resolvePayload(event)?.ruleId
            ?: RuleMatchingEngine.RULE_GENERAL
        if (ruleId != RuleMatchingEngine.RULE_PICKUP || event.isCompleted || event.isRecurringParent) {
            return false
        }

        return try {
            val startDateTime = java.time.LocalDateTime.of(
                event.startDate,
                LocalTime.parse(event.startTime, TIME_FORMATTER)
            )
            val endDateTime = java.time.LocalDateTime.of(
                event.endDate,
                LocalTime.parse(event.endTime, TIME_FORMATTER)
            )
            val effectiveStartTime = if (settings.isAdvanceReminderEnabled && settings.advanceReminderMinutes > 0) {
                startDateTime.minusMinutes(settings.advanceReminderMinutes.toLong())
            } else {
                startDateTime.minusMinutes(1)
            }

            now.isBefore(endDateTime) && !now.isBefore(effectiveStartTime)
        } catch (e: Exception) {
            Log.e("EventActionReceiver", "解析聚合取件时间失败: ${event.id}", e)
            false
        }
    }

    private fun isMiuiIslandMode(context: Context): Boolean {
        return try {
            val settings = (context.applicationContext as App).repository.settings.value
            settings.isLiveCapsuleEnabled && OsUtils.isHyperOS() && XposedModuleStatus.isActive()
        } catch (_: Exception) {
            false
        }
    }
}
