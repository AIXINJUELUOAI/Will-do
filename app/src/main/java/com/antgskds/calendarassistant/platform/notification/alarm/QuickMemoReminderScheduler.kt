package com.antgskds.calendarassistant.platform.notification.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.antgskds.calendarassistant.App
import android.os.Build
import com.antgskds.calendarassistant.shared.util.AppLogger as Log
import com.antgskds.calendarassistant.platform.notification.alarmlegacy.NotificationIds
import com.antgskds.calendarassistant.platform.receiver.QuickMemoReminderReceiver

class QuickMemoReminderScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private val prefs = appContext.getSharedPreferences("quick_memo_alarm_occurrences", Context.MODE_PRIVATE)
    private val window get() = (appContext as App).reminderWindowStore

    fun scheduledIds(): Set<Long> = prefs.all.keys.mapNotNull { it.toLongOrNull() }.toSet()

    private fun scheduledTimes(reminderId: Long): Set<Long> =
        prefs.getStringSet(reminderId.toString(), emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()

    fun reconcile(reminderId: Long, memoId: Long, triggers: Map<Long, Long>): Boolean {
        cancelLegacy(reminderId)
        val desired = triggers.keys
        val previous = scheduledTimes(reminderId)
        (previous - desired).forEach { cancelOccurrence(reminderId, it) }
        val retained = (previous intersect desired).toMutableSet()
        if (window.canRegister) triggers.forEach { (at, occurrenceAt) ->
            if (schedule(reminderId, memoId, at, occurrenceAt)) retained.add(at)
        }
        saveTimes(reminderId, retained)
        return retained.isNotEmpty()
    }

    private fun saveTimes(reminderId: Long, times: Set<Long>) {
        val editor = prefs.edit()
        if (times.isEmpty()) editor.remove(reminderId.toString())
        else editor.putStringSet(reminderId.toString(), times.map { it.toString() }.toSet())
        editor.apply()
    }

    fun schedule(reminderId: Long, memoId: Long, triggerAtMillis: Long, occurrenceAt: Long = triggerAtMillis): Boolean {
        if (reminderId <= 0L || memoId <= 0L) return false
        if (!window.allows(triggerAtMillis)) return false
        val pendingIntent = occurrenceIntent(reminderId, memoId, triggerAtMillis, PendingIntent.FLAG_UPDATE_CURRENT, occurrenceAt) ?: return false
        Log.i(TAG, "schedule reminder=$reminderId memo=$memoId triggerAt=$triggerAtMillis exact=${alarmManager.canScheduleExactAlarms()}")
        val registered = registerQuickMemoReminderAlarm(
            preferred = {
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms() ->
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                    else -> alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
            },
            fallback = {
                Log.w(TAG, "Quick memo alarm permission unavailable; retrying with inexact alarm")
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            },
            onRejected = { error ->
                Log.w(TAG, "Quick memo alarm rejected reminder=$reminderId; retained for reconcile retry", error)
            },
        )
        if (registered) saveTimes(reminderId, scheduledTimes(reminderId) + triggerAtMillis)
        return registered
    }

    fun cancel(reminderId: Long) {
        if (reminderId <= 0L) return
        scheduledTimes(reminderId).forEach { cancelOccurrence(reminderId, it) }
        cancelLegacy(reminderId)
        saveTimes(reminderId, emptySet())
    }

    private fun cancelLegacy(reminderId: Long) {
        // Retire the single-PendingIntent identity used by previous versions even for active reminders.
        val legacy = PendingIntent.getBroadcast(
            appContext, NotificationIds.quickMemoReminder(reminderId),
            Intent(appContext, QuickMemoReminderReceiver::class.java).apply {
                action = QuickMemoReminderReceiver.ACTION_REMIND_QUICK_MEMO
            }, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        legacy?.let { alarmManager.cancel(it); it.cancel() }
    }

    private fun cancelOccurrence(reminderId: Long, at: Long) {
        occurrenceIntent(reminderId, -1L, at, PendingIntent.FLAG_NO_CREATE)?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    private fun occurrenceIntent(reminderId: Long, memoId: Long, at: Long, flag: Int, occurrenceAt: Long = at): PendingIntent? {
        val intent = Intent(appContext, QuickMemoReminderReceiver::class.java).apply {
            action = QuickMemoReminderReceiver.ACTION_REMIND_QUICK_MEMO
            data = Uri.parse("willdo://quick-memo-reminder/$reminderId/$at")
            putExtra(QuickMemoReminderReceiver.EXTRA_QUICK_MEMO_REMINDER_ID, reminderId)
            putExtra(QuickMemoReminderReceiver.EXTRA_QUICK_MEMO_ID, memoId)
            putExtra(QuickMemoReminderReceiver.EXTRA_TRIGGER_AT, occurrenceAt)
        }
        return PendingIntent.getBroadcast(
            appContext, NotificationIds.quickMemoReminder(reminderId), intent,
            PendingIntent.FLAG_IMMUTABLE or flag,
        )
    }

    private companion object {
        const val TAG = "QuickMemoReminder"
    }
}

// Both registration paths can be rejected by the shared system alarm quota.
internal fun registerQuickMemoReminderAlarm(
    preferred: () -> Unit,
    fallback: () -> Unit,
    onRejected: (RuntimeException) -> Unit,
): Boolean = try {
    try {
        preferred()
    } catch (_: SecurityException) {
        fallback()
    }
    true
} catch (error: SecurityException) {
    onRejected(error)
    false
} catch (error: IllegalStateException) {
    onRejected(error)
    false
}
