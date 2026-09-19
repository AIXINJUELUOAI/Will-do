package com.antgskds.calendarassistant.platform.notification.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.antgskds.calendarassistant.shared.util.AppLogger as Log
import com.antgskds.calendarassistant.platform.notification.alarmlegacy.NotificationIds
import com.antgskds.calendarassistant.platform.receiver.QuickMemoReminderReceiver

class QuickMemoReminderScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(reminderId: Long, memoId: Long, triggerAtMillis: Long) {
        if (reminderId <= 0L || memoId <= 0L) return
        cancel(reminderId)
        if (triggerAtMillis <= System.currentTimeMillis()) return
        val pendingIntent = pendingIntent(reminderId, memoId)
        Log.i(TAG, "schedule reminder=$reminderId memo=$memoId triggerAt=$triggerAtMillis exact=${alarmManager.canScheduleExactAlarms()}")
        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms() ->
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                else -> alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } catch (error: SecurityException) {
            Log.w(TAG, "Exact quick memo reminder unavailable; using inexact alarm", error)
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    fun cancel(reminderId: Long) {
        if (reminderId <= 0L) return
        Log.d(TAG, "cancel alarm reminder=$reminderId")
        alarmManager.cancel(pendingIntent(reminderId, -1L))
    }

    private fun pendingIntent(reminderId: Long, memoId: Long): PendingIntent {
        val intent = Intent(appContext, QuickMemoReminderReceiver::class.java).apply {
            action = QuickMemoReminderReceiver.ACTION_REMIND_QUICK_MEMO
            putExtra(QuickMemoReminderReceiver.EXTRA_QUICK_MEMO_REMINDER_ID, reminderId)
            putExtra(QuickMemoReminderReceiver.EXTRA_QUICK_MEMO_ID, memoId)
        }
        return PendingIntent.getBroadcast(
            appContext,
            NotificationIds.quickMemoReminder(reminderId),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private companion object {
        const val TAG = "QuickMemoReminder"
    }
}
