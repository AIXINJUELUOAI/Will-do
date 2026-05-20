package com.antgskds.calendarassistant.core.center

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.MainActivity
import com.antgskds.calendarassistant.R
import com.antgskds.calendarassistant.core.content.EventTimelinePresenter
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.*
import com.antgskds.calendarassistant.service.receiver.EventActionReceiver
import com.antgskds.calendarassistant.service.notification.NotificationIds

class NotificationCenter(
    private val appContext: Context
) {
    companion object {
        private const val TAG = "NotificationCenter"
    }

    fun showStandardNotificationForEvent(event: Event, label: String = "日程开始") {
        val channelId = App.CHANNEL_ID_POPUP
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = event.id?.let { NotificationIds.standardReminder(it) }
            ?: NotificationIds.standardReminder(event.title.ifBlank { "event" })
        val tapIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            notificationId,
            tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val app = appContext as? App
        val eventActionQueryApi = app?.eventActionQueryApi
        val effectiveRuleId = eventActionQueryApi?.resolveEffectiveRuleId(
            intentRuleId = null,
            fallbackTag = event.tag,
            event = event
        ) ?: event.tag

        val actionText = eventActionQueryApi?.actionTextForRule(effectiveRuleId).orEmpty()
        val presentation = app?.notificationPresentationQueryApi?.buildPresentation(
            startTime = event.startTime,
            endTime = event.endTime,
            location = event.location,
            label = label,
            actionText = actionText
        )
        val contentText = presentation?.contentText ?: if (label.isNotEmpty()) label else "点击查看详情"

        val builder = NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle(EventTimelinePresenter.present(appContext, event).title)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (event.color.hashCode() != 0) {
            builder.setColor(event.color.hashCode())
        }

        try {
            val matched = app?.scheduleCenter?.events?.value?.find { it.id == event.id }
            val actionButton = eventActionQueryApi?.buildActionButton(effectiveRuleId, matched)
            if (actionButton != null) {
                val actionIntent = Intent(appContext, EventActionReceiver::class.java).apply {
                    action = actionButton.intentAction
                    putExtra(EventActionReceiver.EXTRA_EVENT_ID, event.idString)
                }
                val pendingAction = PendingIntent.getBroadcast(
                    appContext,
                    notificationId + 100,
                    actionIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(R.drawable.ic_notification_small, actionButton.text, pendingAction)
            }
        } catch (e: Exception) {
            Log.e(TAG, "检查事件状态失败: ${e.message}")
        }

        manager.notify(notificationId, builder.build())
    }

    fun showStandardNotification(
        eventId: String,
        title: String,
        label: String,
        eventLocation: String = "",
        eventStartTime: String = "",
        eventEndTime: String = "",
        eventTag: String = "",
        eventColor: Int = 0,
        eventRuleId: String? = null
    ) {
        val channelId = App.CHANNEL_ID_POPUP
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = NotificationIds.standardReminder(eventId)
        val tapIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            notificationId,
            tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val app = appContext as? App
        val scheduleCenter = app?.scheduleCenter
        val eventActionQueryApi = app?.eventActionQueryApi
        val matchedEvent = scheduleCenter?.events?.value?.find { it.id == eventId.toLongOrNull() }

        val effectiveRuleId = eventActionQueryApi?.resolveEffectiveRuleId(
            intentRuleId = eventRuleId,
            fallbackTag = eventTag,
            event = matchedEvent
        ) ?: eventTag

        val actionText = eventActionQueryApi?.actionTextForRule(effectiveRuleId).orEmpty()
        val presentation = app?.notificationPresentationQueryApi?.buildPresentation(
            startTime = eventStartTime,
            endTime = eventEndTime,
            location = eventLocation,
            label = label,
            actionText = actionText
        )
        val contentText = presentation?.contentText ?: if (label.isNotEmpty()) label else "点击查看详情"

        val builder = NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (eventColor != 0) {
            builder.setColor(eventColor)
        }

        try {
            val actionButton = eventActionQueryApi?.buildActionButton(effectiveRuleId, matchedEvent)
            if (actionButton != null) {
                val actionIntent = Intent(appContext, EventActionReceiver::class.java).apply {
                    action = actionButton.intentAction
                    putExtra(EventActionReceiver.EXTRA_EVENT_ID, eventId)
                }
                val pendingAction = PendingIntent.getBroadcast(
                    appContext,
                    notificationId + 100,
                    actionIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(R.drawable.ic_notification_small, actionButton.text, pendingAction)
            }
        } catch (e: Exception) {
            Log.e(TAG, "检查事件状态失败: ${e.message}")
        }

        manager.notify(notificationId, builder.build())
    }
}
