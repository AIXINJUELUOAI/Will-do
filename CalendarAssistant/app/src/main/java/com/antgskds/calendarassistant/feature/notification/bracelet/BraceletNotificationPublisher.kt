package com.antgskds.calendarassistant.feature.notification.bracelet

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.MainActivity
import com.antgskds.calendarassistant.R
import com.antgskds.calendarassistant.feature.notification.model.NotificationSnapshot
import com.antgskds.calendarassistant.feature.schedule.domain.model.Event
import com.antgskds.calendarassistant.feature.schedule.domain.model.isCheckedIn
import com.antgskds.calendarassistant.feature.schedule.domain.model.isCompleted
import com.antgskds.calendarassistant.platform.notification.alarmlegacy.NotificationIds
import com.antgskds.calendarassistant.shared.query.DailySummaryPayload

class BraceletNotificationPublisher(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun notifySchedule(snapshot: NotificationSnapshot): Boolean {
        if (!isEnabled()) return false
        val stableKey = snapshot.scheduleInstanceKey?.stableKey
            ?: snapshot.metadata["eventId"]?.let { "single:$it" }
            ?: snapshot.key.value
        val startTs = snapshot.metadata["startTS"].orEmpty()
        val event = findEvent(snapshot)
        val identityKey = "schedule:$stableKey:$startTs"
        val dedupeKey = "$identityKey:${scheduleStateBucket(event)}"
        val content = BraceletNotificationFormatter.schedule(snapshot, event)
        return notifyOnce(
            dedupeKey = dedupeKey,
            notificationIdentityKey = identityKey,
            content = content,
            source = "schedule",
            smallIcon = R.drawable.ic_stat_event
        )
    }

    fun notifyScheduleEventUpdate(event: Event): Boolean {
        if (!isEnabled()) return false
        val eventId = event.id ?: return false
        val stableKey = if (event.parentId > 0L) {
            "rec:${event.parentId}:${event.startTS}"
        } else {
            "single:$eventId"
        }
        val identityKey = "schedule:$stableKey:${event.startTS}"
        val dedupeKey = "$identityKey:${scheduleStateBucket(event)}"
        return notifyOnce(
            dedupeKey = dedupeKey,
            notificationIdentityKey = identityKey,
            content = BraceletNotificationFormatter.schedule(event),
            source = "schedule_update",
            smallIcon = R.drawable.ic_stat_event
        )
    }

    fun notifyWeatherWarning(sourceKey: String, title: String, contentText: String, smallIcon: Int? = null): Boolean {
        if (!isEnabled()) return false
        return notifyOnce(
            dedupeKey = "weather:warning:$sourceKey",
            notificationIdentityKey = "weather:warning:$sourceKey",
            content = BraceletNotificationFormatter.weatherWarning(title, contentText),
            source = "weather_warning",
            smallIcon = smallIcon ?: R.drawable.ic_notification_small
        )
    }

    fun notifyWeatherForecast(sourceKey: String, title: String, contentText: String, smallIcon: Int? = null): Boolean {
        if (!isEnabled()) return false
        return notifyOnce(
            dedupeKey = "weather:forecast:$sourceKey",
            notificationIdentityKey = "weather:forecast:$sourceKey",
            content = BraceletNotificationFormatter.weatherForecast(title, contentText),
            source = "weather_forecast",
            smallIcon = smallIcon ?: R.drawable.ic_notification_small
        )
    }

    fun notifyDailySummary(payload: DailySummaryPayload, isMorning: Boolean): Boolean {
        if (!isEnabled()) return false
        val bucket = if (isMorning) "morning" else "evening"
        return notifyOnce(
            dedupeKey = "daily:${payload.targetDate}:$bucket",
            notificationIdentityKey = "daily:${payload.targetDate}:$bucket",
            content = BraceletNotificationFormatter.dailySummary(payload, isMorning),
            source = "daily_summary",
            smallIcon = R.drawable.ic_notification_small
        )
    }

    fun notifyQuickMemoResult(memoId: Long, text: String, failed: Boolean = false): Boolean {
        if (!isEnabled()) return false
        val state = if (failed) "failed" else "success"
        return notifyOnce(
            dedupeKey = "quickmemo:$memoId:$state",
            notificationIdentityKey = "quickmemo:$memoId",
            content = BraceletNotificationFormatter.quickMemo(text, failed),
            source = "quickmemo_$state",
            smallIcon = R.drawable.ic_stat_quickmemo
        )
    }

    private fun notifyOnce(
        dedupeKey: String,
        notificationIdentityKey: String,
        content: BraceletNotificationContent,
        source: String,
        smallIcon: Int
    ): Boolean {
        if (!canNotify()) {
            Log.w(TAG, "bracelet notify skipped: notification permission denied key=$dedupeKey")
            return false
        }
        if (prefs.getBoolean(dedupeKey, false)) {
            Log.d(TAG, "bracelet notify deduped key=$dedupeKey")
            return false
        }

        val notificationId = NotificationIds.braceletNotification(notificationIdentityKey)
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            notificationId,
            Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(appContext, App.CHANNEL_ID_BRACELET)
            .setSmallIcon(smallIcon)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setLocalOnly(false)
            .setContentIntent(pendingIntent)
            .build()

        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, notification)
        prefs.edit().putBoolean(dedupeKey, true).apply()
        Log.d(TAG, "bracelet notify source=$source key=$dedupeKey title=${content.title} text=${content.text}")
        return true
    }

    private fun findEvent(snapshot: NotificationSnapshot): Event? {
        val app = appContext as? App ?: return null
        val eventId = snapshot.metadata["eventId"]?.toLongOrNull() ?: return null
        return runCatching {
            app.scheduleCenter.events.value.firstOrNull { it.id == eventId }
                ?: app.calendarCenter.getEvent(eventId)
        }.getOrNull()
    }

    private fun scheduleStateBucket(event: Event?): String {
        return when {
            event == null -> "unknown"
            event.isCheckedIn || event.isCompleted -> "checked"
            else -> "pending"
        }
    }

    private fun isEnabled(): Boolean =
        (appContext as? App)?.settingsQueryApi?.settings?.value?.braceletModeEnabled == true

    private fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TAG = "BraceletNotify"
        private const val PREFS_NAME = "bracelet_notification_dedupe"
    }
}
