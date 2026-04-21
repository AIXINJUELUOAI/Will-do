package com.antgskds.calendarassistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.antgskds.calendarassistant.core.calendar.CalendarReverseSyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 日历同步 BroadcastReceiver
 * 由 AlarmManager 定期触发，执行反向同步
 */
class CalendarSyncReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "CalendarSyncReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "收到定期同步广播")

        CalendarReverseSyncScheduler.schedule(context)

        val syncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val syncCenter = (context.applicationContext as App).syncCenter

        syncScope.launch {
            try {
                val result = syncCenter.syncFromCalendar()
                if (result.isSuccess) {
                    val count = result.getOrNull() ?: 0
                    if (count > 0) {
                        Log.d(TAG, "定期反向同步成功：从系统日历同步了 $count 个事件")
                    }
                } else {
                    Log.w(TAG, "定期反向同步失败：${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "定期反向同步异常", e)
            }
        }
    }
}
