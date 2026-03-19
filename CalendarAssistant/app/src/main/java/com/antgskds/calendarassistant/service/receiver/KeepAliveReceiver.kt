package com.antgskds.calendarassistant.service.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.antgskds.calendarassistant.core.util.AccessibilityGuardian
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class KeepAliveReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_KEEP_ALIVE) return

        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                AccessibilityGuardian.restoreIfNeeded(
                    context,
                    isBackground = true
                )
            } catch (e: Exception) {
                Log.e(TAG, "KeepAlive error", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "KeepAliveReceiver"
        private const val ACTION_KEEP_ALIVE = "com.antgskds.calendarassistant.ACTION_KEEP_ALIVE"
        private const val REQUEST_CODE = 9201

        fun schedule(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, KeepAliveReceiver::class.java).apply {
                action = ACTION_KEEP_ALIVE
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val triggerAt = SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_HALF_HOUR
            alarmManager.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME,
                triggerAt,
                AlarmManager.INTERVAL_HALF_HOUR,
                pendingIntent
            )
            Log.d(TAG, "KeepAlive scheduled every 30 minutes")
        }
    }
}
