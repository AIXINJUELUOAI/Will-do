package com.antgskds.calendarassistant.service.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.core.util.AccessibilityGuardian
import com.antgskds.calendarassistant.core.weather.WeatherSyncWorker
import com.antgskds.calendarassistant.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Log.d("BootReceiver", "System/package restore trigger received, rescheduling alarms...")

            val app = context.applicationContext as App

            // 1. 恢复数据相关的闹钟 (StoreRootNode 内部会调 NotificationScheduler)
            app.scheduleCenter.refreshAndScheduleAll()

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
