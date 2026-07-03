package com.antgskds.calendarassistant.platform.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.antgskds.calendarassistant.App
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ReminderReconcileReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RECONCILE_REMINDERS) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as? App
                app?.reminderCenter?.reconcileAllNow()
            } catch (e: Exception) {
                Log.e(TAG, "Reminder reconcile failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "ReminderReconcile"
        private const val ACTION_RECONCILE_REMINDERS = "com.antgskds.calendarassistant.ACTION_RECONCILE_REMINDERS"
        private const val REQUEST_CODE = 9301
        private const val FIRST_DELAY_MS = 5 * 60 * 1000L

        fun schedule(context: Context) {
            val appContext = context.applicationContext
            val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pendingIntent = PendingIntent.getBroadcast(
                appContext,
                REQUEST_CODE,
                Intent(appContext, ReminderReconcileReceiver::class.java).apply {
                    action = ACTION_RECONCILE_REMINDERS
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val triggerAt = SystemClock.elapsedRealtime() + FIRST_DELAY_MS
            try {
                alarmManager.setInexactRepeating(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    AlarmManager.INTERVAL_FIFTEEN_MINUTES,
                    pendingIntent
                )
                Log.d(TAG, "Reminder reconcile scheduled every 15 minutes")
            } catch (e: SecurityException) {
                Log.e(TAG, "Missing permission for reminder reconcile", e)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Alarm quota reached, skip reminder reconcile schedule", e)
            }
            requestNow(appContext)
        }

        fun requestNow(context: Context) {
            val appContext = context.applicationContext
            appContext.sendBroadcast(
                Intent(appContext, ReminderReconcileReceiver::class.java).apply {
                    action = ACTION_RECONCILE_REMINDERS
                }
            )
        }
    }
}
