package com.antgskds.calendarassistant.calendar.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.antgskds.calendarassistant.core.center.CalendarCenter
import java.util.concurrent.Executors

class CalDAVSyncReceiver : BroadcastReceiver() {

    companion object {
        private val executor = Executors.newSingleThreadExecutor()
    }

    override fun onReceive(context: Context, intent: Intent?) {
        // ✅ 关键修复：在后台线程执行，避免主线程 Room IllegalStateException
        val pendingResult = goAsync() // 告诉系统我们还在工作，不要提前回收
        executor.execute {
            try {
                CalendarCenter.getInstance(context.applicationContext).onScheduledSyncTick()
                // ✅ 同步完成后刷新内存中的事件列表，让 UI 能看到导入的事件
                (context.applicationContext as? com.antgskds.calendarassistant.App)?.scheduleCenter?.refreshEvents()
            } catch (e: Exception) {
                android.util.Log.e("CalDAVSync", "onScheduledSyncTick failed", e)
            } finally {
                pendingResult.finish() // 通知系统工作完成
            }
        }
    }
}
