package com.antgskds.calendarassistant.platform.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.antgskds.calendarassistant.shared.util.AppLogger as Log
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.shared.util.AccessibilityGuardian
import com.antgskds.calendarassistant.feature.weather.domain.WeatherSyncWorker
import com.antgskds.calendarassistant.feature.settings.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Log.d("BootReceiver", "System/package restore trigger received, rescheduling alarms...")

            val app = context.applicationContext as App

            // 1. 恢复数据与提醒/胶囊闹钟
            app.scheduleCenter.refreshAll()
            app.reminderCenter.reconcileAll()
            // Phase 2 修复：重启会清空 AlarmManager，从持久化 Registry 重排新通知链路的系统闹钟。
            val pendingResult = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    try {
                        app.notificationCenter.rescheduleAllAlarms()
                    } catch (error: Throwable) {
                        Log.e("BootReceiver", "Failed to restore notification alarms", error)
                    }
                    try {
                        app.quickMemoCenter.rescheduleReminders()
                    } catch (error: Throwable) {
                        Log.e("BootReceiver", "Failed to restore quick memo reminders", error)
                    }
                } finally {
                    pendingResult.finish()
                }
            }
            app.widgetCenter.requestRefresh()

            // 2. 恢复运行时调度（早晚报/保活/反向同步/短信监听）
            app.runtimeCenter.restoreAfterBoot()

            // 3. 恢复天气定时刷新
            WeatherSyncWorker.syncForSettings(context, SettingsRepository(context).loadSettings())

            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            AccessibilityGuardian.checkAndRestoreIfNeeded(
                context,
                scope,
                isBackground = true
            )
        }
    }
}
