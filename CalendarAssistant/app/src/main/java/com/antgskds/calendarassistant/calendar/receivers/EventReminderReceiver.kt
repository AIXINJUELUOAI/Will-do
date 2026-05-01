package com.antgskds.calendarassistant.calendar.receivers

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.R
import com.antgskds.calendarassistant.calendar.helpers.STATE_PENDING
import com.antgskds.calendarassistant.calendar.models.Event
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class EventReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val eventId = intent?.getLongExtra(EXTRA_EVENT_ID, 0L) ?: 0L
        if (eventId == 0L) return

        val title = intent?.getStringExtra(EXTRA_TITLE).orEmpty()
        val description = intent?.getStringExtra(EXTRA_DESCRIPTION).orEmpty()
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                handleReminder(context.applicationContext, eventId, title, description)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleReminder(context: Context, eventId: Long, titleExtra: String, descriptionExtra: String) {
        val event = resolveDisplayableEvent(context, eventId)
        if (event == null) {
            NotificationManagerCompat.from(context).cancel(eventId.toInt())
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) return
        }

        ensureChannel(context)
        val title = titleExtra.ifBlank { event.title.ifBlank { context.getString(R.string.app_name) } }
        val description = descriptionExtra.ifBlank { event.description }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(description)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(eventId.toInt(), notification)
    }

    private fun resolveDisplayableEvent(context: Context, eventId: Long): Event? {
        val app = context.applicationContext as? App ?: return null
        if (app.settingsQueryApi.settings.value.isLiveCapsuleEnabled) {
            Log.d(TAG, "胶囊开启，跳过普通提醒: eventId=$eventId")
            return null
        }

        val event = app.calendarCenter.getEvent(eventId)
        if (event == null) {
            Log.d(TAG, "事件不存在，跳过普通提醒: eventId=$eventId")
            return null
        }
        if (event.archivedAt != null || event.state != STATE_PENDING || event.endTS <= System.currentTimeMillis() / 1000L) {
            Log.d(TAG, "事件已归档或非待办，跳过普通提醒: eventId=$eventId")
            return null
        }
        return event
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        )
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val EXTRA_EVENT_ID = "extra_event_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_DESCRIPTION = "extra_description"
        private const val TAG = "EventReminderReceiver"
        private const val CHANNEL_ID = "event_reminders"
        private const val CHANNEL_NAME = "Event Reminders"
    }
}
