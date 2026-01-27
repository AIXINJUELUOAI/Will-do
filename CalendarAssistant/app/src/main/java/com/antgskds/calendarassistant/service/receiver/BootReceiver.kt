package com.antgskds.calendarassistant.service.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.antgskds.calendarassistant.data.repository.AppRepository

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Boot completed, rescheduling alarms...")

            // 1. 恢复数据相关的闹钟 (AppRepository 内部会调 NotificationScheduler)
            val repository = AppRepository.getInstance(context)
            repository.loadAndScheduleAll()

            // 2. 恢复早晚报调度
            DailySummaryReceiver.schedule(context)
        }
    }
}