package com.antgskds.calendarassistant.platform.notification.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationFailureReason
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationKey
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationResult
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationState
import com.antgskds.calendarassistant.feature.api.notification.ports.SystemAlarmGateway
import com.antgskds.calendarassistant.platform.notification.receiver.NotificationAlarmReceiver

class AndroidSystemAlarmGateway(context: Context) : SystemAlarmGateway {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override suspend fun schedule(
        key: NotificationKey,
        triggerAtEpochMillis: Long,
        allowWhileIdle: Boolean
    ): NotificationResult {
        val pendingIntent = createSchedulePendingIntent(key)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && allowWhileIdle) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtEpochMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtEpochMillis, pendingIntent)
            }
            NotificationResult.Success(key, NotificationState.SCHEDULED)
        } catch (error: SecurityException) {
            Log.e(TAG, "Missing alarm permission for notification key=${key.value}", error)
            NotificationResult.Failure(key, NotificationFailureReason.SCHEDULE_FAILED, error.message, error)
        } catch (error: IllegalStateException) {
            Log.e(TAG, "Failed to schedule notification alarm key=${key.value}", error)
            NotificationResult.Failure(key, NotificationFailureReason.SCHEDULE_FAILED, error.message, error)
        }
    }

    override suspend fun cancel(key: NotificationKey): NotificationResult {
        val pendingIntent = createPendingIntent(key, PendingIntent.FLAG_NO_CREATE)
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
        return NotificationResult.Success(key, NotificationState.CANCELLED)
    }

    private fun createSchedulePendingIntent(key: NotificationKey): PendingIntent {
        return PendingIntent.getBroadcast(
            appContext,
            requestCodeFor(key),
            createIntent(key),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createPendingIntent(key: NotificationKey, lookupFlag: Int): PendingIntent? {
        return PendingIntent.getBroadcast(
            appContext,
            requestCodeFor(key),
            createIntent(key),
            lookupFlag or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createIntent(key: NotificationKey): Intent {
        val intent = Intent(appContext, NotificationAlarmReceiver::class.java).apply {
            action = ACTION_TRIGGER_NOTIFICATION
            putExtra(EXTRA_NOTIFICATION_KEY, key.value)
        }
        return intent
    }

    private fun requestCodeFor(key: NotificationKey): Int {
        return key.value.hashCode() and Int.MAX_VALUE
    }

    companion object {
        private const val TAG = "NotificationAlarmGateway"
        const val ACTION_TRIGGER_NOTIFICATION = "com.antgskds.calendarassistant.notification.TRIGGER"
        const val EXTRA_NOTIFICATION_KEY = "extra_notification_key"
    }
}
