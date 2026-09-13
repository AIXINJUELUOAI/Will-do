package com.antgskds.calendarassistant.platform.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.antgskds.calendarassistant.shared.util.AppLogger as Log
import com.antgskds.calendarassistant.App
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class QuickMemoReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REMIND_QUICK_MEMO) return
        val reminderId = intent.getLongExtra(EXTRA_QUICK_MEMO_REMINDER_ID, -1L).takeIf { it > 0L } ?: return
        Log.i(TAG, "received alarm reminder=$reminderId")
        val pendingResult = goAsync()
        val app = context.applicationContext as App
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                app.quickMemoCenter.deliverReminder(reminderId)
            } catch (error: Throwable) {
                Log.e(TAG, "Quick memo reminder failed reminderId=$reminderId", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_REMIND_QUICK_MEMO = "com.antgskds.calendarassistant.action.REMIND_QUICK_MEMO"
        const val EXTRA_QUICK_MEMO_REMINDER_ID = "quick_memo_reminder_id"
        const val EXTRA_QUICK_MEMO_ID = "quick_memo_id"
        private const val TAG = "QuickMemoReminder"
    }
}
