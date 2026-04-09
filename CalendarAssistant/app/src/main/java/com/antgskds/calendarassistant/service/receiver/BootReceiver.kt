package com.antgskds.calendarassistant.service.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.antgskds.calendarassistant.core.calendar.CalendarReverseSyncScheduler
import com.antgskds.calendarassistant.core.util.AccessibilityGuardian
import com.antgskds.calendarassistant.core.weather.WeatherSyncWorker
import com.antgskds.calendarassistant.data.repository.AppRepository
import com.antgskds.calendarassistant.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Log.d("BootReceiver", "System/package restore trigger received, rescheduling alarms...")

            // 1. 恢复数据相关的闹钟 (AppRepository 内部会调 NotificationScheduler)
            val repository = AppRepository.getInstance(context)
            repository.loadAndScheduleAll()

            // 2. 恢复早晚报调度
            DailySummaryReceiver.schedule(context)

            // 3. 恢复后台保活检查
            KeepAliveReceiver.schedule(context)

            // 4. 恢复定期反向同步
            CalendarReverseSyncScheduler.schedule(context)

            // 5. 恢复天气定时刷新
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
